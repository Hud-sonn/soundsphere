import os
from datetime import datetime, timedelta, timezone
from jose import JWTError, jwt  # type: ignore
from fastapi import Depends, HTTPException
from fastapi.security import HTTPBearer

ALGORITHM = "HS256"
_bearer = HTTPBearer(auto_error=False)


def create_token(user_id: str, role: str = "user") -> str:
    """Create a JWT token with user_id (sub) and role claims."""
    secret = os.getenv("JWT_SECRET")
    expire_minutes = int(os.getenv("JWT_EXPIRE_MINUTES", "10080"))
    payload = {
        "sub": user_id,
        "role": role,
        "exp": datetime.now(timezone.utc) + timedelta(minutes=expire_minutes),
    }
    return jwt.encode(payload, secret, algorithm=ALGORITHM)  # type: ignore


def decode_token(token: str) -> dict:
    """Decode a JWT token and return the payload as a dict."""
    secret = os.getenv("JWT_SECRET")
    try:
        payload = jwt.decode(token, secret, algorithms=[ALGORITHM])  # type: ignore
        return payload
    except JWTError:
        raise HTTPException(status_code=401, detail="Invalid or expired token")


def get_current_user(credentials=Depends(_bearer)) -> str:  # type: ignore
    """Backward compatible: returns user_id (sub) from the token."""
    if credentials is None:
        raise HTTPException(status_code=401, detail="Missing authorization header")
    decoded = decode_token(credentials.credentials)
    user_id: str = decoded.get("sub")
    if user_id is None:
        raise HTTPException(status_code=401, detail="Invalid token")
    return user_id


def get_current_user_info(credentials=Depends(_bearer)) -> dict:  # type: ignore
    """Returns dict with user_id and role from the token."""
    if credentials is None:
        raise HTTPException(status_code=401, detail="Missing authorization header")
    decoded = decode_token(credentials.credentials)
    return {"user_id": decoded.get("sub"), "role": decoded.get("role", "user")}


def get_optional_user(credentials=Depends(_bearer)) -> str:  # type: ignore
    """Backward compatible: returns user_id or empty string."""
    if credentials is None:
        return ""
    try:
        decoded = decode_token(credentials.credentials)
        return decoded.get("sub", "")
    except HTTPException:
        return ""


def admin_required(credentials=Depends(_bearer)) -> dict:  # type: ignore
    """Requires admin role. Returns dict with user_id and role."""
    if credentials is None:
        raise HTTPException(status_code=401, detail="Missing authorization header")
    decoded = decode_token(credentials.credentials)
    role = decoded.get("role", "user")
    if role != "admin":
        raise HTTPException(status_code=403, detail="Admin access required")
    return {"user_id": decoded.get("sub"), "role": role}
