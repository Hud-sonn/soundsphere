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
    TrackPayload,
)
from services.limiter import limiter

logger = logging.getLogger("soundsphere-auth")

router = APIRouter(prefix="/user")

_READ_LIMIT = "300/hour"
_WRITE_LIMIT = "120/hour"


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
        .select("*, playlist_tracks(track_id, position, added_at, tracks(*))")
        .eq("id", playlist_id)
        .execute()
    )
    if not playlist.data:
        raise HTTPException(status_code=404, detail="Playlist not found")
    playlist = playlist.data[0]
    if playlist["user_id"] != user_id:
        raise HTTPException(status_code=404, detail="Playlist not found")
    return playlist


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
    _require_user(db, user_id)
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
    rows = (
        db.table("playlists")
        .select("*, playlist_tracks(track_id, position, added_at, tracks(*))")
        .eq("user_id", user_id)
        .order("updated_at", desc=True)
        .execute()
    )
    return [_playlist_response(row) for row in rows.data]


@router.get("/playlists/{playlist_id}")
@limiter.limit(_READ_LIMIT)
async def get_playlist(
    playlist_id: str,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    db = get_supabase()
    _require_user(db, user_id)
    playlist = _get_owned_playlist(db, user_id, playlist_id)
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
    created = (
        db.table("playlists")
        .insert(
            {
                "user_id": user_id,
                "name": body.name,
                "cover_url": body.cover_url,
            },
            returning="representation",
        )
        .execute()
    )
    playlist = created.data[0]
    playlist["track_count"] = 0
    playlist["tracks"] = []
    return playlist


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
@limiter.limit(_WRITE_LIMIT)
async def add_playlist_track(
    playlist_id: str,
    body: AddPlaylistTrackRequest,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    db = get_supabase()
    _require_user(db, user_id)
    playlist = _get_owned_playlist(db, user_id, playlist_id)
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
        }
    ).execute()
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
    _get_owned_playlist(db, user_id, playlist_id)
    db.table("playlist_tracks").delete().eq("playlist_id", playlist_id).eq(
        "track_id", track_id
    ).execute()
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
    return {"status": "ok"}


@router.delete("/history")
@limiter.limit(_WRITE_LIMIT)
async def clear_history(request: Request, user_id: str = Depends(get_current_user)):
    db = get_supabase()
    _require_user(db, user_id)
    db.table("history").delete().eq("user_id", user_id).execute()
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
