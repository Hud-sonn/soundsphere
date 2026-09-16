"""Plain data models shared by scrapers and (later) the events router."""

from __future__ import annotations

from dataclasses import asdict, dataclass, field


@dataclass
class EventInfo:
    source: str = ""
    source_id: str = ""
    title: str = ""
    url: str = ""
    start: str = ""  # ISO datetime when the site provides one, else ""
    end: str = ""
    venue: str = ""
    city: str = ""
    country: str = ""
    image: str = ""
    category: str = ""
    description: str = ""
    artists: list[str] = field(default_factory=list)
    lineup: list[str] = field(default_factory=list)  # supporting artists (Bandsintown)
    ticket_url: str = ""
    sold_out: bool = False

    def to_dict(self) -> dict:
        return asdict(self)


@dataclass
class ReleaseInfo:
    source: str = ""
    source_id: str = ""
    artist: str = ""
    title: str = ""
    release_date: str = ""  # YYYY-MM-DD when precision allows
    album_type: str = ""  # album | single | ep | compilation
    artwork: str = ""
    url: str = ""
    track_count: int = 0

    def to_dict(self) -> dict:
        return asdict(self)
