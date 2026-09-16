"""Shared HTTP helpers — one UA/timeout policy for all scrapers."""

from __future__ import annotations

import time

import httpx

BROWSER_UA = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"
)

DEFAULT_TIMEOUT = httpx.Timeout(25.0, connect=10.0)

# Retry config for intermittent SSL handshake timeouts.
_MAX_RETRIES = 3
_RETRY_DELAY = 2.0  # seconds between retries


class BotBlockedError(RuntimeError):
    """Raised when a site serves a bot wall (Cloudflare/406) to plain HTTP."""


def client() -> httpx.Client:
    return httpx.Client(
        headers={"User-Agent": BROWSER_UA, "Accept-Language": "en-US,en;q=0.9"},
        timeout=DEFAULT_TIMEOUT,
        follow_redirects=True,
    )


def fetch_text(url: str) -> str:
    """GET a page and return decoded text, mapping bot walls to BotBlockedError."""
    with client() as c:
        resp = c.get(url)
    if resp.status_code in (401, 403, 406, 429):
        raise BotBlockedError(f"{resp.status_code} from {url}")
    resp.raise_for_status()
    return resp.text


def fetch_json(url: str, params: dict | None = None) -> dict | list:
    """GET JSON with retry for intermittent SSL/network timeouts."""
    last_exc: Exception | None = None
    for attempt in range(_MAX_RETRIES):
        try:
            with client() as c:
                resp = c.get(url, params=params)
            resp.raise_for_status()
            return resp.json()
        except (httpx.ConnectTimeout, httpx.ReadTimeout, httpx.RemoteProtocolError) as e:
            last_exc = e
            if attempt < _MAX_RETRIES - 1:
                time.sleep(_RETRY_DELAY * (attempt + 1))
    raise last_exc  # type: ignore[misc]
