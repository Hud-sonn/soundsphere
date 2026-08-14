"""Admin endpoints: dashboard stats, API error log, user management, crash reports.

Every route except POST /admin/crashes/ingest requires the admin role claim
in the Bearer JWT (see auth/jwt.py `admin_required`). Reads use generous
per-IP rate limits; the ingest endpoint is not admin-gated because it is the
upload target for the Android app's crash reporter.
"""

import logging
from datetime import datetime, timedelta, timezone
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Request

from auth.jwt import admin_required, get_optional_user
from db.supabase import get_supabase
from models.schemas import CrashReportRequest
from services.limiter import limiter

logger = logging.getLogger("soundsphere-auth")

router = APIRouter(prefix="/admin")

_READ_LIMIT = "1000/hour"
_INGEST_LIMIT = "60/hour"


@router.get("/health")
@limiter.limit(_READ_LIMIT)
async def admin_health(request: Request, claims: dict = Depends(admin_required)):
    return {"status": "ok", "admin": claims["user_id"]}


@router.get("/stats/overview")
@limiter.limit(_READ_LIMIT)
async def stats_overview(request: Request, claims: dict = Depends(admin_required)):
    db = get_supabase()
    now = datetime.now(timezone.utc)

    def days_ago(n: int) -> str:
        return (now - timedelta(days=n)).isoformat()

    def count(table: str, col: str = "id", **filters) -> int:
        query = db.table(table).select(col, count="exact")
        for column, value in filters.items():
            if column == "gte":
                for k, v in value.items():
                    query = query.gte(k, v)
            elif column == "lt":
                for k, v in value.items():
                    query = query.lt(k, v)
        result = query.execute()
        return result.count if result.count is not None else len(result.data)

    users = db.table("users").select("id,email,role,is_verified,created_at,last_active").execute()
    all_users = users.data
    total_users = len(all_users)
    verified = sum(1 for u in all_users if u.get("is_verified"))
    admins = sum(1 for u in all_users if u.get("role") == "admin")
    new_7d = sum(1 for u in all_users if (u.get("created_at") or "") >= days_ago(7))
    new_30d = sum(1 for u in all_users if (u.get("created_at") or "") >= days_ago(30))
    active_7d = sum(1 for u in all_users if (u.get("last_active") or "") >= days_ago(7))
    active_30d = sum(1 for u in all_users if (u.get("last_active") or "") >= days_ago(30))

    error_logs = db.table("api_error_logs").select("status_code", count="exact").execute()
    crashes = db.table("crash_reports").select("id", count="exact").execute()
    crash_unhandled = db.table("crash_reports").select("id", count="exact").eq("handled", False).execute()

    return {
        "users": {
            "total": total_users,
            "verified": verified,
            "admins": admins,
            "new_7d": new_7d,
            "new_30d": new_30d,
            "active_7d": active_7d,
            "active_30d": active_30d,
        },
        "content": {
            "tracks": count("tracks"),
            "likes": count("liked_tracks", col="user_id"),
            "playlists": count("playlists"),
            "playlist_tracks": count("playlist_tracks"),
            "history": count("history"),
            "follows": count("followed_artists", col="user_id"),
        },
        "errors_24h": len(error_logs.data),
        "crashes": {
            "total": len(crashes.data),
            "unhandled": len(crash_unhandled.data),
        },
    }


@router.get("/errors")
@limiter.limit(_READ_LIMIT)
async def api_errors(
    request: Request,
    status_code: Optional[int] = None,
    path: Optional[str] = None,
    ip: Optional[str] = None,
    limit: int = 100,
    claims: dict = Depends(admin_required),
):
    """Recent API error log entries (4xx/5xx incl. slowapi 429s)."""
    db = get_supabase()
    query = db.table("api_error_logs").select(
        "id,method,path,status_code,client_ip,user_id,detail,created_at"
    ).order("created_at", desc=True).limit(min(limit, 500))
    if status_code is not None:
        query = query.eq("status_code", status_code)
    if path:
        query = query.ilike("path", f"%{path}%")
    if ip:
        query = query.ilike("client_ip", f"%{ip}%")
    result = query.execute()
    return {"errors": result.data}


@router.get("/errors/summary")
@limiter.limit(_READ_LIMIT)
async def api_errors_summary(request: Request, claims: dict = Depends(admin_required)):
    """Aggregations over the error log: worst paths, IPs, status codes."""
    db = get_supabase()
    rows = db.table("api_error_logs").select(
        "method,path,status_code,client_ip,created_at"
    ).gte("created_at", (datetime.now(timezone.utc) - timedelta(days=7)).isoformat()).execute()
    data = rows.data

    by_status: dict = {}
    by_path: dict = {}
    by_ip: dict = {}
    for row in data:
        status = row["status_code"]
        by_status[status] = by_status.get(status, 0) + 1
        path = row["path"]
        by_path[path] = by_path.get(path, 0) + 1
        ip = row["client_ip"]
        by_ip[ip] = by_ip.get(ip, 0) + 1

    top_paths = sorted(by_path.items(), key=lambda kv: kv[1], reverse=True)[:10]
    top_ips = sorted(by_ip.items(), key=lambda kv: kv[1], reverse=True)[:10]
    return {
        "total": len(data),
        "by_status": [{"status": k, "count": v} for k, v in sorted(by_status.items())],
        "top_paths": [{"path": k, "count": v} for k, v in top_paths],
        "top_ips": [{"ip": k, "count": v} for k, v in top_ips],
    }


@router.get("/users")
@limiter.limit(_READ_LIMIT)
async def list_users(
    request: Request,
    search: Optional[str] = None,
    role: Optional[str] = None,
    limit: int = 100,
    claims: dict = Depends(admin_required),
):
    db = get_supabase()
    query = db.table("users").select(
        "id,email,username,role,is_verified,created_at,last_active"
    ).order("created_at", desc=True).limit(min(limit, 500))
    if search:
        query = query.or_(f"email.ilike.%{search}%,username.ilike.%{search}%")
    if role:
        query = query.eq("role", role)
    users = query.execute().data

    return {
        "users": [
            {
                "id": u["id"],
                "email": u["email"],
                "username": u["username"],
                "role": u["role"],
                "is_verified": u["is_verified"],
                "created_at": u["created_at"],
                "last_active": u["last_active"],
            }
            for u in users
        ]
    }


@router.put("/users/{user_id}/role")
@limiter.limit(_READ_LIMIT)
async def set_user_role(
    user_id: str,
    request: Request,
    role: str = "user",
    claims: dict = Depends(admin_required),
):
    if role not in ("user", "admin"):
        raise HTTPException(status_code=422, detail="Role must be 'user' or 'admin'")
    db = get_supabase()
    db.table("users").update({"role": role}).eq("id", user_id).execute()
    return {"ok": True, "user_id": user_id, "role": role}


@router.get("/crashes")
@limiter.limit(_READ_LIMIT)
async def list_crashes(
    request: Request,
    fatal: Optional[bool] = None,
    limit: int = 50,
    claims: dict = Depends(admin_required),
):
    db = get_supabase()
    query = db.table("crash_reports").select(
        "id,user_id,app_version,app_version_code,flavor,os,device_model,"
        "manufacturer,exception,message,fatal,reported_at,handled"
    ).order("reported_at", desc=True).limit(min(limit, 200))
    if fatal is not None:
        query = query.eq("fatal", fatal)
    return {"crashes": query.execute().data}


@router.get("/crashes/{crash_id}")
@limiter.limit(_READ_LIMIT)
async def crash_detail(crash_id: str, request: Request, claims: dict = Depends(admin_required)):
    db = get_supabase()
    result = db.table("crash_reports").select("*").eq("id", crash_id).execute()
    if not result.data:
        raise HTTPException(status_code=404, detail="Crash report not found")
    return {"crash": result.data[0]}


@router.delete("/crashes/{crash_id}")
@limiter.limit(_READ_LIMIT)
async def delete_crash(crash_id: str, request: Request, claims: dict = Depends(admin_required)):
    db = get_supabase()
    db.table("crash_reports").delete().eq("id", crash_id).execute()
    return {"ok": True}


@router.post("/crashes/ingest")
@limiter.limit(_INGEST_LIMIT)
async def ingest_crash(
    payload: CrashReportRequest,
    request: Request,
    user_id: str = Depends(get_optional_user),
):
    """App-side crash upload. Not admin-gated; accepts an optional JWT so the
    report can be attributed to a user without the app sending any PII."""
    db = get_supabase()
    row = {
        "user_id": user_id or None,
        "app_version": payload.app_version,
        "app_version_code": payload.app_version_code,
        "flavor": payload.flavor,
        "os": payload.os,
        "device_model": payload.device_model,
        "manufacturer": payload.manufacturer,
        "exception": payload.exception,
        "message": payload.message,
        "stack_trace": payload.stack_trace,
        "thread_name": payload.thread_name,
        "fatal": payload.fatal,
    }
    if payload.reported_at:
        row["reported_at"] = payload.reported_at
    db.table("crash_reports").insert(row).execute()
    return {"ok": True}