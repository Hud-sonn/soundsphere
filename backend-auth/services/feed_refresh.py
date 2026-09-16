"""Feed cache refresh — the cron job that populates release + event caches.

Reads unique artist names from followed_artists, fetches from external
sources (iTunes, Deezer, Bandsintown, tickethub), deduplicates, and
upserts into artist_releases_cache / artist_events_cache.

Runs every 12 hours via asyncio background task in main.py.
Can also be triggered manually via POST /internal/refresh-feed.
"""

import logging
import time
from datetime import datetime, timezone, timedelta

from db.supabase import get_supabase

logger = logging.getLogger("soundsphere-auth")

# Rate limit: 200ms between external API calls.
_REQ_DELAY = 0.2


def _get_unique_artists(db) -> list[str]:
    """Get all unique artist_name values from followed_artists."""
    result = db.table("followed_artists").select("artist_name").execute()
    rows = result.data or []
    names = list({r["artist_name"] for r in rows if r.get("artist_name")})
    logger.info("Feed refresh: %d unique artists to fetch", len(names))
    return names


def _fetch_releases(artist_names: list[str]) -> list[dict]:
    """Fetch releases from iTunes + Deezer for a list of artists."""
    from scrapers.releases import itunes, deezer

    all_releases: dict[str, dict] = {}  # key = f"{source}:{source_id}"

    for name in artist_names:
        # iTunes
        try:
            for rel in itunes.search_artist_releases(name, limit=50):
                key = f"itunes:{rel.source_id}"
                if key not in all_releases:
                    all_releases[key] = {
                        "artist_name": name,
                        "source": "itunes",
                        "source_id": rel.source_id,
                        "title": rel.title,
                        "release_date": rel.release_date or None,
                        "album_type": rel.album_type,
                        "artwork": rel.artwork,
                        "url": rel.url,
                        "track_count": rel.track_count,
                    }
        except Exception as e:
            logger.warning("iTunes fetch failed for %s: %s", name, e)
        time.sleep(_REQ_DELAY)

        # Deezer
        try:
            for rel in deezer.artist_releases(name, limit=50):
                key = f"deezer:{rel.source_id}"
                if key not in all_releases:
                    all_releases[key] = {
                        "artist_name": name,
                        "source": "deezer",
                        "source_id": rel.source_id,
                        "title": rel.title,
                        "release_date": rel.release_date or None,
                        "album_type": rel.album_type,
                        "artwork": rel.artwork,
                        "url": rel.url,
                        "track_count": rel.track_count,
                    }
        except Exception as e:
            logger.warning("Deezer fetch failed for %s: %s", name, e)
        time.sleep(_REQ_DELAY)

    logger.info("Feed refresh: %d unique releases fetched", len(all_releases))
    return list(all_releases.values())


def _fetch_events(artist_names: list[str]) -> list[dict]:
    """Fetch events from Bandsintown + tickethub for a list of artists."""
    from scrapers.bandsintown.scraper import fetch_artist_events
    from scrapers.nigerian import tickethub

    all_events: dict[str, dict] = {}  # key = f"{source}:{source_id}"

    # Bandsintown (per-artist API calls)
    for name in artist_names:
        try:
            for event in fetch_artist_events(name):
                key = f"bandsintown:{event.source_id}"
                if key not in all_events:
                    all_events[key] = {
                        "source": "bandsintown",
                        "source_id": event.source_id,
                        "title": event.title,
                        "artist_name": name,
                        "url": event.url,
                        "start_time": event.start or None,
                        "end_time": event.end or None,
                        "venue": event.venue,
                        "city": event.city,
                        "country": event.country,
                        "image": event.image,
                        "ticket_url": event.ticket_url,
                        "sold_out": event.sold_out,
                        "description": event.description,
                    }
        except Exception as e:
            logger.warning("Bandsintown fetch failed for %s: %s", name, e)
        time.sleep(_REQ_DELAY)

    # tickethub (full listing, match against followed artists)
    try:
        th_events = tickethub.fetch_events()
        th_music = tickethub.music_events(th_events)
        names_lower = {n.lower(): n for n in artist_names}
        for event in th_music:
            # Match if any followed artist name appears in title/venue/category.
            # Matched → attributed so /feed/events shows it to that user.
            # Unmatched → kept with artist_name=None as the global discover
            # pool served by /feed/events/discover (never discarded).
            text = f"{event.title} {event.venue} {event.category}".lower()
            matched = next(
                (orig for low, orig in names_lower.items() if low in text),
                None,
            )
            key = f"tickethub:{event.source_id}"
            if key not in all_events:
                all_events[key] = {
                    "source": "tickethub",
                    "source_id": event.source_id,
                    "title": event.title,
                    "artist_name": matched,
                    "url": event.url,
                    "start_time": event.start or None,
                    "end_time": event.end or None,
                    "venue": event.venue,
                    "city": event.city,
                    "country": event.country,
                    "image": event.image,
                    "ticket_url": "",
                    "sold_out": False,
                    "description": event.description,
                }
    except Exception as e:
        logger.warning("Tickethub fetch failed: %s", e)

    logger.info("Feed refresh: %d unique events fetched", len(all_events))
    return list(all_events.values())


def _upsert_releases(db, releases: list[dict]) -> int:
    """Upsert releases into artist_releases_cache. Returns count upserted."""
    if not releases:
        return 0
    now = datetime.now(timezone.utc).isoformat()
    for r in releases:
        r["fetched_at"] = now
    # Batch upsert (Supabase handles 500-row batches well)
    batch_size = 200
    upserted = 0
    for i in range(0, len(releases), batch_size):
        batch = releases[i : i + batch_size]
        try:
            db.table("artist_releases_cache").upsert(batch).execute()
            upserted += len(batch)
        except Exception as e:
            logger.error("Release upsert batch failed: %s", e)
    return upserted


def _upsert_events(db, events: list[dict]) -> int:
    """Upsert events into artist_events_cache. Returns count upserted."""
    if not events:
        return 0
    now = datetime.now(timezone.utc).isoformat()
    for e in events:
        e["fetched_at"] = now
    batch_size = 200
    upserted = 0
    for i in range(0, len(events), batch_size):
        batch = events[i : i + batch_size]
        try:
            db.table("artist_events_cache").upsert(batch).execute()
            upserted += len(batch)
        except Exception as e:
            logger.error("Event upsert batch failed: %s", e)
    return upserted


def _cleanup_stale(db, days: int = 7) -> None:
    """Delete cache entries older than N days."""
    cutoff = (datetime.now(timezone.utc) - timedelta(days=days)).isoformat()
    try:
        db.table("artist_releases_cache").delete().lt("fetched_at", cutoff).execute()
        db.table("artist_events_cache").delete().lt("fetched_at", cutoff).execute()
        logger.info("Feed refresh: cleaned up entries older than %d days", days)
    except Exception as e:
        logger.error("Cache cleanup failed: %s", e)


def refresh_feed() -> dict:
    """Run the full feed refresh cycle. Returns a summary dict.

    Called by:
    - asyncio background task (every 12h)
    - POST /internal/refresh-feed (manual trigger)
    """
    start = time.time()
    db = get_supabase()

    # 1. Get unique artist names
    artists = _get_unique_artists(db)
    if not artists:
        logger.info("Feed refresh: no followed artists, skipping")
        return {"artists": 0, "releases": 0, "events": 0, "duration_s": 0}

    # 2. Fetch releases + events
    releases = _fetch_releases(artists)
    events = _fetch_events(artists)

    # 3. Upsert into cache
    releases_upserted = _upsert_releases(db, releases)
    events_upserted = _upsert_events(db, events)

    # 4. Cleanup stale entries
    _cleanup_stale(db)

    duration = time.time() - start
    summary = {
        "artists": len(artists),
        "releases": releases_upserted,
        "events": events_upserted,
        "duration_s": round(duration, 1),
    }
    logger.info("Feed refresh complete: %s", summary)
    return summary
