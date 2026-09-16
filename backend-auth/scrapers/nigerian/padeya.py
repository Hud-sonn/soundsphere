"""padeya.com — events from the /events listing Flight data + detail pages.

Verified 2026-09-15:
- /events embeds an initialEvents array (id/title/slug/description,
  start/end_datetime, timezone, venue_name, address, city/state/country,
  lat/lng) in React Flight data.
- /events/{slug} is server-rendered (200) and carries schema.org Event
  JSON-LD plus a breadcrumb (Home > Events > city > category > event).
"""

from __future__ import annotations

import re

from ..bandsintown.scraper import extract_schema_events
from ..common.http import fetch_text
from ..common.models import EventInfo
from ._flight import decode_flight, first_field, split_segments

BASE = "https://padeya.com"

# {"id":"<uuid>","title":... with optional Flight backslash-escapes.
_EVENT_MARKER = re.compile(r'\{\\?"id\\?":\\?"[0-9a-fA-F-]{8,}\\?",\\?"title\\?":')

_CATEGORY_RE = re.compile(r"/events/c/([a-z0-9-]+)")
_CITY_RE = re.compile(r"/events/city/([a-z0-9-]+)")


def parse_listing(html: str) -> list[EventInfo]:
    blob = decode_flight(html)
    events: dict[str, EventInfo] = {}
    for segment in split_segments(blob, _EVENT_MARKER):
        slug = first_field(segment, "slug")
        title = first_field(segment, "title")
        if not slug or not title:
            continue
        events[slug] = EventInfo(
            source="padeya",
            source_id=first_field(segment, "id"),
            title=title,
            url=f"{BASE}/events/{slug}",
            start=first_field(segment, "start_datetime"),
            end=first_field(segment, "end_datetime"),
            venue=first_field(segment, "venue_name"),
            city=first_field(segment, "city"),
            # Country comes from listing data when present, else unknown —
            # never hardcoded (users are in different countries).
            country=first_field(segment, "country") or "",
            description=first_field(segment, "description")
            or first_field(segment, "short_tagline"),
        )
    return list(events.values())


def fetch_event_detail(slug: str) -> EventInfo | None:
    """Enrich one event from its server-rendered page (JSON-LD + breadcrumb)."""
    html = fetch_text(f"{BASE}/events/{slug}")
    found = extract_schema_events(html, source="padeya")
    event = found[0] if found else EventInfo(source="padeya", url=f"{BASE}/events/{slug}")
    crumbs = re.findall(r'"item":"https?://padeya\.com(/events/(?:c|city)/[a-z0-9-]+)"', html)
    for crumb in crumbs:
        if m := _CATEGORY_RE.search(crumb):
            event.category = m.group(1)
        elif m := _CITY_RE.search(crumb):
            event.city = m.group(1)
    if not event.title:
        event.title = slug.replace("-", " ")
    return event


def fetch_events() -> list[EventInfo]:
    return parse_listing(fetch_text(f"{BASE}/events"))


def matching(events: list[EventInfo], artist_names: list[str]) -> list[EventInfo]:
    """Events whose title/description/venue mention any of the artists."""
    names = [a.lower() for a in artist_names if a]
    return [
        e
        for e in events
        if any(n in f"{e.title} {e.description} {e.venue}".lower() for n in names)
    ]
