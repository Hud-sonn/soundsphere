"""Public playlist sharing endpoints.

Anyone with a valid share token can read the shared playlist — no JWT
required. This is what powers shared-playlist deep links: the app opens
`soundsphere://p/{token}` and the website preview fetches
`GET /share/playlists/{token}`.

Safety: the token is unguessable (`secrets.token_urlsafe(16)`), lookups
only ever select by `share_token`, and the response never leaks the
owner's `user_id` — only their public username and avatar. The endpoint
is rate limited per IP like everything else.
"""

import logging

from fastapi import APIRouter, HTTPException, Request

from db.supabase import get_supabase
from services.activity import log_activity
from services.limiter import limiter

logger = logging.getLogger("soundsphere-auth")

router = APIRouter(prefix="/share")

# Generous per-IP budget for link previews, but still capped. 429s are
# recorded in api_error_logs by the middleware in main.py.
_SHARE_LIMIT = "120/minute"


def _public_playlist_response(playlist: dict) -> dict:
    """Shape a shared playlist: same track shape as /user/playlists, plus a
    minimal `owner` object (username + avatar only, never user_id)."""
    raw_tracks = playlist.pop("playlist_tracks", []) or []
    tracks = []
    for item in sorted(raw_tracks, key=lambda t: t.get("position", 0)):
        meta = item.get("tracks") or {}
        tracks.append(
            {
                "position": item.get("position", 0),
                "added_at": item.get("added_at"),
                "track": {
                    "id": meta.get("id", ""),
                    "title": meta.get("title", ""),
                    "artist": meta.get("artist", ""),
                    "album": meta.get("album"),
                    "duration": meta.get("duration", 0),
                    "artwork_url": meta.get("artwork_url"),
                    "source": meta.get("source", "youtube"),
                    "genre": meta.get("genre"),
                    "year": meta.get("year"),
                },
            }
        )
    owner = playlist.pop("owner", None) or {}
    return {
        "id": playlist.get("id", ""),
        "name": playlist.get("name", ""),
        "cover_url": playlist.get("cover_url"),
        "share_token": playlist.get("share_token"),
        "track_count": len(tracks),
        "tracks": tracks,
        "owner": {
            "username": owner.get("username", ""),
            "avatar_url": owner.get("avatar_url"),
        },
    }


@router.get("/playlists/{token}")
@limiter.limit(_SHARE_LIMIT)
async def get_shared_playlist(request: Request, token: str):
    db = get_supabase()
    if not token:
        raise HTTPException(status_code=404, detail="Playlist not found or sharing disabled")

    rows = (
        db.table("playlists")
        .select(
            "id, name, cover_url, share_token, created_at, updated_at, "
            "user_id, playlist_tracks(track_id, position, added_at, tracks(*))"
        )
        .eq("share_token", token)
        .execute()
    )
    if not rows.data:
        raise HTTPException(status_code=404, detail="Playlist not found or sharing disabled")
    playlist = rows.data[0]
    log_activity(None, "share_view", f"playlist {token}")

    owner_rows = (
        db.table("users")
        .select("username, avatar_url")
        .eq("id", playlist["user_id"])
        .execute()
    )
    playlist["owner"] = owner_rows.data[0] if owner_rows.data else {}
    playlist.pop("user_id", None)

    return _public_playlist_response(playlist)