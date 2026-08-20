# Upstream Fixes to Port — Precise Change Spec

Two changes ported from upstream Metrolist (`mostafaalagamy/Metrolist`, GPL-3.0),
verified line-by-line against the current state of this repo. Every line
number below was confirmed against the actual current files — re-view each
file immediately before editing, since any prior edit in this session shifts
line numbers for everything after it.

**Before starting**: clone `https://github.com/mostafaalagamy/Metrolist.git`
fresh (not shallow — full clone, `git clone` without `--depth 1`, so
`git show` works) to cross-reference the original commits directly if
anything below is ambiguous:
```
git clone https://github.com/mostafaalagamy/Metrolist.git
cd Metrolist
git show ec9a6b3ec1ac8bc6cf362cab3eee9e632598e86f
git show 0d37cc4658c18ac43123264edf48f7224b77d506
```

Do this first. The line numbers and code below are accurate as of this
writing, but confirm against a fresh view of both this repo and the
Metrolist commits before editing — do not blindly paste.

---

## Change 1 — Stream validation reliability fix

**Source**: Metrolist commit `ec9a6b3ec1ac8bc6cf362cab3eee9e632598e86f`,
"fix: playback once again"

**Why**: fixes a confirmed, reproduced bug — playback breaks specifically
when a YouTube account is logged in, and appears to "fix itself" only via
logout/re-login. Root cause, confirmed against this repo's actual code:

1. `webRemixFailedIds` is a permanent, never-expiring blocklist. Once a
   videoId's WEB_REMIX stream fails validation once, it stays permanently
   blocked for the rest of the app process's life. The only reset path
   (`clearWebRemixFailures()`) only fires as a side effect of a successful
   cipher config refresh — it has no relationship to login/logout. The
   "re-login fixes it" behavior users are seeing is very likely a
   coincidental side effect of login triggering enough retry activity to
   incidentally hit that same refresh path, not a real fix.
2. `validateStatus()` sends a bare HEAD request with no `Referer`, `Origin`,
   or per-client `User-Agent` — nothing identifying which client/context the
   request is coming from. This is a plausible, direct trigger for
   YouTube's bot-detection, especially on authenticated (logged-in)
   requests, which receive more scrutiny than unauthenticated ones.

**File**: `app/src/main/kotlin/com/soundsphere/music/utils/YTPlayerUtils.kt`

### 1a. Replace the permanent failure set with a TTL-based one

**Current code, lines 44-61** (confirmed exact):
```kotlin
    // Track videoIds whose WEB_REMIX stream URL 403'd on the ExoPlayer GET, so the next resolution
    // falls through to the fallback clients instead of skipping HEAD validation and looping.
    private val webRemixFailedIds = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )

    fun markWebRemixFailed(videoId: String) {
        webRemixFailedIds.add(videoId)
    }

    /**
     * Cleared when the cipher recovers (player config refreshed after a stream rejection): the
     * prior WEB_REMIX failures were caused by the stale cipher, so let resolution try WEB_REMIX
     * again instead of staying pinned to a lower fallback client for the rest of the process.
     */
    fun clearWebRemixFailures() {
        webRemixFailedIds.clear()
    }
```

**Replace with**:
```kotlin
    private const val WEB_REMIX_FAILURE_TTL_MS = 5 * 60 * 1000L

    // Temporarily skip WEB_REMIX after its stream is rejected so refresh can fall through without
    // permanently pinning a video to fallback clients after a transient CDN failure.
    private val webRemixFailures = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun markWebRemixFailed(videoId: String) {
        webRemixFailures[videoId] = System.currentTimeMillis()
    }

    private fun hasRecentWebRemixFailure(videoId: String): Boolean {
        val failedAt = webRemixFailures[videoId] ?: return false
        if ((System.currentTimeMillis() - failedAt) !in 0 until WEB_REMIX_FAILURE_TTL_MS) {
            webRemixFailures.remove(videoId, failedAt)
            return false
        }
        return true
    }

    /**
     * Cleared when the cipher recovers (player config refreshed after a stream rejection): the
     * prior WEB_REMIX failures were caused by the stale cipher, so let resolution try WEB_REMIX
     * again instead of waiting out the TTL.
     */
    fun clearWebRemixFailures() {
        webRemixFailures.clear()
    }
```

### 1b. Update the call site that checks the failure set

**Current code, lines 425-442** (confirmed exact):
```kotlin
                // WEB_REMIX authenticated CDN URLs can 403 on HEAD yet serve fine on the byte-range
                // GET that ExoPlayer makes. Skip HEAD validation for the main client and let ExoPlayer
                // try directly, UNLESS this videoId already 403'd on GET (markWebRemixFailed) — then
                // fall through to the fallback clients. Saves a validateStatus round-trip per resolve.
                
                val isUgcOrPodcast = musicVideoType == "MUSIC_VIDEO_TYPE_UGC" ||
                                     musicVideoType?.contains("PODCAST") == true ||
                                     musicVideoType == null

                if (currentClient.clientName == "WEB_REMIX" &&
                    !webRemixFailedIds.contains(videoId) &&
                    !isUgcOrPodcast
                ) {
                    Timber.tag(logTag).d("WEB_REMIX — skipping HEAD validation, letting ExoPlayer try directly")
                    Timber.tag(TAG).i("Playback: client=${currentClient.clientName}, videoId=$videoId")
                    successClient = currentClient.clientName
                    break
                }

                if (validateStatus(streamUrl)) {
```

**Replace with** (two changes: add an explicit skip-and-continue block for a
recent failure, and update the remaining reference from the old set to the
new function):
```kotlin
                if (currentClient.clientName == "WEB_REMIX" && hasRecentWebRemixFailure(videoId)) {
                    Timber.tag(logTag).d("Skipping WEB_REMIX after a rejected stream for $videoId")
                    continue
                }

                // WEB_REMIX authenticated CDN URLs can 403 on HEAD yet serve fine on the byte-range
                // GET that ExoPlayer makes. Skip HEAD validation for the main client and let ExoPlayer
                // try directly. Failed WEB_REMIX streams are filtered before this point.
                // Saves a validateStatus round-trip per resolve.

                val isUgcOrPodcast = musicVideoType == "MUSIC_VIDEO_TYPE_UGC" ||
                                     musicVideoType?.contains("PODCAST") == true ||
                                     musicVideoType == null

                if (currentClient.clientName == "WEB_REMIX" &&
                    !isUgcOrPodcast
                ) {
                    Timber.tag(logTag).d("WEB_REMIX — skipping HEAD validation, letting ExoPlayer try directly")
                    Timber.tag(TAG).i("Playback: client=${currentClient.clientName}, videoId=$videoId")
                    successClient = currentClient.clientName
                    break
                }

                if (validateStatus(streamUrl, currentClient.streamHeaders())) {
```

**Important — this repo does NOT use the `YouTubeClient`-object refactor
Metrolist made.** In this repo, `successClient`/`bestFallbackClient` are
plain `String` (confirmed: `var bestFallbackClient: String? = null`,
`var successClient: String? = null`, around lines 221-222), not the full
`YouTubeClient` object Metrolist switched to. **Do not port that part of
Metrolist's refactor.** Instead, add the `streamHeaders()` extension
function keyed directly off `currentClient` (which is already a
`YouTubeClient` in scope at this point in the loop, per existing code) —
see 1c below. This keeps the string-based `successClient`/
`bestFallbackClient` fields exactly as they are in this repo; only the
`validateStatus()` call itself gains a headers parameter.

### 1c. Add headers to `validateStatus()` and add the header-building extension

**Current code, line 643** (confirmed exact):
```kotlin
    private fun validateStatus(url: String): Boolean {
```

Find the full current function body (starts at line 643) and change its
signature, adding the new headers to the request builder inside it:

```kotlin
    private fun validateStatus(
        url: String,
        requestHeaders: Map<String, String>,
    ): Boolean {
        Timber.tag(logTag).d("Validating stream URL status")
        try {
            val requestBuilder = okhttp3.Request.Builder()
                .head()
                .url(url)

            requestHeaders.forEach { (name, value) ->
                requestBuilder.header(name, value)
            }

            // ...rest of the existing function body stays exactly as-is —
            // do not remove the existing cookie-header logic for private
            // tracks, only add the loop above before it...
```

Then, immediately after the `validateStatus` function's closing brace, add
this new extension function:

```kotlin
    private fun YouTubeClient.streamHeaders(): Map<String, String> =
        buildMap {
            put("User-Agent", userAgent)
            put("Accept", "*/*")
            put("Accept-Language", "en-US,en;q=0.9")

            when (clientName) {
                "WEB_REMIX" -> {
                    put("Referer", "https://music.youtube.com/")
                    put("Origin", "https://music.youtube.com")
                }

                "WEB_CREATOR" -> {
                    put("Referer", "https://studio.youtube.com/")
                    put("Origin", "https://studio.youtube.com")
                }

                else -> {
                    put("Referer", "https://www.youtube.com/")
                    put("Origin", "https://www.youtube.com")
                }
            }
        }
```

Before adding this, confirm `YouTubeClient` has a `userAgent` property
accessible from this file (check the `YouTubeClient` class definition,
likely in the `innertube` module) — if the property is named differently,
adjust the `put("User-Agent", userAgent)` line to match the actual property
name rather than assuming.

### 1d. Update every remaining reference to the old set name

Search the whole file for any remaining reference to `webRemixFailedIds`
beyond what's covered above (there should be none left after 1a/1b, but
confirm via a full-file search before considering this change complete) —
every reference must now go through `hasRecentWebRemixFailure()` or the new
`webRemixFailures` map, not the old set.

### Testing after Change 1

1. Log into a YouTube account within the app, attempt playback of a song
   that previously failed to play while logged in. Confirm it now plays
   without needing to log out and back in.
2. Confirm logged-out playback is unaffected (should work exactly as
   before — this change only adds previously-missing request context, it
   doesn't remove anything from the logged-out path).
3. Confirm a genuinely broken/unavailable video still correctly falls
   through to fallback clients rather than looping indefinitely (the TTL
   change means a video can retry WEB_REMIX again after 5 minutes even if
   still broken — confirm this doesn't cause a visible retry-loop or delay
   for the user, since the check only affects internal client selection,
   not user-facing retry behavior).

---

## Change 2 — Add a first-party fallback config source (Faraday-style)

**Source**: Metrolist commit `0d37cc4658c18ac43123264edf48f7224b77d506`,
"feat: port faraday cipher config store"

**Why**: this repo currently has exactly ONE remote source for cipher/player
configs — `ZemerTeam/zemer-cipher`, a third-party repo (confirmed: the
single `REMOTE_URL` in `PlayerConfigStore.kt`, decoded to
`raw.githubusercontent.com/MetrolistGroup/Metrolist/main/app/src/main/assets/player_configs.json`
— note this actually currently points at **Metrolist's own asset file**,
not zemer-cipher directly, despite the variable name; confirm this during
implementation, it may already be pointing somewhere adjusted from what was
originally documented). Metrolist's fix adds a SECOND, independent source
ahead of the existing one in priority — reducing single-point-of-failure
risk on one third party.

**Decision already made**: adopt this two-source fallback pattern now for
the immediate resilience win, using Metrolist's own `MetrolistGroup/faraday`
repo as the interim first-party-style primary source. **This is temporary**
— the goal is to eventually replace `MetrolistGroup/faraday` with a
genuinely first-party SoundSphere-owned config source once that's built,
keeping `zemer-cipher` (or the current remote source) as the ultimate
fallback either way. Structure the code so swapping the primary source URL
later is a one-line change, not a re-architecture.

**File**: `app/src/main/kotlin/com/soundsphere/music/utils/cipher/PlayerConfigStore.kt`

This is a larger, more structural change than Change 1 — it touches most of
the file. Rather than a line-by-line diff (current file is 417 lines,
Metrolist's version is 462), implement it as a restructure following this
exact shape, cross-checked against `git show 0d37cc4658...` on the cloned
Metrolist repo for exact syntax:

### 2a. Replace the single `REMOTE_URL` with two named sources

Current (lines 32-35):
```kotlin
    private val REMOTE_URL by lazy {
        val encoded = "aHR0cHM6Ly9yYXcuZ2l0aHVidXNlcmNvbnRlbnQuY29tL01ldHJvbGlzdEdyb3VwL01ldHJvbGlzdC9tYWluL2FwcC9zcmMvbWFpbi9hc3NldHMvcGxheWVyX2NvbmZpZ3MuanNvbg=="
        String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8)
    }
```

Replace with two explicit sources and a `RemoteSource` data class:
```kotlin
    private const val FARADAY_URL =
        "https://raw.githubusercontent.com/MetrolistGroup/faraday/master/registry/player_configs.json"
    private const val ZEMER_URL =
        "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"

    private data class RemoteSource(
        val name: String,
        val url: String,
        val cacheFileName: String,
        val metaFileName: String,
    )

    private data class FetchResult(
        val changed: Boolean,
        val reachedServer: Boolean,
    )

    private val FARADAY_SOURCE =
        RemoteSource("Faraday", FARADAY_URL, "configs_faraday.json", "configs_faraday.meta")
    private val ZEMER_SOURCE =
        RemoteSource("Zemer", ZEMER_URL, "configs_remote.json", "configs_remote.meta")
```

**Important**: keep the existing cache/meta filenames (`configs_remote.json`,
`configs_remote.meta`) assigned to the Zemer source specifically, not
Faraday — this preserves any existing cached data on users' devices across
the update rather than orphaning it (matches Metrolist's own comment on this
exact point in their diff).

### 2b. Replace single-source state with per-source state

Current (lines 47-48, 100-104):
```kotlin
    private const val CACHE_FILE = "configs_remote.json"
    private const val META_FILE = "configs_remote.meta"
```
and
```kotlin
    @Volatile
    private var lastAttemptReachedServer = false
```

Remove `CACHE_FILE`/`META_FILE` constants (superseded by the per-source
filenames in `RemoteSource` above). Remove the shared
`lastAttemptReachedServer` flag (superseded by `FetchResult.reachedServer`,
returned per-call instead of stored as shared mutable state — this is also
a correctness improvement, since the old shared flag could be read/written
across concurrent-ish calls incorrectly).

Add per-source config storage alongside the existing `bundledConfigs`/
`mergedConfigs` (lines 53-57):
```kotlin
    @Volatile
    private var faradayConfigs: Map<String, FunctionNameExtractor.HardcodedPlayerConfig> = emptyMap()

    @Volatile
    private var zemerConfigs: Map<String, FunctionNameExtractor.HardcodedPlayerConfig> = emptyMap()
```

### 2c. Update `initialize()` / `applyCachedOverlay()` → `applyCachedOverlays()`

Current `applyCachedOverlay()` (lines 145-155) loads ONE cached source.
Rename to `applyCachedOverlays()` (plural) and load both:
```kotlin
    internal fun applyCachedOverlays() {
        faradayConfigs = loadCachedSource(FARADAY_SOURCE)
        zemerConfigs = loadCachedSource(ZEMER_SOURCE)
        mergedConfigs = mergeAllConfigs()
    }

    private fun loadCachedSource(source: RemoteSource): Map<String, FunctionNameExtractor.HardcodedPlayerConfig> {
        val cached = parseSource("cached ${source.name} copy") {
            cacheFile(source)?.takeIf { it.exists() }?.readText()
        }
        return if (cached != null) {
            Timber.tag(TAG).d("Overlaying cached ${source.name} configs (${cached.size} hashes)")
            cached
        } else {
            cacheFile(source)?.delete()
            metaFile(source)?.delete()
            emptyMap()
        }
    }

    private fun mergeAllConfigs(): Map<String, FunctionNameExtractor.HardcodedPlayerConfig> =
        PlayerConfigParser.merge(PlayerConfigParser.merge(bundledConfigs, zemerConfigs), faradayConfigs)
```

Note the merge order: bundled, then Zemer, then Faraday layered on top —
Faraday takes priority when both sources have an entry for the same hash,
matching "Faraday first" priority. Verify this merge order against
Metrolist's actual `mergeAllConfigs()` implementation in their diff before
finalizing — the exact precedence matters and should be confirmed, not
assumed.

Update the call site in `initialize()` (currently calls
`applyCachedOverlay()`, line 135) to call `applyCachedOverlays()` instead.

### 2d. Update `cacheFile()`/`metaFile()`/`readMeta()`/`writeMeta()` to take a `RemoteSource`

Current (lines 373-396) — all four functions currently take no parameters
and reference the single `CACHE_FILE`/`META_FILE` constants. Update each to
accept a `source: RemoteSource` parameter and use `source.cacheFileName`/
`source.metaFileName` instead of the old constants. Update every call site
throughout the file accordingly.

### 2e. Update the fetch chain — `fetchAndApply()`, `fetchAndApplyResetting()`, `refreshIfStale()`

This is the core logic change. Current `fetchAndApply()` (lines 262-309)
fetches ONE source and returns `Boolean`. Change it to accept a
`source: RemoteSource` parameter, use `source.url`/`source.name` in place of
`REMOTE_URL`, and return `FetchResult` instead of `Boolean` (so callers know
whether the server was actually reached, not just whether the config
changed).

Replace `fetchAndApplyResetting()` (lines 237-241) with a fallback-chain
function:
```kotlin
    private fun fetchFallbackChain(targetHash: String?, resetCooldown: () -> Unit): Boolean {
        val faraday = fetchAndApply(FARADAY_SOURCE)
        val zemer = if (targetHash != null && targetHash !in faradayConfigs) {
            fetchAndApply(ZEMER_SOURCE)
        } else {
            FetchResult(changed = false, reachedServer = false)
        }
        if (!faraday.reachedServer && !zemer.reachedServer) resetCooldown()
        return faraday.changed || zemer.changed
    }
```

This only fetches Zemer if Faraday's cached table doesn't already have the
specific broken hash being looked up — Zemer is a genuine fallback, not
fetched on every refresh. Update the two callers of the old
`fetchAndApplyResetting()` — `forceRefresh()` and
`refreshAfterStreamRejection()` — to call `fetchFallbackChain()` instead,
passing through whatever hash value is already available at each call site
(check the existing `forceRefresh(missingHash: ...)` and
`refreshAfterStreamRejection()` signatures — the latter may need a
`playerHash: String?` parameter added to match, mirroring Metrolist's
change to that function's signature).

Update `refreshIfStale()` (lines 243-254) to call
`fetchAndApply(FARADAY_SOURCE)` specifically (the periodic background
refresh only needs to keep the primary source fresh, not both — matches
Metrolist's approach) and update `readMeta()` call to
`readMeta(FARADAY_SOURCE)`.

### 2f. Update `applyRemote()` and `mergedConfigs` assembly

Current `applyRemote()` (lines 317-335) merges bundled + one remote table
directly. This needs restructuring so each source updates its own
`faradayConfigs`/`zemerConfigs` field, then `mergedConfigs` is recomputed
via `mergeAllConfigs()` — check Metrolist's actual diff for the precise
restructure here, since this is the part most likely to need careful
adaptation rather than direct copying (this repo's `applyRemote()` has
extra defensive comments/behavior around disk-write-failure handling not
present in the original pre-fix Metrolist version — preserve that existing
defensive behavior, don't regress it while porting the multi-source
change).

### Testing after Change 2

1. Fresh install / cleared cache: confirm the app still loads bundled
   configs correctly and doesn't crash if either remote source is
   temporarily unreachable.
2. Confirm a cipher failure correctly tries Faraday first, and only
   fetches from Zemer if Faraday's table doesn't have the specific broken
   player hash — add logging/verify via Timber output during testing that
   this sequencing is actually happening as designed, not fetching both
   unconditionally every time.
3. Confirm existing users (who have an existing `configs_remote.json`/
   `configs_remote.meta` cache from before this change) don't lose that
   cached data — it should now be attributed to the Zemer source
   specifically and continue working.

---

## Explicitly NOT included in this pass

- Metrolist commit `0f316d1211c13c5f149777b2426e3be007b17619` ("fix: lyrics
  and media3") — checked against this repo. The dependency-version portion
  (Media3, Material3, AGP, ktor, ksp, jsoup downgrades) is **not
  applicable** — this repo's `gradle/libs.versions.toml` is already on the
  exact versions Metrolist downgraded to, confirmed. The small null-guard
  fix for malformed playlist deep-links (`MainActivity.kt`, handling a null
  or literal-string-"null" `list` query parameter before navigating) is
  real and worth adding separately, but is unrelated to Changes 1/2 above —
  track as its own small, separate task if wanted.
