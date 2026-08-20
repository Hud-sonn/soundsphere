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

### Release v1.2.1 — stability fixes (pushed)
- `chore(release): bump app to 1.2.1 (versionCode 11)` — was 1.2.0/10. Human-authorized.
- `feat(messages): add v1.2.1 stability-fixes announcement` — new messages.json entry
  (announcement-2026-08-20) covering playlist sync fixes, AI playlist daily cap, dual-source
  player config fallback, and noting Recognize-music / bot-check fixes still in progress.
- Pushed to main, tagged `v1.2.1`, tag pushed → release.yml builds, signs, and publishes the
  APKs (foss + gms).

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