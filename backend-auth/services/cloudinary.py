"""
Cloudinary integration for user avatar images.

Uploads are performed client-side with an unsigned upload preset, so no
credentials are needed in the app. Deleting a replaced avatar, however, is an
Admin API operation and therefore always happens here, server-side, using the
account credentials supplied through the environment.

Environment variables:
    CLOUDINARY_CLOUD_NAME   - the account's cloud name (public)
    CLOUDINARY_API_KEY      - account API key (secret)
    CLOUDINARY_API_SECRET   - account API secret (secret)
"""

import logging
import os
from urllib.parse import urlparse

import httpx

logger = logging.getLogger("soundsphere-auth")

CLOUDINARY_CLOUD_NAME = os.getenv("CLOUDINARY_CLOUD_NAME", "").strip()
CLOUDINARY_API_KEY = os.getenv("CLOUDINARY_API_KEY", "").strip()
CLOUDINARY_API_SECRET = os.getenv("CLOUDINARY_API_SECRET", "").strip()

_ADMIN_API_URL = "https://api.cloudinary.com/v1_1"
_ADMIN_TIMEOUT = httpx.Timeout(15.0)


def _configured() -> bool:
    return bool(CLOUDINARY_CLOUD_NAME and CLOUDINARY_API_KEY and CLOUDINARY_API_SECRET)


def is_cloudinary_url(url: str) -> bool:
    """True when [url] is a media-delivery URL of this account's cloud."""
    if not url or not _configured():
        return False
    try:
        host = urlparse(url).netloc.lower()
    except ValueError:
        return False
    return host == "res.cloudinary.com" and f"/{CLOUDINARY_CLOUD_NAME}/" in url


def public_id_from_url(url: str) -> str | None:
    """Extracts the Cloudinary public_id (folder included, extension dropped)
    from a delivery URL such as
    .../image/upload/v1700000000/soundsphere/avatars/abc123.jpg
    Returns None when the URL has no recognizable upload path."""
    try:
        path = urlparse(url).path
    except ValueError:
        return None
    marker = "/image/upload/"
    idx = path.find(marker)
    if idx < 0:
        return None
    rest = path[idx + len(marker):]
    # Drop the optional /v<version>/ segment that Cloudinary prefixes.
    if rest.startswith("v") and "/" in rest:
        rest = rest.split("/", 1)[1]
    # Drop the file extension from the last path segment.
    filename = rest.rsplit("/", 1)[-1]
    if "." in filename:
        rest = rest.rsplit(".", 1)[0]
    return rest or None


async def delete_avatar(url: str) -> bool:
    """Best-effort deletion of a Cloudinary image from a delivery URL.

    The Admin API delete requires account authentication, which is exactly why
    this runs here and not in the app. Returns True when the resource was
    deleted (or there is nothing to delete); False and logs otherwise. Never
    raises — callers must not fail a user request because of cleanup.
    """
    if not _configured():
        logger.warning("Cloudinary not configured; skipping avatar deletion")
        return False
    if not is_cloudinary_url(url):
        logger.info("Avatar URL is not a Cloudinary URL; nothing to delete")
        return True
    public_id = public_id_from_url(url)
    if not public_id:
        logger.warning("Could not parse Cloudinary public_id from %s", url)
        return False
    endpoint = f"{_ADMIN_API_URL}/{CLOUDINARY_CLOUD_NAME}/resources/image/upload/{public_id}"
    try:
        async with httpx.AsyncClient(timeout=_ADMIN_TIMEOUT) as client:
            response = await client.delete(
                endpoint,
                auth=(CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET),
            )
        if response.status_code == 200:
            logger.info("Deleted Cloudinary avatar %s", public_id)
            return True
        logger.warning(
            "Cloudinary delete returned %d for %s: %s",
            response.status_code,
            public_id,
            response.text[:200],
        )
        return False
    except httpx.HTTPError as exc:
        logger.warning("Cloudinary delete request failed for %s: %s", public_id, exc)
        return False