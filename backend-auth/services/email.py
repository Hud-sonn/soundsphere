import os
import secrets
import smtplib
import socket
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText


def generate_otp() -> str:
    return f"{secrets.randbelow(900000) + 100000}"


def _smtp_connect(host: str, port: int, timeout: int = 15) -> smtplib.SMTP:
    """Connect to an SMTP server over IPv4 only.

    smtplib prefers IPv6 addresses when the resolver returns them, and
    Render free instances have no IPv6 route, which surfaces as
    `[Errno 101] Network is unreachable`. Resolving explicitly to IPv4
    addresses sidesteps that entirely.
    """
    last_error: Exception | None = None
    for addr in socket.getaddrinfo(host, port, socket.AF_INET, socket.SOCK_STREAM):
        server = None
        try:
            server = smtplib.SMTP(timeout=timeout)
            server.connect(addr[4][0], port)
            return server
        except (OSError, smtplib.SMTPException) as e:
            last_error = e
            if server is not None:
                try:
                    server.close()
                except Exception:
                    pass
    raise OSError(f"Could not connect to {host}:{port}") from last_error


def send_otp_email(to_email: str, otp: str, purpose: str = "verify"):
    user = os.getenv("GMAIL_USER")
    app_pw = os.getenv("GMAIL_APP_PASSWORD")
    if not user or not app_pw:
        raise RuntimeError("GMAIL_USER and GMAIL_APP_PASSWORD must be set")

    subject_map = {
        "verify": "Soundsphere — Your verification code",
        "reset": "Soundsphere — Password reset code",
        "resend": "Soundsphere — New verification code",
    }

    msg = MIMEMultipart("alternative")
    msg["Subject"] = subject_map.get(purpose, subject_map["verify"])
    msg["From"] = user
    msg["To"] = to_email

    html = f"""
    <div style="background:#0A0908;padding:40px 20px;font-family:sans-serif;">
      <div style="max-width:400px;margin:0 auto;">
        <h1 style="color:#C6AC8F;font-size:14px;letter-spacing:4px;text-transform:uppercase;">Soundsphere</h1>
        <p style="color:#A89890;font-size:13px;margin-top:24px;">
          {purpose.replace('_', ' ').title()} — enter this code in the app:
        </p>
        <div style="background:#22333B;border-radius:12px;padding:24px;text-align:center;margin:24px 0;">
          <span style="font-size:36px;font-weight:bold;letter-spacing:8px;color:#C6AC8F;">{otp}</span>
        </div>
        <p style="color:#6B5B53;font-size:11px;">This code expires in 10 minutes.</p>
        <p style="color:#6B5B53;font-size:11px;margin-top:8px;">If you didn't request this, ignore this email.</p>
      </div>
    </div>
    """
    msg.attach(MIMEText(html, "html"))

    with _smtp_connect("smtp.gmail.com", 587) as server:
        server.starttls()
        server.login(user, app_pw)
        server.sendmail(msg["From"], to_email, msg.as_string())
