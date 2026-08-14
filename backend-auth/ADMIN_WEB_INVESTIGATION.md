# Admin Web — Investigation & Design

Research report for an operator dashboard ("admin web") for Soundsphere's backend.
Prepared 2026-08-13. No code was changed; this document is the only deliverable.

---

## 1. Backend structure (backend-auth/)

### Stack
- **FastAPI 0.111** + uvicorn, Python 3.12 (`requirements.txt`), deployed on Render free tier
  (`rootDir: backend-auth`, start command `uvicorn main:app --host 0.0.0.0 --port $PORT`, service `srv-d9muocgae00c73ah4aug`, https://soundsphere-auth.onrender.com).
- **Supabase** (project `ysfktparruosuegzdnwt`) accessed only via the **service role key**
  (`db/supabase.py` singleton) — the Android app never talks to Supabase directly.
- Env vars required at boot (`main.py`): `SUPABASE_URL`, `SUPABASE_SERVICE_KEY`, `JWT_SECRET`,
  `GMAIL_USER`, `GMAIL_APP_PASSWORD`. Optional: `JWT_EXPIRE_MINUTES` (default 10080 = 7 days),
  `ALLOWED_HOSTS` (default `localhost,127.0.0.1,*.onrender.com`), `ALLOWED_ORIGINS`
  (default `http://localhost:8081,http://localhost:3000`), `GROQ_API_KEY`/`GROQ_MODEL`/`GROQ_URL`.
- CORS allow-list (`ALLOWED_ORIGINS`) must be extended before any browser-based admin UI
  can call the API.

### JWT auth (`auth/jwt.py`)
- HS256, payload `{sub: user_id, role: "user"|"admin", exp}`.
- `create_token(user_id, role="user")` — role comes from `users.role` DB column at login/verify
  (`routers/auth.py` login/verify: `create_token(user["id"], role=user.get("role", "user"))`).
- Dependencies:
  - `get_current_user` → returns `user_id` (401 if missing/invalid/expired).
  - `get_current_user_info` → dict `{user_id, role}`.
  - `get_optional_user` → `""` when no/invalid token.
  - **`admin_required` already exists** → 401 without token, **403 unless `role == "admin"`**,
    returns `{user_id, role}`. An admin router can plug into this dependency with zero new auth code.
- **How roles are set:** only by the `users.role` column (read at token issue). There is **no**
  endpoint to change roles today — an admin (or a migration/manual Supabase update) is needed.
  The JWT itself is not re-checked against the DB per request (role is trusted from the claim
  until expiry), so a role change takes effect on next login/token refresh.
- Tokens are stored app-side in EncryptedSharedPreferences (`AuthRepository`), cleared on 401.

### Rate limiting (`services/limiter.py`)
- slowapi `Limiter(key_func=get_remote_address)` — **per-IP**, in-memory (no Redis; resets on
  service restart). Per-endpoint limits:
  - `POST /auth/register` 5/hour, `/auth/verify` 10/hour, `/auth/resend-otp` 3/hour,
    `/auth/login` 10/hour, `/auth/forgot-password` 5/hour, `/auth/reset-password` 5/hour
  - `/user/*` read 300/hour, write 120/hour
  - `POST /ai/generate-playlist` 10/day, `POST /ai/detect-artist` 30/hour
  - `GET /health` 100/minute
- Admin endpoints should get their own (generous or absent) limits; note slowapi counts by IP,
  so a NATed admin office network could hit limits — keep admin limits high or exempt.

### Existing endpoints
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/health` | none | returns `{status, version}`; pinged by the app every 15 min (RenderKeepAliveWorker) |
| POST | `/auth/register` | none | OTP-first; writes `pending_registrations`, creates Supabase Auth user |
| POST | `/auth/verify` | none | creates `users` row, returns JWT |
| POST | `/auth/resend-otp` | none | |
| POST | `/auth/login` | none | updates `last_active`, returns JWT |
| POST | `/auth/forgot-password` / `/auth/reset-password` | none | Supabase Auth recovery codes |
| GET | `/auth/me` | Bearer | returns user row |
| GET/PUT | `/user/profile` | Bearer | username / avatar_url |
| GET/POST | `/user/liked`, `/user/liked/{id}` DELETE | Bearer | upserts `tracks` + `liked_tracks` |
| GET/POST | `/user/playlists`, `/user/playlists/{id}` GET/PUT/DELETE | Bearer | `playlists` + nested `playlist_tracks` |
| POST/DELETE | `/user/playlists/{id}/tracks`, `/user/playlists/{id}/tracks/{track_id}` | Bearer | |
| GET/POST/DELETE | `/user/history` | Bearer | max 500 pulled |
| GET/POST/DELETE | `/user/follows`, `/user/follows/{artist_id}` | Bearer | `followed_artists` |
| GET/PUT | `/user/settings` | Bearer | `user_settings` JSONB |
| POST | `/ai/generate-playlist` | Bearer + consent | Groq LLM → InnerTube search resolution |
| POST | `/ai/detect-artist` | Bearer | artist detection for search bar |

There is **no admin router, no /metrics, no structured logging endpoint** today. Unhandled
exceptions are caught by a global handler that logs only the exception *type* (`main.py:92`).

### Migrations
Only two delta migrations live in the repo (`migrations/001_otp_code_tracking.sql`,
`002_user_settings.sql`); the base schema was created in the Supabase dashboard and is not in
git. Column lists below are inferred from code usage (`routers/*.py`, `services/email.py`,
`main.py`), not from live introspection.

---

## 2. Database data inventory (Supabase, from code + migrations)

| Table | Columns (inferred) | Contents / admin usefulness |
|---|---|---|
| `users` | `id` (uuid PK), `email`, `username`, `password_hash`, `avatar_url`, `auth_provider` (`email`), `is_verified` (bool), `role` (default `"user"`), `created_at`, `last_active` | **Core user table.** Admin value: total users, verified/unverified split, signups/day (created_at), active users (last_active), role distribution, per-user lookup. |
| `otp_codes` | `id`, `email`, `purpose` (`verify`/`reset`), `created_at`, `expires_at`, `notified` (bool), `otp_hash` (nullable; no longer stored) | OTP send-tracking only (codes live in Supabase Auth). Admin value: failed-email delivery signal (rows older than 10 min never `notified`). Rows pruned by the backend; table stays small. |
| `pending_registrations` | `email`, `username`, `password_hash` | Half-finished registrations (email sent, OTP not redeemed). Admin value: **funnel metric** — abandoned registrations, recovery targets. |
| `tracks` | `id` (PK, = YouTube video id), `title`, `artist`, `album`, `duration`, `artwork_url`, `source` (`youtube`/`audius`), `genre`, `year` | Denormalized track metadata shared across users (upsert by id). Admin value: total tracks, top artists/songs by global likes. |
| `liked_tracks` | `user_id`, `track_id`, `liked_at` (PK composite via upsert `on_conflict=user_id,track_id`) | Likes. Admin value: total likes, likes/day, avg likes/user, top tracks. |
| `playlists` | `id` (uuid PK), `user_id`, `name`, `cover_url`, `created_at`, `updated_at`, `track_count` (denormalized, recounted on change) | Admin value: playlist count, avg playlist size, top creators. |
| `playlist_tracks` | `playlist_id`, `track_id`, `position`, `added_at` | Join table. Admin value: total playlist entries, growth rate. |
| `history` | `user_id`, `track_id`, `played_at` (no PK constraint enforced in code; rows appended) | Playback history (server mirror of local events). **Grows unbounded — no pruning exists.** Admin value: plays/day, DAU proxy, top tracks/artists, retention analysis. |
| `followed_artists` | `user_id`, `artist_id`, `artist_name`, `followed_at` (upsert on `user_id,artist_id`) | Admin value: total follows, top artists. |
| `user_settings` | `user_id` (PK, FK→users ON DELETE CASCADE), `settings` (jsonb), `created_at`, `updated_at` | Per-user JSONB settings mirror (dark mode, theme, `ai_playlist_consent`, …). Admin value: **AI consent opt-in rate**, feature adoption (dark mode %, etc.). |
| `auth.users` (Supabase Auth, out of band) | — | Handled by Supabase, not the backend; OTP/reset codes live here. |

**Admin-dashboard-friendly aggregates (all computable via the supabase-py service-role client):**
- User counts: total, verified, new in last 7/30 days, users active in last 7/30 days (`last_active`).
- Content: total likes, playlists, playlist tracks, history rows, followed artists, tracks.
- AI usage: count of `user_settings` rows with `settings->>'ai_playlist_consent' = 'true'`;
  note there is **no dedicated AI-usage counter** today (no table records generate-playlist calls).
- Auth funnel: `pending_registrations` rows older than N minutes = abandoned signups;
  `otp_codes` rows with `notified=false` past `expires_at` = undelivered codes.
- Health of sync: users with `last_active` within 24h vs total logged-in accounts.

---

## 3. App-side data & crash reporting

### What the app knows locally
- **Room DB** (`app/src/main/kotlin/com/soundsphere/music/db/entities/`): `song`, `album`,
  `artist`, `playlist`, `playlist_song_map`, `song_artist_map`, `song_album_map`,
  `album_artist_map`, `format`, `play_count`, `lyrics`, `set_video_id`, `speed_dial_item`,
  `event` (playback history: `songId`, `timestamp`, `playTime`), `podcast`, `search_history`,
  `recognition_history`, `related_song_map`, `artist_page_cache`.
  Room schema files exported to `app/schemas/` (KSP `room.schemaLocation`).
- **DataStore preferences** (`constants/PreferenceKeys.kt`): ~150 keys — theme (`darkMode`,
  `pureBlack`, `dynamicTheme`, `selectedThemeColor`), AI consent (`ai_playlist_consent`),
  pause-listening/search history, proxy settings, LastFM session, Listen Together session,
  sync timestamps (`last_like_song_sync`, …), updater state, etc.
- **Auth** (`data/AuthRepository.kt`): JWT in Keystore-backed EncryptedSharedPreferences
  (`soundsphere_auth` / `auth_token`), plain-prefs fallback on Keystore failure (logged via
  Timber).
- **Sync** (`data/SyncRepository.kt`): union-merge sync of likes/playlists/history/follows/
  settings; DataStore keys `sync_playlist_map` (local playlist id ↔ server uuid) and
  `sync_pending_like_pushes` (offline like retries); on 401 clears the token.
- **BuildConfig** (`app/build.gradle.kts`): `API_BASE_URL` = `https://soundsphere-auth.onrender.com`
  (debug overridable via `AUTH_DEV_BASE_URL`), `versionName 1.1.7` / `versionCode 9`,
  `ARCHITECTURE "universal"`, `LASTFM_API_KEY`/`LASTFM_SECRET` (from env/secrets),
  `CAST_AVAILABLE`/`UPDATER_AVAILABLE` per flavor.
  **Flavors are `foss` (default), `gms`, `izzy`** — there is no "fossil" flavor.
- App already pings `/health` every 15 min (`utils/RenderKeepAliveWorker.kt`) to keep the free
  Render instance awake — precedent for app→backend telemetry traffic.

### Crash reporting today
- **No Crashlytics / ACRA / Sentry / Firebase** anywhere in the app.
- `utils/CrashHandler.kt`: `Thread.setDefaultUncaughtExceptionHandler` installed first thing in
  `App.onCreate` (`App.kt:72`). On crash it builds a plain-text log
  (`buildCrashLog`: manufacturer, device model, Android version/SDK, app version name/code,
  stack trace), launches `ui/screens/CrashActivity` with it, then kills the process.
- `utils/Utils.kt:25 reportException(Throwable)` — helper used for Timber-only logging of
  non-fatal exceptions. No network upload exists anywhere.

### Crash-log ingestion design sketch (app → backend)
- **Where to hook (app):** extend `CrashHandler.buildCrashLog`/`uncaughtException` to
  fire-and-forget POST before launching CrashActivity; non-fatals via `reportException`.
  New call would live in `api/SyncService.kt` style (OkHttp + `BuildConfig.API_BASE_URL`),
  or a small `CrashReporter` class injected via Hilt. Best to send with the **existing JWT**
  (`AuthRepository.getToken()`) so the server can attribute crashes to users without the app
  sending PII in the body.
- **Payload** (small, JSON):
  ```json
  {
    "app_version": "1.1.7",
    "app_version_code": 9,
    "flavor": "foss",
    "os": "Android 14 (SDK 34)",
    "device_model": "Pixel 8",
    "manufacturer": "Google",
    "exception": "java.lang.NullPointerException",
    "message": "…",
    "stack_trace": "… (truncated ~8–16 KB)",
    "thread_name": "main",
    "fatal": true,
    "reported_at": "2026-08-13T10:00:00Z"
  }
  ```
- **Triggers:** fatal crash (in `uncaughtException`), optionally non-fatal exceptions in
  `reportException`; rate-limit to e.g. 1 batch per app session and 5/day/user client-side.
- **Privacy:** never send email/username/device identifiers; user attribution comes from the
  JWT `sub` only on the server; device model + OS version are coarse and acceptable; keep the
  feature behind the existing AI-consent-style opt-in or a new "send crash reports" setting
  (default on for fatal crashes is common, but document it); stack traces can contain local
  file paths — truncate and strip `file://` paths before upload. Supabase service role means
  the endpoint must enforce auth + rate limits itself.

---

## 4. Admin web design proposal

### Option chosen: add an `/admin` router to the existing backend, served by the same Render service
Rationale:
1. **Zero new infrastructure.** Render free tier already runs `soundsphere-auth`; the
   service-role Supabase client and JWT plumbing already exist. A separate FastAPI app would
   duplicate env secrets, CORS, rate limits, and a second always-on Render free instance
   (free tier sleeps; the keep-alive worker only pings one URL).
2. **`admin_required` dependency already exists** (`auth/jwt.py:63`) — role-gating is a
   one-liner per endpoint, matching the repo's existing patterns (same `get_supabase()`,
   same slowapi decorators, same `_require_user` helper style).
3. **Static admin UI can be served by the same app** under `/admin` (FastAPI `StaticFiles`),
   mirroring the repo precedent of a plain HTML site (`website/index.html` — hand-written
   HTML/CSS/JS, no framework). No build step, deployable with the existing
   `uvicorn main:app` start command.
4. Security note: the admin surface is then exposed on the public host; mitigate with
   `admin_required` on every route (403 for non-admin), high-but-present rate limits,
   `ALLOWED_ORIGINS` updated to include the admin origin, and `TrustedHostMiddleware` already
   restricting hosts to `*.onrender.com` (already in place).

### How roles get assigned
- No endpoint exists. Two options: (a) one-time manual Supabase update
  (`update public.users set role='admin' where email='operator@…'`), or (b) an admin-only
  endpoint `POST /admin/users/{id}/role` (role-gated) so the first admin can bootstrap others.
  Because tokens cache the role claim, the admin must re-login after the change.

### Proposed admin endpoints (`routers/admin.py`, prefix `/admin`, all `Depends(admin_required)`)
| Method | Path | Purpose |
|---|---|---|
| GET | `/admin/health` | service version, uptime-ish info, env feature flags (`GROQ_API_KEY` set?) |
| GET | `/admin/stats/overview` | one-shot dashboard: users (total/verified/new 7d/new 30d/active 7d/active 30d), likes, playlists, playlist_tracks, history rows, follows, tracks, AI consent count |
| GET | `/admin/stats/users?days=30` | daily signup + active series (for a chart) |
| GET | `/admin/stats/activity?days=30` | daily likes / playlist additions / history plays |
| GET | `/admin/stats/ai` | consent rate, and (if a counter is added later) generation counts |
| GET | `/admin/users?search=&role=&limit=` | user list w/ role, verified, last_active, created_at |
| GET | `/admin/users/{id}` | full user detail + row counts (likes/playlists/history/follows/settings) |
| PUT | `/admin/users/{id}/role` | set/revoke admin role (bootstrap path) |
| GET | `/admin/crashes?limit=&fatal=&app_version=` | crash reports list |
| GET | `/admin/crashes/{id}` | full crash detail |
| DELETE | `/admin/crashes/{id}` | mark handled / delete |
| POST | `/admin/crashes/ingest` | **not** admin-gated — this is the app-side upload endpoint (see below) |

Auth mechanism: reuse `admin_required` (JWT Bearer + role claim). Rate limiting: slowapi
per-IP with generous limits (`1000/hour` reads), plus a higher limit on the ingest endpoint
(`60/hour` per IP) — the app only crashes occasionally, so 60/h is far above real traffic.

### Proposed admin web UI stack
- **Plain HTML + CSS + vanilla JS** served as static files by FastAPI at `/admin` (repo
  precedent: `website/index.html`). Single `index.html` + `admin.js` + minimal CSS, or
  `StaticFiles(directory="admin_web", html=True)` mounted under `/admin`.
- Login flow: small login form → `POST /auth/login` (existing endpoint) → store JWT in
  `localStorage` (or sessionStorage) → attach `Authorization: Bearer` to all calls; show 403
  page if role ≠ admin. No framework, no build step, no npm — consistent with the project's
  existing static-site approach and free-tier constraints.

### Crash-log ingestion design
- **Migration** `migrations/003_crash_reports.sql`:
  ```sql
  create table if not exists public.crash_reports (
    id uuid primary key default gen_random_uuid(),
    user_id uuid references public.users (id) on delete set null,
    app_version text not null,
    app_version_code int not null default 0,
    flavor text not null default '',
    os text not null default '',
    device_model text not null default '',
    manufacturer text not null default '',
    exception text not null default '',
    message text not null default '',
    stack_trace text not null,
    thread_name text not null default '',
    fatal boolean not null default true,
    reported_at timestamptz not null default now(),
    handled boolean not null default false
  );
  create index if not exists crash_reports_reported_at_idx
    on public.crash_reports (reported_at desc);
  create index if not exists crash_reports_app_version_idx
    on public.crash_reports (app_version);
  ```
  (Follows repo migration conventions: `if not exists`, explicit table, index on the
  dashboard's primary sort key. `user_id` nullable + `on delete set null` so deleting an
  account doesn't destroy crash telemetry.)
- **Endpoint:** `POST /admin/crashes/ingest`, auth via `get_optional_user`-style (accept JWT
  if present, allow anonymous so crashes before login are still captured), schema
  `CrashReportRequest` in `models/schemas.py` (mirrors payload above, stack_trace capped ~16KB
  by Pydantic `max_length`), inserts via `get_supabase()`. No RLS concerns — service role.
- **App integration points:** `utils/CrashHandler.kt` (add upload in `uncaughtException`
  before `killProcess`; reuse the existing `buildCrashLog` data, split into structured JSON),
  `utils/Utils.kt` `reportException` for non-fatals, new Hilt-injected
  `data/CrashReporter.kt` using OkHttp + `BuildConfig.API_BASE_URL` + optional JWT from
  `AuthRepository`. Optionally gate behind a new DataStore key (e.g. `CrashReportingEnabledKey`).
- **Privacy notes:** no email/username in payload; user attributed server-side via JWT `sub`;
  truncate stack traces; strip local paths; documented opt-in/opt-out setting; DELETE retention
  path for the operator; consider purging `stack_trace` after N days via a cron/SQL job.

### Deployment on Render
1. Add `routers/admin.py` + `models` additions + static `admin_web/` dir; wire
   `app.include_router(admin_router)` in `main.py`.
2. Apply `migrations/003_crash_reports.sql` in the Supabase SQL editor (repo convention: SQL
   files applied manually; no auto-migration tool).
3. Update `ALLOWED_ORIGINS` env var on service `srv-d9muocgae00c73ah4aug` to include the admin
   page origin (same host → same-origin if served from `/admin`, so this may be unnecessary;
   keep `*.onrender.com` in `ALLOWED_HOSTS` — already default).
4. Set the first admin: `update public.users set role='admin' where email='…';` in Supabase;
   the operator logs in on the admin page (existing `/auth/login`).
5. **Same service** — commit to `main` triggers auto-deploy (autoDeploy=yes). No new service,
   no render.yaml changes. The app's existing keep-alive ping keeps the instance warm.
6. (Optional) if a standalone admin origin is ever wanted, the repo's static-site pattern
   (`soundsphere-website` static service serving `website/`) is the precedent — but a static
   site cannot hold secrets, so the API must still come from the backend service.

---

## 5. Open questions / caveats
- **Roles:** no current admin exists (need manual SQL bootstrap) and role claims are cached in
  JWTs until expiry (7 days default) — after promoting someone, they must re-login.
- **No AI usage counter** exists; `crash_reports` and an optional `ai_usage_log` would be the
  only new telemetry tables. Consider a lightweight `ai_generations` counter table if Groq cost
  visibility matters.
- **`history` grows unbounded** — worth surfacing in the dashboard (row count + oldest row).
- Base schema (users/tracks/playlists/…) is not versioned in the repo; column names above are
  code-inferred. Verify against the Supabase dashboard before writing migration 003 joins.
- Slowapi is per-IP and in-memory; a Render restart resets counters (fine for admin dashboards).

## Appendix — inferred schema quick reference
```
users(id uuid pk, email, username, password_hash, avatar_url, auth_provider,
      is_verified bool, role text default 'user', created_at, last_active)
otp_codes(id, email, purpose, created_at, expires_at, notified bool, otp_hash nullable)
pending_registrations(email, username, password_hash)
tracks(id text pk, title, artist, album, duration int, artwork_url, source, genre, year)
liked_tracks(user_id, track_id, liked_at)          -- upsert on (user_id, track_id)
playlists(id uuid pk, user_id, name, cover_url, created_at, updated_at, track_count int)
playlist_tracks(playlist_id, track_id, position, added_at)
history(user_id, track_id, played_at)              -- no pruning
followed_artists(user_id, artist_id, artist_name, followed_at) -- upsert on (user_id, artist_id)
user_settings(user_id uuid pk fk→users, settings jsonb, created_at, updated_at)
```