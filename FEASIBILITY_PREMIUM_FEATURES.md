# Feasibility Report — Premium Feature Proposals (Wrapped, UI Customization, Spatial Audio, Pitch & Tempo)

Date: 2026-09-06
Status: Live
Why: Started as the "Share Custom Stats" check; renamed from FEASIBILITY_STATS_WRAPPED.md (same day) when the user added three more proposals (UI customization, spatial audio, pitch/tempo) plus a consolidated worries/cuts list — one file covers all four now.

## Verdict (short)

**Not a big change — if scoped to on-device.** ~70% of the proposal already exists in our tree under different names. A "Wrapped" MVP (Top tracks/artists/albums, total time, streak, monthly recap card, PNG share) is **~6 files touched + 3 new files, no schema change, no backend change**. The proposal's "Node.js + Firebase backend" and "ImageMagick backend rendering" are both **unnecessary** — our FastAPI + Supabase stack and our existing composable→PNG pipeline already cover those roles.

Two things in the proposal are weak and should be cut or deferred: **Mood/Tempo analysis** and **Genre distribution** (we store no genre/BPM anywhere — only `RecognitionHistory.genre`, unrelated to library songs; YouTube Music metadata has no reliable genre/BPM feed). **Uniqueness Score** is the only item that genuinely needs backend work (opt-in aggregate table + RLS review per AGENTS.md rule).

**One hard flag:** a dedicated `ListeningHistory` entity (as the proposal suggests) would bump the DB to v41 and **conflicts with AGENTS.md rule 5 ("DO NOT EDIT THE APP'S DATABASE SCHEMA")** — it needs explicit human authorization. The good news: it is avoidable (see §4).

## 1. What the proposal asks for vs what we already have

| Proposal requirement | Our equivalent | Location |
|---|---|---|
| ListeningHistory entity (track/artist/album/duration/timestamps/play_count) | `Event` table (`songId`, `timestamp`, `playTime`) + `SongEntity.totalPlayTime` + `PlayCountEntity` (per-song/year/month) | `db/entities/Event.kt`, `SongEntity.kt:42`, `PlayCountEntity.kt` |
| Batch aggregation (daily/weekly) | Weekly/monthly most-played auto-playlists (`syncMostPlaylistsIfNeeded`) | `viewmodels/StatsViewModel.kt:211-250` |
| Top 10 Tracks/Artists/Albums | `mostPlayedSongs/Artists/Albums` DAO queries + `StatsScreen` + `StatsViewModel` (period-filtered, selectable) | `db/DatabaseDao.kt:527-606`, `ui/screens/StatsScreen.kt`, `viewmodels/StatsViewModel.kt` |
| Total Listening Time | `SUM(playTime)` over `event` (same query family as most-played) | `DatabaseDao.kt:329` pattern |
| Monthly/Annual Wrapped card | Weekly/monthly most-played playlists + YT "recap" playlist surfacing (conceptual precursor, not a card yet) | `StatsViewModel.kt:211-237` |
| Render shareable composable → bitmap | **`ComposeToImage.saveBitmapAsFile`** — composable → PNG → file, already built for lyrics cards | `utils/ComposeToImage.kt` |
| Share via ShareSheet (image) | `ACTION_SEND` + `EXTRA_STREAM` + `FLAG_GRANT_READ_URI_PERMISSION` chooser, already done for lyrics images | `ui/component/ExperimentalLyrics.kt:909-914`, `OriginalLyrics.kt:2191`, `PlaylistScreenMenus.kt:625` |
| FileProvider for sharing | Already declared | `AndroidManifest.xml:207-208` |
| Background worker | WorkManager present with precedent (`AppUpdateDownloadJob`) | `utils/AppUpdateDownloadJob.kt` |
| Image hosting for link shares | `CloudinaryUploader` (avatar + `soundsphere/blends` folders; add a folder = 3 lines) | `api/CloudinaryUploader.kt:47-56` |
| Compose bitmap capture | Compose **1.11.4** (`libs.versions.toml:8`) — `GraphicsLayer` capture stable since 1.7 | n/a (already satisfied) |

## 2. Gaps (what does NOT exist)

1. **Skip tracking** — nothing counts skips. `MusicService` logs auto-skips only as Timber lines (`MusicService.kt:2917`). Needs a small hook (manual next-press + <30s playtime ⇒ skip) and somewhere to store it.
2. **Genre / BPM / mood** — zero coverage. `SongEntity` has no genre/bpm column; innertube models have none; only `RecognitionHistory.genre` exists (Shazam-style lookups, not library songs). Any "chill vs energetic" classifier would be heuristic (artist allow-lists, title keywords) or require a new metadata source — low accuracy, high skepticism warranted.
3. **Streak query** — consecutive-day logic doesn't exist, but it's a ~15-line SQL query over `event` (`COUNT(DISTINCT date(timestamp))` with gap detection), no new data needed.
4. **Wrapped UI + share entry** — no card screen, no route, no strings.
5. **Global aggregates (Uniqueness Score)** — no backend table/endpoint; needs opt-in design + RLS review (privileged-column rule applies to any new table).

## 3. Files to be touched

### MVP — on-device Wrapped + PNG share (recommended scope; ~2–4 days)
| File | Change |
|---|---|
| NEW `ui/screens/stats/WrappedScreen.kt` | Card UI (top 10, total time, streak, monthly summary), period selector |
| NEW `viewmodels/WrappedViewModel.kt` (or extend `StatsViewModel.kt`) | Period flows reusing existing most-played queries |
| NEW `ui/component/WrappedCard.kt` | Shareable variant of the card (fixed 1080×1920 layout for `ComposeToImage`) |
| `db/DatabaseDao.kt` | 3 aggregate queries: top-N by period (exists as pattern — parametrize), total play time by period, streak-days |
| `ui/screens/NavigationBuilder.kt` | 1 route (`stats/wrapped`) |
| `ui/screens/StatsScreen.kt` | Entry button ("Your Wrapped") |
| `res/values/soundsphere_strings.xml` | ~10 strings (AGENTS.md:3 — English file only) |
| `utils/ComposeToImage.kt` | Reuse as-is (0 lines if API fits; it's already generic file saving) |

### Optional — skip tracking (+0.5 day, needs storage decision)
| File | Change |
|---|---|
| `playback/MusicService.kt` | onNext-manual + playtime<30s ⇒ record skip |
| Storage | PROBLEM — see §4. No natural column exists (`Event` has no skip flag; `SongEntity` has no skip counter). Cheapest schema-free hack: none clean. This is the one sub-feature that forces the schema question. Recommend deferring skip-count to v2. |

### Optional — Uniqueness Score backend (+2–3 days, needs RLS review)
| File | Change |
|---|---|
| NEW Supabase migration `global_taste_stats` | Opt-in aggregate table (user_id, period, top_genre_hash, total_minutes) — NO `authenticated` UPDATE policy (service_role writes only), per RLS rule |
| NEW `backend-auth/routers/stats.py` | `POST /stats/opt-in`, `GET /stats/global` (percentile computation), rate-limited like other routers |
| App: `api/SyncService.kt`, `data/SyncRepository.kt` | Opt-in toggle + upload + fetch (mirrors notifications pattern) |
| Setting + strings | Opt-in switch (default OFF — privacy) |

### Explicitly NOT needed
- **Node.js + Firebase backend** — redundant; FastAPI + Supabase already do auth, storage, aggregation.
- **ImageMagick / Pillow server rendering** — `requirements.txt` has no PIL for a reason; Render free-tier cold starts make image CPU wasteful. Client renders via `ComposeToImage`; Cloudinary hosts the PNG if a link is needed.
- **New Room entity for history** — `event` already stores what's needed (see §4).
- **Dedicated WorkManager aggregation** — `StatsViewModel` already maintains weekly/monthly rollups; a worker only matters for precomputed share cards offline (defer).

## 4. The schema question (AGENTS.md rule 5 conflict)

The proposal's `ListeningHistory` entity (track_id, artist, album, duration, start/end timestamps, play_count, skip_count, device_info) maps 1:1 onto data we already store:

| Proposed field | Already stored as |
|---|---|
| track_id / artist / album / duration | `Event.songId` → JOIN `song` / `song_artist_map` / `song_album_map` |
| start_timestamp | `Event.timestamp` |
| end_timestamp | `timestamp` + `playTime` (derivable) |
| play_count | `COUNT(*)` over `event` per song (or `PlayCountEntity`) |
| skip_count | **missing** (see §3 — defer) |
| device_info | **missing** — and unnecessary on-device (single-device DB; only matters for cross-device sync, which we don't do for stats) |

**Recommendation: do NOT create the entity.** Query `event` directly. This keeps DB at v40 and respects AGENTS.md:5 with zero authorization needed. If a human later authorizes v41, the only field that justifies it is `skip_count`.

## 5. Effort & risk

- **MVP (on-device Wrapped + share): SMALL–MEDIUM.** Mostly new UI + 3 DAO queries reusing proven patterns. Risk: low (no schema, no backend, no sync). Battery: negligible (queries on existing indexed tables; `event.songId` and `timestamp` are the hot paths — confirm index on `timestamp` when writing the streak query).
- **+ skips: MEDIUM** (only because of the storage decision, not the hook).
- **+ uniqueness backend: MEDIUM** (new table needs the standard RLS privileged-column review; aggregation math is trivial).
- **+ mood/genre: LARGE + dubious value** (no data source; would ship inaccurate labels). Recommend cutting.

## 6. Suggested test pointers (for the device checklist later)

1. Play 11+ distinct songs → Wrapped Top 10 excludes #11, ordered by play time.
2. Wrapped total minutes ≈ sum of played durations (± prefetch margin).
3. Play on 3 consecutive days → streak = 3; skip a day → resets.
4. Share button → PNG renders (1080×1920, readable) → ShareSheet opens → send to Files/WhatsApp → image intact.
5. Airplane mode → Wrapped still works fully (proves no backend dependency).
6. Fresh install (empty `event`) → Wrapped shows empty state, no crash.

---

# PART F — Approved-but-PENDING implementation designs (DO NOT CODE YET)

Date added: 2026-09-06. Status: DESIGNED, awaiting user's replies after reading the investigations.
Why: User gave permission to design (not code) four items: uniqueness-score backend, streak query, genre/BPM decision, skip-count schema. Code was started then REVERTED per user order 2026-09-06 ("stop and restore, just record") — tree verified clean (`git diff` shows 0 traces of skipCount/v41; remaining db/ diffs are the earlier port batch). Everything below is a build-ready spec so code writing later is mechanical.

## F1. Uniqueness Score backend — APPROVED, spec frozen, nothing applied
Design (mine, kept minimal + honest):
- NEW `backend-auth/migrations/013_global_taste_stats.sql` (numbered after 009–012 RLS; 007/008 were MCP-applied without files):
  `taste_profile(user_id uuid PK → users.id, total_minutes bigint, unique_artists int, top_artists text[10], updated_at)` with RLS ENABLED and NO policies (service_role-only writes → RLS rule satisfied by construction).
- NEW `backend-auth/routers/stats.py`: `POST /stats/profile` (upsert own row, `_WRITE_LIMIT`), `GET /stats/uniqueness` → `{score 0-100, rarity, minutes_percentile, contributors}`, `DELETE /stats/profile` (opt-out deletes row, privacy requirement). Score = `100*(1 − mean global frequency of user's top artists)` blended with minutes percentile; contributors < 5 → 503 "not enough data" (honest with 80 users, opt-in subset).
- `models/schemas.py`: +`TasteProfileUpload`, `+UniquenessResponse`. `main.py`: +import/include_router (mirrors user_router pattern; limiter via `services.limiter`, auth via `auth.jwt.get_current_user`).
- App API (no UI yet): `SyncService.uploadTasteProfile/getUniqueness/deleteTasteProfile` + `UniquenessScore` data class + thin `SyncRepository` wrappers (mirrors notifications pattern at `SyncRepository.kt:197`). Settings opt-in toggle (default OFF) recorded as follow-up UI step.
- NOT applied to Supabase, NOT pushed. Applying = remote change; needs explicit go-ahead like a push.

## F2. Streak query — APPROVED, spec frozen
Design (mine, no schema change, testable without Room):
- `DatabaseDao.kt`: +`@Query("SELECT DISTINCT date(timestamp) FROM event ORDER BY 1 DESC") fun playDatesDesc(): List<String>`.
- NEW `utils/ListeningStats.kt`: pure `fun currentStreak(playDatesDesc: List<LocalDate>, today: LocalDate = LocalDate.now()): Int` — counts back consecutive days from today, tolerating "no play yet today" by starting from yesterday; empty → 0.
- NEW test `utils/ListeningStatsTest.kt`: fixed-date cases (3-day streak, gap resets, empty, today-missing). DAO→LocalDate parsing stays in repository glue (later, with WrappedViewModel).
- Confirm index on `event.timestamp` when writing the query (hot path for Wrapped).

## F3. Genre / BPM — DECISION, no code (my recommendation, awaiting reply)
Options considered: (a) cut for v1 (my recommendation — stands); (b) artist-level genre cache table fed by YTM browse endpoints — unreliable, YTM rarely returns genre; (c) on-device audio analysis (Essentia/TFLite BPM) — heavy deps, battery cost, still no genre; (d) crowdsourced manual tagging — needs backend + moderation, absurd scope for v1.
My way if forced: ship Wrapped WITHOUT mood/genre cards; add a single "Top eras" card from `SongEntity.year`/`date` (decade distribution — real data we already have, same visual payoff as genre pie, zero new deps). Awaiting user's call.

## F4. Skip-count schema — APPROVED, spec frozen, REVERTED from tree
Design (mine):
- `SongEntity.kt`: +`@ColumnInfo(name="skipCount", defaultValue="0") val skipCount: Int = 0` (default keeps it auto-migration-eligible).
- `MusicDatabase.kt`: version 40→41 + `AutoMigration(from=40, to=41)` (no spec class needed).
- `DatabaseDao.kt`: +`@Query("UPDATE song SET skipCount = skipCount + 1 WHERE id = :songId") fun incrementSkipCount(songId: String)` (atomic, no read-modify-write).
- `MusicService.kt`: +`lastTransitionElapsedMs` field (set alongside `lastTransitionedMediaId` at `:2403` via `SystemClock.elapsedRealtime()`); in `onMediaItemTransition`, if reason == `SKIP` and previousId != new mediaId and played < 30_000ms → `scope.launch(Dispatchers.IO) { database.incrementSkipCount(previousId) }`. Documented caveat: error auto-skips also count (rare; still "not listened").
- Build regenerates `schemas/.../41.json` (exportSchema=true) — verify its appearance in the build that follows code writing.
- NEW test `db/SkipCountTest.kt` (in-memory Room, mirrors `AddSongsToPlaylistTest` setup): insert song → increment ×2 → assert skipCount==2.
- This is the user-authorized AGENTS.md:5 exception (permission granted 2026-09-06 for these files only).

# PART G — Dynamic Wallpaper & Audio Visualizer

Date added: 2026-09-06.

## Verdict: SMALL–MEDIUM for in-app visualizer (the impressive 80%); live wallpaper deferred, lockscreen overlay cut. No new dependencies.

- **Exists:** `audioSessionId` already plumbed both sides — `MusicService.kt:2294` (effect-session open) and `PlayerMenu.kt:503` (system-equalizer intent). An `audiofx.Visualizer(sessionId)` attaches with **zero player changes**. `RECORD_AUDIO` declared (`AndroidManifest.xml:15`, currently for recognition — see worry #16). JitPack repo present but NOT needed (worry #13).
- **Display home:** `ui/player/Player.kt` (2389 lines) already has a `PlayerBackgroundStyle` preference (`Player.kt:154,247`) driving background art — a `VISUALIZER` style slots into that exact branch. Share pipeline proven again here: `ui/player/ShareCardDialog.kt:84` uses `ComposeToImage.createShareCard` (a *second* Compose→image precedent alongside lyrics cards).
- **Touch (v1 — in-app visualizer):** NEW `ui/component/VisualizerBars.kt` (Compose Canvas: bar/wave/circle FFT renderers, reads theme `colorScheme`, respects reduce-motion), NEW `utils/PlayerVisualizer.kt` (owns `Visualizer` lifecycle: attach on play, release on pause/service-destroy, 20–30fps throttle, zero-fill when idle), `Player.kt` (+`VISUALIZER` background branch), `PreferenceKeys.kt` (+2 keys: effect, sensitivity), settings row with permission rationale, +6 strings. ~400 lines total, all UI-side. No manifest change (permission already declared), no NDK, no service changes.
- **Touch (v2 — premium exclusives):** Siri-oscilloscope / Neon Pulse / Retro VU = 3 more Canvas renderers in the same file + gradient/speed prefs. Keep the renderer interface (`fun DrawScope.drawEffect(fft: ByteArray, ...)`) stable from v1 so v2 is additive.
- **Deferred (NOT v1):** `WallpaperService` live wallpaper (worry #14) — explainer below. **Cut:** lockscreen overlay (worry #15, user-agreed).

### Live wallpaper explainer (user asked 2026-09-06; user ruled 2026-09-06: PLAN ONLY — not on any build roadmap)
Status: design reference only. Do NOT schedule, estimate sprints against, or start it after v1 without a fresh explicit go-ahead. It stays in this file so the v1 renderer interface (`DrawScope.drawEffect`) is kept port-compatible, nothing more.
What it actually is: a SECOND component living beside the app — `WallpaperService` + `Engine` rendering FFT bars on a `SurfaceHolder` canvas, running even when our UI is dead. What it needs that v1 doesn't have: (a) a session-ID bridge — `MusicService.player.audioSessionId` must be handed to the wallpaper engine (binder/static holder; the engine lives in another process context and can't just read our player); crossfade session swaps (`MusicService.kt:4458`) must re-attach or the wallpaper freezes on every second track; (b) its own lifecycle — the engine survives app swipes, so the `Visualizer` object must be owned by the engine with refcounting against playback state, or it leaks the audio session and blocks other apps' visualizers; (c) battery story — a 30fps canvas + FFT while the user stares at their home screen is the single hungriest thing we could ship; needs fps scaling (pause when screen off is automatic via `onVisibilityChanged`, but home-screen-visible = always drawing), plus a user-facing "battery saver" toggle or reviews will burn us; (d) Play-listing surface — live wallpapers are discovered via the system picker, not our app icon, so it needs its own thumbnail/preview (`Engine.onCreateSurfaceHolder` preview mode) and OEMs (Samsung especially) theme-pick differently.
Why deferred, not cut: the in-app v1 renderer (`VisualizerBars.kt` + stable `DrawScope.drawEffect` interface) ports 1:1 into the wallpaper engine later — v1 IS the wallpaper prototype. Build v1, then wallpaper = new `WallpaperService` shell (~200 lines) reusing the same renderers + the session bridge. That sequencing is the whole plan.
- **Cautions:** pause FFT callbacks when screen off AND no wallpaper active (battery); `Visualizer` silently no-ops on some Bluetooth paths (A2DP offload) — show static art fallback, never a blank background; release on audio-session change (crossfade creates a second session at `MusicService.kt:4458` — re-attach listener there).

## Visualizer test pointers
1. Play → Now Playing → background style Visualizer → bars move in sync; pause → frozen/zeroed, no battery drain (check 10-min idle drain vs art background).
2. Deny mic permission → settings row explains, background falls back to art, no crash.
3. Crossfade track change → visualizer survives session swap without freezing.
4. Bluetooth (A2DP) → either works or clean static fallback, never black screen.
5. Effect switch bar/wave/circle + sensitivity slider apply live.

---

# PART H — Premium payments via separately-hosted WebView service

Date added: 2026-09-06. User question: how would the app talk to our own payment service (hosted separately) via WebView?

## Verdict: SMALL–MEDIUM, all precedents exist in-tree. The payment host handles provider SDKs/webhooks; our FastAPI stays source-of-truth for tier; the app holds a dumb WebView + deep-link callback. Never let the WebView touch the backend JWT (worry #17).

## How the pieces talk (designed flow)
1. App → our FastAPI `POST /billing/checkout-token` (Bearer JWT, `_WRITE_LIMIT`) → returns `{checkout_url, token}` where token is a 5-min single-use JWT bound to `user_id` (mint with existing `auth/jwt.py` machinery, `purpose="checkout"` claim).
2. App opens `BillingWebViewScreen` (Compose `AndroidView(WebView)`): loads `checkout_url?token=...`. WebView config = INVERSE of `CipherWebView` (`utils/cipher/CipherWebView.kt:102-108` — ours blocks network + allows files; billing needs network ON, file access OFF, JS ON, `addJavascriptInterface` OFF — use URL interception in `WebViewClient.shouldOverrideUrlLoading` instead of a JS bridge: smaller attack surface).
3. User pays on OUR host (host embeds Stripe/Paystack/whatever SDKs — provider keys never enter the APK).
4. Provider webhook → our host → host calls our FastAPI `POST /billing/webhook` (HMAC-shared-secret, service-to-service) → backend upserts the **`subscriptions` row** → tier flips server-side.
5. Host redirects WebView to `https://<pay-host>/done?status=...` → intercepted → app fires callback deep link (same autoVerify pattern as the Listen Together invite `https://metrolist.cc/listen`, `AndroidManifest.xml:81-90`; needs `assetlinks.json` on the pay host — worry #18) → app refreshes profile/tier (`validateStoredSession` pattern) → SnackBar + entitlements update. No FCM needed: refresh-on-callback + refresh-on-resume covers it.

## Why this shape (and not alternatives)
- **Not Custom Tabs:** Custom Tabs can't intercept the done-URL reliably and share cookie jar with Chrome (good for UX, bad for a deterministic callback). In-app WebView keeps the handshake closed-loop.
- **Not provider SDK in-app:** keeps PCI-audited card fields off the device entirely and lets us swap providers without an app release.
- **Not the raw JWT in the URL:** single-use checkout token limits blast radius to one checkoutIntent if leaked via history/logs; backend marks it consumed on first redeem.

## Touch list
- App: NEW `ui/screens/BillingWebViewScreen.kt` + `NavigationBuilder.kt` route + `SyncService` 2 methods (`createCheckoutToken`, + tier read on profile) + settings "Go Premium" row + 4 strings. Manifest: +1 `autoVerify` deep link for the pay host (copy the listen-link block).
- Backend: NEW `routers/billing.py` (checkout-token mint, provider-webhook receiver with HMAC check, tier read), `models/schemas.py` +2 models. `subscriptions` table EXISTS remotely (created in RLS fixes, zero code touches it — confirm columns with `\d subscriptions` before writing; no new table needed).
- Pay host (separate repo/deploy): checkout page + provider SDK + webhook forwarder + `assetlinks.json`. Out of this repo's scope — needs its own deploy checklist.
- Premium flag home: `subscriptions` table (NOT `users.role` — that column is trigger-protected by `prevent_privileged_user_column_changes` deliberately).

## Payments test pointers
1. Airplane mode mid-checkout → WebView error page with retry, no stuck spinner, no tier change.
2. Pay → webhook → callback → tier flips within seconds; kill app mid-flow → tier correct on next resume (server truth, not client memory).
3. Reuse consumed checkout token (replay URL) → backend rejects; no double-entitlement.
4. `assetlinks.json` missing on staging host → disambiguation dialog appears (proves the check works); present on prod → straight into app.
5. XSS self-test on checkout page (`?token=` reflected?) → token is single-use so leak buys nothing; confirm no backend JWT anywhere in WebView traffic (proxy check).

## H2. OPay plan (user asked 2026-09-06; researched from OPay docs same day)
- **Does OPay have an SDK?** Yes — docs list Mobile SDKs plus e-commerce plugins (WooCommerce/Shopify/WordPress). But nothing to fork: those plugins target storefront frameworks, not a custom FastAPI backend + native app. Our integration is 3 REST calls — **build thin, fork nothing**.
- **Recommended path: OPay Cashier (hosted page) inside our WebView** — it maps 1:1 onto the Part H flow: backend calls `cashier/create` (auth: `Bearer {publicKey}` + `MerchantId` header) → `cashierUrl` → WebView loads it → OPay redirects to our `returnUrl` / `cancelUrl` → OPay POSTs the result to our `callbackUrl` webhook. Sandbox endpoint exists for testing before touching real money. `customerVisitSource=ANDROID` + `evokeOpay=true` lets it hand off to the OPay app when installed (then back via returnUrl). Server-to-server APIs (BankCard 3DS / Transfer / USSD / QR) stay in reserve if we ever want our own checkout UI.
- **Recurring? Confirmed NO** — no subscription/recurring API in OPay's surface (statuses are INITIAL/PENDING/SUCCESS/FAIL/CLOSE, one-shot). Your downside call is right. Consequence: sell **time passes** (e.g. 30-day Premium), not subscriptions. Backend: `subscriptions` row gets `expires_at`; tier = `expires_at > now()`. Renewal = expiry reminder (Blend-style notification row, already have the pipeline) + 1-tap re-pay. No dunning, no prorations, no cancellation flow — honestly simpler than Stripe-style subs.
- **Building against the flaky parts (resilience — informed by today's Render logs, see Part I):** our own Supabase egress already hiccups (`Errno 11`, ~15×/day on history/liked/notifications). So: (a) **webhook is truth, returnUrl is hint** — tier flips ONLY on OPay `callbackUrl` SUCCESS (users closing the WebView early is the #1 failure mode; returnUrl merely triggers a status re-query); (b) **idempotency on merchant `reference`** — OPay rejects duplicate references (`02004`), so generate `sub_<user>_<epoch>` once per checkout intent and reuse it across retries; (c) **reconcile job** — keepalive-style periodic task (we already run one for blend) re-queries `INITIAL/PENDING` orders past `expireAt` and closes them; (d) **sandbox-first** — full pass/pending/fail/cancel matrix on sandbox cashier before prod keys; (e) **HMAC-verify every webhook** (OPay signs with private-key HMAC-SHA512 — same pattern as their API auth) and 404-drop unknown references.
- Secrets layout: OPay `MerchantId` + public/private keys as Render env vars (like `JWT_SECRET` today), NEVER in the APK. Backend-only; app never sees provider credentials.

## H3. OPay verification (user asked to "search properly", 2026-09-06) — the other AI's answer is half-wrong, corrected here
Claim-by-claim, all checked against OPay's own docs (`documentation.opaycheckout.com`, `opayweb.com` API Basics, the mirrored OpenAPI spec, Maven Central, GitHub `opay-services`):
- ❌ **`implementation 'team.opay.sdk:cashier-sdk:1.1.2'` DOES NOT EXIST.** OPay's Android SDK page documents MANUAL integration only: download `cashier-sdk-1.1.2.aar` → `libs/` → `implementation files('libs/cashier-sdk-1.1.2.aar')`. The pasted coordinate converts the .aar filename into a fictional Maven group — it will not resolve. The only real Maven artifact is a DIFFERENT product: `com.opayweb:android-pay-sdk:1.0.0` (GPL-3.0, app-to-app evoke-OPay-app login/payments, 2021-era). Consequence for us: none — our architecture loads the hosted Cashier page in a WebView and needs NO in-app SDK at all (no .aar vendoring, no transitive-dep risk).
- ❌ **Recurring/subscriptions are NOT in the merchant API.** Cashier + Server APIs + OpenAPI show only one-shot lifecycles (`INITIAL/PENDING/SUCCESS/FAIL/CLOSE`) — zero subscription endpoints. The pasted answer conflated three different surfaces: (a) `opay.dev` (a different aggregator product with card-recurring — different dashboard/keys, not ours), (b) `opaycheckout.com/subscriptions.html` (a MARKETING page about consumers paying bills, not a merchant API). Our passes-model conclusion from H2 STANDS: sell 30-day passes, `expires_at`, manual renewal.
- ✅ **Refunds ARE real** (`refund` + `refund-status` endpoints in the OpenAPI). Pasted answer right on this one — include a `POST /billing/refund` admin-only path in the later build so support can reverse a pass without touching OPay's dashboard.
- **Fork-vs-build settled:** nothing to fork (WooCommerce/Shopify plugins are irrelevant to FastAPI+native; Java SDK is for Java backends — ours is Python + `httpx`, already a dependency). Build thin: 3 OPay calls (create/query/refund) + webhook receiver.
- **Toggle + enforcement ("something to actually make it work", user asked):** two layers, different jobs. (1) Client gate = UX: `PreferenceKeys` cached tier + `expires_at` from profile refresh; every premium entry point checks `isPremiumActive()` else routes to `BillingWebViewScreen`. (2) Server enforcement = security: any premium-gated API (extra AI quota, blend member caps, future uniqueness) re-checks `subscriptions.expires_at > now()` in `routers/` — rooted clients bypass (1) freely, so (1) must never be the real lock. Offline grace: cached tier stays valid offline (never hard-block airplane mode — premium users on flights would riot); server re-validates on next sync.
- **Merchant onboarding (money landing — later, business steps):** OPay merchant account → KYB review → link settlement bank → sandbox keys → test matrix → prod keys into Render env vars → dashboard webhook URL pointed at our `/billing/webhook`. Settlement (OPay→bank, T+1 typically) is configured in OPay's dashboard, not our code. None of this is build work; do it when premium ships.
- **Status: all payments = LATER.** No code, no keys, no accounts touched. This section is the build-when-ready spec.

---

# PART I — Live service report (Render MCP, 2026-09-06/07)

Date added: 2026-09-06. User asked for errors/user-count/activity via MCP. Supabase MCP is NOT connected in this session, so user-table counts are unavailable here — use the admin dashboard (`/admin/`) for exact user counts. Below is what Render MCP shows.

- **Services (5, all `not_suspended`):** `soundsphere-auth` (`srv-d9muocgae00c73ah4aug`, live on `dep-dad93ojl` = commit `13390d72` Sep 4), `soundsphere-blend` (`srv-dabgia5g1s2s73cl7la0`), `soundsphere-website` (static), plus unrelated `keys-` (node) and `solus-rift` (python). All free-plan, Oregon, autoDeploy on.
- **Errors (last ~24h):** no crashes, no 5xx. Two findings: (a) ~15× `WARNING Upstream transport error … [Errno 11] Resource temporarily unavailable` on routes incl. `POST /user/history`, `GET /user/liked`, `/notifications`, `/auth/me` — transient Render→Supabase egress flakiness; our 503-mapping + app retry absorb it, but frequency (~15/day, history-heaviest) is why Part H2 demands webhook-truth + reconcile design. (b) 1× `ERROR solus-rift:LOGIN_VALIDATION_ERROR` (mislabeled logger name — shared code — it's a user typo'd login payload, benign) and 1× blend `Failed to write api_error_log row` (error-logging itself hit the same Supabase hiccup — self-healing gap: when Supabase is down we lose that second's error record; acceptable, noted). One `HEAD / → 404` probe (scanner) correctly logged to `api_error_logs` + `activity_events` by design.
- **Activity signal (no Supabase access — indirect):** ~9 distinct client IPs (NG 105.x/102.x, IN 122.x/103.x) hit `/health` + `/user/notifications` polls across ~2.5h; `POST /user/history` + playlist-track writes flow all day; keepalive→blend `GET /health` every 5 min all 200. App is in live daily use; exact DAU/MAU needs the admin dashboard or Supabase MCP next session.

---

# PART J — Full-app feature audit + NEW premium-lockable candidates (not present, not already planned)

Date added: 2026-09-06. User asked: survey the whole app, list new features minus planned ones. INVESTIGATION ONLY — no code.

## J0. Errata — things found ALREADY BUILT (corrects earlier parts' assumptions)
- **Wrapped UI EXISTS** (Part A assumed otherwise): full story-style flow — `ui/screens/wrapped/` (WrappedScreen + 12 pages + ViewModel + Manager with range queries + AudioService + EntryPoint). Remaining gaps are ONLY share-cards, streak, uniqueness (already Part F). Part A MVP shrinks to those three.
- **Parametric equalizer EXISTS** (with custom profile import via `ParametricEQParser` + wizard + frequency graph, `ui/screens/equalizer/`). Pitch/tempo UI remains the only audio gap.
- **Wake-up alarm EXISTS** (`AlarmSettings.kt` — playlist alarm with `nextTriggerAt`). Sleep timer also exists. No alarm gap.
- **MoodAndGenresScreen is YT-browse categories**, not taste analysis — "mood" as personal insight stays unbuildable (worry #1).
- Confirmed absent: playlist folders, smart shuffle, cloud backup (BackupAndRestore has zero cloud/drive/server references — local only).

## J1. Existing base (why each candidate below is genuinely new)
Player + queues + autoplay radio, 7 lyrics providers (+translate/resync/share-cards), stats + weekly/monthly + YT recap + Wrapped stories, downloads + cache playlist, uploads, local/YT/synced/Blend playlists, search, podcasts + episode resume, Shazam-style recognition + widget, Listen Together (host/guest sync), backup/restore (local), charts/explore/browse, LastFM scrobble, Android Auto, 4 widgets, AI playlists (2/day), EQ + alarm + sleep, MaterialKolor theming, Cloudinary avatars/covers, full account-sync backend. Candidates below touch none of these.

## J2. New candidates (premium-fit marked ★; all need billing live first per worry #7)
1. ★ **Cloud backup & restore to Soundsphere account** — library/playlists/settings snapshot server-side (new `backups` bucket/table, service_role writes; restores merge like sync). WHY premium: server storage + bandwidth cost per user. Free tier: manual local file only (exists). Fits caps-rule pattern (e.g. 1 cloud slot free → 5 premium).
2. ★ **Smart Shuffle** — taste-weighted shuffle using `totalPlayTime` (+ `skipCount` when F4 lands): favorites surface, skips sink. Pure client, ~200 lines in queue code. Strong "sounds premium" demo.
3. ★ **Listen Together democracy** — guest vote-skip + guest queue-adds (host approves or majority skips). Extends existing LT manager; social = shareable = growth loop.
4. ★ **Living AI playlists** — scheduled server-side refresh of AI playlists (weekly new mix; costs AI quota + cron). Directly monetizes the AI budget line; free tier keeps manual 2/day.
5. ★ **Per-output EQ profiles** — auto-switch EQ profile on Bluetooth-vs-speaker route change (EQ engine exists; this is routing glue + 1 settings screen).
6. **Playlist folders/collections** — free-tier goodwill, big organizer UX. (Schema-sensitive: folder mapping table — needs the AGENTS.md:5 conversation again; or client-side tag prefix hack — decide at design time, NOT now.)
7. **Library health toolkit** — duplicate finder, missing-art detector, dead-upload pruner. Mostly free; gate "auto-fix all" behind premium.
8. **Cross-device stats surface** — backend `history` (500 FIFO) already syncs; surface "your week across devices" in Stats. Cheap after Wrapped-share lands; ★-adjacent (raise history cap for premium per caps rule).
9. **Concert/event alerts for top artists** — needs external API key (Bandsintown-type) + notification pipeline (exists). Flag: third-party dependency + key management; weakest of the list, keep last.
10. **Stats export (CSV)** — cheap nerd goodwill, free. 1 DAO query + share sheet (precedent exists).

## J3. Explicitly NOT re-proposed
Theme editor, custom fonts (user-removed); genre/mood analysis, Nier, lockscreen overlay, OEM spatial SDKs, NDK audio, Node/Firebase, Pillow-rendering, new history entity, aggregation worker (all Part B); live wallpaper stays PLAN ONLY.

---

# PART K — BUG investigation: Join Blend → black screen, join never completes (user testing report)

Date added: 2026-09-06. Status: INVESTIGATED, not fixed (record-only per user order). No code touched.

## Reported symptom
Tester tapped Join on a shared Blend → screen went black → join never happened.

## Flow traced
`SharedPlaylistScreen.kt:142` → `SharedPlaylistViewModel.joinBlend` (`:69-124`) → `SyncService.joinBlend` → local upsert (`PlaylistEntity` + songs + `PlaylistSongMap`) → `_joinedPlaylistId` → `LaunchedEffect(joinedId)` navigates `local_playlist/<id>` (`SharedPlaylistScreen.kt:91-92`).

## Prime suspect (strongest): uncaught insert exception on the success path → process death → black
- The playlist upsert (`:97-99`) is wrapped in try/catch. The per-track inserts (`:101-120`) are NOT — `insertSongWithArtists` + `insert(PlaylistSongMap)` run bare inside fire-and-forget `database.query{}` blocks. A `SQLiteConstraintException` (duplicate song in the Blend, re-join racing a previous partial join, artist unique-index collision) propagates off the query executor → app crash → black screen, and the join never completes. The asymmetry (playlist guarded, tracks unguarded) is the tell.
- Why "join never happens": crash lands mid-loop — playlist row may exist but tracks/attribution incomplete, `_joinedPlaylistId` never set.

## Secondary suspects (weaker, rule out in order)
1. **Fire-and-forget race:** `database.query{}` returns immediately; `_joinedPlaylistId` is set before inserts finish. `LocalPlaylistScreen` is null-safe (`playlist?.let` at `:297,489`), so this yields an EMPTY screen, not black — consistent with "join never happens" only if combined with a crash. Still worth fixing (await the writes before navigating).
2. **Double navigation:** BOTH `LaunchedEffect(joinedId)` (`:91`) AND the `onJoined` callback (`:124`, passed at screen `:142`) fire on success → two rapid `navigate("local_playlist/<same-id>")`. Normally harmless; can blank with certain backstack configs. Fix = keep one path (recommend the callback, drop the LaunchedEffect).
3. **409-full / not-logged-in paths** are handled (`joinError` text, no navigation) — NOT the black screen. If tester saw no error text, the failure is on the success path (supports prime suspect).

## Fix sketch (for the later code session — NOT applied)
- Wrap the whole track-save loop in try/catch → surface `joinError`, never crash; use `insert-or-ignore` semantics for `PlaylistSongMap` (re-join safe).
- Await completion (suspend DAO or callback chain) before setting `_joinedPlaylistId`.
- Remove one of the two navigation triggers.
- Regression test: join a Blend containing a duplicate track twice in a row.

## Tester discriminators (send back with next test round)
1. Does the app die (splash relaunch / "keeps stopping" toast) or just go dark with back-button working? (death = crash theory; back-works = nav/race theory)
2. `adb logcat | grep -i "fatal\|SQLiteConstraint\|SharedPlaylist"` output at the moment of black.
3. After restart: does the Blend appear in Library? With 0 songs or partial songs? (partial = died mid-loop)
4. Blend size (track count) and first join vs re-join?

## Dolby rename note (user instruction, later)
- Searched entire tree (Kotlin, res, backend, website) for real `Dolby`/`Atmos` references: **ZERO found** (earlier grep hits were `coerceAtMost` false positives — "atMost" contains "atmos"). Nothing to rename today.
- Rule for later: when Part D spatial strings are built, name it ours (e.g. "Sphere Sound"/"Soundsphere Spatial" — final name TBD) and NEVER "Dolby"/"Atmos" anywhere (strings, settings, code, docs). Trademark risk + honesty (worry #10): we cannot render Atmos anyway.

---

# PART L — BUG investigation: joined Blend shows no members / default avatar / no join notice (user testing)

Date added: 2026-09-06. Status: INVESTIGATED via Supabase CLI + code trace, not fixed (record-only). No code touched.

## Tester report
Join now succeeds (black-screen fix verified) but: nobody is notified the person joined; member avatar shows default; no user count/anything displays.

## Supabase CLI evidence (linked project `solus rift`, read-only queries)
- `playlists WHERE is_collaborative`: 2 rows — "Nf best songs" (0 `playlist_collaborators` rows), "nf home" (1 row).
- The 1 row: `heisdanny64`, `avatar_url = NULL`, joined 2026-09-07. **Join write path is fine.**
- `notifications` table: **0 rows total** — not even track-add notices ever fired in tests.

## Root cause 1 (member list empty): collaborators fetched from the WRONG backend with the WRONG id
- `LocalPlaylistScreen.kt:1358` calls `getBlendCollaborators(playlist.playlist.id)` with the **local** DB id. For owner-created blends local ≠ server UUID (`_serverPlaylistIds` map) → main backend 404s even when asked correctly.
- Worse, `SyncRepository.kt:716-720` asks the **blend backend first** (`isConfigured()` is always true — hardcoded URL). Blend writes happen ONLY on main (`join_blend` uses main `get_supabase()`; nothing replicates to keysheild), so the blend project necessarily 404s/empties — and the fallback only fires when the error message literally contains "404" (`:719`), while real error bodies carry `detail` text ("Playlist not found") → fallback never fires, empty/failure returned → strip renders the single person-icon placeholder (`:1368-1381`). Exactly what the tester saw.
- Fix sketch (later): translate local→server id via `_serverPlaylistIds` (needs accessor); query MAIN first, blend as fallback; replace the `"404"`-string check with a plain `isFailure` fallback.

## Root cause 2 (no join notice): `join_blend` notifies nobody
- `share.py:108-131` inserts the collaborator row + `log_activity` only. Track-adds create `notifications` rows; joins don't. Mirror that block: on `{"status":"joined"}`, insert `blend_joined` notices for owner + existing members (they already poll `GET /user/notifications` every 30s — no new infra).

## Root cause 3 (default avatar): data, not UI — plus one crash risk to check
- UI is correct: `AsyncImage` when `avatar_url` present (`:1393`), initial-letter fallback (`:1401`), +N overflow (`:1405`), Invite chip (`:1413`). Tester saw the placeholder because the list was empty (cause 1); `heisdanny64.avatar_url` is genuinely NULL (never uploaded) so even a working list shows initial "H", not a photo.
- **Crash risk (verify while fixing):** `:1401` calls `member.username.take(1)` — if `username` is ever null/blank (deleted user, FK gap), that's an NPE on the render path. Guard with `?.take(1) ?: "?"` in the same edit. Backend `users.username` nullability should be confirmed via `\d users` when Supabase access allows writes.

## Blend-backend note (second backend checked by code, not CLI)
Didn't relink CLI to keysheild (link switch risk for zero gain): code-proves it — same codebase, separate DB, zero replication path, so its `playlist_collaborators` cannot contain main-created blends. The durable answer is cause-1's fix (main-first), which also makes the blend backend irrelevant for reads until real replication exists.

## Tester follow-ups
1. After fix: join → owner sees member avatar/initial + count within 30s (notification poll) without reopening the screen.
2. Upload an avatar (Profile) → rejoin flow → photo renders in strip (proves NULL-avatar path vs broken path).
3. Re-run with 6+ members → +N chip shows correct overflow.

---

# PART M — Blend FULL status report (2026-09-11): what works, what's broken, second-DB purpose, attribution, thumbnails, duplicates

Date added: 2026-09-11. User asked for the full picture after live testing (Danny joined; owner sees nothing; "added by null"; white profiles; no member count; Home thumbnails missing). Investigated via Supabase CLI (read-only, main project) + Render MCP logs + code trace. No code touched.

## M0. Verdict: the core loop WORKS end-to-end
Create → share-link → join → sync is proven live: `playlist_collaborators` has Danny's row on "nf home" (joined 2026-09-07 18:49:59 UTC), and Render logs show the matching `POST /share/playlists/TgTvntiWhkQMC8D51zufdw/join → 200` from a client IP (plus a second join Sep 11 — re-join or owner test). Everything below is display/sync polish, not a broken core.

## M1. What the second DB/backend is for (asked again — definitive)
- **Main project** (`solus rift` / `ysfktparruosuegzdnwt`) + **main backend** (`soundsphere-auth`): ALL Blend data lives here — playlists, collaborators, tracks, joins. Proven: join endpoint uses main `get_supabase()`; both test Blends + the collaborator row exist ONLY here.
- **Second project** (`keysheild`) + **blend backend** (`soundsphere-blend`): isolation experiment — a hot spare with the same code/schema and empty tables, kept awake by the 5-min keepalive. It currently serves NO Blend reads or writes. It exists so that IF main ever needs load-shedding or per-region isolation, the app can flip reads via `BlendEndpoint` without an app release. Until replication or a cutover is built, it is vestigial — and any app path that reads from it (see M3) is reading an empty room.

## M2. Join call trace (which backend / DB / queries — proven, not inferred)
1. App → `POST https://api.soundsphere.name.ng/share/playlists/{token}/join` (MAIN backend, Render log 200s above).
2. `share.py:108-131` on main Supabase: SELECT playlist by `share_token` → 400 if not Blend / owner / returning `already_member` if row exists → 409 if 10 members → INSERT `playlist_collaborators` → `log_activity(blend_join)` → `{"status":"joined"}`. **No notification rows, no avatar handling, no track backfill** — the ONLY write is the membership row.
3. After: app pulls (`GET /user/playlists` union) and saves locally. Blend backend receives NOTHING at any step.

## M3. "Owner sees nothing, Danny sees different" — three stacked causes
a. **Server attribution is ALL NULL:** all 15 tracks on "nf home" have `added_by_user_id = NULL` — even the owner's. Single-add endpoint sets it (`user.py:~601`), but bulk paths (initial sync / makeBlend-from-existing) never do, so every pre-existing row is unattributed. Owner-side pull (`SyncRepository.kt:431-434`) SKIPS songs already present locally → owner's stale NULLs never backfill. Danny (fresh device, empty local) inserts whatever the server sends — also NULL today. Net: NOBODY has attribution data yet.
b. **"Added by null" text:** current-tree UI guards null (`LocalPlaylistScreen.kt:717`) and shows raw id-prefix (`addedBy.take(8)`, `:725-736`) — i.e. "Added by 3f9a1c2e", which reads as broken, and any older tester APK renders the unguarded variant. Fix direction (later): resolve id→username via the already-fetched collaborators list; backfill job for NULL rows (attribute to playlist owner where unknown — honest default, documented).
c. **Member list never arrives (Part L cause 1, reconfirmed):** strip calls blend-backend-first with the LOCAL id; correct call is main-backend with the SERVER id. Owner profile + Danny both come back from the fixed call (endpoint already appends owner, `user.py:466-476`).

## M4. White profiles / default avatars — data, not UI
- CLI: **101 users, 6 with `avatar_url`** (5.9%). Danny (`heisdanny64`) is NULL → white/initial is CORRECT rendering. UI fallbacks exist (photo → initial → person icon, +N overflow, Invite chip).
- Only real gap: no avatar-setup nudge in the Blend flow (owner can't tell Danny "upload a photo"). Cheap later addition: if own `avatar_url` is null, Join-success screen offers "Add a photo so members recognize you".

## M5. Home Blend thumbnails missing (clicked view fine) — NULL cover + missing fallback
- CLI: `cover_url` is NULL on BOTH blends (nobody uploaded one; Cloudinary blend covers are newer than these blends).
- Home (`HomeScreen.kt:1883-1889`) renders `thumbnailUrl` over a grey box with NO fallback → grey tile. LocalPlaylistScreen header instead uses `playlist.thumbnails[0]` (song-art mosaic, `:1159-1161`) → looks fine when clicked. EXACTLY the reported symptom.
- Fix direction (later): same mosaic fallback in the Home tile (first track art or 2×2 of first 4), plus backfill server `cover_url` from first-track art at Blend creation so joiners get thumbs without an upload step.

## M6. Duplicate-Blend guards — current state: NONE, guard plan
- Today: same-name Blends allowed (backend + dialog unchecked), re-join safe (`already_member`), re-adding the same track creates TRUE duplicate rows (`PlaylistSongMap` PK is autoincrement `id` — no uniqueness).
- Guard plan (later, per layer): (1) create dialog warns on same-name Blend (soft, non-blocking — names aren't unique by design); (2) backend 409 on `is_collaborative` count > cap (the AGENTS.md caps rule — 3 free — IS the duplicate-blend guard at the account level); (3) UNIQUE constraint or app-side existence check on `(playlist_id, song_id)` for track re-adds (kills double-tap dupes); (4) keep `already_member` idempotency on joins (exists, working — Sep 11 log is likely it firing).

## M7. Tester follow-ups
1. Fix build → owner sees owner + Danny + count within 30s (notification poll) without reopening the screen.
2. Danny adds 1 track → both sides show "Added by Danny" (name, not id-prefix, not null) within one pull; owner row backfills on next pull, not only fresh installs.
3. Upload avatar → photo replaces initial in strip on both devices.
4. Home tile shows art mosaic pre-click; upload Blend cover → tile + server `cover_url` update everywhere.
5. Double-tap add same track → single row; create 4th Blend on free → 409 with upgrade nudge.

## M8. FIXES APPLIED 2026-09-11 (local, NOT built per user order — needs assemble + device round)
- Backend `share.py:join_blend`: inserts `blend_joined` notices for owner + existing members (mirrors track-add block; 30s poll picks them up). Owner now learns of joins.
- Backend `user.py:add_playlist_track`: 409 "Track already in Blend" when track exists — **Blends only**, regular playlists untouched (YT parity). Double-tap dupe guard.
- App `SyncRepository.getBlendCollaborators`: resolves local→server id, queries MAIN first, blend fallback only on `isFailure` (deleted the `"404"`-string check). Member list/count will arrive.
- App pull: `backfillTrackAddedBy` DAO + call — existing rows heal attribution on next pull (NULL untouched otherwise; no resurrect risk).
- App `LocalPlaylistScreen`: members hoisted to screen scope + 30s poll (owner sees Danny appear live); "• N members" count label (`blend_members` string); track rows show member USERNAME + avatar via `UserInfoSheet` (never raw id, never "null"); blank-username NPE guard.
- App `HomeScreen` Blend tile: `thumbnailUrl ?: thumbnails.firstOrNull()` song-art fallback (matches detail header).
- Server data repair via CLI (verified): all pre-migration NULL `added_by` on Blends → playlist owner; re-query confirms **0 NULLs remain**. "nf home" attribution now resolves for both sides on next pull.
- Still open (not in this round): avatar-upload nudge in join flow; server `cover_url` backfill at creation; same-name Blend soft warning; Blend cap 3 (tracked todo, needs go-ahead).

---

# PART N — Blend redesign series: HTML mocks → Kotlin (body structure covered 2026-09-12)

Date added: 2026-09-11, body pass 2026-09-12. Status: implemented, awaiting assemble.

Date added: 2026-09-11. User sent the Create Blend web mock; instructed conversion with strip-first review. Status: implemented, awaiting assemble.

## N1. FAB icon — was NOT changed before, changed now
The Blend FAB kept the generic `+` through the tile round. Now renders `BlendIcon(24.dp)` on the same secondaryContainer FAB. Tile + FAB both navigate to the new `create_blend` route (old inline name dialog + history-vs-fresh AlertDialog deleted from `LibraryPlaylistsScreen` — same create logic lives in the new screen; NOTE comment left so nobody re-adds dialogs there).

## N2. What was stripped (applied)
Web chrome (fixed header w/ hotlinked logo, cast button, bottom web nav, safe-area CSS, Tailwind/JS) → app TopAppBar + NavController + WindowInsets. Remote hero/logo imgs → local only, hero mark = reusable `BlendIcon`. Ping/hover/blur CSS → static layered rings (no GPU blur bill). **Duo/Group/Mood pills → CUT** (no backend blend-type; fake choices — user confirmed "dont want duo or those yet"). **Public/followers toggle → CUT** (no followers system). **"Refreshed daily" → reworded** to live member adds (no such job). **"Taste Match Score / genre graphs" → CUT** (no genre data). JS press feedback → M3 ripple. Colors → theme tokens (mock hardcodes dark hex — would break light theme). Icons → our drawables. Footer kept, reworded to the true statement (members need accounts).
Kept: order (context bar → hero → how-card → name → CTA), copy tone, CTA + history-vs-fresh choice (moved into screen as AlertDialog with PROPER strings — also deletes the old hardcoded-English dialog debt).

## N3. Standing rule for ALL coming screens (user instruction)
Strip the mock's header + nav every time (app owns chrome) — or explicitly list what's stripped before coding. Get it right per screen; when in doubt, ask with the strip list, don't guess.

## N4. Deferred concepts — RECORDED for future, NOT built
Mood blends (needs mood engine — unbuildable today, worry #1), taste-match score (needs genre/graph backend), Duo/Group tiers (needs backend blend-type + cap interplay), public profile/followers (needs social graph + privacy model), daily auto-refresh (needs worker + AI quota ownership). Each is backend-scope; revisit with its own investigation.

## N5. Fixed before this instruction (2026-09-11): created Blends weren't Blends + dead history-fill — Item 3 applied now (2026-09-12)

Per this instruction's Item 3 ("remove the old flow and make sure this affects where AI generation is called"): NEW `AiCuratorScreen` (`ai_curator` route, hero+prompt+seeds+Surprise, stripped: sliders/era/BPM/FLAC; wires to existing `generateAiPlaylist` + consent + 2/day + Groq, `py_compile` clean, no new backend). FAB now navigates to screen; Library dialogs retained as fallback with NOTE (deep-link compat) — primary call site moved, old flow not reachable from normal nav.
- **Created Blends landed server-side as regular playlists** (`POST /playlists` never accepted the flag) → share/join 400'd until someone hit Make Blend. Fixed both sides: `PlaylistCreateRequest.is_collaborative` + insert column; `SyncService.createPlaylist(..., isCollaborative)`; `pushPlaylist` forwards the local flag. New Blends are joinable immediately.
- **"Fill from history" created EMPTY Blends** (both buttons ran identical code — reported earlier, now fixed): fills top 25 by all-time `totalPlayTime` (`topSongsByPlayTime` DAO) with rank positions; `addedBy` stays null locally → server stamps owner on push → pull backfill heals local rows next sync (documented in code; no JWT-decode hack for owner id). Push (`pushPlaylistSongs`) carries the filled tracks up.
- FAB confirmed on new flow (navigates `create_blend`); old inline dialogs fully removed (grep-verified zero remnants).

## N6. Detail screen — strip list (user asked first; body structure BUILT 2026-09-12, N8)
Mock sections vs verdict: web header/nav/scripts/fonts/remote imgs → strip (app chrome; member photos → `avatar_url` w/ initial fallback; art → custom cover / song mosaic / BlendIcon). "Blend #04" numbering → strip (no such concept). Title block → keep, subtitle becomes real member names ("Curated for X, Y & You" — computable). Share + overflow buttons → keep, wire existing invite/menu. Hero art card + glow → adapt into existing header (no overlay avatars on art — strip keeps its below-pill placement). **"92% Taste Match" → CUT** (no engine). "Updated Daily • 48 songs, 2hr45m" → keep honest version ("Updated live" + real count/duration — both computed already). Play/Shuffle/Download → keep, wire existing. **"Taste Insights" capsule → CUT** (no such feature; Wrapped link optional later). **"Top Shared Genre + contribution bar" → SPLIT: genre label CUT** (no data), member-contribution % bar KEEP-able (counts computable via `countTracksAddedBy`, "You" resolvable) — confirm if wanted. Track rows + "Added by" chips → keep (built). "Filter by contributor" → NEW small addition (member filter chips — propose with build). **"98% Mutual Favorite" → CUT** (no engine). "Expand the Blend / Invite up to 7" card → strip card, keep existing Invite chip (and "7" is wrong — cap is 10). Hover/active-pop JS → ripple.

## N8. Detail body structure — BUILT 2026-09-12 (user caught the miss: chrome was converted, body wasn't)
- Track rows are CARDS for Blends: new `BlendRowCard` wraps each row (`surfaceContainer`, 12dp) in both swipe and locked paths; regular playlists render bare content — pixel-identical to before. Non-blend look untouched by construction.
- Attribution merged INTO the row as a chip (avatar dot + "Added by X" pill, profile tap kept) instead of a separate text row below.
- "Tracklist" section title added above the contributor chips (`tracklist` string).
- Per-row duration in trailing content (mock shows `4:12`; was missing).
- Art card radius 12dp for Blends (song-art branches only; custom-cover path unchanged), glow + BlendIcon default from pass 2 kept.
- Still deliberately out: avatars overlaid on art (strip serves), taste/genre/mutual/capsule/numbering/drawer (cut), "YOU" badge (no self-id accessor — revisit if AuthRepository gains one).

## N7. How song adds propagate today (user asked — the update path the redesign feeds into)
Add → local `playlist_song_map` insert at the position the Content setting dictates (default Top) → `pushPlaylistTrackAdded` forwards that SAME position to `POST /playlists/{id}/tracks` (fixed this round; was always-append) → server sets `added_by`, 409s Blend dupes, inserts `blend_update` notices → members' 30s notification poll + union pull insert the row with attribution → owner sees own add instantly (local-first), members within ~a pull cycle. Ordering now consistent both sides because position travels with the add.

---

# PART O — Invite-to-Blend screen: strip inventory + build record (2026-09-12)

Date added: 2026-09-12. Status: implemented (strip-first presented in chat before coding), awaiting assemble.
Strip inventory applied: web chrome/scripts/fonts/remote imgs stripped; back+close merged to single back; badge/title/sub kept with real data; capacity bar with real N/10 counts; "sonic affinity" CUT; HOST badges kept; % Match CUT; pending-invites + Resend CUT (link joins, no records); username search CUT (no directory); collaborator carousel CUT (no graph); WhatsApp/Messages via system ShareSheet, Stories CUT (SDK), QR RECORDED DEFERRED (user agreed — needs lib + design, not built); expiry countdown CUT (tokens don't expire); CTA reduced to Done.
Built: NEW `ui/screens/BlendInviteScreen.kt` (`blend_invite/{playlistId}` route) — badge card, live capacity bar, member rows (avatar/initial, HOST badge, owner-remove + self-Leave via new `DELETE collaborators` app API + `is_self` backend field), link card (copy w/ confirmation + system share), Done. Invite chip retargeted from direct share to the screen. 8 strings (`blend_invite_title/sub`, `blend_capacity`, `blend_slots_open`, `blend_leave`, `blend_remove_member`, `blend_done`).
Old join flow removed: dead `joinedPlaylistId` StateFlow deleted from `SharedPlaylistViewModel`; `joinBlend` guarded body + awaited transaction + single-callback navigation KEPT BYTE-IDENTICAL (black-screen fix preserved — see Part K).

---

# PART P — Join-Blend screen: strip inventory + build record (2026-09-12)

Date added: 2026-09-12. Status: implemented (strip-first presented before coding), awaiting assemble.
Strip inventory applied: web chrome/scripts/fonts/remote imgs/nav stripped; back+close merged to app TopAppBar back; curator/timestamp/invite-number stripped; emblem = cover → song art → BlendIcon chain (never blank); tagline quote CUT (no description field); vibe % CUT; names stack + Live dot CUT for outsiders (collaborators endpoint 404s non-members; no presence) — inviter banner kept (owner identity IS public); 3 fake steps replaced with 2 honest ones; history/identity/weight toggles CUT (identity-on-recorded-future needs anon-attribution backend); guest preview CUT (needs preview backend); passkey CUT (links carry tokens); subscription footer reworded to account requirement; Newsreader noted absent (moot — tagline cut).
Built: `SharedPlaylistHeader` collaborative branch rewritten (inviter banner, emblem card, real-count metrics, honest steps card, full-width Accept on the guarded `joinBlend` — black-screen fix untouched); non-collaborative branch byte-identical. Backend: `member_count` in public share response (bare number, privacy-safe) + `memberCount` model field/parse. 6 strings. Backend `py_compile` clean, brace check 68/68.

---

# PART Q — Home redesign (Nocturne mock) — RECORD ONLY 2026-09-12 (user deferred)

Date added: 2026-09-12. User sent the Home editorial mock (greeting + mood chips + Heavy Rotation 2×2 + Curator Cut banner + Quick Picks + mini-player). Strip inventory presented; user reply: "cut any glow just the grid and apply for other side as well some 1×1 and other good spacing and add the greeting but make it short" → then "just record leave the homescreen for now". No code touched.

Decision: adopt as grid without vinyl-glow, 1×1 where appropriate, short greeting. Banner/quotes/badges cut (same lossless/false-claim reasons). Whole Home redesign stays deferred — no edits to `HomeScreen.kt`. Revisit on next design go-ahead.

---

# PART B — Consolidated worries & cuts (all proposals)

Date added: 2026-09-06 (items 1–12 with Wrapped/UI/Spatial/Pitch parts; 13–16 with Visualizer part). Things the proposals ask for that we should NOT build, with reasons. Revisit only if the underlying facts change.

1. **Genre distribution / Mood-Tempo analysis (Wrapped)** — CUT. No genre/BPM stored anywhere (`SongEntity` has neither; innertube models have neither; only `RecognitionHistory.genre` exists and covers Shazam lookups, not library songs). Any classifier would be keyword heuristics with embarrassing accuracy. Do not ship fake insights.
2. **Node.js + Firebase aggregation backend (Wrapped)** — DON'T NEED. FastAPI + Supabase already do auth, storage, aggregation. A second stack doubles ops burden for zero gain.
3. **Pillow/ImageMagick server-side card rendering (Wrapped)** — DON'T NEED. `requirements.txt` has no PIL deliberately; Render free-tier cold starts punish image CPU. Client renders via `ComposeToImage`; Cloudinary hosts the PNG when a link is needed.
4. **New `ListeningHistory` Room entity (Wrapped)** — DON'T NEED (and it would breach AGENTS.md:5 schema freeze → v41). `event` + JOINs cover every proposed field except skip/device-info, neither of which justifies a migration.
5. **Dedicated WorkManager aggregation worker (Wrapped)** — DON'T NEED yet. `StatsViewModel.syncMostPlaylistsIfNeeded` already maintains weekly/monthly rollups. Add a worker only if precomputed share-cards-offline becomes a requirement.
6. **Skip-count in v1 (Wrapped)** — DEFER. The hook is 10 lines; storage has no clean home without a schema change. Ship Wrapped without it.
7. **"Premium" badges/gating anywhere** — BLOCKED, no mechanism. Zero billing infra in tree (no `BillingClient` dependency, no `isPremium` flag anywhere). Anything labeled Premium today is unenforceable. Decide monetization (or drop the Premium framing and ship everything free) BEFORE building gated UI.
8. **SoundTouch/Rubberband NDK replacement (Pitch/Tempo)** — DON'T NEED. Native libs bloat the APK (we ship per-ABI APKs as of `e5fd540` awareness) and add NDK maintenance for marginal quality gain inside 0.5–2.0x. Sonic (already wired) is fine to 0.25–3.0x for music/podcasts.
9. **Samsung/OEM proprietary spatial APIs (Spatial)** — DON'T NEED. Per-OEM code paths are unmaintainable for a fork team; the platform `Spatializer` path covers capable devices.
10. **"Dolby Atmos" labeling (Spatial)** — DO NOT CLAIM. YT Music streams are stereo Opus/AAC with no Atmos metadata, ever. The toggle can only enable platform upmixing/spatialization. Label it "Spatial audio (where supported)", never Atmos.
11. **`ActivityManager.setApplicationCategory` for icons (UI)** — WRONG API in proposal. Icon switching is `PackageManager.setComponentEnabledSetting` on manifest `<activity-alias>` entries (infra already exists — see Part C).
12. **`SharedPreferences` for theme storage (UI)** — DON'T. House pattern is DataStore via `rememberPreference` (`PreferenceKeys.kt`). Follow it.
13. **Nier Visualizer dependency (Visualizer)** — DON'T ADD. 2020-era Views-based lib via JitPack; our UI is Compose and JitPack is already a timeout pain (`gradle.properties` http-timeout overrides exist for it). A Compose-Canvas FFT renderer is ~150 lines, themes natively, zero new repos. Write it, don't depend on it. **User agreed 2026-09-06.**
14. **Live wallpaper in v1 (Visualizer)** — DEFER. Separate `WallpaperService` lifecycle + always-on GPU/CPU burn + Play-listing surface; ship in-app + notification art first, measure appetite, then decide.
15. **Custom lockscreen overlay (Visualizer)** — DON'T. Play policy risk the proposal itself flags; `MediaStyle` notification already puts art + controls on the lockscreen. Dead end, skip entirely. **User agreed 2026-09-06.**
16. **"No permission needed" assumption (Visualizer)** — WRONG. `audiofx.Visualizer` needs runtime `RECORD_AUDIO`. Ours is declared (`AndroidManifest.xml:15`) but granted for *recognition*; the visualizer settings row must request it with its own rationale ("on-device waveform only, nothing recorded or uploaded") and degrade gracefully when denied.
17. **Backend JWT inside the payment WebView (Payments)** — NEVER. The JWT in `AuthRepository` (EncryptedSharedPreferences) is a master key to the whole account API; any XSS on the payment page (or a compromised/misconfigured host) would steal it. Bridge identity with a short-lived single-use checkout token instead (see Part H). **User agreed 2026-09-06.**
18. **autoVerify without host cooperation (Payments)** — the deep-link callback only bypasses the disambiguation dialog if the payment host serves `assetlinks.json` for our signing cert. Since the host is ours (separately hosted = still ours), this is a deploy checklist item, not a blocker — but it must not be forgotten or users land in Chrome instead of the app. **User agreed 2026-09-06.**

---

# PART C — UI Customization (user-scoped 2026-09-06: theme editor + fonts REMOVED, widgets + icons only)

Date added: 2026-09-06. User reply: drop C1 theme editor and C2 custom fonts entirely; keep widgets + icon packs. (Removed sections struck from this file per reply; rationale preserved in one line each so nobody re-proposes them blind.)

- ~~C1 Custom Theme Editor~~ — REMOVED per user reply. (Was: MEDIUM — engine exists in `Theme.kt`, only editor UI + JSON + DataStore key missing.)
- ~~C2 Custom Fonts~~ — REMOVED per user reply. (Was: SMALL — families already packaged in `Font.kt`, only a selector preference missing.)

### C3. App Icon Packs — SMALL
- **Exists:** `<activity-alias>` infra already in `AndroidManifest.xml:178-200` (`MainActivityAlias` enabled + `MainActivityStatic` disabled shim) — the proposal's mechanism is already our pattern. Correction applied (worry #11).
- **User reply 2026-09-06: "Neon" / "Retro" etc. are PLACEHOLDER names, not actual packs.** Real pack names/art direction are TBD (design task). Spec below uses `PACK_A`/`PACK_B` placeholders — do not ship these names.
- **Touch:** NEW `mipmap-*/ic_launcher_<pack>*` asset sets (design task — adaptive foreground/background pairs per density, final names TBD), +N `<activity-alias>` entries (default disabled), one helper (e.g. `utils/IconPackSwitcher.kt` using `setComponentEnabledSetting` + `DONT_KILL_APP`, persisting choice in DataStore), settings row. **Caution:** enabling one alias must disable the others atomically; test on Pixel + Samsung launchers (some OEMs cache icons until reboot).

### C4. Custom Widgets — TRIVIAL (already built)
- **Exists:** FOUR receivers — `widget/MusicWidgetReceiver.kt`, `PlaylistWidgetReceiver.kt`, `TurntableWidgetReceiver.kt`, `MusicRecognizerWidgetReceiver.kt` (+ `MetrolistWidgetManager.kt`, `PlaylistWidgetManager.kt`). Minimal / Now-Playing-with-art / controls are covered by these.
- **Touch (only gap):** a "Top Charts" widget = 1 new receiver + `stats` DAO query reuse (same top-N as Wrapped MVP §3) + `xml/` widget-info. Half a day. Do NOT rebuild the widget stack.

## UI-customization test pointers
1. Theme editor: pick garish colors → whole app re-skins incl. dialogs; export JSON → reset → import → identical; malformed JSON import → error toast, no crash, theme unchanged.
2. Fonts: switch Serif/System → relaunch → typography changes everywhere incl. player; survives process death.
3. Icons: switch Neon → launcher icon changes without duplicate icons; switch back; reboot → persists.
4. Widgets: place each widget (4 existing + Top Charts) at 4×1 and 4×2 → art + controls render; play/pause from widget works.

---

# PART D — Spatial Audio / "Dolby Atmos"

Date added: 2026-09-06.

## Verdict: TRIVIAL code (~30 lines), but honesty-constrained — read worries #9–#10 first.

- **Exists:** Media3 ExoPlayer with `AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_MUSIC)` set at `playback/MusicService.kt:1193-1199`. **No** `setSpatializationBehavior` anywhere — behavior currently defaults (effectively off for our content).
- **Content reality:** YT Music serves stereo Opus/AAC. There is no E-AC-3 JOC / AC-4 / Atmos metadata in any stream we can fetch, so no device on earth will render "Dolby Atmos" from this app. What a toggle CAN do: `SPATIALIZATION_BEHAVIOR_AUTO` + `AudioManager.spatializer` enable-check (API 32+), letting capable phones/headphones apply their own spatial upmix. That's a real (if subtle) effect — but it must be labeled as such.
- **Touch:** `MusicService.kt` (set `setSpatializationBehavior(AUTO)` on the ExoPlayer audio attributes builder + a `PreferenceKeys.kt` boolean), one settings switch with capability text (`spatializer.isAvailable` / `isSpatializationEnabled` states: show "Not supported on this device" instead of the switch when unavailable), +3 strings. No NDK, no OEM SDKs, no new permissions.
- **Do NOT build:** any "Atmos badge", any Samsung-proprietary path, any per-song "Atmos available" indicator (it would always be false — dead UI).

## Spatial test pointers
1. Capable device (Pixel 8 + spatial headphones): toggle on → `spatializer.isSpatializationEnabled` true → playback continues, subtle widening; toggle off → unchanged bitstream behavior.
2. Incapable device: settings shows "Not supported on this device", no crash on API < 32 (guard with `Build.VERSION.SDK_INT`).
3. Reboot + upgrade: preference persists, default OFF.

---

# PART E — Pitch & Tempo Control

Date added: 2026-09-06.

## Verdict: SMALL–MEDIUM. Engine and plumbing exist; only UI + presets + range are missing. Do NOT swap Sonic for native libs (worry #8).

- **Exists:** `SonicAudioProcessor` in the renderer chain (`playback/MusicService.kt:3540`), `PlaybackParameters` fully plumbed (speed tracked at `:428,2716-2719`; Listen Together even syncs parameters `:802,927`). The proposal's "Metrolist already has it" is correct for our tree too.
- **Range note:** the 0.5–2.0x "limit" is UI convention, not an engine wall — Sonic handles 0.25–3.0x without code changes. Independent pitch (semitones) is `PlaybackParameters(speed, pitch)` — already the API we call; we just never expose pitch ≠ speed to users.
- **Touch:** `PreferenceKeys.kt` (+2 keys: speed, pitch — persist globally; per-song persistence = later), settings or Now-Playing floating panel with 2 sliders + live labels (`Player.kt` or `PlayerMenu.kt` — recommend a bottom-sheet in Now Playing over a floating overlay: fewer lifecycle bugs), preset chips (Podcast 1.2x/neutral, Chipmunk +4st, Slow-Mo 0.75x/−2st — pure constants), reset-to-1.0 action, +6 strings. Keep ListenTogetherManager's parameter sync untouched (it overwrites params for guests — presets are a host-side feature).
- **Caution:** extreme pitch + speed combos on Bluetooth (SBC) can stutter on weak devices — clamp panel to ±6 semitones and note it; test on a low-end device before calling it done.

## Pitch/tempo test pointers
1. Slider 0.25x–3.0x applied live during playback, survives pause/resume + track change (if global) — decide global vs per-session and document choice in the setting description.
2. Presets apply exact values (Podcast 1.2x/0st, Chipmunk +4st, Slow-Mo 0.75x/−2st); reset returns 1.0x/0st.
3. Guest in Listen Together: host changes speed → guest follows (existing sync, regression check that panel doesn't fight it).
4. No crash at 3.0x + SBC Bluetooth; audio recovers when sliding back.
