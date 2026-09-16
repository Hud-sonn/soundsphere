# Soundsphere — Change Log (development)

This file is the running record of everything changed, added, or fixed in
Soundsphere. It exists so nothing is lost between sessions and so the
user-facing release notes (`changelog.md`) are easy to assemble.

**Rule for development agents:** every change, fix, or feature MUST be
recorded here before the task is marked done — a short, concrete bullet
with the area, what changed, and push status (`pushed` / `local`).
Newest date block goes on top.

### 2026-09-16 — Events screen + discover pool + scraper fixes (local, NOT pushed)
- `feat(events): data-driven multi-artist Events screen (local, NOT pushed)` — new `events` route (`EventsScreen.kt` + `EventsViewModel.kt`). Top: hero card for nearest followed-artist event + "From artists you follow" list. Bottom: "Discover" list from new `GET /feed/events/discover` via `SyncService.getFeedDiscoverEvents()`. Expandable date cards use real fields only (venue/city/country/date/description/ticket/soldout); ticket buttons open browser; imageless events get a venue-initial fallback tile. Home "Upcoming Concerts" title now navigates to `events`. New strings: events_live, from_artists_you_follow, discover_events, book_tickets, event_details, no_upcoming_events, error_loading_events.
- `feat(feed): GET /feed/events/discover endpoint (local, NOT pushed)` — serves upcoming events NOT tied to the user's follows: tickethub unmatched listings (artist_name NULL) + events cached for other users' artists, soonest first, limit 50.
- `fix(feed): tickethub unmatched kept as discover pool + matched attributed (local, NOT pushed)` — cron no longer discards non-matching tickethub events; matched ones now carry the matched artist name (previously NULL, which made them invisible to `/feed/events` IN-filter). No migration needed (artist_name already nullable per 007).
- `fix(scraper): tickethub + padeya country no longer hardcoded (local, NOT pushed)` — tickethub Flight data has no country field (verified live: id/title/slug/dates/venue/city/image/category/address/location only), so country is now "" unknown instead of "Nigeria". padeya keeps site-provided value with "" fallback. Per-event city/venue still shown; no user-country field exists anywhere in backend (verified by grep), so no per-country scraping.
- `fix(scraper): Bandsintown event images (local, NOT pushed)` — probe (`/tmp/opencode/bit_image_probe.py`, main code untouched) proved `artist.image_url`/`thumb_url` ride along in every events response; scraper now extracts it (zero extra calls). Smoke test asserts `photos.bandsintown.com` image. All 7 scrapers OK: tickethub 29 (21 music), padeya listing/detail OK, schema extractor OK, Bandsintown OK, iTunes 8, Deezer 25. Bandsintown SSL handshake timeouts remain intermittent (failed 3x then passed on retry).
- `docs: padeya left unwired (local, NOT pushed)` — module ships ready `fetch_events()` + `matching()` but cron never calls it; live yield is 1 non-music event (sports demo), no music filter, per-event detail cost, tickethub overlap. Wiring is ~15 lines via `matching()` when music yield improves.
- Design strip-list (Events/Live mock → built): mock header CUT (app owns top bar); hero ADAPT (nearest followed event, real image/fallback, no fake resident badge/follow); VIP presale bento CUT (fabricated passcode/claims); date cards ADAPT (two multi-artist lists, real-fields drawers); rehearsal tapes CUT (no audio); audiophile bento CUT (marketing copy); sticky CTA ADAPT (per-card ticket button); bottom-nav Events tab CUT (app tabs unchanged, screen pushed from home).
- Build: `assembleFossDebug` BUILD SUCCESSFUL with constrained heaps (2048M/1536M, workers=2); `gradle.properties` restored, no diff.

### 2026-09-16 — Home screen feed sections (local, NOT pushed)
- `feat(home): New Release Radar section on home screen (local, NOT pushed)` — fetches releases from `/feed/releases` (24h window) via new `SyncService.getFeedReleases()` + `HomeViewModel.feedReleases` state flow. Renders a hero card (artwork + artist + title + album type + track count + release date) for the latest release, plus a horizontal scrollable row of up to 10 additional releases. Sections only appear when data exists (hidden when empty). Clicking opens release URL in external browser.
- `feat(home): Upcoming Concerts section on home screen (local, NOT pushed)` — fetches events from `/feed/events` (7d window) via new `SyncService.getFeedEvents()` + `HomeViewModel.feedEvents` state flow. Renders a colored alert ribbon card for the nearest concert (image + title + venue/city/country + date + sold out badge), plus a horizontal scrollable row of up to 10 additional concert cards with images. Sections only appear when data exists. Clicking opens ticket URL in external browser.
- `fix(backend): feed releases cutoff changed from 7d to 24h (local, NOT pushed)` — `/feed/releases` and `/feed/all` now return releases from the last 24 hours instead of 7 days. Events remain at 7-day window.
- `fix(app): add missing recently_played string resource (local, NOT pushed)` — resolved pre-existing build error.

### 2026-09-16 — RLS fix + design mock recorded (local, NOT pushed)
- `fix(security): enable RLS on ai_generation_usage + recently_played (local, NOT pushed)` — both tables had RLS disabled (Supabase security advisor critical finding). Now enabled with policies: `ai_generation_usage` gets SELECT-only for authenticated (read own usage), no write policies (backend service_role handles all writes). `recently_played` gets SELECT/INSERT/DELETE for authenticated (own rows), backend service_role also writes. Critical security advisory resolved.
- `docs: home screen design mock recorded (local, NOT pushed)` — `HOME_SCREEN_MOCK.html` saved with full HTML/CSS mock of home screen. Sections documented in `SCRAPER_REWRITE_PLAN.md`: Live Concert Alert Ribbon, New Release Radar (hero card), Acoustic World Tour (horizontal scroll concert cards), Heavy Rotation (track list, not part of this feature). Design tokens: surface #141312, primary #eae0d5, secondary #ddc2a4, font Hanken Grotesk, dark mode.

### 2026-09-16 — Feed cache tables + cron + API endpoints (local, NOT pushed)
- `feat(feed): artist_releases_cache + artist_events_cache tables (local, NOT pushed)` — migration 007. `artist_releases_cache` keyed by (artist_name, source, source_id) with release_date, album_type, artwork, url, track_count. `artist_events_cache` keyed by (source, source_id) with artist_name, start_time, end_time, venue, city, country, ticket_url, sold_out. Both RLS-enabled with NO authenticated write policies — service_role only. Indexes on artist_name+date for fast personalized queries.
- `feat(feed): background cron job refreshes cache every 12h (local, NOT pushed)` — `services/feed_refresh.py` reads unique artist names from `followed_artists`, fetches releases (iTunes+Deezer) + events (Bandsintown REST API + tickethub.ng), deduplicates, upserts into cache tables, cleans up entries older than 7 days. Runs as asyncio background task in `main.py` lifespan (starts 60s after boot, then every 12h). Manual trigger: `POST /internal/refresh-feed`. No new dependencies (no APScheduler — uses asyncio.sleep).
- `feat(feed): GET /feed/releases, GET /feed/events, GET /feed/all endpoints (local, NOT pushed)` — `routers/feed.py`. All auth-required (Bearer JWT). `/feed/releases` returns releases from last 7 days for artists the user follows. `/feed/events` returns upcoming events (start_time > now) for followed artists. `/feed/all` returns both combined. All query cache tables (not external APIs) — fast response. `POST /internal/refresh-feed` triggers manual cache refresh.
- Next: home screen sections (user designs), event screen (user designs), new releases screen (user designs).

### 2026-09-16 — Bandsintown scraper rewrite + architecture plan (local, NOT pushed)
- `feat(scrapers): rewrite bandsintown scraper from blocked website to working REST API (local, NOT pushed)` — `rest.bandsintown.com/artists/{name}/events/?app_id=js_api_client` is a public, keyless endpoint (Squarespace integration). Returns rich JSON: venue (name, city, country, coordinates), lineup (supporting artists), offers (ticket URL, status), description, artist metadata. Tested with 25+ artists globally (Burna Boy/O Beach Ibiza, Asake/The O2 London + Accor Arena Paris, Omah Lay/17 events, Chris Brown/28 events, Olivia Rodrigo/82 events). Old approach (HTML scraping `www.bandsintown.com`) is dead — Cloudflare 403. `EventInfo` model gains `lineup`, `ticket_url`, `sold_out` fields. `common/http.py` gains retry logic (3 attempts with exponential backoff) for intermittent SSL handshake timeouts on `rest.bandsintown.com`. `extract_schema_events()` kept for padeya.com compatibility. Test results 2026-09-16: all 7 scrapers OK (tickethub 30 events, padeya 1 event, bandsintown 1 event via API, itunes 8 releases, deezer 25 releases). `SCRAPER_REWRITE_PLAN.md` created with full architecture: cron reads `followed_artists` → fetches releases (iTunes+Deezer) + events (Bandsintown+tickethub+padeya) → writes to cache tables → API serves personalized views. Backend capacity check: soundsphere-auth at 90 MB / 512 MB (0.2% CPU), can handle scrapers. Per-user search recording: `followed_artists` table IS the per-user artist list, no separate search history needed.
- Next: home screen sections (user designs), event screen (user designs), new releases screen (user designs).

### 2026-09-15 — Concert/release scrapers in own folders (local, NOT pushed)
- `feat(scrapers): backend-auth/scrapers/ — bandsintown, nigerian, releases, common (local, NOT pushed)` — each source in its own folder, stdlib + httpx only (no new requirements, no browser automation). `common/http.py` (shared UA/timeouts, `BotBlockedError` for 401/403/406/429), `common/models.py` (`EventInfo`/`ReleaseInfo` dataclasses). `bandsintown/scraper.py` extracts schema.org Event JSON-LD from any page (Bandsintown/Songkick artist pages are Cloudflare-walled for plain HTTP — probed 403/406 on 2026-09-15 — so the fetcher raises `BotBlockedError` and the extractor is tested against padeya detail pages carrying the same markup). **UPDATE 2026-09-16: bandsintown scraper rewritten to use REST API (rest.bandsintown.com, app_id=js_api_client) — now works.** `nigerian/tickethub.py` parses homepage React Flight `initialRails` event objects (canonical URL `tickethub.ng/{slug}`, verified 308→200) + `music_events()` category filter. `nigerian/padeya.py` parses `/events` Flight `initialEvents` + enriches via detail-page JSON-LD/breadcrumb (category, city) + `matching()` artist-name text filter. `releases/itunes.py` (one keyless query returns albums+singles; exact-artist match, artwork upscaled to 600px) + `releases/deezer.py` (authoritative record_type). `test_scrapers.py` (`uv run --with httpx python -m scrapers.test_scrapers`, exit nonzero only on FAIL). Test results 2026-09-15: tickethub OK (30 events, 21 music), padeya-listing OK (1 event — thin inventory on site, parser verified against blob), padeya-detail OK, schema-extractor OK, bandsintown BLOCKED (wall confirmed, handled), itunes OK (8 Ayra Starr releases, album/ep/single), deezer OK (25 Burna Boy releases). Fixed during test: padeya event marker missed backslash-escaped uuid quotes (caught only 1 unescaped copy).
- Next: `artist_events` + `artist_releases` cache tables (service_role-only writes per RLS rule), `routers/events.py` (`GET /events/upcoming`, `GET /releases/new`), daily cron refresh, Home sections + `ConcertsScreen.kt`/`ReleasesScreen.kt`.

User-facing release notes live in `changelog.md` (curated by the core
team at release time) — this file is the source they fold from.

### 2026-09-14 — Premium tier scaffold (backend, pushed)
- `feat(billing): premium tier-checking + cap enforcement (pushed)` — new `backend-auth/routers/billing.py` queries `subscriptions` table (service_role only, no `authenticated` UPDATE policy) to determine tier via `is_pro && expires_at > now()`. No caching — every cap check queries fresh, so expiration is instant. Caps: playlists 20→∞, liked 2000→∞, followed 200→∞, playlist tracks 500→1000, history 500→∞, AI 2→10/day, Blends 3→10. `get_user_tier()`, `get_cap()`, `check_cap()`, `check_blend_creation_cap()` helpers. Security: client never sends tier info; `user_settings.settings` CHECK prevents `is_pro`/`plan` keys; `prevent_privileged_user_column_changes` trigger protects `users.role`. Payment integration (OPay/Stripe) will write to `subscriptions` via service_role webhooks only.
- `feat(user): tier-aware cap enforcement (pushed)` — `user.py` cap checks now call `check_cap()` from billing module instead of hardcoded constants. `create_playlist` gains Blend ownership cap (3 free, 10 premium). `_prune_history` is now tier-aware (unlimited for premium). Old `_PLAYLIST_SYNC_LIMIT`, `_LIKED_LIMIT`, etc. constants removed (dead code).
- `feat(ai): tier-aware AI generation cap (pushed)` — `ai.py` daily limit now queries `get_cap(db, user_id, "ai_daily")` instead of hardcoded 2. Premium users get 10/day. Expiration handling: when `expires_at` passes, `get_user_tier()` returns 'free' automatically — no cron job needed, no user action required.
- `docs: premium scaffold + payment integration plan (pushed)` — `PREMIUM_SCAFFOLD.md` records the tier-checking infrastructure, security model, and cap definitions. `PAYMENT_INTEGRATION_PLAN.md` proposes OPay hosted checkout via WebView (same pattern as Spotify/YouTube): backend creates order → returns `cashierUrl` → app loads in WebView → OPay redirects to deep link → webhook verifies payment → `subscriptions` table updated. Includes in-app payment screen mock (plan cards, subscribe button), `payment_orders` table schema, and 3-phase implementation plan (backend → UI → polish). Updated with: UI ownership matrix (we own pre-checkout + success, OPay owns payment input), share URL hiding proposal (Cloudflare Worker at `sndsph.re` redirects to deep links, keeps `api.soundsphere.name.ng` internal).
- `docs: Cloudflare Worker setup for share URL hiding (pushed)` — `CLOUDFLARE_WORKER_SETUP.md` documents the alternative approach: add `soundsphere.name.ng` to Cloudflare (free), change nameservers at WhoGoHost, deploy Worker at `share.soundsphere.name.ng` that redirects `/p/{token}` → `soundsphere://p/{token}` and `/s/{id}` → `soundsphere://song/{id}`. Backend URL never appears in share links. Worker is free (100k req/day), SSL automatic, no Render changes needed.
- `feat(share): switch share links from api.soundsphere.name.ng to share.soundsphere.name.ng (pushed)` — Share strings in `soundsphere_strings.xml` now use `share.soundsphere.name.ng` instead of `api.soundsphere.name.ng`. Deep link host check in `MainActivity.kt` now also matches `share.soundsphere.name.ng`. Backend API URLs (`build.gradle.kts`) untouched — internal only. Pending Cloudflare Worker deployment at `share.soundsphere.name.ng` — Worker **proxies** (not redirects) to backend so `api.soundsphere.name.ng` never appears in browser URL bar. Rewrites `og:url` meta tags in HTML responses (backend has hardcoded `api.soundsphere.name.ng` in Open Graph tags for social media previews) and Location headers from backend redirects.
- `chore: version bump to 1.2.3 (versionCode 13), tagged v1.2.3 (pushed)`

### 2026-09-13 — Playlist-detail mock M3 wiring + token gradients (pushed)
- `feat(theme): playlist header firelight + sleeve gradients on tokens (pushed)` — `LocalPlaylistScreen` hero gains mock ambience with zero hardcoded hex: two `Brush.radialGradient` glows (`secondaryContainer` 30%, `secondary` 15%), 252dp sleeve `Brush.linearGradient` (`surfaceContainerLowest→High→Lowest`) + `onSurface` 8% sheen, all `shapes.medium` (mock rounded-xl); header art branches (empty/single/collage) `RoundedCornerShape(3dp/12dp)` → `shapes.medium`. Art style kept via gradients + existing collage; all 8 remote mock images, lossless pill/badges, acoustic-profile card, genre chips, mini-player, JS/fonts/CDN CUT per strip list.
- `feat(theme): Play circle → M3 secondary pill (pushed)` — mock dual controller mapped to existing play action: `Button(secondary, CircleShape, 56dp, icon + Play labelLarge)`; shuffle/menu circles untouched; download/add/fav/share stay in existing menu.
- `feat(theme): playlist row art shapes.small via new param (pushed)` — `SongListItem` gains `thumbnailShape` (default = current 3dp, no other caller affected); `LocalPlaylistScreen` passes `shapes.small` (mock rounded-lg). `assembleFossDebug` BUILD SUCCESSFUL (RAM-constrained, `gradle.properties` restored, no diff). Not pushed.

### 2026-09-13 — Library mock corners wired to theme tokens (local, 2026-09-13, NOT pushed)
- `feat(theme): Library mock round corners → existing Material shapes (local, NOT pushed)` — no new theme values (mock radii already covered: `rounded-2xl` 16dp = `shapes.large`, `rounded-full` = `CircleShape`, 8dp = `shapes.small`): `PlaylistGridItem` art `RoundedCornerShape(thumbnailCornerRadius())` 3dp → `MaterialTheme.shapes.large` (mock playlist cards); `ChipsRow` 3× `RoundedCornerShape(16.dp)` → `CircleShape` (mock pill chips); `CreateBlendTile` thumb `RoundedCornerShape(8.dp)` → `MaterialTheme.shapes.small`. Strip mapping: TopHeader/FAB/bottom-bar/mini-player/outer shell/Tailwind+fonts+blur/shadows/gradient chrome/emoji art CUT (app owns chrome, no remote code/images); FilterChips/SortHeader/playlist grid incl. Blend suffix KEEP mapped to existing components. Song/album thumbnail radius (3dp) untouched — playlist cards only. `assembleFossDebug` BUILD SUCCESSFUL (RAM-constrained, `gradle.properties` restored, no diff). Not pushed.

### 2026-09-12 — user-facing docs + build/test pass + version fix (local, 2026-09-12)
- `fix(version): 1.3.0 → 1.2.2-beta` — versionName corrected in build.gradle.kts, changelog.md header updated to match.
- `docs(messages): messages.json new announcement (local, 2026-09-12, NOT pushed)` — new top entry `announcement-2026-09-12` "Blend is here — playlists you build together (Beta)": step-by-step how-to create a Blend, invite friends, what you can do (contribution bar, filter, remove, leave), limits (3 Blends free, 10 members, 500 songs), behind-the-scenes fixes summary. Human-readable, no dev jargon.
- `docs(changelog): changelog.md v1.2.2-beta entry (local, 2026-09-12, NOT pushed)` — new top section covering Blend (create, invite, collaborate), AI Curator screen, all Blend fixes (dedup, attribution, cover sync, position sync, black-screen join), cache fixes, search, Android Auto slider, behind-the-scenes reliability improvements.
- `build: assembleFossDebug SUCCESSFUL + unit tests green (local, 2026-09-12, NOT pushed)` — RAM-constrained build, `gradle.properties` restored clean.
- `docs(updates): HOW_UPDATES_WORK.md (2026-09-13, local, NOT pushed)` — full end-to-end research: tag → release workflow → GitHub Release → in-app updater → version comparison → download → install. Key finding: `-beta` suffix causes patch version to parse as 0, so beta tags do NOT auto-update existing stable users (correct behavior). Asset matching by exact filename (`Soundsphere.apk`, `Soundsphere-with-Google-Cast.apk`).
- `cleanup(tags): deleted 66 Metrolist upstream tags + stray `nightly`/`v` tags (2026-09-13, pushed)` — remote now has only 11 SoundSphere tags (v1.1 through v1.2.2-beta).
- `fix(version): 1.3.0 → 1.2.2-beta (2026-09-13, pushed)` — versionName corrected, changelog/messages updated, tag recreated.

### Upstream 2026-09-04/05 review + cache-bug port + unit test report (local, 2026-09-06) — 28/28 tests, APK built, DO NOT PUSH (user instruction)
- `build(app): assembleFossDebug SUCCESSFUL 2026-09-06` (RAM-constrained, `gradle.properties` restored, no diff). APK `app/build/outputs/apk/foss/debug/app-foss-debug.apk` (54.6 MB) contains all ports + ggpht fix. Unit tests 28/28 green (see bullet below + `TEST_PLAN_UPSTREAM.md`).
- `state(repo): 91 changed paths, all UNCOMMITTED on main, NOT PUSHED — do not push` (user explicitly forbade pushing twice, 2026-09-06). Only commits since `13390d72` are the two Weblate picks below; everything else (port batch, Blend UI, Recently Played, RLS notes, Listen Together app-side, cache-bug port, YouTubeUtils fix, tests) is working-tree only.
- `docs(feasibility): FEASIBILITY_STATS_WRAPPED.md` (`Date: 2026-09-06`) — "Share Custom Stats / Wrapped" proposal checked against tree. Verdict: NOT a big change if scoped on-device (~70% exists: `Event` table, most-played DAO queries, `StatsScreen`, `ComposeToImage` PNG pipeline, FileProvider + EXTRA_STREAM share precedent, WorkManager, Cloudinary, Compose 1.11.4). Node.js/Firebase + Pillow/Imagemagick explicitly NOT needed. Flags: mood/genre unbuildable (no genre/BPM stored anywhere — recommend cut); skip tracking deferred (no storage without schema change); dedicated ListeningHistory entity would violate AGENTS.md:5 (DB stays v40 — `event` covers it, needs no authorization); Uniqueness Score is the only backend-needing item (opt-in aggregate table + RLS review). MVP file list + test pointers inside. Local only, not pushed.
- `docs(feasibility): renamed FEASIBILITY_STATS_WRAPPED.md → FEASIBILITY_PREMIUM_FEATURES.md` (same day, untracked file) + appended **Part B worries/cuts (12 items)**, **Part C UI customization** (verdict: mostly small — theme editor MEDIUM w/ 12-token M3 design note; fonts SMALL; icon packs SMALL on existing activity-alias infra; widgets TRIVIAL — 4 receivers already exist, only Top-Charts widget is new), **Part D spatial audio** (TRIVIAL ~30 lines but honesty-constrained: YTM stereo can never be Atmos — label "spatial where supported", no OEM SDKs; proposal's Android-15 prerequisite overstated, Spatializer path is API 32+), **Part E pitch/tempo** (SMALL–MEDIUM: Sonic + PlaybackParameters already plumbed incl. Listen-Together sync; only sliders/presets UI missing; NDK SoundTouch/Rubberband rejected — APK bloat). Hard block recorded: ZERO billing infra in tree (no BillingClient, no isPremium) — nothing can be labeled "Premium" until monetization is decided. Test pointers per part inside. Local only, not pushed.
- `docs(feasibility): Part F — approved-but-PENDING designs, NO CODE` — user approved designing (not coding) uniqueness backend (spec frozen: `013_global_taste_stats.sql` RLS-no-policy table + `routers/stats.py` POST/GET/DELETE + schemas + main.py wiring + SyncService/Repository wrappers, opt-in UI as follow-up; NOT applied to Supabase), streak query (spec frozen: `playDatesDesc` DAO + pure `currentStreak` in NEW `utils/ListeningStats.kt` + unit test, no schema), genre/BPM decision (my call: cut for v1, substitute "Top eras" card from existing `SongEntity.year` — awaiting user reply), skip-count schema (spec frozen: `skipCount` column + v41 + spec-less auto-migration + atomic `incrementSkipCount` + MusicService SKIP-hook + `SkipCountTest`; user-authorized AGENTS.md:5 exception for these files only). Code was started then FULLY REVERTED per user order (verified: 0 traces of skipCount/v41 in `git diff`). Awaiting user's replies after reading + code-writing go-ahead. Local only, not pushed.
- `docs(feasibility): Part G — visualizer investigation + worries 13–16` — verdict SMALL–MEDIUM for in-app only: `audioSessionId` already plumbed both sides (`MusicService.kt:2294`, `PlayerMenu.kt:503`) so `audiofx.Visualizer` attaches with zero player changes; display home is the existing `PlayerBackgroundStyle` branch (`Player.kt:247`); `ShareCardDialog.kt:84` is a second Compose→image precedent. Cuts: Nier dep (write ~150-line Compose Canvas renderer instead), live wallpaper v1 (defer), lockscreen overlay (policy risk, MediaStyle suffices), "no permission" assumption (RECORD_AUDIO runtime rationale required; already declared for recognition). Cautions recorded: screen-off pause, Bluetooth A2DP no-op fallback, crossfade session re-attach (`MusicService.kt:4458`). File list + test pointers inside. Local only, not pushed.
- `docs(feasibility): Part C user replies applied + Part H payments` — C1 theme editor + C2 fonts REMOVED per user (one-line tombstones kept so nobody re-proposes blind); C3 icon names marked PLACEHOLDER/TBD (Neon/Retro were my invention, not actual packs). NEW Part H (payment via separately-hosted WebView): SMALL–MEDIUM — designed closed-loop flow (app→FastAPI single-use checkout token→WebView→provider webhook→our backend flips `subscriptions` row→deep-link callback→tier refresh), WebView config = inverse of `CipherWebView`, no JS bridge (URL interception), no Custom Tabs, no in-app provider SDK; premium flag lives in `subscriptions` (exists remotely, zero code — confirm columns before writing), NOT trigger-protected `users.role`. New worries #17 (never JWT in WebView) + #18 (assetlinks.json on pay host). File lists + test pointers inside. Local only, not pushed.
- `docs(feasibility): replies recorded — worries 13/15/17/18 AGREED (2026-09-06)` + wallpaper explainer (why deferred: session bridge, engine lifecycle/refcounting, battery + Play-picker surface; v1 renderers port 1:1 later) + **Part H2 OPay plan** (researched from OPay docs: Cashier hosted page maps 1:1 onto Part H flow — `cashierUrl`/`returnUrl`/`callbackUrl`, sandbox available, `evokeOpay` app handoff; Mobile SDK exists but nothing to fork — 3 REST calls, build thin; recurring CONFIRMED absent → sell 30-day passes with `expires_at`, Blend-notification renewal reminders; resilience from today's logs: webhook-truth, idempotent `reference`, reconcile job, sandbox matrix, HMAC verify; keys as Render env vars) + **Part I live service report** (Render MCP: 5 services all live, auth on `dep-dad93ojl`; ~15×/day Supabase egress `Errno 11` warnings absorbed by 503-mapping; 1 benign login-validation error; 1 error-log-write failure; activity ≈9 distinct client IPs in 2.5h + all-day history writes; Supabase MCP not connected — exact user counts need admin dashboard). Local only, not pushed.
- `docs(feasibility): Part H3 OPay verification — other-AI answer corrected (2026-09-06)` — `team.opay.sdk:cashier-sdk:1.1.2` does NOT exist on Maven (OPay docs: manual .aar only; only `com.opayweb:android-pay-sdk:1.0.0` is on Central and it's a different app-to-app product) — our WebView architecture needs no SDK at all; recurring CONFIRMED absent again (pasted answer conflated opay.dev aggregator + bill-pay marketing page with the merchant API) so passes-model stands; refunds confirmed real (admin refund path noted for later); fork-vs-build settled (build thin, 3 REST calls); toggle+enforcement designed (client gate UX + server re-check, offline grace, never client-as-lock); merchant onboarding checklist recorded as later business steps. All payments = LATER, nothing touched. Local only, not pushed.
- `docs(feasibility): live wallpaper ruled PLAN ONLY (2026-09-06)` — Part G explainer tagged: design reference, not on any build roadmap, no sprint estimates, fresh go-ahead required; kept solely so the v1 renderer interface stays port-compatible. Local only, not pushed.
- `fix(blend): members/attribution/notices/thumbs/dupes round (local, 2026-09-11, BUILT 23:28)` — `assembleFossDebug` BUILD SUCCESSFUL (18m56s, RAM-constrained; first attempt hit the 15-min tool timeout with zero output, rerun foreground → green), APK 54.6 MB fresh, `gradle.properties` restored clean. Backend changes (join notices, dupe 409) ride the next backend push — NOT live yet. Not pushed.
- `docs(feasibility): Part Q Home mock — RECORD ONLY (2026-09-12, no code)` — editorial mock reviewed strip-first; user decision: grid without glow, 1×1 + good spacing, short greeting; deferred — `HomeScreen.kt` untouched. Recorded in Part Q.
- `feat(blend): Join screen from web mock (local, 2026-09-12, NOT built)` — `SharedPlaylistHeader` collaborative branch rewritten per strip inventory (inviter banner, emblem w/ BlendIcon fallback, real song+member counts, honest 2-step card, full-width Accept on guarded join; non-Blend branch untouched). Backend `member_count` in public share response (privacy-safe bare number) + app model/parse. 6 strings. `py_compile` clean. Not built, not pushed — needs assemble + link-join device round.
- `feat(blend): Invite screen from web mock (local, 2026-09-12, NOT built)` — NEW `BlendInviteScreen` (`blend_invite/{playlistId}`): badge card, live N/10 capacity bar, member rows (HOST badge, owner-remove + self-Leave), link card (copy + ShareSheet), Done. Backend `is_self` on collaborators; app `removeCollaborator` + repo wrapper. Invite chip retargeted. Dead `joinedPlaylistId` flow removed; guarded `joinBlend` body kept identical (black-screen fix preserved). QR recorded deferred per user (no lib, no build). 8 strings. Not built, not pushed — needs assemble + device round.
- `feat(blend): detail body structure — card rows, chip, Tracklist, duration (local, 2026-09-12, NOT built)` — user caught chrome-only conversion; body now converted: `BlendRowCard` wraps Blend rows (surfaceContainer/12dp, both swipe+locked paths; non-Blends byte-identical), attribution merged into row as avatar-dot chip, "Tracklist" title (`tracklist` string), per-row duration in trailing, art radius 12dp for Blends. Plus AGENTS.md redesign-completeness rule (section inventory before coding, body-is-part-of-redesign, applies to whole series). Docs HTML regenerated + index date 2026-09-12. Not built, not pushed — needs assemble + device round.
- `feat(blend): detail redesign pass 2 — hero art, updated-live, docs HTML (local, 2026-09-11, NOT built)` — header artwork gets ambient glow (280dp secondaryContainer circle) + `BlendIcon(120dp)` as default art for cover-less Blends (branch 0; song-art branches untouched); metadata line prefixed "Updated live •" for Blends (`blend_updated_live` string — honest version of mock's "Updated Daily"). Deliberately NOT moved: member avatars stay in the below-pill strip (tested, working) instead of overlaid on the art card. Detail mock now fully converted except cut items (taste badge, genre, mutual favorite, capsule, numbering, drawer card). `backend-auth/docs/FEASIBILITY_PREMIUM_FEATURES.html` generated + index link. Not built, not pushed — needs assemble + light/dark device round.
- `feat(blend): detail redesign pass 1 — curated-for, real Invite, HTML docs (local, 2026-09-11, NOT built)` — header shows "Curated for A, B" (`blend_curated_for` string, real member names); Invite chip fires the real token-link ShareSheet (was a snackbar dead-end). Detail mock strip status: taste badge/genre/mutual-favorite/Taste-Insights/numbering cut per N6; contributions bar + contributor filter built (previous bullet); art-card glow + member-on-card + "Updated live" line held for pass 2. `backend-auth/docs/FEASIBILITY_PREMIUM_FEATURES.html` generated (same wrapped pattern + Date/Status/Why header) + index link. Not built, not pushed — needs assemble + device round.
- `feat(blend): contributions bar + contributor filter (local, 2026-09-11, NOT built)` — header shows real per-member track-share bar + legend (`blend_contributions` string; theme primary/secondary/tertiary segments; counts refreshed with the 30s member poll via new `countTracksAddedBy` DAO; no genre labels anywhere). Tracklist gains All/member `FilterChip` row (reuses `R.string.filter_all`; pre-attribution NULL rows always stay visible so the filter never hides history). Both callbacks from the detail-mock review now included. Not built, not pushed — needs assemble + device round.
- `feat(ai): Curator screen from mock, old dialog flow retired as fallback (local, 2026-09-12, NOT built)` — NEW `AiCuratorScreen` (`ai_curator`): hero, prompt field, 4 seed chips (no emoji), Surprise Me, Synthesize → same Groq `generateAiPlaylist` + consent gate + 2/day + `playlists_created` mapping; sliders/era/BPM/FLAC stripped per N strip list. FAB routes to screen; Library dialogs kept as fallback with NOTE — primary call site moved. Wires where AI is called (Library FAB + future callers) via the screen route. `py_compile` clean. Not built, not pushed — needs assemble + curate→playlist device round.
- `fix(blend): created Blends are actually Blends + history-fill works (local, 2026-09-11, NOT built)` — root-caused in create trace: `POST /playlists` never accepted `is_collaborative` → every new Blend landed server-side regular → share/join 400'd until Make Blend. Fixed: `PlaylistCreateRequest.is_collaborative` + insert column; `SyncService.createPlaylist(..., isCollaborative)`; `pushPlaylist` forwards flag. "Fill from history" ran identical code to "Start fresh" (dead option) → now fills top 25 by `totalPlayTime` (new `topSongsByPlayTime` DAO, rank positions; `addedBy` null locally → server stamps owner → pull backfill heals). FAB confirmed on `create_blend` flow, old dialogs grep-verified gone. Backend `py_compile` clean. Not built, not pushed — needs assemble + create→share→join device round.
- `feat(blend): CreateBlendScreen redesign from web mock (local, 2026-09-11, NOT built)` — NEW `ui/screens/CreateBlendScreen.kt` (`create_blend` route): hero rings + reusable `BlendIcon`, honest 2-step how-card, name field, full-width CTA, history-vs-fresh dialog (now with proper strings — kills the old hardcoded-English debt), same insert+push+navigate logic. FAB icon swapped generic `+` → `BlendIcon(24.dp)` (was missed in tile round); tile + FAB navigate to screen; old inline dialogs deleted with NOTE. Stripped per review: Duo/Group/Mood pills, public/followers toggle, daily-refresh + taste claims, web chrome/scripts/remote imgs (Part N strip list + standing strip-headers/navs rule + deferred-concepts record). 14 new strings. Not built (no order), not pushed — needs assemble + light/dark device check.
- `feat(blend): reusable theme-adaptive BlendIcon + Create tile + default art (local, 2026-09-11, NOT built)` — NEW `ui/component/BlendIcon.kt`: user's SVG redrawn as Canvas paths (dual aura rings, lens, wave, dots) with ALL colors from `MaterialTheme.colorScheme` (tertiary/primary pairs + onSecondaryContainer accent) so it follows light/dark/user themes; size-independent 64-unit geometry; single reusable component for all upcoming designs (static `ic_blend_default.xml` draft deleted, not shipped). Library gets "Create Blend" tile (list + grid, `blend_create` string) tapping into the existing create dialog (same as Blend FAB). Default art: Library list/grid placeholders + Home tile render `BlendIcon` for cover-less Blends. Not built, not pushed — needs assemble + device round (light/dark check).
- `fix(blend): cover/profile/position round (local, 2026-09-11, NOT built per order)` — covers show everywhere now: `PlaylistThumbnail` takes `customUrl` (both Library tiles), header seeds `overrideThumbnail` from DB (survives reopen), `pushPlaylistCover` + `pushPlaylistTrackAdded` no longer silently drop for joined Blends (server-id passthrough), server order now receives local position (Top/bottom setting honored end-to-end instead of always-append). NEW `UserProfileScreen` (`user/{userId}?playlistId={playlistId}` route: avatar, Owner/Member chip, songs-added count, member-since) wired to strip avatars + attribution rows (dead-end sheet replaced); `BlendCollaborator.addedAt` parsed; `blend_owner/blend_member` strings. Backend `py_compile` clean. No APK build, not pushed — needs assemble + device round. — backend: `join_blend` now inserts `blend_joined` notices for owner+members; `add_playlist_track` 409s duplicate tracks on Blends only. App: `getBlendCollaborators` resolves server id + main-first + `isFailure` fallback; pull backfills `addedBy` on existing rows (new `backfillTrackAddedBy` DAO); members hoisted + 30s poll + "N members" count (`blend_members` string); track rows show resolved usernames + avatars (blank-guard); Home tile song-art fallback. Server repair via CLI: pre-migration NULL `added_by` → owner, verified 0 remain. Owner sees owner+Danny+count; "Added by Danny" both sides. Not built, not pushed.
- `docs(feasibility): Part M Blend FULL report (2026-09-11, CLI+MCP+code, no code touched)` — core loop proven live (Danny's row 2026-09-07 + matching Render `POST join → 200` ×2, all MAIN backend). Second DB/backend purpose restated: isolation spare, empty, serves nothing until replication/cutover. Join trace: single membership-row write, no notices/avatars/backfill. Attribution: all 15 server rows `added_by=NULL` (bulk paths never set it) + owner pull skips existing rows (no backfill) + UI shows raw id-prefix ("Added by 3f9a…") — fix = username resolution via collaborators + owner-default backfill. Avatars: 101 users / 6 with photos (Danny NULL = correct white rendering; add upload nudge later). Home thumbs: server `cover_url` NULL both blends; header uses song mosaic, Home has no fallback — fix = mosaic fallback + backfill at creation. Duplicates: zero guards today (names, re-adds); guard plan per layer incl. caps-rule 409 as the account-level duplicate-Blend guard. 5 tester follow-ups. Local only, not pushed.
- `docs(feasibility): Part L Blend-members investigation via Supabase CLI (2026-09-06, record-only, no code)` — tester: join works, but no join notice, default avatar, no member list. CLI evidence: 2 Blends ("nf home" has 1 row: `heisdanny64`, `avatar_url=NULL`; join write path fine), `notifications` table 0 rows. Causes: (1) `getBlendCollaborators` asks blend backend first with LOCAL id — blend project has no rows by construction + fallback needs literal "404" in message so never fires → empty strip; fix = server-id translation + main-first + `isFailure` fallback; (2) `join_blend` (`share.py:131`) inserts zero notifications — mirror track-add notices; (3) avatar NULL is real data (UI fallbacks correct) + NPE risk at `LocalPlaylistScreen.kt:1401` (`username.take(1)` unguarded) to harden in same edit. Tester follow-ups recorded. Local only, not pushed.
- `docs(feasibility): Part K Join-black-screen investigation (2026-09-06, record-only, no code)` — tester tapped Join Blend → black, join never completes. Prime suspect: per-track inserts (`SharedPlaylistViewModel.kt:101-120`) run unguarded in fire-and-forget `database.query{}` (playlist upsert at :97-99 IS guarded — the asymmetry is the tell); a constraint exception there kills the process mid-loop. Secondaries: navigate-before-writes race + double navigation (LaunchedEffect :91 AND onJoined callback :124). Fix sketch + 4 tester discriminators (death-vs-dark, logcat, Library-after-restart, size/re-join?) recorded for the later code session.
- `fix(blend): Join black screen — entire join path guarded + writes awaited (local, 2026-09-06)` — `SharedPlaylistViewModel.joinBlend` whole post-join block (server call, mapping, all inserts) now in try/catch → any failure shows `joinError` text instead of killing the process; fire-and-forget `database.query{}` replaced with suspend `database.withTransaction{}` so navigation only fires after every row landed (map insert already IGNORE → re-join safe); removed the redundant `LaunchedEffect(joinedId)` navigator in `SharedPlaylistScreen.kt:91` — single navigation path via the `onJoined` callback (which was a no-op before). `assembleFossDebug` BUILD SUCCESSFUL, `gradle.properties` restored. Not pushed. Tester re-check: join same Blend twice + join a large Blend → no black, second join shows existing library copy. Dolby rename: full-tree search found ZERO real Dolby/Atmos references (prior hits were `coerceAtMost` false positives) — nothing to rename today; rule recorded that Part D strings must use our own name, never Dolby/Atmos. Local only, not pushed.
- `docs(AGENTS): account-level caps rule (2026-09-06)` — new AGENTS.md section: all caps server-enforced by `user_id` (cache-clear-proof), Blend 3/user free, EVERY cap raised for premium with `subscriptions.expires_at` as tier source (never `users.role`); no ad-hoc exceptions while billing is absent. NO CODE — implementation (Blend 3-cap + premium tiers) is a tracked todo awaiting go-ahead.
- `docs(feasibility): Part J feature audit (2026-09-06, investigation only)` — whole-app survey with errata (Wrapped stories + parametric EQ + wake alarm ALREADY exist — shrinks Part A/E assumptions; MoodAndGenres is YT-browse not taste; backup local-only; no folders/smart-shuffle). 10 new premium-lockable candidates (cloud backup, smart shuffle, LT democracy, living AI playlists, per-output EQ, folders, library health, cross-device stats, concert alerts, CSV export) + explicit not-re-proposed list. No code touched.
- `port(translations): Russian d7c3f6b9 + Swedish cb1c148d` — committed locally on top of `13390d72`, NOT pushed. ~76 Weblate picks remain; `/tmp/metrolist` clone is GONE (no such directory as of 2026-09-06) — weekly upstream watch must re-clone before continuing the batch.
- `fix(tests): 28/28 pass` — (unchanged from 2026-09-05 report below; YouTubeUtils ggpht code fix + 3 test updates included and green).
- `port(upstream 1eda3c8): cache bugs` — `MusicService.markCachedIfFullyDownloaded` falls back to `ContentMetadata.getContentLength(playerCache.getContentMetadata)` + 1s re-check when `format.contentLength` unknown; REPEAT transitions + STATE_ENDED now mark cached; `SelectionSongsMenu.onRemoveFromCache` param + "Remove from cache" item; `CachePlaylistScreen` selection menu wired to new `CachePlaylistViewModel.removeSongsFromCache(ids)` (drops bytes + clears `dateDownload` explicitly); polling loop uses `cachedContentLength()` (format → playerCache meta → downloadCache meta). Upstream `partitionCachedSongs`/`CachePlaylistPartitionTest` hunks not portable (no such function in our diverged ViewModel).
- `port(upstream 5253a4d): redundant podcast heading` — deleted "Latest Episodes" header block in `HomeScreen.kt`. InnerTubeX `withEmbeddedConfigFallback` hunk SKIPPED (no `YtConfigParser.fetchEmbeddedConfig` in vendored innertube; our `PlayerConfigStore` dual-source already covers it).
- `skip(upstream a902053): Zemer lyrics` — niche provider, not needed for build; our registry already has its order-merge fix. Optional later.
- `docs(tests): TEST_PLAN_UPSTREAM.md` (`Date: 2026-09-05`) — verdict table + manual checklist (cache playlist ×6, podcasts ×1, regression ×5) + unit results.
- `fix(tests): 28/28 pass` — added missing `ShadowContentResolver` import (`ContentResolverExtTest` 2/2); `ServerClockTest` reset-case updated to `assertNull` (old contract contradicted the null-on-stale fix; callers are null-safe via `...OrNull`); `YouTubeUtilsTest` resolved per user approval — root cause was June `73a2ebf1` simplification that replaced behavior without updating tests: fixed REAL ggpht bug in working code (`YouTubeUtils.kt:28` appended `-s544` → malformed `=s88-s544`; now replaces with `=w-h-p`/`=s-p` format), updated 3 stale expectations to intended behavior (`-p` smart-crop on googleusercontent, i.ytimg returned unchanged with comment documenting the dropped maxresdefault upgrade).
- Status: local only. NO assemble re-run, NO push — awaiting user go-ahead.

### Upstream Metrolist port batch + feature implementations (local, 2026-09-05) — BUILD SUCCESSFUL (`:app:assembleFossDebug`), not pushed
**Scope:** 9 upstream commits ported + 2 translation picks + 4 Soundsphere-original features finished. 65 files changed, +2387/−923. APK: `app/build/outputs/apk/foss/debug/app-foss-debug.apk`. All local, nothing pushed.

#### A. Upstream ports (from `/tmp/metrolist`, adapted to Soundsphere package names)
- `port(upstream c36076996): stale song selections` — selection state no longer goes stale after list changes. **Test:** long-press songs in any song list → select several → pull-to-refresh / rotate → selection still matches the same songs.
- `port(upstream fd6262f7b): harden uploads/downloads` — **ADAPTED, streaming reverted.** Upstream's combined `innerTube.uploadSong(filename, contentLength, content: () -> InputStream)` does not exist in Soundsphere's vendored `innertube` (ours has `initSongUpload` + `uploadSongData`). `YouTube.kt:3541` reverted to ByteArray two-step (`initSongUpload` → `uploadSongData`, `X-Goog-Upload-Status == "final"` check restored); call sites `AutoPlaylistScreen.kt:281` + `LibrarySongsScreen.kt:191` now `openInputStream(uri).use { readBytes() }` on `Dispatchers.IO` before calling `uploadSong(filename, data)`. `UploadProgressInputStream` class + `UploadProgressInputStreamTest` kept as dead code (harmless, do not delete yet — decision point for later). **Test:** Library → Songs → upload MP3; Auto-playlist upload; confirm progress bar reaches 100% and song appears in YT uploads.
- `port(upstream 5247e9a39): release cleanup + safe reconciliation` — `SyncUtils.createPlaylist` local-first fallback, `scheduleAddToPlaylist`, `syncPlaylistSuspend`, `localSongIndexesAbsentFromRemote` + `preservedSongs` reconciliation so a bad remote pull can't wipe local songs. **Test:** create playlist offline → goes online → appears on server; remove a song on web YT → pull keeps local-only songs.
- `port(upstream 732dd13): metadata preservation` — `SongEntity.withUpdatedMetadata`, `refreshSongMetadata` (`PlayerConnection.kt`), `replaceSongArtists` (`SyncUtils.kt`), multi-artist edit in `SongMenu.kt:210` (creates local `ArtistEntity` for unknown names), `SongMenu.kt:222` re-fetches `database.song(id).first()` after edit (added missing `flow.first` import). **Test:** song overflow → Edit artists → add second artist → save → artist list shows both; play song → metadata (title/artists) refreshes without losing download.
- `port(upstream 024c8d94b): playback/library reliability` — central `DownloadUtil.download()` overloads (`Song`/`SongItem`/`MediaMetadata`/`String`, `DownloadUtil.kt:269`), `shouldPrepareDownload` guard, `SongEntity.withLibraryMembership`, `songIdsWithoutArtists` DAO + `findSongIdsWithoutArtists`/`DB_QUERY_BATCH_SIZE` batched liked/library sync rewrites (transactional), `HomeViewModel` speed-dial externalized to `buildSpeedDialItems` (+ `HomeSpeedDialTest`), `OnlineSearchViewModel.resolveSearchMetadata` parallel async, innertube pagination `flatMap` fix (`YouTube.kt:1198`) + `check()` instead of silent break (`Utils.kt`), `LibraryPage.extractArtists` for uploaded songs, `MusicDatabase.withTransaction` simplified to `delegate.withTransaction`. **Test:** download same song twice concurrently → single download; like 200+ songs → sync completes without OOM; Home speed-dial shows correct items; online search resolves artwork fast.
- `port(upstream 413737b48): viewType hoist` — `viewType` hoisted to `LibraryScreen.kt` so playlist/song tiles don't flash/recompose on tab switch. **Test:** Library → switch Songs/Albums/Artists/Playlists tabs fast → no tile flicker.
- `port(upstream 084342aad): heartbeat stutter fix` — `shouldSeekDuringActivePlayback` (2s tolerance) + `lastAppliedServerTime` staleness guard in `ListenTogetherManager.kt`. **Test:** Listen Together session → no audible stutter every heartbeat; position stays smooth.
- `port(upstream 59b53ac): add-to-playlist position` — `AddToPlaylistPosition` enum + `AddToPlaylistPositionKey` (`PreferenceKeys.kt`), `ContentSettings.kt` dialog, `MusicService.addToTargetPlaylist` preference, `AddToPlaylistDialog.kt` + `AddToPlaylistDialogOnline.kt` `prepend` wiring. **Test:** Settings → Content → add-to-playlist position → Top → add song to playlist → song lands at position 0; switch to Bottom → lands last.
- `port(upstream 98a1e0c): Android Auto OOM fix` — `DatabaseDao.searchSongsExtended` (single query incl. playlist matches, `ORDER BY totalPlayTime DESC, id`, `LIMIT :previewSize` where −1 = unlimited in SQLite), `AndroidAutoSearchLocalLimitKey` (`PreferenceKeys.kt:559`, default 75), `MediaLibrarySessionCallback.kt` both search blocks rewritten (removed `allSongs()` full-table load + N-query fan-out; second block keeps `songId` guarantee via `database.song(songId)`), settings slider (`AndroidAutoSettings.kt:121`, values 10/25/50/75/100/150/200/unlimited) + 3 strings (`soundsphere_strings.xml:1053`), `scrollBehavior` param removed (`NavigationBuilder.kt:480` updated), `first` import added where needed. **NOT ported:** upstream voice-search flow (`isVoiceSearch` exact-playlist match, `VoiceSearchMatcher`) — bigger divergence, out of scope; local `isVoiceSearch` val kept only for the songId-guarantee condition. **Test:** Settings → Android Auto → Search options slider → set 10 → AA search returns ≤10 local songs; set Unlimited → all; cold-start AA browse doesn't OOM on large libraries.
- `port(translations): Russian d2b72d3a + Swedish cb1c148d` — `values-*/strings.xml` + `soundsphere_strings.xml` only, no code. ~76 Weblate picks remain unported (mechanical batch, safe to do later).

#### B. Soundsphere-original features (finished this session)
- `feat(recently-played): full implementation` — `RecentlyPlayedEntity.kt` (Room v40, schemas `39.json`/`40.json`), DAO `recentlyPlayed`/`replaceRecentlyPlayed`/`clearRecentlyPlayed`, `MusicService.recordRecentlyPlayed` (song + container hook), `HomeScreen.kt` Recently Played section, backend `recently_played` table + `GET/POST/DELETE /user/recently-played` + replace-with-latest pull in `SyncRepository` (was investigation-only in `INVESTIGATION_BLEND.md`). **Test:** play 5 songs → Home shows Recently Played in play order → pull-to-refresh on Home keeps order; logout/login → list restored from server.
- `fix(security): RLS column-write fixes APPLIED` — audit `SECURITY_AUDIT_RLS.md` previously ended at "no code change yet"; now applied via MCP migrations 009–012 on BOTH Supabase projects (`ysfktparruosuegzdnwt` + `yuukseizasygckaeonrh`): `prevent_privileged_user_column_changes` trigger on `users` (role/is_verified/password_hash/last_active), `prevent_privileged_playlist_column_changes` on `playlists` (track_count/share_token/is_collaborative), `no_privileged_keys_in_settings` CHECK on `user_settings`, `subscriptions` table with no `authenticated` UPDATE policy, row-level policies on `playlist_tracks`/`playlist_collaborators`. Verified with role-play SQL tests. **Test:** as logged-in user via anon key, `UPDATE users SET role='admin'` → rejected; `UPDATE playlists SET track_count=999` → rejected; normal owner rename → allowed.
- `feat(blend): app UI completion` — `PlaylistEntity.isCollaborative` + `PlaylistSongMap.addedByUserId` (DB v40), `SyncService` parses `is_collaborative`/`added_by`/`addedByUserId` (+ `BlendCollaborator`, `AppNotification`, `joinBlend`, `getCollaborators`, `updatePlaylist`, notifications endpoints), `SyncRepository` union pull + `makeBlend` + `playlistCoverChanged` + `getBlendCollaborators` + `BLEND_BASE_URL` BuildConfig (`build.gradle.kts:147`), `BlendEndpoint`/`BlendService`/`BlendRepository` (double-project: `isDoubleProjectEnabled = BlendEndpoint.isConfigured()`), `LibraryPlaylistsScreen.kt` Create-Blend FAB at 144dp + slot-freed prompt, `PlaylistMenu.kt` Make Blend, `SharedPlaylistScreen.kt` + `SharedPlaylistViewModel.kt` Join Blend (saves `PlaylistEntity(isCollaborative=true)` to Library + Home), `LocalPlaylistScreen.kt` `Blend • Collaborative` pill + member strip + `Added by` → `UserInfoSheet.kt` (reusable) + `CloudinaryUploader.uploadBlendCover` (`soundsphere/blends`), `HomeScreen.kt` BlendPlaylists section, `Items.kt` `• Blend` subtitle + badge, `CreatePlaylistDialog.kt` sync-cap dialog, `AddToPlaylistDialog` stale-selection fix (`songIds = null`), `MediaItemExt.kt` helpers, `ContentResolverExt.kt` `fileNameAndSize` (+ test). **Test:** Library → + → Create Blend → invite via share link → second account joins → both add songs → each sees `Added by <name>`; owner deletes → members lose access; Home shows Blend section.
- `fix(listen-together): app-side 5-point fix` — `ServerClock.positionAt` → `Long?` (null on stale >60s), PLAY only forward hard-seek, drift loop re-reads `latestDriftPosition/EffectiveAt` per 250ms iteration, heartbeat 8s (`ListenTogetherManager.kt`), ping 5s (`ListenTogetherClient.kt`), `lastAppliedServerTime` guard; `PlaybackSyncTest` added. **Test:** start session → kill host network 70s → guest keeps playing (no snap-back); rejoin → resyncs forward only.
- `fix(menus): onGetSong signature simplification` — 9 menus (`AlbumMenu`, `PlaylistMenu`, `QueueMenu`, `SongMenu`, `YouTubeAlbumMenu`, `YouTubePlaylistMenu`, `YouTubeSelectionSongMenu`, `YouTubeSongMenu`, `PlayerMenu`) + `YouTubeArtistMenu` `artistFollowChanged` wiring; `MiniPlayer.kt` removed `AddToPlaylistButton` + dialog (−57 lines); `Items.kt` `SongListItem` thumbnail no longer resized for downloaded songs. **Test:** every overflow menu (song/album/playlist/queue/player) still opens and all actions work; mini-player has no duplicate add-to-playlist button.
- `docs(AGENTS): upstream-tracking rule` — `AGENTS.md` (+12) weekly InnerTubeX/Metrolist watch rule + date-rule header. **Test:** n/a (process doc).
- `feat(tests): 10 new test files` — `PlaylistSyncTest`, `PlaybackSyncTest`, `PlaylistDuplicatesBatchedTest`, `AddSongsToPlaylistTest`, `SongEntityTest`, `ContentResolverExtTest`, `DownloadUtilTest`, `HomeSpeedDialTest`, `PageHelperTest` (+comma case), `PlayerConfigStoreEpochTest`/`CooldownTest`, `UploadProgressInputStreamTest`. **Test:** `./gradlew :app:testFossDebugUnitTest` (not yet run — do before push).

#### C. Verification status
- `:app:assembleFossDebug` BUILD SUCCESSFUL (2026-09-05, RAM-constrained: 2048M/1536M, 2 workers); `gradle.properties` restored (no diff).
- Unit tests NOT yet run — run `:app:testFossDebugUnitTest` before push.
- All changes local, nothing pushed. Needs: user `push` command → commit + push app + backend (backend auto-deploys; keepalive keeps blend warm).

### Fix: deleted playlist songs reappearing (local, 2026-09-04) — Path B + Path C sync fix + durable retry, not yet pushed
- `fix(sync): SongMenu remove-from-playlist now syncs to backend` — `SongMenu.kt:829` overflow `Remove from playlist` previously did `database.transaction{ move+delete }` + optional YT `scheduleRemoveFromPlaylist` but never called `syncRepository.playlistTrackRemoved`. Added `LocalSyncRepository` import + field (`SongMenu.kt:74`, `SongMenu.kt:127`) and call `syncRepository.playlistTrackRemoved(ps.map.playlistId, ps.map.songId)` after DB delete, so Blends and synced YT playlists no longer leave ghost rows server-side that `pullPlaylists()` `SyncRepository.kt:392` would re-add.
- `fix(sync): bulk selection Delete now syncs to backend + YT` — `SelectionSongsMenu.kt:616` bulk `Delete` previously did local `move+delete` only. Added `playlistBrowseId: String? = null` param (`SelectionSongsMenu.kt:78`), `LocalSyncRepository` import/field (`SelectionSongsMenu.kt:54`, `86`), and loop calling `playlistBrowseId?.let{ syncUtils.scheduleRemoveFromPlaylist(...) }` + `syncRepository.playlistTrackRemoved(cur.playlistId, cur.songId)` for each `cur` (`SelectionSongsMenu.kt:616`). Threaded `playlistBrowseId = playlist?.playlist?.browseId` from `LocalPlaylistScreen.kt:858`. Correct reference impl remains `LocalPlaylistScreen.kt:583-591` swipe path.
- `fix(sync): durable retry for offline playlist-track deletes` — added `SyncRepository.kt:111` `_pendingPlaylistTrackRemoves: StateFlow<Set<String>>` (`playlistId|songId` keys, `PENDING_PLAYLIST_TRACK_REMOVES_KEY` `DataStore`), `markPendingPlaylistTrackRemove`/`clearPendingPlaylistTrackRemove`, `pushPlaylistTrackRemoved` now marks pending on `reportError` and clears on success/404, `pullPlaylists` `SyncRepository.kt:432` skips `pendingKey in _pendingPlaylistTrackRemoves` so offline deletes are not re-added, and `pullAll()` `SyncRepository.kt:288` now runs `retryPendingPlaylistTrackRemoves` (like `retryPendingLikes`) on next full sync. Prevents "deleted while offline → reappears next day" after the B/C fixes.

### Docs + Admin + Blend polish (local, 2026-09-01)
- `docs(backend): HTML docs from MDs, viewable only from backend domain` — `backend-auth/docs/*.html` (AGENTS, CHANGES, INVESTIGATION_BLEND, BLEND_UI_PLAN, BLEND_IMPLEMENTATION_REPORT, SECURITY_AUDIT_RLS, KNOWN_ISSUES, README) with `Date: 2026-09-01` / `Status` / `Why` header per new AGENTS.md rule, `backend-auth/routers/docs.py` + `main.py` `mount /docs` (StaticFiles, backend domain only, not in APK), `backend-auth/docs/index.html` links all docs together + links to `../admin/` (only `api.soundsphere.name.ng/docs` has them, per AGENTS.md).
- `docs(AGENTS): add date rule` — `AGENTS.md:1` now starts `Date: 2026-09-01 / Status: Live / Why: Added date rule…` and HTML `docs/AGENTS.html` shows same date/why.
- `feat(blend): whole Blend history file` — `BLEND_IMPLEMENTATION_REPORT.md` (`Date: 2026-09-01`) timeline 2026-08-21→2026-09-01, what changed + why + how to use.
- `feat(admin): more features + error cleanup` — `admin_web/index.html` new `Docs` tab + `Error log` `Clean up >7d` button (`DELETE /admin/errors/cleanup` in `admin.py:29` deletes `api_error_logs` older than 7 days), `BlEND_UI_PLAN` link in docs index.
- `fix(blend): double-project isolation` — `yuukseizasygckaeonrh` (keysheild) activated, tables `users`/`playlists`/`playlist_collaborators`/`playlist_tracks`/`tracks`/`notifications` created, `soundsphere-blend` Render `srv-dabgia5g1s2s73cl7la0` (`https://soundsphere-blend.onrender.com`, `dep-dabgjug…`) with `SUPABASE_URL`/`SUPABASE_SERVICE_KEY` (second project) + `JWT_SECRET=ddcce95…` (same), `PYTHONPATH=backend-auth`, `Root Directory → backend-auth` (dashboard), `app/BlendEndpoint`/`BlendService`/`BlendRepository` + `BuildConfig.BLEND_BASE_URL`, `isDoubleProjectEnabled = BlendEndpoint.isConfigured()` (was `false`).
- `fix(blend): Library 3-FAB overlap` — `LibraryPlaylistsScreen.kt:762` `padding(bottom=72→144dp)` so `Create@16 / AI@72 / Blend@144` all visible; `Items.kt:1035` `• Blend` subtitle + `group` badge; `LocalPlaylistScreen` header `Blend • Collaborative` pill + member strip + `Added by` → `UserInfoSheet` (reusable) + `Cloudinary` `soundsphere/blends` cover sync.
- `fix(blend): missing POST /user/playlists/{id}/collaborators` — `user.py:483` `add_collaborator` (10 cap, `already_member`).
- `fix(security): harden RLS column-write` — audit `SECURITY_AUDIT_RLS.md` (Date: 2026-09-01) flagged 9× (b) fragile `UPDATE (id=user_id)` whole-row policies; `ai_generation_usage.generation_count` is (a) safe (separate table, no `authenticated` policy). No code change yet per audit-only.
- Not pushed — local only.

### Backend — Blend + caps + notifications + Listen Together fix (pushed)
- `feat(backend): Blend (collaborative playlists)` — migration `008_blend_collaborative_playlists` (`added_by_user_id` on `playlist_tracks`, unique `playlist_collaborators`, indexes) already applied via MCP. Helper `_get_accessible_playlist`, union `GET /user/playlists` (owned + `playlist_collaborators`), `GET /user/playlists/{id}` now accessible, `POST /playlists/{id}/tracks` stores `added_by_user_id` and caps at 500, `PUT /playlists/{id}` handles `is_collaborative` + `cover_url`, `GET/DELETE /playlists/{id}/collaborators`, `POST /share/playlists/{token}/join` (cap 10, 409 if full, `is_collaborative` guard). Share `GET /share/playlists/{token}` now returns `is_collaborative` and `added_by_user_id` per track. `POST /playlists` capped at 20, `POST /liked` 2000, `POST /follows` 200, `GET/PUT /notifications` (Blend updates) + `_prune_history` 500 FIFO. `POST /playlists/{id}/tracks` for Blends creates `notifications` rows for other members (type `blend_update`, no webhook — polling, `GET /user/notifications` every 30s). Rate limits doubled: `_READ_LIMIT 300→600/h`, `_WRITE_LIMIT 120→300/h`, `_SYNC_WRITE_LIMIT 600/h` kept for bulk playlist sync. `SyncService` now parses `is_collaborative`/`added_by_user_id`.
- `fix(backend): delete tombstone + 404-drop` — `SyncRepository` now keeps `sync_deleted_playlists` `DataStore` set; `pullPlaylists` skips `server.id` in tombstone, `pushPlaylistDeleted` adds tombstone on success and 404 and emits `playlistSlotFreed` for auto-promote. `PlaylistMenu` + `LocalPlaylistScreen` both call `playlistDeleted()`; multi-select path will be covered by the same tombstone.
- `fix(backend): Listen Together loop` — 5-point fix for the 4s heartbeat stale-clock loop (`ServerClock.positionAt` → `Long?` explicit stale, `Manager` PLAY only forward hard-seek, `pendingSync`/`bypass` stale guards, `drift` loop now re-reads `latestDriftPosition/EffectiveAt` each 250 ms iteration and breaks on stale, `Client` ping `25s→5s` + wake-lock refresh, `revision` + `serverTime` staleness guard with `lastAppliedServerTime`).
- `fix(backend): Supabase transport + logging` — `main.py` `global_exception_handler` now `logger.exception` with route, `httpx.TransportError → 503` instead of 500 + traceback.
- Pushed: backend-auth only (app Blend UI + DB v40 + `UserInfoSheet` + `CloudinaryUploader.uploadBlendCover` + `Home Blend` section remain `local`).

### Release v1.2.1 — stability fixes (pushed)
- `chore(release): bump app to 1.2.1 (versionCode 11)` — was 1.2.0/10. Human-authorized.
- `feat(messages): add v1.2.1 stability-fixes announcement` — new messages.json entry
  (announcement-2026-08-20) covering playlist sync fixes, AI playlist daily cap, dual-source
  player config fallback, and noting Recognize-music / bot-check fixes still in progress.
- Pushed to main, tagged `v1.2.1`, tag pushed → release.yml builds, signs, and publishes the
  APKs (foss + gms).

### AI model fix — Groq endpoint working (pushed)
- `fix(backend): switch AI model to GPT-OSS 120B` — `llama-3.3-70b-versatile` was deprecated
  on Groq (503 "model_not_found"). Switched to `openai/gpt-oss-120b` (500 tps, 131k context).
  Restored `json_object` response format (GPT-OSS 120B doesn't support json_schema).
  Increased `max_tokens` from `count * 40 + 256` to `count * 64 + 512` (GPT-OSS uses
  internal reasoning tokens that consume quota). Confirmed: 30 tracks returns successfully.
  Three commits: `02ebcf3b`, `c5d3c6a2`, `ea3fcef4`. Pushed.

### Investigations completed (local)
- `docs(investigation): Blend collaborative playlists` — full report in `INVESTIGATION_BLEND.md`.
  Covers: DB scaffolding (playlist_collaborators table exists, 0 rows, not used), missing
  `added_by_user_id` column, permission split (members add/remove, owner delete/rename),
  conflict handling (accept race for v1), real-time vs refresh (refresh for v1), second
  backend architecture (JWT is stateless HS256, same secret verifies on both backends,
  Android stores token in EncryptedSharedPreferences, manual per-request auth header).
- `docs(investigation): Recently Played on Home screen` — appended to `INVESTIGATION_BLEND.md`.
  History is per-song only (no playlist tracking), synced via add-only union merge (never
  deletes), grows unboundedly. "Recently Played" needs new `recently_played` table (capped,
  prunable), new backend endpoints, new "replace-with-latest" sync pattern (different from
  existing add-only union), new Home section. ~3 days estimated. Alternative: use existing
  `event` table directly (~0.5 day) but no playlist tracking or cross-device recency.

### Profile sync fix + AI backend deploy (pushed)
- `fix(profile): cache profile data from login response` — `handleTokenResult()` previously
  discarded the full `UserResponse` (email, username, avatar_url) from the login endpoint,
  only saving the JWT. After app deletion + reinstall, the profile (including avatar) was
  invisible until the next cold-start `/auth/me` round-trip, and avatar was never auto-synced.
  Now `cacheAccountProfile()` is called on login with all three fields, and the function
  accepts an optional `avatarUrl` parameter. Also updated `updateUsername()` and
  `updateAvatar()` callers to pass the returned avatar URL. Pushed.

### Playlist sync overhaul + AI generation cap + Change 2 port (pushed)
- `fix(sync): playlist pulls were stored but invisible` — `pullPlaylists()` created pulled
  playlists with `bookmarkedAt = null`, but every Library query filters
  `WHERE bookmarkedAt IS NOT NULL`, so server playlists never appeared in the UI. New
  pulled playlists now set `bookmarkedAt = now()` and show up immediately.
- `fix(sync): inverted playlist-id mapping` — `_serverPlaylistIds` maps localId→serverId but
  `pullPlaylists()` queried it with server.id as the key (never matched). Now builds a
  reverse serverIdToLocalId map and matches by id first, with a name fallback that only
  claims local playlists not already bound to a different server playlist (fixes duplicate
  names like the two "nf home" / two "NF AI Mix" collapsing into one row).
- `fix(sync): login pull could be silently dropped` — `pullMutex.tryLock()` returned
  immediately when a pull was in flight; now `withLock()` queues and always runs.
- `fix(sync): one bad pull stage aborted the rest` — `pullAll()` now wraps each stage
  (likes/playlists/history/follows/settings) in `runPullStage()` so a single failure can't
  kill the whole sync.
- `fix(sync): mappings/pending likes leaked across accounts` — new logout collector in
  `SyncRepository` clears `_serverPlaylistIds`, `_pendingLikePushes` and their DataStore
  keys when `isLoggedIn` flips false.
- `fix(sync): cold start never pulled` — `AuthViewModel.validateStoredSession` now calls
  `syncRepository.onLoggedIn()` after restoring a valid session, so reopening the app with
  a saved login syncs playlists (previously only fresh logins pulled).
- `feat(ai): daily generation cap (2/day/user, account-level)` — new Supabase table
  `ai_generation_usage` (PK user_id+usage_date; migration `006_ai_generation_limits.sql`,
  applied to live project) + backend enforcement in `routers/ai.py`: count checked before
  the Groq call, 429 with a friendly message when exhausted, incremented on success. The
  old IP-based slowapi guard was raised to 50/day (coarse); the real cap is account-level
  so clearing the app cache can't reset it. Client shows a dedicated
  `ai_playlist_limit_reached` toast in LibraryPlaylistsScreen and OnlineSearchResult.
- `feat(cipher): dual-source player-config fallback (Faraday first, Zemer fallback)` —
  ported `docs/upstream_fixes_spec.md` Change 2 (Metrolist commit
  `0d37cc4658c18ac43123264edf48f7224b77d506`). `PlayerConfigStore.kt` restructured: two
  named `RemoteSource`s (Faraday interim first-party-style primary, Zemer fallback), per-
  source tables/cache/meta (old `configs_remote.*` files now belong to Zemer so existing
  installs keep their cache), `FetchResult` replaces the shared `lastAttemptReachedServer`
  flag, `fetchFallbackChain()` fetches Zemer only when the specific broken hash is still
  missing, periodic refresh keeps only Faraday fresh, `refreshAfterStreamRejection()` now
  takes the current player hash. `CipherDeobfuscator.onStreamRejected()` passes
  `currentPlayerHash`. Local build passed (assembleFossDebug). Not pushed.
- `fix(sync): remove deprecated distinctUntilChanged on StateFlow` — compiler error in the
  current Kotlin; the logout collector now collects `isLoggedIn` directly (StateFlow is
  already distinct).

### Upstream fix port — stream validation reliability (pushed)
- `fix(playback): port Metrolist stream-validation fix (YTPlayerUtils.kt)` — per
  `docs/upstream_fixes_spec.md` Change 1. Replaced the permanent
  `webRemixFailedIds` blocklist with a 5-minute TTL map (`webRemixFailures` /
  `hasRecentWebRemixFailure()`); added an explicit skip-and-continue for
  recently failed WEB_REMIX streams before the no-HEAD-validation shortcut;
  `validateStatus()` now sends per-client request headers (User-Agent,
  Accept, Accept-Language, and Referer/Origin for WEB_REMIX / WEB_CREATOR /
  other clients) via a new `YouTubeClient.streamHeaders()` extension. Fixes
  the "playback breaks while logged in until logout/re-login" bug — the old
  blocklist never expired and its only reset path was an unrelated cipher
  refresh. Local build passed (assembleFossDebug). Pushed.
- `fix(auth): gate the sidebar drawer on auth routes (MainActivity.kt)` —
  `ModalNavigationDrawer` now sets `gesturesEnabled` false on Splash/Auth so
  the drawer can't be swiped open there; `onNavigate` defensively closes the
  drawer without navigating on auth-gated routes; hamburger trigger hidden on
  those routes too. Local build passed. Pushed.

### Release prep for v1.2.0 (local, pending human review)
- `feat(announcements): system notification + unread indicator` — when a new
  announcement arrives, the app now posts a system notification (new
  "announcements" channel, tap opens the full announcements feed) and shows a
  red dot over the sidebar profile avatar plus a badge on the Announcements
  sidebar item while there are unseen entries. The feed marks everything as
  read on open; a "Mark all as read" button was added to the feed; the
  notification is dismissed once all announcements are seen.
- `chore(release): bump app to 1.2.0 (versionCode 10)` — was 1.1.7/9.
- `docs(changelog): rewrite changelog.md` — removed the dead Metrolist-era v13.x
  history (below v1.1); added a human-readable v1.2.0 entry (what/why, no dev
  jargon) compiled from the CHANGES.md records since v1.1.4; kept v1.1 → v1.1.4
  Soundsphere entries. Format markers (`---vX.Y.Z`) preserved so release.yml's
  `parse_changelog.sh` still extracts release notes from the v1.2.0 tag.
- `docs(messages): rewrite messages.json` — replaced the test announcement with
  a welcome/thank-you entry for v1.2.0 that thanks everyone for feedback and
  lists the user-facing changes made from it; kept the Aug 16 welcome.
- NOT pushed: user reviews changelog + messages first; then push all local
  work to main and tag `v1.2.0` so release.yml builds, signs and publishes.

### Player download button + settings string + recognition crash + Cloudinary avatar (local)
- `ops: Render log review (soundsphere-auth, Aug 19)` — avatar flow verified end-to-end:
  `PUT /user/profile` 200 with `avatar_url` saved as a `res.cloudinary.com/.../soundsphere/avatars/...` URL in
  the users table (first avatar, so no delete was triggered — expected). Found a **pre-existing, recurring**
  `httpx.ReadError: [Errno 11] Resource temporarily unavailable` (500s through `_require_user` → Supabase GET;
  ~8 occurrences Aug 18-19 on multiple instances; the app retries and usually recovers). Root cause is the
  module-level singleton supabase client (db/supabase.py) whose pooled HTTP/2 connections go stale — no retry
  anywhere. **Decision (documented, NOT implemented):** retry wrapper around `get_supabase()` that catches
  `httpx.ReadError`, resets the singleton client and retries the query (single choke point — 70 `.execute()`
  call sites make per-call-site wrapping non-viable). Left for a later session per human request.
- `push: messages.json added to main (00f7580d)` — the announcement feed was
  never tracked by git, so the app's raw.githubusercontent fetch was 404ing;
  now live with a test announcement. Verified the raw URL returns the feed.
- `push: backend-auth pushed to main (95e11fc8)` — Cloudinary avatar deletion is
  live on Render (deploy triggered, autoDeploy=yes). App-side changes still local.
- `build: :app:assembleFossDebug SUCCESSFUL (12m45s, constrained)` — all changes
  compile; gradle.properties restored to zero diff, daemons stopped.
- `fix(recognition): AudioRecord crash in "Recognize music"` — `stop() called on an
  uninitialized AudioRecord` no longer possible: `recordAudio()` now re-checks the
  RECORD_AUDIO permission at construction time, verifies `getMinBufferSize`, checks
  `AudioRecord.STATE_INITIALIZED` after construction before any lifecycle call,
  only calls `stop()` when the instance actually started recording
  (`recordingStarted` + `RECORDSTATE_RECORDING` guard) and wraps stop/release in
  try/catch as a last-resort safety net. `recognize()` rethrows
  `CancellationException` instead of swallowing it as an Error state. Each
  "Try again" still builds a fresh AudioRecord instance.
- `feat(ui): download button added to the old player design` — previously only the
  new design had it; the classic layout now shows share → download → like, with
  the same add/remove download logic and download/offline/progress icons.
- `fix(strings): Listen Together setting now reads "sidebar"` —
  `listen_together_in_top_bar` / `_desc` updated since the toggle lives in the
  sidebar now, not the top app bar.
- `feat(avatar): Cloudinary avatar upload (Option A)` — app uploads the cropped
  avatar unsigned to Cloudinary (new `CloudinaryUploader`, public cloud name +
  unsigned preset, `soundsphere/avatars` folder), saves the secure URL locally and
  pushes it via the existing `PUT /user/profile` (`AuthViewModel.updateAvatar`).
  The backend (`services/cloudinary.py`) best-effort deletes the previous
  Cloudinary image on replace using the signed Admin API — unsigned uploads
  cannot delete, so cleanup runs server-side. Env vars added to Render
  (placeholders only, no real keys): `CLOUDINARY_CLOUD_NAME`,
  `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `CLOUDINARY_UPLOAD_PRESET`.
  App constants in `CloudinaryUploader.kt` must be filled with the real public
  values before uploads work.

### Loading & error states (local)
- `build: :app:assembleFossDebug SUCCESSFUL (13m17s, constrained)` — all loading &
  error-state changes compile; gradle.properties restored, daemons stopped.
- `feat(ui): reusable ErrorRetryPlaceholder component` — centered message + retry
  button mirroring the OnlinePlaylistScreen pattern, used by all new error states.
- `feat(ui): HistoryScreen Remote tab now has loading / error / empty states` —
  previously blank on failure; `isRemoteLoading` + `remoteError` added to
  HistoryViewModel with re-entry guard on `fetchRemoteHistory()`, plus "No remote
  history found" empty state and error+retry.
- `feat(ui): error + retry for infinite-shimmer screens` — Browse, New Release,
  YouTube Browse, Mood & Genres, Charts, Explore, online search results, Account,
  Artist, Artist Items screens no longer shimmer forever when the request fails;
  each ViewModel gained an `error` flow (set on failure, cleared on reload) with a
  `retry()`/reload path and the screen shows the error message + retry button.
  Home feed (HomeViewModel) shows error+retry when the feed fails and nothing is
  on screen; AlbumScreen is DB-driven so it needed no change.
- `feat(ui): LoginScreen shows a progress overlay while the account is being
  validated` — previously the WebView just sat there during `accountInfo()` checks.
- `feat(strings): new error strings` — `error_unknown`, `error_loading_explore`,
  `error_loading_browse`, `error_loading_new_release`, `error_loading_mood_and_genres`,
  `error_loading_artist`, `error_loading_search`, `error_loading_account`,
  `error_loading_home`, `error_loading_remote_history`, `remote_history_empty`.

### Wallpaper background fix + Bekky contributor asset (local)
- `fix(theme): timestamped wallpaper filenames so re-picks actually change the
  image` — the picker previously overwrote a single fixed file
  (`wallpaper_background.jpg`) and persisted the same `file://` URI every time,
  so Coil (whose cache key is the URI string) kept serving the old bitmap.
  `copyWallpaperToInternalStorage` now writes `wallpaper_<epoch>.jpg` and deletes
  previous `wallpaper_*` files, giving each pick a fresh URI → fresh cache key →
  the new image renders in both the theme preview and the app background.
- `fix(playback): bot detection on the primary WEB_REMIX client no longer aborts
  playback` — the `BOT_DETECTED` throw in YTPlayerUtils.kt was removed; when YouTube
  flags the authenticated primary request ("Sign in to confirm you're not a bot"),
  the code now logs a warning and falls through to the anonymous fallback clients
  (VISIONOS / ANDROID_VR / TVHTML5_SIMPLY, all `loginSupported=false` so they carry no
  auth headers even when logged in), which previously worked only when logged out.
- `style(topbar): smaller, centered wordmark` — "Soundsphere" dropped from
  `titleLarge` to `titleMedium` and the logo+text row is now centered via
  `Arrangement.Center` + `fillMaxWidth`.
- `docs(known-issues): refine entry #0` — verified client matrix (VISIONOS/ANDROID_VR/
  TVHTML5_SIMPLY are `loginSupported=false` so their requests stay anonymous even
  logged in); confirmed the `BOT_DETECTED` throw at YTPlayerUtils.kt:181-188 is the
  sole point that blocks the fallback loop; added poToken session-mismatch suspect
  (visitor-bound token riding authenticated requests).
- `docs(known-issues): add investigation entry #0 for logged-in playback bot detection` —
  authenticated `/player` calls carry `Cookie` + `SAPISIDHASH` Authorization headers
  (InnerTube.kt:158-165); the `BOT_DETECTED` throw in YTPlayerUtils.kt:176-188 aborts
  before fallback clients are tried, so re-login only temporarily masks the issue.
- `build: :app:assembleFossDebug OK (constrained 2GiB/2 workers)` — wallpaper fix,
  Bekky avatar, wordmark + announcements all compile cleanly.
- `fix(theme): persist wallpaper as a copied file instead of the picker's content URI` —
  the theme picker saved the raw `content://` URI from `GetContent` into DataStore,
  but that temporary read grant expires once the process is killed, so the image
  silently failed to load on later launches while the extracted theme color kept
  working. The picker now copies the picked image into app-private storage
  (`filesDir/wallpaper_background.jpg`) and persists a stable `file://` path, which
  both the theme preview and the app background render layer read fine.
- `feat(about): add Bekky contributor avatar` — new `drawable-nodpi/bekky.jpg`
  asset shown in the About page collaborators list (new `avatarRes` field on
  `Contributor`, rendered as a local image when no GitHub avatar URL exists).

### Home top bar wordmark + Announcements feed UI (local)
- `feat(ui): add italic "Soundsphere" wordmark to the home top bar` — next to
  the app logo, using the bundled `bbh_bartle` script font with
  `FontStyle.Italic` (same joined-handwritten look as the Wrapped pages).
- `feat(ui): add on-demand Announcements screen` — new `AnnouncementsScreen`
  (changelog-style bottom sheet) listing the full announcement feed fetched
  fresh via `MessageService.fetchMessages(forceRefresh = true)` with markdown
  bodies; marks every fetched id as seen on open so the launch popup does not
  resurface them. Reached from a new "Announcements" entry (newspaper icon) in
  the navigation drawer, wired through a new `onShowAnnouncements` callback on
  `SoundsphereSidebar`. Strings: `announcements`, `announcements_empty`.

### Lyrics providers — SimpMusic + Unison (local)
- `feat(lyrics): add SimpMusic and Unison providers` — two new top-level Gradle
  modules `simpmusic/` and `unison/` (registered in `settings.gradle.kts` and
  `app/build.gradle.kts`), each with its own `build.gradle.kts`, `models/` and a
  main API client class, matching the `kugou/`/`lrclib/`/`paxsenix/` structure.
  SimpMusic fetches `https://api-lyrics.simpmusic.org/v1/{videoId}` (community
  lyrics keyed on YouTube video id; prefers synced LRC, then rich-sync, then
  plain). Unison fetches `https://unison.boidu.dev/lyrics` — by video id first,
  then by metadata (song/artist/album/duration) as fallback; TTML responses are
  converted to the app's LRC format via the existing `TTMLParser` (depends on
  `:betterlyrics`).
- `feat(lyrics): register new providers in registry` — `SimpMusicLyricsProvider`
  and `UnisonLyricsProvider` (thin wrappers implementing `LyricsProvider`,
  following the `LrcLibLyricsProvider` pattern) added to `LyricsProviderRegistry`
  and appended after the existing 7 entries in `getDefaultProviderOrder()`, so
  they are tried last as fallback-of-last-resort. `deserializeProviderOrder`
  now appends any default-order providers missing from a stored order, so
  existing users' saved orders automatically gain the new providers at the end
  (the reorder dialog and `LyricsHelper` fallback loop then pick them up without
  any further change).
- `feat(settings): add provider toggles and reorder support` — `EnableSimpMusicKey`
  and `EnableUnisonKey` boolean prefs (default true, matching the
  `EnableBetterLyricsKey` pattern); two new toggle rows in the Content settings
  "Provider selection" dialog (`enable_simpmusic`, `enable_simpmusic_desc`,
  `enable_unison`, `enable_unison_desc` strings added); both providers appear in
  the drag-reorder priority dialog once enabled so users can promote them.
- No changes to the 7 existing providers, romanization, `LyricsUtils.kt`, or the
  AI translation feature — additive only.

### Build fixes (local)
- `fix(build): resolve compile errors after theme-look sweep` — `Thumbnail.kt`
  no longer calls composable `thumbnailCornerRadius()` in a non-composable
  default param (computed before `remember` instead); `RecognitionScreen.kt`
  got the missing `thumbnailCornerRadius` import; `AccountSettingsScreen.kt`
  hoists the UCrop toolbar color/`isSystemInDarkTheme`/`stringResource` reads
  out of the launcher callback into the composable scope and gained the
  `Intent`/`toArgb` imports; `ThemeScreen.kt` gained the `SwitchDefaults`
  import. `assembleFossDebug` now builds green.

## 2026-08-16

### Announcements (local)
- `feat(ui): add lightweight in-app announcement system` — new GitHub-hosted
  `messages.json` (repo root, same location as `changelog.md`) with `{id, title,
  body, dismissable}` entries. New `MessageService` fetches it from the raw GitHub
  URL on launch using the same 2-hour cache/TTL pattern as `Updater`. Unseen
  messages (ids tracked in DataStore `SeenAnnouncementIdsKey`, comma-separated)
  appear as a dismissible `AnnouncementSheet` — same presentation and markdown
  body rendering as `UpdateChangelogSheet` — and the id is marked seen on
  dismiss. Entirely GitHub-JSON + local seen-state, no backend call; scoped to
  infrequent general announcements (not a messaging system). First announcement
  added. `announcement_got_it` string added.

### About / Credits (local)
- `feat(ui): add Bekky and Bevah Studio to About screen collaborators` — the
  previously empty collaborators list now shows Bekky (UI/UX Designer,
  `credits_ui_ux_designer`) and Bevah Studio (Creative Team,
  `credits_creative_team`), each with a distinct `MaterialShapes` polygon
  (Clover4Leaf / PuffyDiamond). `Contributor.githubHandle` is now nullable: with
  no handle the avatar falls back to the soundsphere mark, the GitHub trailing
  icon is hidden, and the row click is a no-op — no broken link or blank button.
  The `collaborators.isNotEmpty()` section wrapper now renders with its spacer +
  heading.

### Now Playing / Layout (local)
- `fix(ui): lift Now Playing transport controls off the bottom edge` — increased
  the spacer below the controls (30dp → 56dp) so the prev/play/next row sits
  comfortably higher and users don't have to stretch.
- `fix(ui): reduce default display density a touch` — out-of-the-box density
  scale is now 0.95 (95%) instead of 1.0, so the app feels slightly smaller on
  default settings. `DensityScale.DEFAULT(0.95, "Default (95%)")` added; "Native
  (100%)" remains available for full size. Fallback reads in `DensityScaler` and
  `AppearanceSettings` updated; users who explicitly chose 100% are unchanged.

## 2026-08-16

### Account / Profile (local)
- `feat(ui): add profile icon on homepage top bar` — a circular avatar (YouTube account
  image, falling back to the local Soundsphere avatar, then a default account icon) now
  appears in the top app bar on the Home route only; tapping it opens Account Settings.
- `feat(ui): allow editing Soundsphere username and avatar` — Account Settings' Soundsphere
  section now shows avatar + username + email with an Edit dialog: username saved via new
  `AuthService.updateProfile` (`PUT /user/profile`) + `AuthViewModel.updateUsername`, avatar
  picked via Photo Picker, square-cropped with UCrop into app cache, and persisted locally
  (`SoundsphereAvatarUrlKey`) — no backend upload endpoint exists, so the avatar is
  local-only. New strings: `edit_account`, `edit_account_title`, `edit_avatar`,
  `change_avatar`, `action_save`, `auth_no_email`; removed duplicate `action_cancel`.
- `feat(ui): show local avatar in sidebar header` — Sidebar profile avatar now falls back
  to the locally-picked Soundsphere avatar when no YouTube account image is set.

### Theme / Look (local)
- `feat(ui): use wallpaper as whole-app background` — new "Use wallpaper as app background"
  toggle in Theme → Auto-generated (visible once a wallpaper is picked). When enabled, the
  wallpaper is rendered behind the entire app with a 35% dark scrim for legibility
  (`WallpaperBackgroundKey`); not applied when the pure-black theme is active.
- `feat(ui): make theme Look shape-aware everywhere` — `LocalThemeVariant` CompositionLocal
  exposed from `SoundsphereTheme`; new `thumbnailCornerRadius()` (12dp Material U, 3dp
  Earthy) replaces 31 hardcoded `RoundedCornerShape(ThumbnailCornerRadius)` sites across
  grids, menus, history, podcasts, mini player and player. Theme mockups now render with the
  selected variant. Container/card surfaces that exactly match an Earthy shape token
  (8/12/16/24dp) now use `MaterialTheme.shapes.*` so Material U rounds them while Earthy
  stays pixel-identical.

### Library (local)
- `fix(ui): prioritize song covers collage for playlists without custom cover` — playlist
  thumbnails now show the song-covers collage unless a user-picked cover (gallery file or
  uploaded custom thumbnail) exists, then fall back to the single YouTube thumbnail.

## 2026-08-15

### UI (local)
- `fix(ui): remove GitHub repo links from user-facing UI` — removed "View repository",
  license link, developer GitHub button from AboutScreen; removed "View on GitHub" FAB
  from ChangelogScreen; removed clickable link from Metrolist attribution (text remains);
  removed unused string resources (`github_releases_url`, `view_on_github`,
  `credits_view_repo`, `credits_license_name`, `credits_license_desc`, `credits_github`).
  Changelog `@username` link generation kept as requested.
- `fix(ui): detect YouTube bot detection errors` — added detection for "confirm you're not
  a bot" / "sign in to confirm" responses from YouTube's player API. When triggered,
  throws `BOT_DETECTED` error that stops playback immediately (no endless fallback retries)
  and shows "YouTube requires re-authentication. Please sign out and sign in again from
  Settings." in the player error UI. Also stops playback in MusicService without retrying.
- `feat(ui): expand theme screen with 3-mode color system` — replaced flat palette with
  three switchable modes: **Hand-picked** (19 curated seed colors), **Combos** (10 pre-built
  color pairs from complementary color research), and **Auto-generated** (Material You
  wallpaper-based theming with image picker). Mode persists via `PaletteModeKey`.
- `feat(ui): add curated color combos` — 10 hand-picked complementary pairs (Ocean Sunset,
  Teal+Coral, Lavender+Plum, Midnight+Mint, Mustard+Indigo, Terracotta+Sage,
  Peach+Dusty Blue, Coral+Black, Ocean+Sand, Royal Purple+Gold) with research-backed
  hex codes. Each combo shows primary/accent split swatch.
- `feat(ui): add wallpaper-based theme extraction` — Auto-generated mode now includes
  image picker to select a wallpaper/photo. App extracts dominant color using
  `extractThemeColor()` and uses it as theme seed. Wallpaper URI persisted in
  `WallpaperUriKey` across restarts.
- `feat(ui): Material U theme variant` — added "Look" selector (Earthy / Material U) with
  new `ThemeVariant` enum and `MaterialUShapes` (rounder: 8/12/16/24/32 dp vs 4/8/12/16/24).
  Shape selection wired through `SoundsphereTheme` → `Theme.kt`. Default stays Earthy.
- `feat(ui): add combo accent color persistence` — `ComboAccentColorKey` stores the accent
  color when a combo is selected, enabling future UI elements to use both primary + accent.

### Navigation / Account Settings (local)
- `refactor(ui): restructure sidebar as pure navigation drawer` — Sidebar no longer opens
  AccountSettingsDialog; instead, profile header is non-tappable identity context. Drawer
  items now: Profile → Account Settings (new page), Stats, App Settings, Integrations,
  Updates (with badge). Removed Changelog, About, History, Listen Together from sidebar.
  Sidebar simplified from 4 params to 3 (removed `listenTogetherInTopBar`, `onOpenAccount`,
  `onOpenChangelog`).
- `feat(ui): add Listen Together + Stats to sidebar with toggle` — Listen Together and
  Stats are back in the hamburger sidebar. Listen Together shows in sidebar when
  `listenTogetherInTopBar` preference is true, hidden when false (bottom nav). Stats is
  always visible in sidebar. Sidebar now accepts `listenTogetherInBottomNav` parameter.
- `feat(ui): create AccountSettingsScreen` — new full-page screen replacing the old
  AccountSettingsDialog popup. Sections: Account Info (avatar + name + email), YouTube
  Account (login/logout/status), Soundsphere Account (logout), Advanced YouTube Settings
  (token editor, More Content toggle, YT Sync toggle), Devices Signed In (Soundsphere +
  YouTube status), Listening History (navigates to history page). Delete account placeholder
  noted in code but not built yet.
- `refactor(ui): remove AccountSettingsDialog` — deleted `AccountSettingsDialog` composable
  from `Dialog.kt` and removed its usage from `MainActivity.kt`. Account settings now opens
  as a proper navigation destination, not a modal dialog.
- `fix(ui): homepage profile navigates to Account Settings` — the profile picture/name in
  HomeScreen's AccountPlaylists section now navigates to `account_settings` instead of
  `account` (YouTube playlists page), aligning with the new navigation structure.

### Material You Design (local)
- `feat(ui): Material You design updates` — updated auth screens to use theme colors
  instead of hardcoded colors. Updated ListenTogetherSettings log level colors to use
  theme colors. Created MaterialYouAnimation utility for consistent spring animations.
  All screens now use Material3 components with proper theme colors.
- `fix(ui): resolve compile errors from theme overhaul` — fixed coil3 imports in
  ThemeScreen (coil.compose → coil3.compose, added crossfade import), added missing
  `themeVariant`/`onThemeVariantChange` params to LandscapeThemeLayout, threaded
  `comboAccentColorInt`/`onComboAccentColorChange`/`wallpaperUri`/`onWallpaperUriChange`
  through both layout functions and ThemeControls, added `extractThemeColor` import,
  removed duplicate imports, fixed `Material3SettingsGroup` title (String) and
  `description` (was `subtitle`) usage in AccountSettingsScreen. Build verified:
  `assembleFossDebug` successful.

### Backend / Infra (pushed: RLS + cleanup)
- `fix(backend): enable RLS on all Supabase tables` — added Row Level Security policies
  to all 18 tables (previously 3 had RLS disabled, now all do). Policies allow
  `service_role` full access, `authenticated` users can read/write their own data,
  anon can only read public data (tracks, lyrics).
- `chore(backend): clean up stale pending_registrations` — deleted 9 stuck OTP entries
  (oldest 10 days old, never verified).

### Known issues (local)
- `fix(known): log ReadError exceptions as low priority` — added issue #9 to
  KNOWN_ISSUES.md. Backend ReadError exceptions from mobile client disconnects are
  noisy but harmless; should be logged at DEBUG instead of ERROR.

### Infra (local)
- `chore(infra): clone metroserver for Listen Together self-hosting` — cloned
  MetrolistGroup/metroserver (Go WebSocket server, GPL-3.0) to `metroserver/`,
  added to `.gitignore`. No Soundsphere-specific changes needed — server is generic.

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