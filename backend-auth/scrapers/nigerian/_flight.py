"""Next.js React Flight decoding shared by the Nigerian scrapers.

Listings embed event objects inside self.__next_f.push([1,"..."]) chunks.
Each chunk is a JS string literal, so one json.loads pass decodes the
string layer; each scraper then cuts the decoded text into per-event
segments at its own object-opening marker and reads fields per segment
(per-segment reads stay correct when an event omits a field).
"""

from __future__ import annotations

import json
import re

_FLIGHT_RE = re.compile(r'self\.__next_f\.push\(\[1,("(?:[^"\\]|\\.)*")\]\)', re.DOTALL)


def decode_flight(html: str) -> str:
    parts: list[str] = []
    for match in _FLIGHT_RE.finditer(html):
        try:
            parts.append(json.loads(match.group(1)))
        except (json.JSONDecodeError, ValueError):
            continue
    return " ".join(parts)


def split_segments(blob: str, marker: re.Pattern, max_len: int = 6000) -> list[str]:
    """Cut the blob at each marker match; cap segments so fields can't leak."""
    starts = [m.start() for m in marker.finditer(blob)]
    segments: list[str] = []
    for n, start in enumerate(starts):
        end = starts[n + 1] if n + 1 < len(starts) else start + max_len
        segments.append(blob[start:min(end, start + max_len)])
    return segments


def first_field(segment: str, key: str) -> str:
    """First \"key\":\"value\" in a segment, unescaped (\"\" when absent).

    Handles both Flight-escaped (\\"key\\":\\"v\\") and plain ("key":"v").
    """
    match = re.search(r'\\?"' + re.escape(key) + r'\\?":\\?"((?:[^"\\]|\\.)*)\\?"', segment)
    if not match:
        return ""
    try:
        return json.loads(f'"{match.group(1)}"')
    except (json.JSONDecodeError, ValueError):
        return match.group(1)
