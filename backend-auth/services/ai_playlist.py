"""Server-side AI playlist generation.

The Groq API key is configured on the server (GROQ_API_KEY env var); the
app never sees it. Suggested tracks are resolved against YouTube Music
through the public InnerTube "search" endpoint, mirroring the header
set and context shape the app's innertube module sends (this file is a
standalone Python mirror and does not touch the app's extraction code).

When GROQ_API_KEY is unset the feature reports itself as disabled, acting
as a server-side toggle for the whole AI playlist feature.
"""

import asyncio
import json
import logging
import os
import re

import httpx

logger = logging.getLogger("soundsphere-auth")

GROQ_API_KEY = os.getenv("GROQ_API_KEY", "").strip()
GROQ_MODEL = os.getenv("GROQ_MODEL", "openai/gpt-oss-120b").strip()
GROQ_URL = os.getenv("GROQ_URL", "https://api.groq.com/openai/v1/chat/completions")
GROQ_TIMEOUT = 60

_SEARCH_URL = "https://music.youtube.com/youtubei/v1/search"
_SEARCH_TIMEOUT = 12
_SEARCH_CONCURRENCY = 6

# Songs-only filter for music/search (same param the app uses).
_SONGS_PARAMS = "EgWKAQIIAWoKEAoQCRADEAA%3D"

# Artists-only filter for music/search (same param the app uses).
_ARTIST_PARAMS = "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"

# Header set replicated from the app's InnerTube client (WEB_REMIX).
_SEARCH_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) "
        "Gecko/20100101 Firefox/140.0"
    ),
    "Accept": "application/json",
    "Accept-Language": "en-US,en;q=0.9",
    "Content-Type": "application/json",
    "X-Goog-Api-Format-Version": "1",
    "X-YouTube-Client-Name": "67",
    "X-YouTube-Client-Version": "1.20260114.03.00",
    "X-Origin": "https://music.youtube.com",
    "Referer": "https://music.youtube.com/",
}

_MAX_HISTORY = 40
_MAX_SEARCH_RETRIES = 3

_transport = httpx.AsyncHTTPTransport(retries=0, http2=True)
_client = httpx.AsyncClient(
    timeout=httpx.Timeout(_SEARCH_TIMEOUT),
    limits=httpx.Limits(max_connections=16, max_keepalive_connections=8),
)
_search_semaphore = asyncio.Semaphore(_SEARCH_CONCURRENCY)


def ai_enabled() -> bool:
    """Server-side toggle: the feature only exists when a key is configured."""
    return bool(GROQ_API_KEY)


def _build_context() -> dict:
    return {
        "context": {
            "client": {
                "clientName": "WEB_REMIX",
                "clientVersion": "1.20260114.03.00",
                "gl": "US",
                "hl": "en",
            }
        }
    }


# ===== LLM suggestions =====


async def _llm_suggestions(
    prompt: str, history: list[dict], count: int, artist: str | None = None, mix_similar: bool = False
) -> list[dict]:
    """Ask Groq for `count` (title, artist) song suggestions."""
    history_block = "\n".join(
        f"- {h.get('title', '')} / {h.get('artist', '')}" for h in history[: _MAX_HISTORY]
    )
    constraints = []
    if artist:
        if mix_similar:
            constraints.append(
                f"The user is asking about the artist '{artist}'. "
                f"Include songs by '{artist}' AND songs by other artists with a "
                f"similar style. Do not reply with '{artist}' trivia or biography."
            )
        else:
            constraints.append(
                f"The user is asking about the artist '{artist}'. "
                f"Every single track must be by '{artist}' (their own releases, "
                f"not covers by other artists)."
            )
    constraints.append(
        "Reply with ONLY a JSON object of the form "
        '{"tracks": [{"title": "...", "artist": "..."}, ...]} with exactly '
        f"{count} tracks. Use the canonical artist name. No markdown, no prose."
    )
    system_prompt = (
        "You are a music curation assistant for a YouTube Music client. "
        "Suggest real, well-known songs matching the user's request. "
        + " ".join(constraints)
    )
    user_prompt = f"User request: {prompt}\nRecent listening history for taste:\n{history_block or '(none)'}"

    payload = {
        "model": GROQ_MODEL,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        "temperature": 0.7,
        "max_tokens": count * 64 + 512,
        "response_format": {"type": "json_object"},
    }

    async with httpx.AsyncClient(timeout=GROQ_TIMEOUT) as client:
        response = await client.post(
            GROQ_URL,
            headers={
                "Authorization": f"Bearer {GROQ_API_KEY}",
                "Content-Type": "application/json",
            },
            json=payload,
        )
        if response.status_code != 200:
            raise RuntimeError(f"Groq returned HTTP {response.status_code}: {response.text[:200]}")

    content = response.json().get("choices", [{}])[0].get("message", {}).get("content", "")
    return _parse_suggestions(content, count)


def _parse_suggestions(content: str, count: int) -> list[dict]:
    """Best-effort JSON extraction with markdown fence fallback."""
    candidates = []
    raw = content.strip()
    try:
        data = json.loads(raw)
    except (json.JSONDecodeError, ValueError):
        fenced = re.search(r"```(?:json)?\s*(.*?)```", raw, re.DOTALL)
        text = fenced.group(1) if fenced else raw
        try:
            data = json.loads(text)
        except (json.JSONDecodeError, ValueError):
            start, end = text.find("{"), text.rfind("}")
            if start == -1 or end <= start:
                return []
            try:
                data = json.loads(text[start : end + 1])
            except (json.JSONDecodeError, ValueError):
                return []
    if isinstance(data, dict):
        tracks = data.get("tracks")
        if isinstance(tracks, list):
            data = tracks
    if not isinstance(data, list):
        return []
    for item in data:
        if not isinstance(item, dict):
            continue
        title = str(item.get("title") or "").strip()
        artist = str(item.get("artist") or "").strip()
        if title and artist:
            candidates.append({"title": title, "artist": artist})
    return candidates[:count]


# ===== InnerTube search mirror =====


def _duration_seconds(text: str | None) -> int:
    """'4:32' -> 272, '1:02:03' -> 3723, anything else -> 0."""
    if not text:
        return 0
    try:
        parts = [int(p) for p in text.strip().split(":")]
    except ValueError:
        return 0
    if len(parts) == 1:
        return parts[0]
    if len(parts) == 2:
        return parts[0] * 60 + parts[1]
    if len(parts) == 3:
        return parts[0] * 3600 + parts[1] * 60 + parts[2]
    return 0


def _runs_text(node: dict | None) -> str:
    """Join a flexColumn 'text' node's runs into a plain string."""
    if not isinstance(node, dict):
        return ""
    runs = node.get("runs")
    if not isinstance(runs, list):
        return ""
    return "".join(str(run.get("text") or "") for run in runs if isinstance(run, dict))


def _run_text(node: dict | None) -> str:
    """First run text of a flexColumn 'text' node (the title line)."""
    if not isinstance(node, dict):
        return ""
    runs = node.get("runs")
    if not isinstance(runs, list) or not runs:
        return ""
    first = runs[0] if isinstance(runs[0], dict) else {}
    return str(first.get("text") or "")


def _all_runs(node: dict | None) -> list[dict]:
    if not isinstance(node, dict):
        return []
    runs = node.get("runs")
    return [run for run in runs if isinstance(run, dict)] if isinstance(runs, list) else []


_DURATION_RE = re.compile(r"\d{1,2}[:.,]\d{2}(?:[:.,]\d{2})?")
_YEAR_RE = re.compile(r"(?:19|20)\d{2}")
_VIEWS_RE = re.compile(r"\d[\d.,]*[kmb]?\s*(?:views?|plays?|likes?|subscribers?)")
_METADATA_LABELS = {"song", "video", "single", "album", "episode", "playlist", "podcast"}


def _is_metadata_text(text: str) -> bool:
    """True for metadata bits ('Song', '3:54', '5.2M views') vs artist names."""
    value = text.strip()
    lower = value.lower().replace("\u00a0", " ")
    return (
        bool(_DURATION_RE.match(value))
        or bool(_YEAR_RE.match(value))
        or lower in _METADATA_LABELS
        or "monthly audience" in lower
        or bool(_VIEWS_RE.match(value))
    )


def _is_artist_run(run: dict) -> bool:
    """True when a run links to an artist page (UC... browse id or ARTIST page type)."""
    endpoint = run.get("navigationEndpoint")
    if not isinstance(endpoint, dict):
        return False
    browse = endpoint.get("browseEndpoint")
    if not isinstance(browse, dict):
        return False
    if str(browse.get("browseId") or "").startswith("UC"):
        return True
    config = browse.get("browseEndpointContextSupportedConfigs")
    music = (config or {}).get("browseEndpointContextMusicConfig") if isinstance(config, dict) else None
    return bool(isinstance(music, dict) and music.get("pageType") == "MUSIC_PAGE_TYPE_ARTIST")


def _flex_column(item: dict, index: int) -> dict:
    flex = item.get("flexColumns") or []
    if index >= len(flex) or not isinstance(flex[index], dict):
        return {}
    return flex[index].get("musicResponsiveListItemFlexColumnRenderer") or {}


def _line_2_artist(item: dict) -> str:
    """Artist name from the secondary line: artist links first, then bullets."""
    runs = _all_runs(_flex_column(item, 1).get("text"))
    for run in runs:
        if _is_artist_run(run):
            return str(run.get("text") or "").strip()
    for part in _runs_text(_flex_column(item, 1).get("text")).split("•"):
        stripped = part.strip()
        if stripped and not _is_metadata_text(stripped):
            return stripped
    return ""


def _video_id(item: dict) -> str | None:
    """videoId mirroring the app: playlistItemData, title-column link, or play overlay."""
    pid = item.get("playlistItemData")
    if isinstance(pid, dict) and pid.get("videoId"):
        return pid["videoId"]
    for run in _all_runs(_flex_column(item, 0).get("text")):
        endpoint = run.get("navigationEndpoint")
        if not isinstance(endpoint, dict):
            continue
        watch = endpoint.get("watchEndpoint")
        if isinstance(watch, dict) and watch.get("videoId"):
            return watch["videoId"]
    overlay = item.get("overlay")
    if isinstance(overlay, dict):
        content = (overlay.get("musicItemThumbnailOverlayRenderer") or {}).get("content") or {}
        play = content.get("musicPlayButtonRenderer") or {}
        watch = (play.get("playNavigationEndpoint") or {}).get("watchEndpoint") if isinstance(play, dict) else None
        if isinstance(watch, dict) and watch.get("videoId"):
            return watch["videoId"]
    return None


def _is_song_row(item: dict) -> bool:
    """True when the row plays a track rather than linking to a browse page."""
    endpoint = item.get("navigationEndpoint")
    if endpoint is None:
        return True
    if not isinstance(endpoint, dict):
        return False
    if endpoint.get("watchEndpoint") or endpoint.get("watchPlaylistEndpoint"):
        return True
    overlay = item.get("overlay")
    if isinstance(overlay, dict):
        content = (overlay.get("musicItemThumbnailOverlayRenderer") or {}).get("content") or {}
        play = content.get("musicPlayButtonRenderer") or {}
        watch = (play.get("playNavigationEndpoint") or {}).get("watchEndpoint") if isinstance(play, dict) else None
        if isinstance(watch, dict) and watch.get("videoId"):
            return True
    return False


def _duration_seconds_item(item: dict) -> int:
    """Duration from the fixed countdown column, then the secondary line."""
    for col in item.get("fixedColumns") or []:
        if not isinstance(col, dict):
            continue
        text = (col.get("musicResponsiveListItemFixedColumnRenderer") or {}).get("text")
        secs = _duration_seconds(_runs_text(text))
        if secs:
            return secs
    for run in _all_runs(_flex_column(item, 1).get("text")):
        match = _DURATION_RE.match(str(run.get("text") or "").strip())
        if match:
            return _duration_seconds(match.group(0))
    return 0


def _thumbnail_url(item: dict, video_id: str) -> str:
    thumb = item.get("thumbnail")
    if isinstance(thumb, dict):
        renderer = thumb.get("musicThumbnailRenderer") or {}
        thumbs = (renderer.get("thumbnail") or {}).get("thumbnails") or []
        if thumbs:
            url = str(thumbs[-1].get("url") or "")
            if url.strip():
                return url
    return f"https://i.ytimg.com/vi/{video_id}/hqdefault.jpg"


def _parse_search_payload(payload: dict, expected_artist: str) -> list[dict]:
    """Extract playable track hits from a music/search response."""
    expected_artist = expected_artist.strip().lower()
    hits: list[dict] = []
    seen: set[str] = set()

    def visit(node) -> None:
        if isinstance(node, list):
            for value in node:
                visit(value)
            return
        if not isinstance(node, dict):
            return
        renderer = node.get("musicResponsiveListItemRenderer")
        if isinstance(renderer, dict):
            video_id = _video_id(renderer)
            if not video_id or video_id in seen:
                return
            title = _run_text(_flex_column(renderer, 0).get("text"))
            if not title:
                return
            seen.add(video_id)
            artist = _line_2_artist(renderer)
            hits.append(
                {
                    "id": video_id,
                    "title": title,
                    "artist": artist,
                    "album": _run_text(_flex_column(renderer, 2).get("text")) or None,
                    "duration": _duration_seconds_item(renderer),
                    "artwork_url": _thumbnail_url(renderer, video_id),
                    "matched_artist": bool(expected_artist) and expected_artist in artist.lower(),
                    "song_row": _is_song_row(renderer),
                }
            )
            return
        for value in node.values():
            visit(value)

    visit(payload)
    # Prefer rows whose artist matches the query, then playable song rows.
    hits.sort(
        key=lambda h: (not h.get("song_row", False), not h.get("matched_artist", False))
    )
    return hits


async def _search_track(title: str, artist: str) -> dict | None:
    """Resolve one (title, artist) to a YouTube Music video via music/search."""
    query = f"{title} {artist}".strip() or title
    body = _build_context()
    body["query"] = query
    body["params"] = _SONGS_PARAMS

    for attempt in range(_MAX_SEARCH_RETRIES):
        try:
            async with _search_semaphore:
                response = await _client.post(
                    _SEARCH_URL,
                    headers=_SEARCH_HEADERS,
                    params={"prettyPrint": False},
                    json=body,
                )
        except (httpx.HTTPError, asyncio.TimeoutError) as exc:
            logger.warning("AI search '%s' attempt %d failed: %s", query, attempt, exc)
            await asyncio.sleep(0.5 * (attempt + 1))
            continue
        if response.status_code == 200:
            try:
                payload = response.json()
            except ValueError:
                logger.warning("AI search '%s' returned non-JSON body", query)
                continue
            hits = _parse_search_payload(payload, artist)
            if hits:
                return hits[0]
        elif response.status_code in (403, 429):
            logger.warning("AI search '%s' throttled (HTTP %d)", query, response.status_code)
            await asyncio.sleep(1.0 * (attempt + 1))
            continue
        else:
            logger.warning("AI search '%s' failed (HTTP %d)", query, response.status_code)
            return None
    return None


def _parse_artist_payload(payload: dict) -> dict | None:
    """Extract the top artist hit from an artists-filtered search response."""
    best: dict | None = None
    best_rank = -1
    rank = 0

    def visit(node) -> None:
        nonlocal best, best_rank, rank
        if isinstance(node, list):
            for value in node:
                visit(value)
            return
        if not isinstance(node, dict):
            return
        renderer = node.get("musicResponsiveListItemRenderer")
        if isinstance(renderer, dict):
            rank += 1
            endpoint = renderer.get("navigationEndpoint")
            browse = (endpoint or {}).get("browseEndpoint") if isinstance(endpoint, dict) else None
            browse_id = str((browse or {}).get("browseId") or "")
            if (best_rank == -1 or rank < best_rank) and browse_id.startswith("UC"):
                name = _run_text(_flex_column(renderer, 0).get("text"))
                if name:
                    best = {"name": name.strip(), "browse_id": browse_id}
                    best_rank = rank
            return
        for value in node.values():
            visit(value)

    visit(payload)
    return best


async def detect_artist(query: str) -> dict | None:
    """Detect whether the query names a specific artist, via artists-filtered search."""
    body = _build_context()
    body["query"] = query
    body["params"] = _ARTIST_PARAMS

    for attempt in range(_MAX_SEARCH_RETRIES):
        try:
            async with _search_semaphore:
                response = await _client.post(
                    _SEARCH_URL,
                    headers=_SEARCH_HEADERS,
                    params={"prettyPrint": False},
                    json=body,
                )
        except (httpx.HTTPError, asyncio.TimeoutError) as exc:
            logger.warning("AI artist detect '%s' attempt %d failed: %s", query, attempt, exc)
            await asyncio.sleep(0.5 * (attempt + 1))
            continue
        if response.status_code == 200:
            try:
                payload = response.json()
            except ValueError:
                logger.warning("AI artist detect '%s' returned non-JSON body", query)
                continue
            return _parse_artist_payload(payload)
        elif response.status_code in (403, 429):
            logger.warning("AI artist detect '%s' throttled (HTTP %d)", query, response.status_code)
            await asyncio.sleep(1.0 * (attempt + 1))
            continue
        else:
            logger.warning("AI artist detect '%s' failed (HTTP %d)", query, response.status_code)
            return None
    return None


# ===== Public API =====


async def generate_playlist(
    prompt: str,
    history: list[dict],
    count: int,
    artist: str | None = None,
    mix_similar: bool = False,
) -> list[dict]:
    """Full pipeline: LLM suggestions -> Innertube resolution."""
    if not ai_enabled():
        raise RuntimeError("AI playlist generation is not enabled")

    suggestions = await _llm_suggestions(prompt, history, count, artist, mix_similar)
    results: list[dict] = []
    seen: set[str] = set()
    for suggestion in suggestions:
        track = await _search_track(suggestion["title"], suggestion["artist"])
        if track is None or track["id"] in seen:
            continue
        # "Only this artist" mode: drop hits whose resolved artist doesn't match,
        # so covers/mis-tagged uploads can't sneak in.
        if artist and not mix_similar and not track.get("matched_artist", False):
            continue
        seen.add(track["id"])
        track.pop("matched_artist", None)
        track.pop("song_row", None)
        results.append(track)
        if len(results) >= count:
            break
    return results