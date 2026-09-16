"""International events via Bandsintown REST API.

Verified 2026-09-16: rest.bandsintown.com works with public app_id
'js_api_client' (Squarespace integration). Returns rich JSON with venue,
lineup, ticket links, coordinates. No API key required.

Old approach (website HTML scraping) is dead — Cloudflare walls
www.bandsintown.com for plain HTTP (403). This module uses the REST API.
"""

from __future__ import annotations

import time
import urllib.parse

from ..common.http import BotBlockedError, client, fetch_json
from ..common.models import EventInfo

# Public app_id used by Bandsintown's Squarespace integration.
# Verified working 2026-09-16 with 25+ artists (Nigerian + global).
_APP_ID = "js_api_client"
_BASE = "https://rest.bandsintown.com"

# Rate limit: 200ms between requests to avoid throttling.
_REQ_DELAY = 0.2


def fetch_artist_events(artist_name: str) -> list[EventInfo]:
    """Fetch upcoming events for an artist from Bandsintown REST API.

    Returns a list of EventInfo with lineup, ticket_url, and sold_out
    populated. Raises BotBlockedError if the API returns 403/429.
    """
    slug = urllib.parse.quote(artist_name.strip())
    url = f"{_BASE}/artists/{slug}/events/?app_id={_APP_ID}"
    try:
        data = fetch_json(url)
    except Exception as e:
        msg = str(e).lower()
        if "403" in msg or "429" in msg:
            raise BotBlockedError(f"Bandsintown API blocked: {e}") from e
        raise

    if not isinstance(data, list):
        return []

    events: list[EventInfo] = []
    for item in data:
        if not isinstance(item, dict):
            continue
        venue = item.get("venue") or {}
        offers = item.get("offers") or []
        ticket_url = ""
        sold_out = False
        for offer in offers:
            if isinstance(offer, dict):
                if offer.get("status") == "sold_out":
                    sold_out = True
                if not ticket_url and offer.get("url"):
                    ticket_url = str(offer["url"])

        # Prefer lineup, fall back to nested artist.name.
        # Artist images live on the nested artist dict (image_url/thumb_url) —
        # attach the large one so events render artwork downstream.
        lineup = item.get("lineup") or []
        nested = item.get("artist") or {}
        if not lineup and nested.get("name"):
            lineup = [nested["name"]]
        image = str(nested.get("image_url") or nested.get("thumb_url") or "")

        events.append(
            EventInfo(
                source="bandsintown",
                source_id=str(item.get("id") or ""),
                title=str(item.get("title") or "").strip()
                or str(item.get("description") or "").strip(),
                url=str(item.get("url") or "").split("?")[0],  # strip tracking params
                start=str(item.get("datetime") or item.get("starts_at") or ""),
                end=str(item.get("ends_at") or ""),
                venue=str(venue.get("name") or "").strip(),
                city=str(venue.get("city") or "").strip(),
                country=str(venue.get("country") or "").strip(),
                description=str(item.get("description") or "").strip(),
                image=image,
                lineup=[str(a).strip() for a in lineup if a],
                ticket_url=ticket_url.split("?")[0],  # strip tracking params
                sold_out=sold_out,
            )
        )
    return events


def fetch_artists_events(
    artist_names: list[str], delay: float = _REQ_DELAY
) -> list[EventInfo]:
    """Fetch events for multiple artists with rate limiting.

    Deduplicates by source_id. Returns all events sorted by start time.
    """
    seen: dict[str, EventInfo] = {}
    for name in artist_names:
        if not name or not name.strip():
            continue
        try:
            for event in fetch_artist_events(name):
                if event.source_id and event.source_id not in seen:
                    seen[event.source_id] = event
        except BotBlockedError:
            pass  # skip blocked artists, continue with others
        if delay > 0:
            time.sleep(delay)
    return sorted(seen.values(), key=lambda e: e.start or "")


# Keep extract_schema_events for padeya.com compatibility.
# Padeya detail pages carry schema.org Event JSON-LD that we extract.
import json  # noqa: E402
import re  # noqa: E402

_LD_JSON_RE = re.compile(
    r'<script[^>]+type="application/ld\+json"[^>]*>(.*?)</script>',
    re.IGNORECASE | re.DOTALL,
)


def _as_list(value) -> list:
    if value is None:
        return []
    return value if isinstance(value, list) else [value]


def extract_schema_events(html: str, source: str = "padeya") -> list[EventInfo]:
    """Pull every schema.org Event out of a page's JSON-LD blocks.

    Used for padeya.com detail pages. Not for Bandsintown anymore.
    """
    events: list[EventInfo] = []
    for match in _LD_JSON_RE.finditer(html):
        try:
            data = json.loads(match.group(1).strip())
        except (json.JSONDecodeError, ValueError):
            continue
        nodes: list[dict] = []
        for item in _as_list(data):
            if not isinstance(item, dict):
                continue
            if "@graph" in item:
                nodes.extend(n for n in _as_list(item["@graph"]) if isinstance(n, dict))
            else:
                nodes.append(item)
        for node in nodes:
            if node.get("@type") != "Event":
                continue
            location = node.get("location") or {}
            if isinstance(location, list):
                location = location[0] if location else {}
            address = (location.get("address") if isinstance(location, dict) else {}) or {}
            if isinstance(address, str):
                address = {"addressLocality": address}
            events.append(
                EventInfo(
                    source=source,
                    title=str(node.get("name") or "").strip(),
                    url=str(node.get("url") or "").strip(),
                    start=str(node.get("startDate") or ""),
                    end=str(node.get("endDate") or ""),
                    venue=str(location.get("name") or "").strip(),
                    city=str(address.get("addressLocality") or "").strip(),
                    country=str(address.get("addressCountry") or "").strip(),
                    artists=[a for a in _lineup(node.get("performer")) if a],
                )
            )
    return [e for e in events if e.title]


def _lineup(performer) -> list[str]:
    names: list[str] = []
    for item in _as_list(performer):
        if isinstance(item, str):
            names.append(item.strip())
        elif isinstance(item, dict) and item.get("name"):
            names.append(str(item["name"]).strip())
    return names
