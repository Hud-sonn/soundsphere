"""Server-side AI playlist generation.

The Groq API key is configured on the server (GROQ_API_KEY env var); the
app never sees it. Suggested tracks are resolved against YouTube Music
through the public InnerTube "music/search" endpoint, mirroring the header
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
GROQ_MODEL = os.getenv("GROQ_MODEL", "llama-3.3-70b-versatile").strip()
GROQ_URL = os.getenv("GROQ_URL", "https://api.groq.com/openai/v1/chat/completions")
GROQ_TIMEOUT = 60

_SEARCH_URL = "https://music.youtube.com/youtubei/v1/music/search"
_SEARCH_TIMEOUT = 12
_SEARCH_CONCURRENCY = 6

# Songs-only filter for music/search (same param the app uses).
_SONGS_PARAMS = "EgWKAQIIAWoKEAoQCRADEAA%3D"

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


async def _llm_suggestions(prompt: str, history: list[dict], count: int) -> list[dict]:
    """Ask Groq for `count` (title, artist) song suggestions."""
    history_block = "\n".join(
        f"- {h.get('title', '')} / {h.get('artist', '')}" for h in history[: _MAX_HISTORY]
    )
    system_prompt = (
        "You are a music curation assistant for a YouTube Music client. "
        "Suggest real, well-known songs matching the user's request. "
        "Reply with ONLY a JSON object of the form "
        '{"tracks": [{"title": "...", "artist": "..."}, ...]} with exactly '
        f"{count} tracks. Use the canonical artist name. No markdown, no prose."
    )
    user_prompt = f"User request: {prompt}\nRecent listening history for taste:\n{history_block or '(none)'}"

    payload = {
        "model": GROQ_MODEL,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        "temperature": 0.7,
        "max_tokens": count * 40 + 256,
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


def _line_2_parts(item: dict) -> list[str]:
    """Artist • album line from flexColumns; splits on the bullet separator."""
    flex = item.get("flexColumns") or []
    if len(flex) >= 2:
        col = flex[1].get("musicResponsiveListItemFlexColumnRenderer") or {}
        return [p.strip() for p in _runs_text(col.get("text")).split("•") if p.strip()]
    return []


def _video_id(item: dict) -> str | None:
    pid = item.get("playlistItemData")
    if isinstance(pid, dict) and pid.get("videoId"):
        return pid["videoId"]
    endpoint = item.get("navigationEndpoint")
    if isinstance(endpoint, dict) and endpoint.get("watchEndpoint"):
        vid = endpoint["watchEndpoint"].get("videoId")
        if vid:
            return vid
    return None


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
    """Extract song-style video hits from a music/search response."""
    expected_artist = expected_artist.strip().lower()
    hits: list[dict] = []
    seen: set[str] = set()

    def visit(node: dict) -> None:
        if not isinstance(node, dict):
            return
        renderer = node.get("musicResponsiveListItemRenderer")
        if isinstance(renderer, dict):
            video_id = _video_id(renderer)
            if not video_id or video_id in seen:
                return
            # Skip non-song rows (artists/albums/channels carry no videoId,
            # so reaching here means it is a song or plain video).
            seen.add(video_id)
            parts = _line_2_parts(renderer)
            artist = parts[0] if parts else ""
            album = parts[1] if len(parts) > 1 else None
            fixed = renderer.get("fixedColumns") or []
            duration = 0
            for col in fixed:
                text = _runs_text(
                    (col.get("musicResponsiveListItemFixedColumnRenderer") or {}).get("text")
                )
                secs = _duration_seconds(text)
                if secs:
                    duration = secs
                    break
            title = _runs_text(
                ((renderer.get("flexColumns") or [{}])[0]
                    .get("musicResponsiveListItemFlexColumnRenderer") or {})
                .get("text")
            )
            if not title:
                return
            # A better match contains the searched artist name.
            matched = bool(expected_artist) and expected_artist in artist.lower()
            hits.append(
                {
                    "id": video_id,
                    "title": title,
                    "artist": artist,
                    "album": album,
                    "duration": duration,
                    "artwork_url": _thumbnail_url(renderer, video_id),
                    "matched_artist": matched,
                }
            )
            return
        for value in node.values():
            if isinstance(value, (dict, list)):
                visit(value)

    visit(payload)
    # Prefer artist-matched hits, keep the rest as fallback.
    hits.sort(key=lambda h: (not h.pop("matched_artist"),))
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
                hit = hits[0]
                hit.pop("matched_artist", None)
                return hit
        elif response.status_code in (403, 429):
            logger.warning("AI search '%s' throttled (HTTP %d)", query, response.status_code)
            await asyncio.sleep(1.0 * (attempt + 1))
            continue
        else:
            logger.warning("AI search '%s' failed (HTTP %d)", query, response.status_code)
            return None
    return None


# ===== Public API =====


async def generate_playlist(prompt: str, history: list[dict], count: int) -> list[dict]:
    """Full pipeline: LLM suggestions -> Innertube resolution."""
    if not ai_enabled():
        raise RuntimeError("AI playlist generation is not enabled")

    suggestions = await _llm_suggestions(prompt, history, count)
    results: list[dict] = []
    seen: set[str] = set()
    for suggestion in suggestions:
        track = await _search_track(suggestion["title"], suggestion["artist"])
        if track is None or track["id"] in seen:
            continue
        seen.add(track["id"])
        results.append(track)
        if len(results) >= count:
            break
    return results