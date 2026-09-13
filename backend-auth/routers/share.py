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

from fastapi import Depends

from auth.jwt import get_current_user
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
                "added_by_user_id": item.get("added_by_user_id"),
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
        "is_collaborative": playlist.get("is_collaborative", False),
        "track_count": len(tracks),
        "member_count": playlist.get("member_count", 1),
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
            "id, name, cover_url, share_token, is_collaborative, created_at, updated_at, "
            "user_id, playlist_tracks(track_id, position, added_at, added_by_user_id, tracks(*))"
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
    # Member count (owner + collaborators) so joiners see real capacity.
    # Privacy-safe: a bare number, no member identities for outsiders.
    try:
        members = (
            db.table("playlist_collaborators")
            .select("id", count="exact")
            .eq("playlist_id", playlist["id"])
            .execute()
        )
        playlist["member_count"] = (members.count or len(members.data)) + 1
    except Exception:
        playlist["member_count"] = 1

    return _public_playlist_response(playlist)


@router.post("/playlists/{token}/join")
@limiter.limit("60/minute")
async def join_blend(request: Request, token: str, user_id: str = Depends(get_current_user)):
    db = get_supabase()
    # Find playlist by token
    rows = db.table("playlists").select("id, user_id, is_collaborative, name").eq("share_token", token).execute()
    if not rows.data:
        raise HTTPException(status_code=404, detail="Playlist not found or sharing disabled")
    playlist = rows.data[0]
    if not playlist.get("is_collaborative"):
        raise HTTPException(status_code=400, detail="Playlist is not a Blend")
    if playlist["user_id"] == user_id:
        raise HTTPException(status_code=400, detail="Owner is already a member")
    # Check already member
    existing = db.table("playlist_collaborators").select("id").eq("playlist_id", playlist["id"]).eq("user_id", user_id).execute()
    if existing.data:
        return {"status": "already_member"}
    # Cap at 10 members
    count = db.table("playlist_collaborators").select("id", count="exact").eq("playlist_id", playlist["id"]).execute()
    if (count.count or len(count.data)) >= 10:
        raise HTTPException(status_code=409, detail="Blend is full (10 members)")
    db.table("playlist_collaborators").insert({"playlist_id": playlist["id"], "user_id": user_id}).execute()
    log_activity(user_id, "blend_join", f"playlist {playlist['id']}")
    # Notify owner + existing members (they poll GET /user/notifications every 30s).
    try:
        joiner_row = db.table("users").select("username").eq("id", user_id).execute()
        joiner = joiner_row.data[0].get("username") if joiner_row.data else "Someone"
        collabs = db.table("playlist_collaborators").select("user_id").eq("playlist_id", playlist["id"]).execute()
        owner_id = playlist.get("user_id")
        notify_ids = {r["user_id"] for r in collabs.data} | ({owner_id} if owner_id else set())
        notify_ids.discard(user_id)
        for nid in notify_ids:
            try:
                db.table("notifications").insert({
                    "user_id": nid,
                    "title": "New Blend member",
                    "body": f"{joiner} joined \"{playlist.get('name')}\"",
                    "type": "blend_joined",
                    "data": {"playlist_id": playlist["id"], "user_id": user_id},
                }).execute()
            except Exception:
                pass
    except Exception:
        logger.warning("blend join notification failed", exc_info=True)
    return {"status": "joined"}