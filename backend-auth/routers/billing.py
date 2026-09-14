"""Premium tier checking and cap enforcement.

Security model:
- Tier is determined SOLELY by the subscriptions table (service_role only).
- Client never sends tier info; no caching; every cap check queries fresh.
- Expiration: expires_at > now() is the single source of truth. When it
  passes, the user automatically drops to free tier — no cron job needed.
- The subscriptions table has NO authenticated UPDATE policy (RLS fix 3).
- user_settings.settings CHECK prevents is_pro/plan keys.
- prevent_privileged_user_column_changes trigger protects users.role.

No payment logic lives here — this module only READS the subscriptions table.
Payment integration (OPay/Stripe) will write to subscriptions via service_role
webhooks, which is the ONLY write path to this table.
"""

import logging
from datetime import datetime, timezone

from fastapi import HTTPException

logger = logging.getLogger("soundsphere-auth")

# ── Cap definitions ──────────────────────────────────────────────────────
# (free_limit, premium_limit). None = unlimited.
_CAPS = {
    "playlists":          (20, None),
    "liked_tracks":       (2000, None),
    "followed_artists":   (200, None),
    "playlist_tracks":    (500, 1000),
    "history_rows":       (500, None),
    "ai_daily":           (2, 10),
    "blends_owned":       (3, 10),
}


def get_user_tier(db, user_id: str) -> str:
    """Return 'premium' or 'free' based on subscriptions.expires_at.

    Always queries fresh — no caching, so expiration is instant.
    If the row is missing or expires_at is NULL/past, returns 'free'.
    """
    try:
        row = (
            db.table("subscriptions")
            .select("tier, is_pro, expires_at")
            .eq("user_id", user_id)
            .limit(1)
            .execute()
        )
        if not row.data:
            return "free"
        sub = row.data[0]
        # Both conditions must be true for premium
        if not sub.get("is_pro"):
            return "free"
        expires_at = sub.get("expires_at")
        if not expires_at:
            return "free"
        # Parse the timestamptz — handle both ISO strings and datetime objects
        if isinstance(expires_at, str):
            expires_at = datetime.fromisoformat(expires_at.replace("Z", "+00:00"))
        if expires_at.tzinfo is None:
            expires_at = expires_at.replace(tzinfo=timezone.utc)
        if expires_at <= datetime.now(timezone.utc):
            return "free"
        return "premium"
    except Exception:
        logger.warning("tier check failed for %s, defaulting to free", user_id, exc_info=True)
        return "free"


def get_cap(db, user_id: str, cap_name: str) -> int | None:
    """Return the effective cap for the user. None = unlimited."""
    tier = get_user_tier(db, user_id)
    free_limit, premium_limit = _CAPS.get(cap_name, (0, 0))
    if tier == "premium":
        return premium_limit
    return free_limit


def check_cap(db, user_id: str, cap_name: str, current_count: int) -> None:
    """Raise 409 if the user has hit their cap. Passes silently if unlimited."""
    limit = get_cap(db, user_id, cap_name)
    if limit is None:
        return  # unlimited
    if current_count >= limit:
        raise HTTPException(
            status_code=409,
            detail=f"{cap_name} limit reached ({limit})",
        )


def get_blend_cap(db, user_id: str) -> int:
    """Return the max number of Blends a user can OWN (not join)."""
    return get_cap(db, user_id, "blends_owned") or 3


def check_blend_creation_cap(db, user_id: str) -> None:
    """Enforce the per-user Blend ownership cap (3 free, 10 premium)."""
    limit = get_blend_cap(db, user_id)
    try:
        count = (
            db.table("playlists")
            .select("id", count="exact")
            .eq("user_id", user_id)
            .eq("is_collaborative", True)
            .execute()
        )
        current = count.count or len(count.data)
        if current >= limit:
            raise HTTPException(
                status_code=409,
                detail=f"Blend creation limit reached ({limit} per user)",
            )
    except HTTPException:
        raise
    except Exception:
        logger.warning("blend cap check failed", exc_info=True)
