import json
import os
import secrets
import urllib.error
import urllib.request

API_TIMEOUT = 20


def _service_key() -> str:
    key = os.getenv("SUPABASE_SERVICE_KEY")
    if not key:
        raise RuntimeError("SUPABASE_SERVICE_KEY must be set")
    return key


def _request(method: str, path: str, payload: dict | None = None) -> tuple[int, dict]:
    url = os.getenv("SUPABASE_URL", "").rstrip("/")
    if not url:
        raise RuntimeError("SUPABASE_URL must be set")
    key = _service_key()
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("apikey", key)
    req.add_header("Authorization", f"Bearer {key}")
    try:
        with urllib.request.urlopen(req, timeout=API_TIMEOUT) as resp:
            body = resp.read()
            return resp.status, json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        body = {}
        try:
            body = json.load(e)
        except Exception:
            pass
        return e.code, body


def _create_auth_user(email: str) -> None:
    """Create an unconfirmed Supabase Auth account for the email.

    The account stays unconfirmed until the OTP is redeemed, so the code
    below never treats the address as verified on its own; the app's
    `users` table remains the source of truth. A random password is set so
    the row can never be used for direct Supabase logins.
    """
    payload = {"email": email, "email_confirm": False, "password": secrets.token_urlsafe(48)}
    status, body = _request("POST", "/auth/v1/admin/users", payload)
    if status in (200, 201):
        return
    if status in (409, 422) and "registered" in str(body.get("msg", "")).lower():
        # A leftover row (e.g. from a failed attempt) blocks re-registration:
        # delete it and retry once.
        users = _request("GET", "/auth/v1/admin/users")[1].get("users", [])
        for user in users:
            if user.get("email") == email:
                _request("DELETE", f"/auth/v1/admin/users/{user['id']}")
                break
        status, body = _request("POST", "/auth/v1/admin/users", payload)
        if status in (200, 201):
            return
    raise RuntimeError(f"Supabase Auth user creation failed ({status}): {body.get('msg', body)}")


def _delete_auth_user(email: str) -> None:
    users = _request("GET", "/auth/v1/admin/users")[1].get("users", [])
    for user in users:
        if user.get("email") == email:
            _request("DELETE", f"/auth/v1/admin/users/{user['id']}")
            return


def send_otp_email(to_email: str) -> None:
    """Ask Supabase Auth to email a verification code to the address.

    The code is generated, stored and validated by Supabase Auth; the
    backend never sees it.
    """
    status, body = _request(
        "POST", "/auth/v1/otp", {"email": to_email, "create_user": False}
    )
    if status != 200:
        raise RuntimeError(f"Supabase Auth OTP failed ({status}): {body.get('msg', body)}")


def send_reset_code_email(to_email: str) -> None:
    """Ask Supabase Auth to email a password-reset code to the address."""
    status, body = _request("POST", "/auth/v1/recover", {"email": to_email})
    if status != 200:
        raise RuntimeError(f"Supabase Auth recover failed ({status}): {body.get('msg', body)}")


def verify_otp_code(email: str, otp: str, purpose: str = "verify") -> bool:
    """Validate a code against Supabase Auth (single-use, time-limited).

    `purpose` maps to the Supabase OTP type: "verify" for email
    confirmation and "reset" for the recovery flow.
    """
    otp_type = {"verify": "email", "reset": "recovery"}.get(purpose, "email")
    status, _ = _request(
        "POST", "/auth/v1/verify", {"type": otp_type, "email": email, "token": otp}
    )
    return status == 200
