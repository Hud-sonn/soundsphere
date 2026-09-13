# Upstream Test Plan — 2026-09-04/05 Metrolist commits

Date: 2026-09-05
Status: Live
Why: User asked to check the ~17h-old upstream change, port what's needed, and record what must be tested + run unit tests before build/push.

## Upstream commits reviewed (from `/tmp/metrolist`, fetched 2026-09-05)

| Commit | Age | Subject | Verdict |
|---|---|---|---|
| `5253a4d` | 17h | fix: handle config redirects and redundant podcast heading | **PARTIAL** — podcast header ported; InnerTubeX part skipped (see below) |
| `1eda3c8` | 24h | fix: hopefully fix cache bugs | **PORTED** — all 4 parts (2 adapted, see below) |
| `a902053` | 24h | feat: add Zemer lyrics provider | **SKIPPED** — niche provider ("Lyrics for Jewish music"), not needed for build; optional later |
| `fb1e40b` + older | 2d+ | (previous batch) | Out of scope per user instruction |

## What was ported (local, uncommitted)

### 1. `1eda3c8` — cache bugs (PORTED)
- `MusicService.kt:1607` `markCachedIfFullyDownloaded` — content-length now falls back to `ContentMetadata.getContentLength(playerCache.getContentMetadata(mediaId))` when `song.format?.contentLength` is null (songs whose FormatEntity was never fetched were never flagged to Cache playlist); +1s re-check delay before giving up.
- `MusicService.kt:2390` — `MEDIA_ITEM_TRANSITION_REASON_REPEAT` also marks previous track cached (repeat-one completions were lost).
- `MusicService.kt:2488` — `STATE_ENDED` now marks current item cached (covers queue-end with no further transition).
- `SelectionSongsMenu.kt:79` — new `onRemoveFromCache: (() -> Unit)? = null` param + "Remove from cache" menu item (uses existing `R.string.remove_from_cache`).
- `CachePlaylistScreen.kt:440` — selection menu passes `onRemoveFromCache = { viewModel.removeSongsFromCache(ids) }`.
- `CachePlaylistViewModel.kt:96` — `removeSongFromCache` delegates to new `removeSongsFromCache(ids)` which drops bytes AND clears `dateDownload` flags explicitly (previously flags lingered until the 1s polling loop self-healed).
- `CachePlaylistViewModel.kt:68` — polling loop uses new `cachedContentLength()` = format length → playerCache metadata → downloadCache metadata (adapted equivalent of upstream's `partitionCachedSongs(resolveContentLength)`; our ViewModel has no `partitionCachedSongs`, so upstream's `CachePlaylistPartitionTest` addition was NOT portable — no equivalent unit to test).

### 2. `5253a4d` — redundant podcast heading (PORTED)
- `HomeScreen.kt` — deleted the "Latest Episodes" header block (9 lines); episode sections render with their own titles.

### 3. Skipped with reason
- `5253a4d` `withEmbeddedConfigFallback` — InnerTubeX-only (`YtConfigParser.fetchEmbeddedConfig` doesn't exist in our vendored `innertube`). Our `PlayerConfigStore` dual-source (Faraday → Zemer remote JSON) already covers config-fetch resilience. No action.
- `a902053` Zemer lyrics — 267-line provider + registry + toggle + strings for a niche catalog. Our registry already contains its other hunk (order-merge fix). Optional future port, not needed for build.

## Manual test checklist (for the APK in `app/build/outputs/apk/foss/debug/`)

### Cache playlist (high priority — the bug being fixed)
1. Play a song to completion (no skip) → open Library → Cache playlist → song appears. (Tests: AUTO-reason marking.)
2. Set repeat-one, let a song loop twice → Cache playlist contains it. (Tests: REPEAT-reason marking.)
3. Play a queue to the very end (last song, no repeat) → last song appears in Cache playlist. (Tests: STATE_ENDED marking.)
4. Play a freshly-added song with unknown format length to completion → appears in Cache playlist. (Tests: ContentMetadata fallback. To force: clear app storage, stream (don't download) a new song, let it finish.)
5. Cache playlist → long-press → select 3 songs → overflow → "Remove from cache" → all 3 disappear immediately (no 1s linger, no reappear after refresh). (Tests: batch remove + explicit flag clear.)
6. Single-song overflow → Remove from cache → disappears immediately.

### Podcasts (low priority)
7. Home → Podcasts chip → episode sections render WITHOUT a duplicate "Latest Episodes" header above them.

### Regression (from 2026-09-05 port batch — still untested on device)
8. Upload MP3 from Library → Songs (progress → 100%, appears in uploads).
9. Settings → Content → add-to-playlist position Top/Bottom.
10. Blend create/join/add-song/attribution flow.
11. Recently Played order + restore after relogin.
12. Playlist song delete via swipe / overflow / multi-select → stays deleted after refresh.

## Unit test report
- Command: `./gradlew :app:testFossDebugUnitTest --max-workers=2 --no-daemon` (RAM-constrained per AGENTS.md).

### Result (2026-09-05, updated)
- 28 tests, 14 suites. **28/28 pass, 0 failures.**
- Fixed during run:
  - `ContentResolverExtTest` didn't compile (`ShadowContentResolver` import missing) — added import. Now 2/2 pass.
  - `ServerClockTest.reset` failed (`expected 500 but was null`) — test encoded the PRE-fix contract; updated to `assertNull` + comment. Now 3/3 pass.
  - `YouTubeUtilsTest` 4 failures — root-caused to June `73a2ebf1` simplification (behavior replaced, tests never updated). User approved fix 1+2: REAL ggpht bug fixed in working code (`YouTubeUtils.kt:28` append → replace; `=s88-s544` malformed no more), 3 stale expectations updated to intended behavior (`-p` smart-crop kept, i.ytimg unchanged with documenting comment). Now 5/5 pass.
- All port/feature suites green: `PlaylistSyncTest` 3/3, `PlaybackSyncTest` 1/1, `DownloadUtilTest` 1/1, `HomeSpeedDialTest` 1/1, `AddSongsToPlaylistTest` 2/2, `PlaylistDuplicatesBatchedTest` 1/1, `SongEntityTest` 1/1, `PlayerConfigStore*` 3/3.
- `assembleFossDebug` BUILD SUCCESSFUL (2026-09-06, RAM-constrained); APK `app/build/outputs/apk/foss/debug/app-foss-debug.apk` (54.6 MB) includes the ggpht fix + all ports. `gradle.properties` restored, no diff.
- Not pushed — awaiting user go-ahead.
