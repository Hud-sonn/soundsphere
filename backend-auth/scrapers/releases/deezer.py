"""Deezer public API — artist lookup then discography with record types.

Keyless. record_type is authoritative (album/ep/single), which makes this
the better singles source next to iTunes.
"""

from __future__ import annotations

from ..common.http import fetch_json
from ..common.models import ReleaseInfo

_API = "https://api.deezer.com"


def find_artist_id(artist_name: str) -> int:
    data = fetch_json(f"{_API}/search/artist", params={"q": artist_name})
    items = data.get("data", []) if isinstance(data, dict) else []
    target = artist_name.strip().lower()
    for item in items:
        if str(item.get("name", "")).strip().lower() == target:
            return int(item.get("id") or 0)
    return int(items[0].get("id")) if items else 0


def artist_releases(artist_name: str, limit: int = 100) -> list[ReleaseInfo]:
    artist_id = find_artist_id(artist_name)
    if not artist_id:
        return []
    data = fetch_json(f"{_API}/artist/{artist_id}/albums", params={"limit": limit})
    items = data.get("data", []) if isinstance(data, dict) else []
    releases: dict[int, ReleaseInfo] = {}
    for item in items:
        album_id = int(item.get("id") or 0)
        if not album_id:
            continue
        releases[album_id] = ReleaseInfo(
            source="deezer",
            source_id=str(album_id),
            artist=artist_name.strip(),
            title=str(item.get("title", "")).strip(),
            release_date=str(item.get("release_date", ""))[:10],
            album_type=str(item.get("record_type", "")).lower() or "album",
            artwork=str(item.get("cover_xl") or item.get("cover_big") or ""),
            url=str(item.get("link", "")),
            track_count=int(item.get("nb_tracks") or 0),
        )
    return sorted(releases.values(), key=lambda r: r.release_date, reverse=True)


def recent_releases(artist_name: str, since: str) -> list[ReleaseInfo]:
    return [r for r in artist_releases(artist_name) if r.release_date >= since]
