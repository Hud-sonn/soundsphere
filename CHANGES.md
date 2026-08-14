# Soundsphere — Change Log (development)

This file is the running record of everything changed, added, or fixed in
Soundsphere. It exists so nothing is lost between sessions and so the
user-facing release notes (`changelog.md`) are easy to assemble.

**Rule for development agents:** every change, fix, or feature MUST be
recorded here before the task is marked done — a short, concrete bullet
with the area, what changed, and push status (`pushed` / `local`).
Newest date block goes on top.

User-facing release notes live in `changelog.md` (curated by the core
team at release time) — this file is the source they fold from.

## 2026-08-14

### Backend (pushed: `a0e2dc1e`)
- `feat(backend): add public playlist share links` — `share_token` column +
  partial unique index (migration `004_playlist_share_tokens.sql`, applied to
  Supabase), public `GET /share/playlists/{token}` (no JWT, per-IP
  120/minute, never leaks `user_id`), `POST /user/playlists/{id}/share`
  (idempotent get-or-create token) and `DELETE /user/playlists/{id}/share`
  (revoke). Pre-existing playlists get tokens lazily on first share.
- `feat(admin): add admin dashboard with error logging and crash reports`
  (pushed: `fe751738`) — admin UI over `api_error_logs`/`crash_reports`,
  `log_http_errors` middleware writes 4xx/5xx (incl. 429s).
- `feat(website): add WhatsApp feedback group and updates channel links`
  (pushed: `b869e026`).

### Backend (pushed)
- **Live activity feed + real `last_active`** (`bb59f805`): root cause of the
  misleading "active users" numbers — `last_active` was written ONLY in the
  login route, so it measured last login, not app usage. Now every
  authenticated request throttled-updates `users.last_active` (5-min
  per-user cache) and emits throttled `app_use` events. New
  `activity_events` table (migration 005) records logins, signups, share
  link views, page views, errors and crash ingests (fire-and-forget
  threads; zero added request latency). New `GET /admin/activity` resolves
  emails. **Admin dashboard got a "Live" tab**: 5-second polling feed with
  colored event badges, relative times and a pulsing LIVE indicator.
  Verified on prod with a real user (`marthasmith89977` — `last_active`
  jumped from Aug 13 to the moment she used the app).
- **Admin account**: `hirohudson107@gmail.com` is verified + `admin` role,
  password set; login returns admin JWT. Dashboard: https://api.soundsphere.name.ng/admin

### Docs (local)
- `docs/design-system-plan.md`: detailed Material U + Liquid Glass plan —
  theme color decision (keep `#5E503F` seed + dynamic colors, new surface
  treatment), page-by-page changes, button spec, phased implementation,
  risks.
- **OG share pages** (`routers/web.py`, pending push): backend now serves
  `GET /p/{token}` and `GET /s/{videoId}` as real HTML pages with SEO +
  Open Graph + Twitter meta tags (og:image = playlist cover, falling back to
  the first track's artwork; song pages use the i.ytimg.com thumbnail) so
  WhatsApp/iMessage/Discord previews show artwork. Same dark brand design as
  the static site, server-rendered (works without JS), `soundsphere://p/…`
  deep-link attempt kept. Static `website/p.html` + `website/s.html` deleted
  (were never deployed — the old "not found" links were caused by them not
  existing on the static host at all; backend share API was verified live:
  `GET /share/playlists/<token>` → 200 with tracks).
- `main.py`: `web` router registered; **production `ALLOWED_HOSTS` tightened
  to exactly `api.soundsphere.name.ng` + `soundsphere-auth.onrender.com`**
  (no localhost/wildcard — host-spoofing/DNS-rebinding protection; the
  Render env var was updated and a redeploy triggered). Local testing must
  add `localhost` via the env var — documented in AGENTS.md.

### App (local, uncommitted)
- **Backend endpoint failover**: new `data/BackendEndpoint.kt` — primary
  host `https://api.soundsphere.name.ng` (`BuildConfig.API_BASE_URL`),
  automatic sticky fallback to `https://soundsphere-auth.onrender.com`
  (`API_FALLBACK_BASE_URL`) on network errors, switch-back on success.
  Wired into `SyncService` (all 20 authed calls + `getSharedPlaylist` retry
  once on IOException), `AuthService` (all 7 calls via new `withBackend`
  helper), and `RenderKeepAliveWorker` (health ping doubles as endpoint
  health probe). `build.gradle.kts` gets `API_FALLBACK_BASE_URL` for
  default/release/debug; debug still overrides the primary with
  `AUTH_DEV_BASE_URL` (fallback stays production).

### App (local, uncommitted)
- Share strings now use `https://api.soundsphere.name.ng/p/{token}` and
  `/s/{videoId}` (backend-served OG pages) instead of the static
  `p.html`/`s.html` links.
- Sidebar fixes (needs build + install): profile header now recognizes the
  **Soundsphere account** login (was showing "Guest / sign in" because it
  only reflected the YouTube account) — new `SoundsphereEmailKey` +
  `SoundsphereUsernameKey` prefs cached from `/auth/me` by `AuthViewModel`
  via `SyncRepository.cacheAccountProfile`, cleared on logout; display name
  falls back to Soundsphere username, subtitle shows the registered email
  or "Signed in to Soundsphere". Drawer now closes smoothly after tapping
  any item (account, changelog, navigation). Home/Library/Search removed
  from the drawer (they're in the bottom nav); only Stats/History/
  Together + App section remain.
- Weekly/monthly auto playlists: **kept the plain-YouTube-link fallback**
  for sharing (they're local-only, never get server IDs — confirmed backend
  assigns IDs/tokens fine, e.g. "NF AI Mix" has token `gqrtAitPlJfPwGj5xWPu6A`).
- Playlist sharing end-to-end: `soundsphere://p/{token}` deep link (custom
  scheme, works on every flavor), `SharedPlaylistScreen` +
  `SharedPlaylistViewModel`, share actions in playlist menus now share a
  branded message via server token (falls back to the plain YouTube link /
  song list when unsynced; "Sharing isn't available yet" toast when signed
  out). `share_unavailable` string finally wired.
- **Fix:** share button on the local playlist menu did nothing — the coroutine
  ran on a scope inside the bottom sheet and was cancelled on dismiss; now
  uses the screen-level scope that outlives the sheet.
- Song deep link `soundsphere://song/{videoId}` handled in `MainActivity`;
  song share messages now use the branded landing page
  `https://soundsphere.name.ng/s.html?videoId=…` so links no longer open
  YouTube Music instead of Soundsphere. Player share card text updated too.
- New **AI** settings section: "AI playlists" master toggle (hides the AI FAB
  on Library → Playlists and the AI card in search); "AI lyrics translation"
  row moved under it.
- Homepage sidebar redesign: top bar is now logo + hamburger only (History,
  Stats, Listen Together and Account buttons removed); new
  `ModalNavigationDrawer` with profile header (avatar, username, registered
  email; tap → account dialog), Music section (Home/Library/Search/Stats/
  History/Together), App section (Settings/Changelog/Updates with update
  badge/About), version footer. New `hamburger.xml` vector drawable.
- Carried-over local work from earlier sessions (still uncommitted): AI
  artist relations, NonCancellable save fix, AI naming polish, 422 prompt
  guard.

### Website (local, uncommitted)
- `p.html` and `s.html` **deleted** — replaced by the backend-served
  `/p/{token}` + `/s/{videoId}` pages (static hosting can't provide
  per-token Open Graph tags; scrapers don't run JS). Rest of the website
  stays pure HTML.

### Design
- `docs/design-options.md` added — two theme direction proposals:
  "Material U" (dynamic-color-first, rounder shapes, song-art seed colors)
  and "Liquid Glass" (translucent blurred surfaces over artwork). No code
  changes yet; waiting on direction.

### Known / pending
- `api_error_logs` and `crash_reports` tables have **RLS disabled** — exposed
  to anon/authenticated roles via the anon key. Recommended fix (needs human
  approval): enable RLS with no policies (backend uses the service role,
  which bypasses RLS).
- APK install testing on the TECNO device requires the device to be plugged
  in with free space (`/data` was 98% full).