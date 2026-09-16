"""tickethub.ng — events from homepage React Flight data.

The /discover listing is client-rendered, but the homepage embeds full
event objects (id/title/slug/category/dates/venue/city/images) in
initialRails arrays. Canonical event URL is https://tickethub.ng/{slug}
(verified 2026-09-15: /events/{slug} 308-redirects to /{slug}, 200).
"""

from __future__ import annotations

import re

from ..common.http import fetch_text
from ..common.models import EventInfo
from ._flight import decode_flight, first_field, split_segments

BASE = "https://tickethub.ng"

# {"id":12288,"title":... with optional Flight backslash-escapes.
_EVENT_MARKER = re.compile(r'\{\\?"id\\?":\d+,\\?"title\\?":')


def parse_homepage(html: str) -> list[EventInfo]:
    blob = decode_flight(html)
    events: dict[str, EventInfo] = {}
    for segment in split_segments(blob, _EVENT_MARKER):
        # Skip non-event id/title pairs (nav, footer) — real events carry a slug.
        slug = first_field(segment, "slug")
        title = first_field(segment, "title")
        if not slug or not title:
            continue
        events[slug] = EventInfo(
            source="tickethub",
            source_id=first_field(segment, "id"),
            title=title,
            url=f"{BASE}/{slug}",
            start=first_field(segment, "start_date") or first_field(segment, "date"),
            end=first_field(segment, "end_date"),
            venue=first_field(segment, "venue"),
            city=first_field(segment, "city"),
            # No country field in tickethub Flight data — leave unknown
            # rather than hardcoding. Per-event city/venue still shown.
            country="",
            image=first_field(segment, "image_card") or first_field(segment, "image"),
            category=first_field(segment, "category"),
        )
    return list(events.values())


def fetch_events() -> list[EventInfo]:
    return parse_homepage(fetch_text(f"{BASE}/"))


def music_events(events: list[EventInfo]) -> list[EventInfo]:
    """Concert-grade subset: Concerts/Shows/Parties/Raves categories."""
    wanted = {"concert", "show", "part", "rave", "festival", "music", "tour", "live"}
    return [e for e in events if any(w in e.category.lower() for w in wanted)]
