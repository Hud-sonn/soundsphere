import asyncio
import logging
from datetime import datetime, timedelta, timezone
from fastapi import APIRouter, Depends, HTTPException, Request
from db.supabase import get_supabase
from services.limiter import limiter
from auth.password import hash_password, verify_password, hash_otp, verify_otp

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
from services.email import generate_otp, send_otp_email

router = APIRouter(prefix="/auth")


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
    existing = db.table("users").select("*").eq("email", body.email).execute()
    if existing.data:
        raise HTTPException(status_code=409, detail="Email already registered")
    pw_hash = hash_password(body.password)
    user = (
        db.table("users")
        .insert(
            {
                "email": body.email,
                "username": body.username,
                "password_hash": pw_hash,
                "auth_provider": "email",
                "is_verified": False,
            }
        )
        .execute()
    )
    if not user.data:
        raise HTTPException(status_code=500, detail="Failed to create user")
    user_id = user.data[0]["id"]
    otp = generate_otp()
    otp_hash = hash_otp(otp)
    db.table("otp_codes").insert(
        {
            "user_id": user_id,
            "otp_hash": otp_hash,
            "purpose": "verify",
            "expires_at": (
                datetime.now(timezone.utc) + timedelta(minutes=10)
            ).isoformat(),
        }
    ).execute()
    try:
        await asyncio.to_thread(send_otp_email, body.email, otp, "verify")
    except Exception as e:
        # Log the error but don't fail the request - OTP is still stored in DB
        # In production, you'd want to use proper logging
        print(f"Failed to send OTP email: {e}")
    return {"message": "Verification code sent to email"}


@router.post("/verify")
@limiter.limit("10/hour")
async def verify(body: VerifyOtpRequest, request: Request = None):
    db = get_supabase()
    user = db.table("users").select("*").eq("email", body.email).execute()
    if not user.data:
        raise HTTPException(status_code=400, detail="Invalid verification code")
    user = user.data[0]
    if user["is_verified"]:
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
    otp_records = (
        db.table("otp_codes")
        .select("*")
        .eq("user_id", user["id"])
        .eq("purpose", "verify")
        .order("created_at", desc=True)
        .limit(1)
        .execute()
    )
    if not otp_records.data:
        raise HTTPException(status_code=400, detail="No OTP found. Request a new one.")
    record = otp_records.data[0]
    if datetime.fromisoformat(
        record["expires_at"].replace("Z", "+00:00")
    ) < datetime.now(timezone.utc):
        db.table("otp_codes").delete().eq("id", record["id"]).execute()
        raise HTTPException(status_code=400, detail="OTP expired")
    if not verify_otp(body.otp, record["otp_hash"]):
        raise HTTPException(status_code=400, detail="Invalid OTP")
    db.table("otp_codes").delete().eq("id", record["id"]).execute()
    db.table("users").update({"is_verified": True}).eq("id", user["id"]).execute()
    user["is_verified"] = True
    token = create_token(user["id"], role=user.get("role", "user"))
    return TokenResponse(token=token, user=_user_to_response(user))


@router.post("/resend-otp")
@limiter.limit("3/hour")
async def resend_otp(body: ResendOtpRequest, request: Request = None):
    db = get_supabase()
    user = db.table("users").select("*").eq("email", body.email).execute()
    if not user.data or user.data[0]["is_verified"]:
        return {"message": "If that email is registered, a new code was sent"}
    user = user.data[0]
    db.table("otp_codes").delete().eq("user_id", user["id"]).eq(
        "purpose", "verify"
    ).execute()
    otp = generate_otp()
    otp_hash = hash_otp(otp)
    db.table("otp_codes").insert(
        {
            "user_id": user["id"],
            "otp_hash": otp_hash,
            "purpose": "verify",
            "expires_at": (
                datetime.now(timezone.utc) + timedelta(minutes=10)
            ).isoformat(),
        }
    ).execute()
    try:
        await asyncio.to_thread(send_otp_email, body.email, otp, "resend")
    except Exception as e:
        # Log the error but don't fail the request - OTP is still stored in DB
        print(f"Failed to send OTP email: {e}")
    return {"message": "New code sent"}


@router.post("/login")
@limiter.limit("10/hour")
async def login(request: Request):
    try:
        body = await request.json()
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid JSON body")
    logger.info(f"LOGIN_BODY: {body}")
    try:
        login_req = LoginRequest(**body)
    except Exception as e:
        logger.error(f"LOGIN_VALIDATION_ERROR: {e}")
        raise HTTPException(status_code=422, detail=str(e))
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
    user = user.data[0]
    db.table("otp_codes").delete().eq("user_id", user["id"]).eq(
        "purpose", "reset"
    ).execute()
    otp = generate_otp()
    otp_hash = hash_otp(otp)
    db.table("otp_codes").insert(
        {
            "user_id": user["id"],
            "otp_hash": otp_hash,
            "purpose": "reset",
            "expires_at": (
                datetime.now(timezone.utc) + timedelta(minutes=10)
            ).isoformat(),
        }
    ).execute()
    try:
        await asyncio.to_thread(send_otp_email, body.email, otp, "reset")
    except Exception as e:
        # Log the error but don't fail the request - OTP is still stored in DB
        print(f"Failed to send OTP email: {e}")
    return {"message": "If that email is registered, a reset code was sent"}


@router.post("/reset-password")
@limiter.limit("5/hour")
async def reset_password(body: ResetPasswordRequest, request: Request = None):
    db = get_supabase()
    user = db.table("users").select("*").eq("email", body.email).execute()
    if not user.data:
        return {"message": "If that email is registered, the password has been reset"}
    user = user.data[0]
    otp_records = (
        db.table("otp_codes")
        .select("*")
        .eq("user_id", user["id"])
        .eq("purpose", "reset")
        .order("created_at", desc=True)
        .limit(1)
        .execute()
    )
    if not otp_records.data:
        raise HTTPException(status_code=400, detail="No reset code found")
    record = otp_records.data[0]
    if datetime.fromisoformat(
        record["expires_at"].replace("Z", "+00:00")
    ) < datetime.now(timezone.utc):
        db.table("otp_codes").delete().eq("id", record["id"]).execute()
        raise HTTPException(status_code=400, detail="Reset code expired")
    if not verify_otp(body.otp, record["otp_hash"]):
        raise HTTPException(status_code=400, detail="Invalid reset code")
    db.table("otp_codes").delete().eq("id", record["id"]).execute()
    new_hash = hash_password(body.new_password)
    db.table("users").update({"password_hash": new_hash}).eq("id", user["id"]).execute()
    return {"message": "Password reset successfully"}
