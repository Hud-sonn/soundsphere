"""Feed endpoints — personalized new releases + upcoming concerts.

GET /feed/releases  — new releases from artists the user follows
GET /feed/events    — upcoming concerts from artists the user follows
GET /feed/all       — both combined
POST /internal/refresh-feed — trigger a cache refresh (service-to-service)
"""

import logging
from datetime import datetime, timezone, timedelta

from fastapi import APIRouter, Depends, Request

from auth.jwt import get_current_user
from db.supabase import get_supabase
from services.feed_refresh import refresh_feed

logger = logging.getLogger("soundsphere-auth")
router = APIRouter(prefix="/feed", tags=["feed"])


def _get_followed_artist_names(db, user_id: str) -> list[str]:
    """Get the artist names this user follows."""
    result = (
        db.table("followed_artists")
        .select("artist_name")
        .eq("user_id", user_id)
        .execute()
    )
    rows = result.data or []
    return [r["artist_name"] for r in rows if r.get("artist_name")]


@router.get("/releases")
def get_releases(user_id: str = Depends(get_current_user)):
    """New releases (last 24 hours) from artists the user follows."""
    db = get_supabase()
    artist_names = _get_followed_artist_names(db, user_id)
    if not artist_names:
        return {"releases": [], "artists_followed": 0}

    cutoff = (datetime.now(timezone.utc) - timedelta(hours=24)).date().isoformat()
    result = (
        db.table("artist_releases_cache")
        .select("*")
        .in_("artist_name", artist_names)
        .gte("release_date", cutoff)
        .order("release_date", desc=True)
        .limit(50)
        .execute()
    )
    return {
        "releases": result.data or [],
        "artists_followed": len(artist_names),
    }


@router.get("/events")
def get_events(user_id: str = Depends(get_current_user)):
    """Upcoming concerts from artists the user follows."""
    db = get_supabase()
    artist_names = _get_followed_artist_names(db, user_id)
    if not artist_names:
        return {"events": [], "artists_followed": 0}

    now = datetime.now(timezone.utc).isoformat()
    result = (
        db.table("artist_events_cache")
        .select("*")
        .in_("artist_name", artist_names)
        .gte("start_time", now)
        .order("start_time", desc=False)
        .limit(50)
        .execute()
    )
    return {
        "events": result.data or [],
        "artists_followed": len(artist_names),
    }


@router.get("/events/discover")
def get_events_discover(user_id: str = Depends(get_current_user)):
    """Events NOT tied to artists the user follows (discover pool).

    Sources: tickethub listings that matched no followed artist
    (artist_name IS NULL) + upcoming events cached for other users'
    followed artists. Multi-artist by construction. Ordered soonest
    first, 7-day upcoming window is enforced by the cron's retention.
    """
    db = get_supabase()
    followed = set(_get_followed_artist_names(db, user_id))

    now = datetime.now(timezone.utc).isoformat()
    result = (
        db.table("artist_events_cache")
        .select("*")
        .gte("start_time", now)
        .order("start_time", desc=False)
        .limit(100)
        .execute()
    )
    rows = result.data or []
    discover = [
        r
        for r in rows
        if not r.get("artist_name") or r.get("artist_name") not in followed
    ][:50]
    return {
        "events": discover,
        "artists_followed": len(followed),
    }


@router.get("/all")
def get_feed_all(user_id: str = Depends(get_current_user)):
    """Combined feed: releases + events."""
    db = get_supabase()
    artist_names = _get_followed_artist_names(db, user_id)
    if not artist_names:
        return {"releases": [], "events": [], "artists_followed": 0}

    cutoff = (datetime.now(timezone.utc) - timedelta(hours=24)).date().isoformat()
    now = datetime.now(timezone.utc).isoformat()

    releases = (
        db.table("artist_releases_cache")
        .select("*")
        .in_("artist_name", artist_names)
        .gte("release_date", cutoff)
        .order("release_date", desc=True)
        .limit(50)
        .execute()
    )
    events = (
        db.table("artist_events_cache")
        .select("*")
        .in_("artist_name", artist_names)
        .gte("start_time", now)
        .order("start_time", desc=False)
        .limit(50)
        .execute()
    )
    return {
        "releases": releases.data or [],
        "events": events.data or [],
        "artists_followed": len(artist_names),
    }


@router.post("/internal/refresh")
def trigger_refresh():
    """Manually trigger a feed cache refresh.

    Protected by service-to-service auth in production.
    For now: internal endpoint, not exposed to the app.
    """
    summary = refresh_feed()
    return {"status": "ok", "summary": summary}
