import asyncio
import logging
from datetime import datetime, timedelta, timezone
from fastapi import APIRouter, Depends, HTTPException, Request
from db.supabase import get_supabase
from services.limiter import limiter
from auth.password import hash_password, verify_password

logger = logging.getLogger("solus-rift")
from auth.jwt import create_token, get_current_user_info
from models.schemas import (
    RegisterRequest,
    VerifyOtpRequest,
    LoginRequest,
    ForgotPasswordRequest,
    ResetPasswordRequest,
    ResendOtpRequest,
    TokenResponse,
    UserResponse,
)
from services.email import (
    _create_auth_user,
    _delete_auth_user,
    send_otp_email,
    send_reset_code_email,
    verify_otp_code,
)

router = APIRouter(prefix="/auth")

# Re-send policy: codes are throttled to one per minute, and a code that was
# sent within the validity window is considered "still valid" - the user gets
# an error instead of a new code. The error is only shown once per send; the
# next request (after the cooldown) is allowed through as an escape hatch for
# codes that never arrived. Tracked via the otp_codes table (no codes stored).
OTP_COOLDOWN_SECONDS = 60
OTP_VALID_SECONDS = 600  # 10 minutes


def _code_issue_error(db, email: str, purpose: str):
    """Returns (status, detail) when a code send must be blocked, else None."""
    rows = (
        db.table("otp_codes")
        .select("id, created_at, notified")
        .eq("email", email)
        .eq("purpose", purpose)
        .order("created_at", desc=True)
        .limit(1)
        .execute()
    )
    if not rows.data:
        return None
    last = rows.data[0]
    created = datetime.fromisoformat(last["created_at"].replace("Z", "+00:00"))
    age = (datetime.now(timezone.utc) - created).total_seconds()
    if age < OTP_COOLDOWN_SECONDS:
        return (
            429,
            "A verification code was sent recently. Please wait a minute before requesting another.",
        )
    if age < OTP_VALID_SECONDS and not last["notified"]:
        db.table("otp_codes").update({"notified": True}).eq("id", last["id"]).execute()
        return (
            400,
            "A verification code was already sent and is still valid. Check your inbox. "
            "If you haven't received it, wait a minute and request another.",
        )
    return None


def _record_code_sent(db, email: str, purpose: str) -> None:
    """Record a code send and drop rows older than the validity window so the
    tracking table never grows unbounded."""
    db.table("otp_codes").insert(
        {
            "email": email,
            "purpose": purpose,
            "created_at": datetime.now(timezone.utc).isoformat(),
            "expires_at": (datetime.now(timezone.utc) + timedelta(seconds=OTP_VALID_SECONDS)).isoformat(),
            "notified": False,
        }
    ).execute()
    cutoff = (datetime.now(timezone.utc) - timedelta(seconds=OTP_VALID_SECONDS)).isoformat()
    db.table("otp_codes").delete().eq("email", email).eq("purpose", purpose).lt(
        "created_at", cutoff
    ).execute()


def _clear_code_tracking(db, email: str, purpose: str) -> None:
    db.table("otp_codes").delete().eq("email", email).eq("purpose", purpose).execute()


def _user_to_response(user: dict) -> UserResponse:
    return UserResponse(
        id=user["id"],
        email=user["email"],
        username=user["username"],
        avatar_url=user.get("avatar_url"),
        auth_provider=user["auth_provider"],
        is_verified=user["is_verified"],
        role=user.get("role", "user"),
        created_at=user.get("created_at", ""),
    )


@router.post("/register", status_code=201)
@limiter.limit("5/hour")
async def register(body: RegisterRequest, request: Request = None):
    db = get_supabase()
    email = body.email.strip().lower()
    existing = db.table("users").select("id").eq("email", email).execute()
    if existing.data:
        raise HTTPException(status_code=409, detail="Email already registered")
    issue = _code_issue_error(db, email, "verify")
    if issue:
        raise HTTPException(status_code=issue[0], detail=issue[1])
    # Idempotent re-registration: clear any previous unfinished attempt for
    # this email so the address is never blocked by partial state.
    db.table("pending_registrations").delete().eq("email", email).execute()
    try:
        _create_auth_user(email)
    except Exception as e:
        print(f"Failed to prepare auth account: {e}")
        raise HTTPException(
            status_code=500, detail="Could not send verification code. Please try again."
        )
    pw_hash = hash_password(body.password)
    db.table("pending_registrations").insert(
        {
            "email": email,
            "username": body.username,
            "password_hash": pw_hash,
        }
    ).execute()
    try:
        await asyncio.to_thread(send_otp_email, email)
    except Exception as e:
        # Nothing is recorded until the code is actually delivered: roll back
        # the pending registration and the auth account so the email stays
        # free for a retry.
        db.table("pending_registrations").delete().eq("email", email).execute()
        _delete_auth_user(email)
        print(f"Failed to send OTP email: {e}")
        raise HTTPException(
            status_code=500, detail="Could not send verification code. Please try again."
        )
    _record_code_sent(db, email, "verify")
    return {"message": "Verification code sent to email"}


@router.post("/verify")
@limiter.limit("10/hour")
async def verify(body: VerifyOtpRequest, request: Request = None):
    db = get_supabase()
    email = body.email.strip().lower()
    if not await asyncio.to_thread(verify_otp_code, email, body.otp, "verify"):
        raise HTTPException(status_code=400, detail="Invalid verification code")
    _clear_code_tracking(db, email, "verify")
    # The account is created only now that the email address is proven.
    pending = db.table("pending_registrations").select("*").eq("email", email).execute()
    if not pending.data:
        raise HTTPException(status_code=400, detail="No pending registration found. Register again.")
    pending_row = pending.data[0]
    created = (
        db.table("users")
        .insert(
            {
                "email": email,
                "username": pending_row["username"],
                "password_hash": pending_row["password_hash"],
                "auth_provider": "email",
                "is_verified": True,
            }
        )
        .execute()
    )
    if not created.data:
        raise HTTPException(status_code=500, detail="Failed to create account")
    db.table("pending_registrations").delete().eq("email", email).execute()
    user = created.data[0]
    token = create_token(user["id"], role=user.get("role", "user"))
    return TokenResponse(token=token, user=_user_to_response(user))


@router.get("/me")
async def me(user_info: dict = Depends(get_current_user_info)):
    """Validates the Bearer token and returns the current user. 401 if the
    token is missing, invalid, expired, or the user no longer exists."""
    db = get_supabase()
    user = db.table("users").select("*").eq("id", user_info["user_id"]).execute()
    if not user.data:
        raise HTTPException(status_code=401, detail="Invalid or expired token")
    user = user.data[0]
    return _user_to_response(user)


@router.post("/resend-otp")
@limiter.limit("3/hour")
async def resend_otp(body: ResendOtpRequest, request: Request = None):
    db = get_supabase()
    email = body.email.strip().lower()
    existing = db.table("users").select("id").eq("email", email).execute()
    if existing.data:
        return {"message": "If that email is registered, a new code was sent"}
    pending = db.table("pending_registrations").select("*").eq("email", email).execute()
    if not pending.data:
        return {"message": "If that email is registered, a new code was sent"}
    issue = _code_issue_error(db, email, "verify")
    if issue:
        raise HTTPException(status_code=issue[0], detail=issue[1])
    try:
        await asyncio.to_thread(send_otp_email, email)
    except Exception as e:
        print(f"Failed to send OTP email: {e}")
        raise HTTPException(
            status_code=500, detail="Could not send a new code. Please try again."
        )
    _record_code_sent(db, email, "verify")
    return {"message": "New code sent"}


@router.post("/login")
@limiter.limit("10/hour")
async def login(request: Request):
    try:
        body = await request.json()
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid JSON body")
    try:
        login_req = LoginRequest(**body)
    except Exception as e:
        logger.error(f"LOGIN_VALIDATION_ERROR: {e}")
        raise HTTPException(status_code=422, detail=str(e))
    logger.info(f"LOGIN_ATTEMPT email={login_req.email}")
    db = get_supabase()
    user = db.table("users").select("*").eq("email", login_req.email).execute()
    if not user.data or not user.data[0].get("password_hash"):
        raise HTTPException(status_code=401, detail="Invalid credentials")
    user = user.data[0]
    if not verify_password(login_req.password, user["password_hash"]):
        raise HTTPException(status_code=401, detail="Invalid credentials")
    if not user["is_verified"]:
        raise HTTPException(status_code=401, detail="Invalid credentials")
    db.table("users").update(
        {"last_active": datetime.now(timezone.utc).isoformat()}
    ).eq("id", user["id"]).execute()
    token = create_token(user["id"], role=user.get("role", "user"))
    return TokenResponse(token=token, user=_user_to_response(user))


@router.post("/forgot-password")
@limiter.limit("5/hour")
async def forgot_password(body: ForgotPasswordRequest, request: Request = None):
    db = get_supabase()
    user = db.table("users").select("*").eq("email", body.email).execute()
    if not user.data:
        return {"message": "If that email is registered, a reset code was sent"}
    issue = _code_issue_error(db, user.data[0]["email"], "reset")
    if issue:
        raise HTTPException(status_code=issue[0], detail=issue[1])
    try:
        await asyncio.to_thread(send_reset_code_email, body.email)
    except Exception as e:
        # Log the error but don't fail the request - the reset code is issued
        # by Supabase Auth, which keeps its own state for this flow.
        print(f"Failed to send reset email: {e}")
    _record_code_sent(db, user.data[0]["email"], "reset")
    return {"message": "If that email is registered, a reset code was sent"}


@router.post("/reset-password")
@limiter.limit("5/hour")
async def reset_password(body: ResetPasswordRequest, request: Request = None):
    db = get_supabase()
    user = db.table("users").select("*").eq("email", body.email).execute()
    if not user.data:
        return {"message": "If that email is registered, the password has been reset"}
    user = user.data[0]
    if not await asyncio.to_thread(verify_otp_code, body.email, body.otp, "reset"):
        raise HTTPException(status_code=400, detail="Invalid reset code")
    _clear_code_tracking(db, body.email, "reset")
    new_hash = hash_password(body.new_password)
    db.table("users").update({"password_hash": new_hash}).eq("id", user["id"]).execute()
    return {"message": "Password reset successfully"}
