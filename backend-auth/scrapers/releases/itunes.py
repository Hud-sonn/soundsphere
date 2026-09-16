"""iTunes Search API — albums + singles in one keyless query.

entity=album returns collections of every size; singles carry a
"- Single" name suffix and trackCount 1 (verified 2026-09-15).
"""

from __future__ import annotations

from ..common.http import fetch_json
from ..common.models import ReleaseInfo

_SEARCH_URL = "https://itunes.apple.com/search"


def _album_type(name: str, track_count: int) -> str:
    if name.lower().endswith("- single") or track_count <= 1:
        return "single"
    if name.lower().endswith("- ep") or track_count <= 6:
        return "ep"
    return "album"


def search_artist_releases(artist_name: str, limit: int = 200) -> list[ReleaseInfo]:
    data = fetch_json(
        _SEARCH_URL,
        params={"term": artist_name, "entity": "album", "limit": limit},
    )
    target = artist_name.strip().lower()
    releases: dict[int, ReleaseInfo] = {}
    results = data.get("results", []) if isinstance(data, dict) else []
    for item in results:
        if not isinstance(item, dict) or item.get("wrapperType") != "collection":
            continue
        if str(item.get("artistName", "")).strip().lower() != target:
            continue  # exact-artist only; compilations handled by the caller
        collection_id = int(item.get("collectionId") or 0)
        if not collection_id:
            continue
        name = str(item.get("collectionName", "")).strip()
        releases[collection_id] = ReleaseInfo(
            source="itunes",
            source_id=str(collection_id),
            artist=str(item.get("artistName", "")).strip(),
            title=name,
            release_date=str(item.get("releaseDate", ""))[:10],
            album_type=_album_type(name, int(item.get("trackCount") or 0)),
            artwork=str(item.get("artworkUrl100", "")).replace("100x100bb", "600x600bb"),
            url=str(item.get("collectionViewUrl", "")),
            track_count=int(item.get("trackCount") or 0),
        )
    return sorted(releases.values(), key=lambda r: r.release_date, reverse=True)


def recent_releases(artist_name: str, since: str) -> list[ReleaseInfo]:
    """Releases on/after since (YYYY-MM-DD) — the new-release radar query."""
    return [r for r in search_artist_releases(artist_name) if r.release_date >= since]
