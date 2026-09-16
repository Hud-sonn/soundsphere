"""Smoke-test every scraper against the live web.

Run from backend-auth/:
    uv run --with httpx python -m scrapers.test_scrapers

Per-source status: OK / BLOCKED (bot wall) / FAIL.
Exit code nonzero only on FAIL so cron can tell "site changed its
markup" apart from "site walls bots".
"""

from __future__ import annotations

import sys
import traceback

from .bandsintown.scraper import extract_schema_events, fetch_artist_events
from .common.http import BotBlockedError, fetch_text
from .nigerian import padeya, tickethub
from .releases import deezer, itunes

results: list[tuple[str, str, str]] = []


def check(name: str, func) -> None:
    try:
        detail = func()
    except BotBlockedError as e:
        results.append((name, "BLOCKED", str(e)))
    except Exception as e:  # noqa: BLE001 — smoke test reports, doesn't raise
        results.append((name, "FAIL", f"{type(e).__name__}: {e}"))
        traceback.print_exc()
    else:
        results.append((name, "OK", detail or ""))


def t_tickethub() -> str:
    events = tickethub.fetch_events()
    assert events, "no events parsed"
    music = tickethub.music_events(events)
    first = events[0]
    assert first.title and first.url.startswith("https://tickethub.ng/"), first
    return f"{len(events)} events ({len(music)} music), e.g. {first.title} @ {first.city}"


def t_padeya_listing() -> str:
    events = padeya.fetch_events()
    assert events, "no events parsed"
    first = events[0]
    assert first.title and first.start, first
    return f"{len(events)} events, e.g. {first.title} ({first.start})"


def t_padeya_detail() -> str:
    event = padeya.fetch_event_detail("oyo-kopa-pop-party")
    assert event and event.title, "detail parse failed"
    return f"{event.title} @ {event.venue or event.city} [{event.category}]"


def t_schema_extractor() -> str:
    html = fetch_text("https://padeya.com/events/oyo-kopa-pop-party")
    found = extract_schema_events(html, source="padeya")
    assert found and found[0].start, "no schema.org Events found"
    return f"{len(found)} schema.org Events, first: {found[0].title}"


def t_bandsintown_api() -> str:
    events = fetch_artist_events("Burna Boy")
    assert events, "no events returned from Bandsintown API"
    first = events[0]
    assert first.source_id and first.venue, first
    assert first.start, f"missing start: {first}"
    assert first.image.startswith("https://photos.bandsintown.com/"), first
    return (
        f"{len(events)} event(s), e.g. {first.title or first.description} "
        f"@ {first.venue}, {first.city} {first.country} ({first.start})"
    )


def t_itunes() -> str:
    rels = itunes.recent_releases("Ayra Starr", since="2024-01-01")
    assert rels, "no releases parsed"
    types = {r.album_type for r in rels}
    return f"{len(rels)} releases since 2024, types={sorted(types)}, latest={rels[0].title}"


def t_deezer() -> str:
    rels = deezer.recent_releases("Burna Boy", since="2024-01-01")
    assert rels, "no releases parsed"
    return f"{len(rels)} releases since 2024, latest={rels[0].title} [{rels[0].album_type}]"


def main() -> int:
    check("tickethub", t_tickethub)
    check("padeya-listing", t_padeya_listing)
    check("padeya-detail", t_padeya_detail)
    check("schema-extractor", t_schema_extractor)
    check("bandsintown", t_bandsintown_api)
    check("itunes", t_itunes)
    check("deezer", t_deezer)
    print(f"{'source':<16}{'status':<9}detail")
    failed = 0
    for name, status, detail in results:
        print(f"{name:<16}{status:<9}{detail}")
        failed += status == "FAIL"
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
