"""
Throttled activity tracking.

`maybe_touch_last_active` runs on every authenticated request and keeps
`users.last_active` honest (it used to be written only at login, which made
the admin dashboard's "active users" numbers meaningless). Writes are
throttled per user so the database is not hammered by sync bursts.

`log_activity` records discrete events (login, signup, app usage, share-link
views, errors, crashes) into `activity_events` for the live dashboard feed.
All writes run in daemon threads so request latency never depends on them.
"""

import threading
import time
from datetime import datetime, timezone

from db.supabase import get_supabase

TOUCH_INTERVAL_SECONDS = 300  # users.last_active write throttle
EVENT_INTERVAL_SECONDS = 600  # per-user "app_use" event throttle
_MAX_USERS = 5000

_touch_lock = threading.Lock()
_event_lock = threading.Lock()
_last_touch: dict[str, float] = {}
_last_event: dict[str, float] = {}


def _throttled(cache: dict, lock: threading.Lock, user_id: str, interval: float) -> bool:
    now = time.time()
    with lock:
        if len(cache) > _MAX_USERS:
            cache.clear()
        if now - cache.get(user_id, 0.0) < interval:
            return False
        cache[user_id] = now
        return True


def _fire_and_forget(fn) -> None:
    threading.Thread(target=fn, daemon=True).start()


def _do_touch_last_active(user_id: str) -> None:
    try:
        db = get_supabase()
        db.table("users").update(
            {"last_active": datetime.now(timezone.utc).isoformat()}
        ).eq("id", user_id).execute()
    except Exception:
        pass


def _do_log_activity(user_id, event_type: str, detail: str) -> None:
    try:
        db = get_supabase()
        db.table("activity_events").insert(
            {
                "user_id": user_id or None,
                "event_type": event_type,
                "detail": (detail or "")[:500],
            }
        ).execute()
    except Exception:
        pass


def maybe_touch_last_active(user_id: str) -> None:
    """Update users.last_active if it hasn't been updated recently."""
    if not user_id or not _throttled(_last_touch, _touch_lock, user_id, TOUCH_INTERVAL_SECONDS):
        return
    _fire_and_forget(lambda: _do_touch_last_active(user_id))


def track_app_use(user_id: str) -> None:
    """Record a throttled 'app in use' event for the live feed."""
    if not user_id or not _throttled(_last_event, _event_lock, user_id, EVENT_INTERVAL_SECONDS):
        return
    _fire_and_forget(lambda: _do_log_activity(user_id, "app_use", ""))


def log_activity(user_id: str | None, event_type: str, detail: str = "") -> None:
    """Record a discrete event (login, signup, share_view, page_view, crash, error)."""
    _fire_and_forget(lambda: _do_log_activity(user_id, event_type, detail))