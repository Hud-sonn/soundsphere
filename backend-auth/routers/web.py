"""Web pages for shared links (/p/{token} and /s/{videoId}).

These are served by the backend — not the static site — so the HTML can
carry real Open Graph / Twitter meta tags (title, description, og:image
artwork) that WhatsApp, iMessage, Discord and search engines read without
executing JavaScript. The visual design mirrors the static website's
dark brand palette; the card is server-rendered so it works even with
JS disabled, and a tiny script attempts the soundsphere:// deep link.

Scrapers ignore the page body and only read <head>; the body exists for
humans who tap the link on a device without the app.
"""

import html
import logging
from string import Template

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import HTMLResponse

from db.supabase import get_supabase
from services.activity import log_activity
from services.limiter import limiter

logger = logging.getLogger("soundsphere-auth")

router = APIRouter()

_WEB_LIMIT = "120/minute"

_ARTWORK_FALLBACK = "https://i.ytimg.com/vi/4vgUQGQPk2k/hqdefault.jpg"

# og:image sizes: playlist uses square artwork; song uses the 16:9 video
# thumbnail from i.ytimg.com (reliable, no auth).

_PAGE_CSS = """
  :root {
    --black: #0a0908;
    --jet: #22333b;
    --cream: #eae0d5;
    --khaki: #c6ac8f;
    --stone: #5e503f;
  }
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    background: var(--black);
    color: var(--cream);
    font-family: 'Space Grotesk', 'Helvetica Neue', Arial, sans-serif;
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 24px;
  }
  .grain {
    position: fixed; inset: 0; pointer-events: none; opacity: 0.04; z-index: 100;
    background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='120' height='120'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
  }
  .card {
    width: 100%; max-width: 420px;
    background: var(--jet);
    border: 1px solid var(--stone);
    border-radius: 16px;
    overflow: hidden;
    position: relative;
  }
  .cover-wrap { width: 100%; background: var(--stone); position: relative; }
  .cover-wrap.square { aspect-ratio: 1 / 1; }
  .cover-wrap.wide { aspect-ratio: 16 / 9; }
  .cover-wrap img { width: 100%; height: 100%; object-fit: cover; display: block; }
  .body { padding: 24px; }
  .kicker {
    font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.18em;
    color: var(--khaki); margin-bottom: 10px;
  }
  h1 { font-size: 1.5rem; font-weight: 600; line-height: 1.25; margin-bottom: 6px; word-break: break-word; }
  .meta { color: var(--khaki); font-size: 0.85rem; margin-bottom: 18px; }
  .tracks { list-style: none; margin-bottom: 22px; }
  .tracks li {
    display: flex; align-items: center; gap: 12px; padding: 8px 0;
    border-bottom: 1px solid rgba(94, 80, 63, 0.35); min-width: 0;
  }
  .tracks li:last-child { border-bottom: none; }
  .tracks .num { color: var(--stone); font-size: 0.8rem; width: 20px; flex-shrink: 0; text-align: right; }
  .tracks .t-art {
    width: 36px; height: 36px; border-radius: 6px; object-fit: cover;
    background: var(--stone); flex-shrink: 0;
  }
  .tracks .t-info { min-width: 0; flex: 1; }
  .tracks .t-title { font-size: 0.88rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .tracks .t-artist { font-size: 0.76rem; color: var(--khaki); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .btn {
    display: block; width: 100%; text-align: center; padding: 14px 18px;
    border-radius: 10px; border: none; cursor: pointer; font-family: inherit;
    font-size: 0.95rem; font-weight: 600; letter-spacing: 0.02em; text-decoration: none;
  }
  .btn-primary { background: var(--cream); color: var(--black); margin-bottom: 10px; }
  .btn-ghost { background: transparent; color: var(--khaki); border: 1px solid var(--stone); }
  .btn:hover { opacity: 0.92; }
  .status { text-align: center; color: var(--khaki); font-size: 0.9rem; padding: 48px 24px; }
"""


def _page_head(title: str, description: str, image: str, url: str, page_title: str) -> str:
    """OG + Twitter meta tags. Scrapers (WhatsApp, iMessage, Discord, bots)
    read exactly these tags; no JavaScript is executed."""
    title_e = html.escape(title)
    desc_e = html.escape(description)
    return f"""<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>{html.escape(page_title)}</title>
<link rel="icon" type="image/png" href="https://soundsphere.name.ng/favicon.png">
<meta name="description" content="{desc_e}">
<meta name="robots" content="noindex, nofollow">
<meta property="og:type" content="music.playlist">
<meta property="og:title" content="{title_e}">
<meta property="og:description" content="{desc_e}">
<meta property="og:image" content="{html.escape(image)}">
<meta property="og:url" content="{html.escape(url)}">
<meta property="og:site_name" content="Soundsphere">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:title" content="{title_e}">
<meta name="twitter:description" content="{desc_e}">
<meta name="twitter:image" content="{html.escape(image)}">
<meta name="theme-color" content="#22333b">"""


_PLAYLIST_PAGE = Template("""<!DOCTYPE html>
<html lang="en">
<head>
${head}
<style>${css}</style>
</head>
<body>
<div class="grain"></div>
<div class="card">
  <div class="cover-wrap square">
    <img src="$cover" alt="Playlist cover">
  </div>
  <div class="body">
    <div class="kicker">Shared playlist</div>
    <h1>$name</h1>
    <div class="meta">$meta</div>
    <ul class="tracks">$tracks</ul>
    <button class="btn btn-primary" id="btn-open">Open in Soundsphere</button>
    <a class="btn btn-ghost" href="https://github.com/Hud-sonn/soundsphere/releases/latest" target="_blank" rel="noopener">Get the app</a>
  </div>
</div>
<script>
document.getElementById("btn-open").addEventListener("click", function () {
  window.location.href = "soundsphere://p/$token";
});
window.location.href = "soundsphere://p/$token";
</script>
</body>
</html>""")

_SONG_PAGE = Template("""<!DOCTYPE html>
<html lang="en">
<head>
${head}
<style>${css}</style>
</head>
<body>
<div class="grain"></div>
<div class="card">
  <div class="cover-wrap wide">
    <img src="$cover" alt="Song artwork">
  </div>
  <div class="body">
    <div class="kicker">Shared on Soundsphere</div>
    <h1>Soundsphere</h1>
    <div class="meta">A song was shared with you — open it in Soundsphere</div>
    <button class="btn btn-primary" id="btn-open">Open in Soundsphere</button>
    <a class="btn btn-ghost" href="https://music.youtube.com/watch?v=$video_id" target="_blank" rel="noopener">Open on YouTube Music</a>
    <a class="btn btn-ghost" href="https://github.com/Hud-sonn/soundsphere/releases/latest" target="_blank" rel="noopener">Get the app</a>
  </div>
</div>
<script>
document.getElementById("btn-open").addEventListener("click", function () {
  window.location.href = "soundsphere://song/$video_id";
});
window.location.href = "soundsphere://song/$video_id";
</script>
</body>
</html>""")


def _track_rows(tracks: list[dict], limit: int = 5) -> str:
    rows = []
    for i, item in enumerate(tracks[:limit]):
        meta = item.get("track") or {}
        art = ""
        if meta.get("artwork_url"):
            art = f'<img class="t-art" src="{html.escape(meta["artwork_url"])}" alt="">'
        rows.append(
            "<li>"
            f'<span class="num">{i + 1}.</span>'
            f"{art}"
            '<div class="t-info">'
            f'<div class="t-title">{html.escape(meta.get("title", ""))}</div>'
            f'<div class="t-artist">{html.escape(meta.get("artist", ""))}</div>'
            "</div></li>"
        )
    return "".join(rows)


@router.get("/p/{token}", response_class=HTMLResponse)
@limiter.limit(_WEB_LIMIT)
async def shared_playlist_page(request: Request, token: str):
    db = get_supabase()
    rows = (
        db.table("playlists")
        .select(
            "id, name, cover_url, share_token, user_id, "
            "playlist_tracks(track_id, position, added_at, tracks(*))"
        )
        .eq("share_token", token)
        .execute()
    )
    if not rows.data:
        raise HTTPException(status_code=404, detail="Playlist not found or sharing disabled")
    playlist = rows.data[0]
    log_activity(None, "page_view", f"playlist {token}")

    raw_tracks = playlist.get("playlist_tracks") or []
    tracks = sorted(raw_tracks, key=lambda t: t.get("position", 0))
    cover = playlist.get("cover_url") or (tracks[0].get("tracks") or {}).get("artwork_url") or _ARTWORK_FALLBACK
    owner = ""
    if playlist.get("user_id"):
        owner_rows = (
            db.table("users").select("username").eq("id", playlist["user_id"]).execute()
        )
        if owner_rows.data:
            owner = owner_rows.data[0].get("username", "")

    name = playlist.get("name", "Untitled playlist")
    count = len(tracks)
    meta_parts = []
    if owner:
        meta_parts.append(f"by {owner}")
    meta_parts.append(f"{count} song" if count == 1 else f"{count} songs")
    meta = " · ".join(meta_parts)

    url = f"https://api.soundsphere.name.ng/p/{html.escape(token, quote=True)}"
    head = _page_head(
        title=name,
        description=f"Shared playlist on Soundsphere{(' by ' + owner) if owner else ''} — {meta}",
        image=cover,
        url=url,
        page_title=f"{name} — Soundsphere shared playlist",
    )
    page = _PLAYLIST_PAGE.substitute(
        head=head,
        css=_PAGE_CSS,
        cover=html.escape(cover),
        name=html.escape(name),
        meta=html.escape(meta),
        tracks=_track_rows(tracks),
        token=html.escape(token, quote=True),
    )
    return HTMLResponse(content=page)


@router.get("/s/{video_id}", response_class=HTMLResponse)
@limiter.limit(_WEB_LIMIT)
async def shared_song_page(request: Request, video_id: str):
    if not video_id or len(video_id) > 64:
        raise HTTPException(status_code=404, detail="Song not found")
    log_activity(None, "page_view", f"song {video_id}")
    cover = f"https://i.ytimg.com/vi/{video_id}/hqdefault.jpg"
    url = f"https://api.soundsphere.name.ng/s/{html.escape(video_id, quote=True)}"
    head = _page_head(
        title="A song was shared with you",
        description="Open this song in Soundsphere — the YouTube Music client that plays exactly what you like.",
        image=cover,
        url=url,
        page_title="Soundsphere — Shared song",
    )
    page = _SONG_PAGE.substitute(
        head=head,
        css=_PAGE_CSS,
        cover=cover,
        video_id=html.escape(video_id, quote=True),
    )
    return HTMLResponse(content=page)