import json
import os
import secrets
import smtplib
import socket
import ssl
import urllib.request
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

SMTP_HOST = "smtp.gmail.com"


def generate_otp() -> str:
    return f"{secrets.randbelow(900000) + 100000}"


def _resolve_host(host: str) -> list[str]:
    """Resolve a host to a list of IPs, in resolution order.

    Render free instances can fail to reach Gmail SMTP with
    `[Errno 101] Network is unreachable`: their resolver occasionally
    returns no A records (only AAAA), and the instances have no IPv6 route.
    As a fallback we resolve over DNS-over-HTTPS on port 443, which is
    reachable from the instances (the Supabase REST API uses it).
    """
    addrs: list[str] = []
    try:
        for info in socket.getaddrinfo(host, None, socket.SOCK_STREAM):
            ip = info[4][0]
            if ip not in addrs:
                addrs.append(ip)
    except OSError:
        pass
    if addrs:
        return addrs
    try:
        req = urllib.request.Request(
            f"https://dns.google/resolve?name={host}&type=A",
            headers={
                "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/126.0 Safari/537.36",
                "Accept": "application/json",
            },
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            for answer in json.load(resp).get("Answer", []):
                if answer.get("type") == 1 and answer.get("data") not in addrs:
                    addrs.append(answer["data"])
    except Exception:
        pass
    return addrs


def _raw_connect(host: str, port: int, timeout: int = 15) -> socket.socket:
    """Connect a TCP socket to the host, trying every resolved address."""
    last_error: Exception | None = None
    for ip in _resolve_host(host):
        try:
            return socket.create_connection((ip, port), timeout)
        except OSError as e:
            last_error = e
    raise OSError(f"Could not connect to {host}:{port}") from last_error


class _SmtpSmtp(smtplib.SMTP):
    """SMTP (STARTTLS) that connects through the resilient resolver."""

    def _get_socket(self, host, port, timeout):
        return _raw_connect(host, port, timeout)


class _SmtpSsl(smtplib.SMTP_SSL):
    """SMTPS (implicit TLS) that connects through the resilient resolver."""

    def _get_socket(self, host, port, timeout):
        sock = _raw_connect(host, port, timeout)
        if self.context is not None:
            sock = self.context.wrap_socket(sock, server_hostname=host)
        return sock


def _starttls(server: smtplib.SMTP, host: str) -> None:
    """Upgrade an SMTP connection to TLS, verifying the original hostname.

    smtplib.starttls() accepts no server_hostname in some Python versions,
    which would make certificate verification fail against a bare IP, so we
    drive the STARTTLS handshake ourselves.
    """
    server.ehlo_or_helo_if_needed()
    if not server.has_extn("starttls"):
        raise smtplib.SMTPNotSupportedError("STARTTLS extension not supported by server")
    resp, reply = server.docmd("STARTTLS")
    if resp != 220:
        raise smtplib.SMTPResponseException(resp, reply)
    context = ssl.create_default_context()
    tls = context.wrap_socket(server.sock, server_hostname=host)
    server.sock = tls
    server.file = tls.makefile("rb")
    server.ehlo_or_helo_if_needed()


def _send(server: smtplib.SMTP, user: str, app_pw: str, msg: MIMEMultipart, to_email: str, use_ssl: bool):
    if not use_ssl:
        _starttls(server, SMTP_HOST)
    server.login(user, app_pw)
    server.sendmail(msg["From"], to_email, msg.as_string())


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

    last_error: Exception | None = None
    for port, use_ssl in ((587, False), (465, True)):
        server = None
        try:
            server = _SmtpSsl(SMTP_HOST, port, timeout=15) if use_ssl else _SmtpSmtp(SMTP_HOST, port, timeout=15)
            with server:
                _send(server, user, app_pw, msg, to_email, use_ssl)
            return
        except (OSError, smtplib.SMTPException, ssl.SSLError) as e:
            last_error = e
            if server is not None:
                try:
                    server.close()
                except Exception:
                    pass
    if last_error is not None:
        raise last_error
    raise OSError(f"Could not send email to {to_email}")
