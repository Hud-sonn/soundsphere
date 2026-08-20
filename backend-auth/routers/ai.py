"""Server-side AI playlist generation endpoint.

The app sends only a prompt; Groq keys stay on the server. The endpoint is
gated by:
  1. server-side toggle: GROQ_API_KEY must be configured (503 otherwise);
  2. per-user consent stored in `user_settings.settings.ai_playlist_consent`
     (403 until the app records first-use consent).
"""

import logging
from datetime import date

from fastapi import APIRouter, Depends, HTTPException, Request

from auth.jwt import get_current_user
from db.supabase import get_supabase
from models.schemas import AiPlaylistRequest, ArtistDetectRequest
from services import ai_playlist
from services.limiter import limiter

logger = logging.getLogger("soundsphere-auth")

router = APIRouter(prefix="/ai")

# LLM calls cost money; keep the blast radius small per user. The slowapi
# bound is a coarse IP-based guard; the authoritative per-account cap lives in
# ai_generation_usage (see _DAILY_GENERATION_LIMIT below) so it survives app
# cache clears and device switches.
_GENERATE_LIMIT = "50/day"

# Artist detection is a single search; still cap it to avoid abuse.
_DETECT_LIMIT = "30/hour"

_CONSENT_KEY = "ai_playlist_consent"

# Account-level cap: each user may generate at most this many AI playlists per
# calendar day (UTC). Enforced in Supabase by (user_id, usage_date).
_DAILY_GENERATION_LIMIT = 2


def _require_user(db, user_id: str) -> dict:
    user = db.table("users").select("*").eq("id", user_id).execute()
    if not user.data:
        raise HTTPException(status_code=401, detail="Invalid or expired token")
    return user.data[0]


def _generation_count(db, user_id: str) -> int:
    today = date.today().isoformat()
    row = (
        db.table("ai_generation_usage")
        .select("generation_count")
        .eq("user_id", user_id)
        .eq("usage_date", today)
        .limit(1)
        .execute()
    )
    return row.data[0].get("generation_count", 0) if row.data else 0


def _increment_generation(db, user_id: str) -> None:
    today = date.today().isoformat()
    db.table("ai_generation_usage").upsert(
        {
            "user_id": user_id,
            "usage_date": today,
            "generation_count": _generation_count(db, user_id) + 1,
            "updated_at": "now()",
        },
        on_conflict="user_id,usage_date",
    ).execute()


async def _recent_history(db, user_id: str) -> list[dict]:
    rows = (
        db.table("history")
        .select("played_at, tracks(title, artist)")
        .eq("user_id", user_id)
        .order("played_at", desc=True)
        .limit(ai_playlist._MAX_HISTORY)
        .execute()
    )
    history = []
    for row in rows.data:
        meta = row.get("tracks") or {}
        title = (meta.get("title") or "").strip()
        artist = (meta.get("artist") or "").strip()
        if title:
            history.append({"title": title, "artist": artist})
    return history


@router.post("/generate-playlist")
@limiter.limit(_GENERATE_LIMIT)
async def generate_playlist(
    body: AiPlaylistRequest,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    if not ai_playlist.ai_enabled():
        raise HTTPException(
            status_code=503, detail="AI playlist generation is not enabled yet"
        )

    db = get_supabase()
    _require_user(db, user_id)

    try:
        row = (
            db.table("user_settings")
            .select("settings")
            .eq("user_id", user_id)
            .execute()
        )
    except Exception:
        # user_settings table missing (002 migration not applied yet): treat
        # as no consent instead of surfacing a 500 to the app.
        logger.warning(
            "user_settings table unavailable for %s; treating as no consent",
            user_id,
        )
        row = None
    settings = row.data[0].get("settings", {}) if row and row.data else {}
    if not settings.get(_CONSENT_KEY):
        raise HTTPException(
            status_code=403, detail="AI playlist consent not granted"
        )

    # Account-level daily budget (survives app cache clears: keyed on user_id
    # in Supabase, never on the device). Checked before any Groq call.
    if _generation_count(db, user_id) >= _DAILY_GENERATION_LIMIT:
        raise HTTPException(
            status_code=429,
            detail=(
                f"Daily AI playlist limit reached ({_DAILY_GENERATION_LIMIT} "
                "per day). Try again tomorrow."
            ),
        )

    try:
        history = await _recent_history(db, user_id)
        tracks = await ai_playlist.generate_playlist(
            body.prompt,
            history,
            body.count,
            artist=body.artist,
            mix_similar=body.mix_similar,
        )
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc))
    except Exception as exc:
        logger.error("AI playlist generation failed: %s", exc)
        raise HTTPException(
            status_code=502, detail="AI service unavailable, please try again later"
        )

    if not tracks:
        raise HTTPException(
            status_code=502, detail="No tracks could be generated, please try again"
        )

    _increment_generation(db, user_id)

    return {"prompt": body.prompt, "tracks": tracks}


@router.post("/detect-artist")
@limiter.limit(_DETECT_LIMIT)
async def detect_artist(
    body: ArtistDetectRequest,
    request: Request,
    user_id: str = Depends(get_current_user),
):
    """Best-effort artist detection for the search bar (one cheap search)."""
    db = get_supabase()
    _require_user(db, user_id)

    try:
        artist = await ai_playlist.detect_artist(body.prompt)
    except Exception as exc:
        logger.error("Artist detection failed: %s", exc)
        raise HTTPException(
            status_code=502, detail="Artist detection failed, please try again"
        )
    return {"artist": artist}