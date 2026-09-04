"""Account data sync endpoints (liked tracks, playlists, history, follows).

All endpoints require the same Bearer JWT issued by /auth/login or
/auth/verify. Rows are written through the Supabase service role, so the
RLS-enabled tables are accessible to this backend even though the app
never talks to Supabase directly.

Foreign-key note: liked_tracks / history / playlist_tracks all reference
`tracks.id`, so every relation insert first upserts the track metadata
into `tracks`. The app sends a TrackPayload for exactly this reason.
"""

import logging
import secrets
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, Request

from auth.jwt import get_current_user
from db.supabase import get_supabase
from models.schemas import (
    AddPlaylistTrackRequest,
    FollowAddRequest,
    HistoryAddRequest,
    LikeTrackRequest,
    PlaylistCreateRequest,
    PlaylistUpdateRequest,
    ProfileUpdateRequest,
    RecentlyPlayedAddRequest,
    SettingsUpdateRequest,
    TrackPayload,
)
from services import cloudinary
from services.limiter import limiter

logger = logging.getLogger("soundsphere-auth")

router = APIRouter(prefix="/user")

_READ_LIMIT = "600/hour"
_WRITE_LIMIT = "300/hour"
# Bulk playlist sync pushes one POST per track (a 100+ track playlist would
# blow through _WRITE_LIMIT and fail the whole sync with 429s).
_SYNC_WRITE_LIMIT = "600/hour"

# Account-level sync caps (enforced before writes; grandfather existing rows).
_PLAYLIST_SYNC_LIMIT = 20
_HISTORY_KEEP = 500
_FOLLOWED_ARTIST_LIMIT = 200
_LIKED_LIMIT = 2000
_PLAYLIST_TRACK_LIMIT = 500


def _require_user(db, user_id: str) -> dict:
    """Load the user row for the token subject; 401 when it no longer exists."""
    user = db.table("users").select("*").eq("id", user_id).execute()
    if not user.data:
        raise HTTPException(status_code=401, detail="Invalid or expired token")
    return user.data[0]


def _track_row(track: TrackPayload) -> dict:
    return {
        "id": track.id,
        "title": track.title,
        "artist": track.artist,
        "album": track.album,
        "duration": track.duration,
        "artwork_url": track.artwork_url,
        "source": track.source,
        "genre": track.genre,
        "year": track.year,
    }


def _upsert_track(db, track: TrackPayload) -> None:
    db.table("tracks").upsert(_track_row(track), on_conflict="id").execute()


def _playlist_response(playlist: dict) -> dict:
    """Shape a playlist row (with nested playlist_tracks(tracks(*))) for the app."""
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
    playlist["track_count"] = len(tracks)
    playlist["tracks"] = tracks
    return playlist


def _get_owned_playlist(db, user_id: str, playlist_id: str) -> dict:
    playlist = (
        db.table("playlists")
        .select("*, playlist_tracks(track_id, position, added_at, added_by_user_id, tracks(*))")
        .eq("id", playlist_id)
        .execute()
    )
    if not playlist.data:
        raise HTTPException(status_code=404, detail="Playlist not found")
    playlist = playlist.data[0]
    if playlist["user_id"] != user_id:
        raise HTTPException(status_code=404, detail="Playlist not found")
    return playlist


def _get_accessible_playlist(db, user_id: str, playlist_id: str) -> dict:
    """Allow if user owns the playlist OR is a collaborator (Blend)."""
    playlist = (
        db.table("playlists")
        .select("*, playlist_tracks(track_id, position, added_at, added_by_user_id, tracks(*))")
        .eq("id", playlist_id)
        .execute()
    )
    if not playlist.data:
        raise HTTPException(status_code=404, detail="Playlist not found")
    playlist = playlist.data[0]
    if playlist["user_id"] == user_id:
        return playlist
    collab = (
        db.table("playlist_collaborators")
        .select("id")
        .eq("playlist_id", playlist_id)
        .eq("user_id", user_id)
        .limit(1)
        .execute()
    )
    if collab.data:
        return playlist
    raise HTTPException(status_code=404, detail="Playlist not found")


def _recount_playlist(db, playlist_id: str) -> None:
    """Keep the denormalized `track_count` column in sync with playlist_tracks."""
    try:
        result = (
            db.table("playlist_tracks")
            .select("track_id", count="exact")
            .eq("playlist_id", playlist_id)
            .execute()
        )
        raw = result.count
        try:
            count = int(raw)
        except (TypeError, ValueError):
            count = len(result.data)
    except Exception:
        logger.exception("Could not recount playlist %s", playlist_id)
        return
    db.table("playlists").update({"track_count": count}).eq("id", playlist_id).execute()


# ===== Profile =====


@router.get("/profile")
@limiter.limit(_READ_LIMIT)
async def get_profile(request: Request, user_id: str = Depends(get_current_user)):
    db = get_supabase()
    user = _require_user(db, user_id)
    return {
        "id": user["id"],
        "email": user["email"],
        "username": user["username"],
        "avatar_url": user.get("avatar_url"),
        "auth_provider": user["auth_provider"],
        "is_verified": user["is_verified"],
        "role": user.get("role", "user"),
        "created_at": user.get("created_at", ""),
    }


@router.put("/profile")
@limiter.limit(_WRITE_LIMIT)
async def update_profile(
    body: ProfileUpdateRequest,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    db = get_supabase()
    current_user = _require_user(db, user_id)
    updates = {}
    if body.username is not None:
        updates["username"] = body.username
    if body.avatar_url is not None:
        updates["avatar_url"] = body.avatar_url
    if not updates:
        raise HTTPException(status_code=400, detail="Nothing to update")
    updated = (
        db.table("users").update(updates, returning="representation").eq("id", user_id)
    ).execute()
    user = updated.data[0]
    # Replace-and-delete: once the new avatar is persisted, remove the previous
    # Cloudinary image so replaced avatars don't accumulate. Best-effort — a
    # failed cleanup must never fail the profile update.
    if body.avatar_url is not None:
        old_avatar = current_user.get("avatar_url")
        if old_avatar and old_avatar != body.avatar_url:
            await cloudinary.delete_avatar(old_avatar)
    return {
        "id": user["id"],
        "email": user["email"],
        "username": user["username"],
        "avatar_url": user.get("avatar_url"),
        "auth_provider": user["auth_provider"],
        "is_verified": user["is_verified"],
        "role": user.get("role", "user"),
        "created_at": user.get("created_at", ""),
    }


# ===== Liked tracks =====


@router.get("/liked")
@limiter.limit(_READ_LIMIT)
async def get_liked_tracks(request: Request, user_id: str = Depends(get_current_user)):
    db = get_supabase()
    _require_user(db, user_id)
    rows = (
        db.table("liked_tracks")
        .select("liked_at, tracks(*)")
        .eq("user_id", user_id)
        .order("liked_at", desc=True)
        .execute()
    )
    tracks = []
    for row in rows.data:
        meta = row.get("tracks") or {}
        tracks.append(
            {
                "liked_at": row.get("liked_at"),
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
    return {"tracks": tracks}


@router.post("/liked/{track_id}")
@limiter.limit(_WRITE_LIMIT)
async def like_track(
    track_id: str,
    body: LikeTrackRequest,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    db = get_supabase()
    _require_user(db, user_id)
    # Account-level cap: 2000 liked tracks.
    try:
        existing = (
            db.table("liked_tracks")
            .select("track_id", count="exact")
            .eq("user_id", user_id)
            .execute()
        )
        if (existing.count or len(existing.data)) >= _LIKED_LIMIT:
            # Allow re-liking an already-liked track (idempotent upsert).
            already = any(r.get("track_id") == track_id for r in existing.data)
            if not already:
                raise HTTPException(
                    status_code=409,
                    detail=f"Like limit reached ({_LIKED_LIMIT})",
                )
    except HTTPException:
        raise
    except Exception:
        logger.warning("liked cap check failed", exc_info=True)
    track = body.model_copy(update={"id": track_id})
    _upsert_track(db, track)
    db.table("liked_tracks").upsert(
        {"user_id": user_id, "track_id": track_id},
        on_conflict="user_id,track_id",
    ).execute()
    return {"status": "ok"}


@router.delete("/liked/{track_id}")
@limiter.limit(_WRITE_LIMIT)
async def unlike_track(
    track_id: str,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    db = get_supabase()
    _require_user(db, user_id)
    db.table("liked_tracks").delete().eq("user_id", user_id).eq(
        "track_id", track_id
    ).execute()
    return {"status": "ok"}


# ===== Playlists =====


@router.get("/playlists")
@limiter.limit(_READ_LIMIT)
async def get_playlists(request: Request, user_id: str = Depends(get_current_user)):
    db = get_supabase()
    _require_user(db, user_id)
    owned = (
        db.table("playlists")
        .select("*, playlist_tracks(track_id, position, added_at, added_by_user_id, tracks(*))")
        .eq("user_id", user_id)
        .execute()
    )
    collab_ids = (
        db.table("playlist_collaborators")
        .select("playlist_id")
        .eq("user_id", user_id)
        .execute()
    )
    ids = [r["playlist_id"] for r in collab_ids.data] if collab_ids.data else []
    collab_rows = []
    if ids:
        collab_rows = (
            db.table("playlists")
            .select("*, playlist_tracks(track_id, position, added_at, added_by_user_id, tracks(*))")
            .in_("id", ids)
            .execute()
        ).data
    # Merge owned + collab, dedupe, sort by updated_at desc
    merged = {r["id"]: r for r in owned.data}
    for r in collab_rows:
        merged.setdefault(r["id"], r)
    rows = sorted(merged.values(), key=lambda x: x.get("updated_at") or "", reverse=True)
    return [_playlist_response(row) for row in rows]


@router.get("/playlists/{playlist_id}")
@limiter.limit(_READ_LIMIT)
async def get_playlist(
    playlist_id: str,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    db = get_supabase()
    _require_user(db, user_id)
    playlist = _get_accessible_playlist(db, user_id, playlist_id)
    return _playlist_response(playlist)


@router.post("/playlists", status_code=201)
@limiter.limit(_WRITE_LIMIT)
async def create_playlist(
    body: PlaylistCreateRequest,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    db = get_supabase()
    _require_user(db, user_id)
    # Account-level cap: 20 synced playlists. Grandfathers existing over-limit rows.
    try:
        existing = (
            db.table("playlists").select("id", count="exact").eq("user_id", user_id).execute()
        )
        if (existing.count or len(existing.data)) >= _PLAYLIST_SYNC_LIMIT:
            raise HTTPException(
                status_code=409,
                detail=f"Playlist sync limit reached ({_PLAYLIST_SYNC_LIMIT})",
            )
    except HTTPException:
        raise
    except Exception:
        logger.warning("playlist cap check failed", exc_info=True)
    created = (
        db.table("playlists")
        .insert(
            {
                "user_id": user_id,
                "name": body.name,
                "cover_url": body.cover_url,
                "share_token": secrets.token_urlsafe(16),
            },
            returning="representation",
        )
        .execute()
    )
    playlist = created.data[0]
    playlist["track_count"] = 0
    playlist["tracks"] = []
    return playlist


@router.post("/playlists/{playlist_id}/share")
@limiter.limit(_WRITE_LIMIT)
async def share_playlist(
    playlist_id: str,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    """Get (or lazily create) the unguessable share token for an owned
    playlist. Idempotent: sharing an already-shared playlist returns the
    existing token."""
    db = get_supabase()
    _require_user(db, user_id)
    playlist = _get_owned_playlist(db, user_id, playlist_id)
    token = playlist.get("share_token")
    if not token:
        token = secrets.token_urlsafe(16)
        db.table("playlists").update({"share_token": token}).eq("id", playlist_id).execute()
    return {"share_token": token}


@router.delete("/playlists/{playlist_id}/share")
@limiter.limit(_WRITE_LIMIT)
async def unshare_playlist(
    playlist_id: str,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    """Revoke sharing: the token becomes NULL and every existing link dies."""
    db = get_supabase()
    _require_user(db, user_id)
    _get_owned_playlist(db, user_id, playlist_id)
    db.table("playlists").update({"share_token": None}).eq("id", playlist_id).execute()
    return {"status": "ok"}


# ===== Blend — collaborators =====


@router.get("/playlists/{playlist_id}/collaborators")
@limiter.limit(_READ_LIMIT)
async def get_collaborators(
    playlist_id: str,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    db = get_supabase()
    _require_user(db, user_id)
    _get_accessible_playlist(db, user_id, playlist_id)
    rows = (
        db.table("playlist_collaborators")
        .select("user_id, added_at, users(username, avatar_url)")
        .eq("playlist_id", playlist_id)
        .execute()
    )
    # Also include owner
    playlist = db.table("playlists").select("user_id").eq("id", playlist_id).execute()
    owner_id = playlist.data[0]["user_id"] if playlist.data else None
    owner = None
    if owner_id:
        owner_row = db.table("users").select("username, avatar_url").eq("id", owner_id).execute()
        if owner_row.data:
            owner = {"user_id": owner_id, "username": owner_row.data[0].get("username"), "avatar_url": owner_row.data[0].get("avatar_url"), "is_owner": True}
    members = []
    if owner:
        members.append(owner)
    for r in rows.data:
        u = r.get("users") or {}
        members.append({"user_id": r["user_id"], "username": u.get("username"), "avatar_url": u.get("avatar_url"), "is_owner": False})
    return {"collaborators": members}


@router.delete("/playlists/{playlist_id}/collaborators/{target_user_id}")
@limiter.limit(_WRITE_LIMIT)
async def remove_collaborator(
    playlist_id: str,
    target_user_id: str,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    db = get_supabase()
    _require_user(db, user_id)
    # Only owner or self can remove
    playlist = _get_owned_playlist(db, user_id, playlist_id) if target_user_id != user_id else _get_accessible_playlist(db, user_id, playlist_id)
    # If target is owner, not allowed
    if target_user_id == playlist["user_id"]:
        raise HTTPException(status_code=400, detail="Cannot remove owner")
    # Owner removing someone, or member leaving
    if target_user_id != user_id:
        # Must be owner
        if playlist["user_id"] != user_id:
            raise HTTPException(status_code=403, detail="Only owner can remove members")
    db.table("playlist_collaborators").delete().eq("playlist_id", playlist_id).eq("user_id", target_user_id).execute()
    return {"status": "ok"}


@router.post("/playlists/{playlist_id}/collaborators")
@limiter.limit(_WRITE_LIMIT)
async def add_collaborator(
    playlist_id: str,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    body = await request.json()
    target_user_id = body.get("user_id")
    if not target_user_id:
        raise HTTPException(status_code=400, detail="user_id required")
    db = get_supabase()
    _require_user(db, user_id)
    _get_owned_playlist(db, user_id, playlist_id)
    existing = db.table("playlist_collaborators").select("id").eq("playlist_id", playlist_id).eq("user_id", target_user_id).execute()
    if existing.data:
        return {"status": "already_member"}
    count = db.table("playlist_collaborators").select("id", count="exact").eq("playlist_id", playlist_id).execute()
    if (count.count or len(count.data)) >= 10:
        raise HTTPException(status_code=409, detail="Blend is full (10 members)")
    db.table("playlist_collaborators").insert({"playlist_id": playlist_id, "user_id": target_user_id}).execute()
    db.table("playlists").update({"is_collaborative": True}).eq("id", playlist_id).execute()
    return {"status": "added"}


@router.put("/playlists/{playlist_id}")
@limiter.limit(_WRITE_LIMIT)
async def update_playlist(
    playlist_id: str,
    body: PlaylistUpdateRequest,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    db = get_supabase()
    _require_user(db, user_id)
    _get_owned_playlist(db, user_id, playlist_id)
    updates = {"updated_at": datetime.now(timezone.utc).isoformat()}
    if body.name is not None:
        updates["name"] = body.name
    if body.cover_url is not None:
        updates["cover_url"] = body.cover_url
    if body.is_collaborative is not None:
        updates["is_collaborative"] = body.is_collaborative
    db.table("playlists").update(updates).eq("id", playlist_id).execute()
    return _playlist_response(_get_owned_playlist(db, user_id, playlist_id))


@router.delete("/playlists/{playlist_id}")
@limiter.limit(_WRITE_LIMIT)
async def delete_playlist(
    playlist_id: str,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    db = get_supabase()
    _require_user(db, user_id)
    _get_owned_playlist(db, user_id, playlist_id)
    db.table("playlists").delete().eq("id", playlist_id).execute()
    return {"status": "ok"}


@router.post("/playlists/{playlist_id}/tracks")
@limiter.limit(_SYNC_WRITE_LIMIT)
async def add_playlist_track(
    playlist_id: str,
    body: AddPlaylistTrackRequest,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    db = get_supabase()
    _require_user(db, user_id)
    playlist = _get_accessible_playlist(db, user_id, playlist_id)
    # Per-playlist track cap: 500 (cheap guard; re-uses existing tracks data).
    try:
        track_count = len(playlist.get("playlist_tracks") or [])
        if track_count >= _PLAYLIST_TRACK_LIMIT:
            raise HTTPException(
                status_code=409,
                detail=f"Playlist track limit reached ({_PLAYLIST_TRACK_LIMIT})",
            )
    except HTTPException:
        raise
    except Exception:
        logger.warning("playlist track cap check failed", exc_info=True)
    _upsert_track(db, body.track)
    position = body.position
    if position is None:
        existing = playlist.get("playlist_tracks") or []
        position = max((t.get("position", 0) for t in existing), default=-1) + 1
    db.table("playlist_tracks").insert(
        {
            "playlist_id": playlist_id,
            "track_id": body.track.id,
            "position": position,
            "added_by_user_id": user_id,
        }
    ).execute()
    _recount_playlist(db, playlist_id)
    # Blend: notify other members in-app (no webhook, just DB row, app polls)
    try:
        if playlist.get("is_collaborative"):
            user_row = db.table("users").select("username").eq("id", user_id).execute()
            username = user_row.data[0].get("username") if user_row.data else "Someone"
            collabs = db.table("playlist_collaborators").select("user_id").eq("playlist_id", playlist_id).execute()
            owner_id = playlist.get("user_id")
            notify_ids = {r["user_id"] for r in collabs.data} | ({owner_id} if owner_id else set())
            notify_ids.discard(user_id)
            for nid in notify_ids:
                try:
                    db.table("notifications").insert({
                        "user_id": nid,
                        "title": "Blend updated",
                        "body": f"{username} added \"{body.track.title}\" to {playlist.get('name')}",
                        "type": "blend_update",
                        "data": {"playlist_id": playlist_id, "track_id": body.track.id},
                    }).execute()
                except Exception:
                    pass
    except Exception:
        logger.warning("blend notification failed", exc_info=True)
    return {"status": "ok"}


@router.delete("/playlists/{playlist_id}/tracks/{track_id}")
@limiter.limit(_WRITE_LIMIT)
async def remove_playlist_track(
    playlist_id: str,
    track_id: str,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    db = get_supabase()
    _require_user(db, user_id)
    _get_accessible_playlist(db, user_id, playlist_id)
    db.table("playlist_tracks").delete().eq("playlist_id", playlist_id).eq(
        "track_id", track_id
    ).execute()
    _recount_playlist(db, playlist_id)
    return {"status": "ok"}


# ===== History =====


@router.get("/history")
@limiter.limit(_READ_LIMIT)
async def get_history(request: Request, user_id: str = Depends(get_current_user)):
    db = get_supabase()
    _require_user(db, user_id)
    rows = (
        db.table("history")
        .select("played_at, tracks(*)")
        .eq("user_id", user_id)
        .order("played_at", desc=True)
        .limit(500)
        .execute()
    )
    history = []
    for row in rows.data:
        meta = row.get("tracks") or {}
        history.append(
            {
                "played_at": row.get("played_at"),
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
    return {"history": history}


@router.post("/history")
@limiter.limit(_WRITE_LIMIT)
async def add_history(
    body: HistoryAddRequest,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    db = get_supabase()
    _require_user(db, user_id)
    _upsert_track(db, body.track)
    db.table("history").insert(
        {
            "user_id": user_id,
            "track_id": body.track.id,
            "played_at": body.played_at or datetime.now(timezone.utc).isoformat(),
        }
    ).execute()
    _prune_history(db, user_id)
    return {"status": "ok"}


def _prune_history(db, user_id: str) -> None:
    """Keep only the most recent _HISTORY_KEEP rows for the user (FIFO)."""
    try:
        rows = (
            db.table("history")
            .select("id")
            .eq("user_id", user_id)
            .order("played_at", desc=True)
            .range(_HISTORY_KEEP, _HISTORY_KEEP + 499)
            .execute()
        )
        stale_ids = [row["id"] for row in rows.data]
        if stale_ids:
            db.table("history").delete().in_("id", stale_ids).execute()
    except Exception:
        logger.warning("history prune failed", exc_info=True)


@router.delete("/history")
@limiter.limit(_WRITE_LIMIT)
async def clear_history(request: Request, user_id: str = Depends(get_current_user)):
    db = get_supabase()
    _require_user(db, user_id)
    db.table("history").delete().eq("user_id", user_id).execute()
    return {"status": "ok"}


# ===== Recently played =====

_RECENTLY_PLAYED_KEEP = 50


def _prune_recently_played(db, user_id: str) -> None:
    """Keep only the most recent _RECENTLY_PLAYED_KEEP rows for the user.

    Unlike history (append-only, union-merged), recently-played is a bounded
    recency list: old entries must fall away both here and on the clients.
    Pruning happens inline after each write so storage stays capped without
    needing a scheduled job.
    """
    try:
        rows = (
            db.table("recently_played")
            .select("id")
            .eq("user_id", user_id)
            .order("played_at", desc=True)
            .range(_RECENTLY_PLAYED_KEEP, _RECENTLY_PLAYED_KEEP + 499)
            .execute()
        )
        stale_ids = [row["id"] for row in rows.data]
        if stale_ids:
            db.table("recently_played").delete().in_("id", stale_ids).execute()
    except Exception:
        # Pruning is best-effort housekeeping; never fail the user's write.
        logger.warning("recently_played prune failed", exc_info=True)


@router.get("/recently-played")
@limiter.limit(_READ_LIMIT)
async def get_recently_played(
    request: Request, user_id: str = Depends(get_current_user)
):
    db = get_supabase()
    _require_user(db, user_id)
    rows = (
        db.table("recently_played")
        .select("*")
        .eq("user_id", user_id)
        .order("played_at", desc=True)
        .limit(50)
        .execute()
    )
    return {"recently_played": rows.data}


@router.post("/recently-played")
@limiter.limit(_WRITE_LIMIT)
async def add_recently_played(
    body: RecentlyPlayedAddRequest,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    db = get_supabase()
    _require_user(db, user_id)
    db.table("recently_played").upsert(
        {
            "user_id": user_id,
            "source_type": body.source_type,
            "source_id": body.source_id,
            "source_name": body.source_name,
            "source_thumbnail": body.source_thumbnail,
            "played_at": body.played_at
            or datetime.now(timezone.utc).isoformat(),
        },
        on_conflict="user_id,source_type,source_id",
    ).execute()
    _prune_recently_played(db, user_id)
    return {"status": "ok"}


@router.delete("/recently-played")
@limiter.limit(_WRITE_LIMIT)
async def clear_recently_played(
    request: Request, user_id: str = Depends(get_current_user)
):
    db = get_supabase()
    _require_user(db, user_id)
    db.table("recently_played").delete().eq("user_id", user_id).execute()
    return {"status": "ok"}


# ===== Followed artists =====


@router.post("/follows/{artist_id}")
@limiter.limit(_WRITE_LIMIT)
async def follow_artist(
    artist_id: str,
    body: FollowAddRequest,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    db = get_supabase()
    _require_user(db, user_id)
    # Account-level cap: 200 followed artists.
    try:
        existing = (
            db.table("followed_artists")
            .select("artist_id", count="exact")
            .eq("user_id", user_id)
            .execute()
        )
        if (existing.count or len(existing.data)) >= _FOLLOWED_ARTIST_LIMIT:
            already = any(r.get("artist_id") == artist_id for r in existing.data)
            if not already:
                raise HTTPException(
                    status_code=409,
                    detail=f"Follow limit reached ({_FOLLOWED_ARTIST_LIMIT})",
                )
    except HTTPException:
        raise
    except Exception:
        logger.warning("follow cap check failed", exc_info=True)
    db.table("followed_artists").upsert(
        {
            "user_id": user_id,
            "artist_id": artist_id,
            "artist_name": body.artist_name,
        },
        on_conflict="user_id,artist_id",
    ).execute()
    return {"status": "ok"}


@router.delete("/follows/{artist_id}")
@limiter.limit(_WRITE_LIMIT)
async def unfollow_artist(
    artist_id: str,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    db = get_supabase()
    _require_user(db, user_id)
    db.table("followed_artists").delete().eq("user_id", user_id).eq(
        "artist_id", artist_id
    ).execute()
    return {"status": "ok"}


@router.get("/follows")
@limiter.limit(_READ_LIMIT)
async def get_followed_artists(
    request: Request,
    user_id: str = Depends(get_current_user),
):
    db = get_supabase()
    _require_user(db, user_id)
    rows = (
        db.table("followed_artists")
        .select("artist_id, artist_name, followed_at")
        .eq("user_id", user_id)
        .order("followed_at", desc=True)
        .execute()
    )
    return {
        "artists": [
            {
                "id": row["artist_id"],
                "name": row.get("artist_name", ""),
                "followed_at": row.get("followed_at"),
            }
            for row in rows.data
        ]
    }


# ===== Settings =====


# ===== In-app notifications (Blend updates, etc.) =====


@router.get("/notifications")
@limiter.limit(_READ_LIMIT)
async def get_notifications(request: Request, user_id: str = Depends(get_current_user)):
    db = get_supabase()
    _require_user(db, user_id)
    rows = db.table("notifications").select("*").eq("user_id", user_id).order("created_at", desc=True).limit(20).execute()
    return {"notifications": rows.data}


@router.put("/notifications/{notif_id}/read")
@limiter.limit(_WRITE_LIMIT)
async def mark_notification_read(notif_id: str, request: Request, user_id: str = Depends(get_current_user)):
    db = get_supabase()
    _require_user(db, user_id)
    db.table("notifications").update({"read": True}).eq("id", notif_id).eq("user_id", user_id).execute()
    return {"status": "ok"}


@router.get("/settings")
@limiter.limit(_READ_LIMIT)
async def get_settings(
    request: Request,
    user_id: str = Depends(get_current_user),
):
    db = get_supabase()
    _require_user(db, user_id)
    row = db.table("user_settings").select("settings").eq("user_id", user_id).execute()
    if not row.data:
        return {"settings": {}}
    return {"settings": row.data[0].get("settings", {})}


@router.put("/settings")
@limiter.limit(_WRITE_LIMIT)
async def update_settings(
    body: SettingsUpdateRequest,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    db = get_supabase()
    _require_user(db, user_id)
    from datetime import datetime, timezone
    db.table("user_settings").upsert(
        {
            "user_id": user_id,
            "settings": body.settings,
            "updated_at": datetime.now(timezone.utc).isoformat(),
        },
        on_conflict="user_id",
    ).execute()
    return {"status": "ok"}
