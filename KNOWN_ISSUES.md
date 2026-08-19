# Known Issues / Open Investigations

Working notes for known issues that need human eyes or a future session. Do not delete
without resolving the items below.

## 0. Playback breaks when a YouTube account is logged in — "bot detection" / re-login prompt (open, investigation)

**Symptom:** playback works normally logged out; the moment a YouTube account is logged
in, playback breaks and the app surfaces a bot-detection error that prompts the user to
re-login repeatedly.

**Investigated 2026-08-18 (code walk-through, re-verified after build; not yet device-tested):**

- The only request-path difference between logged-in and logged-out `/player` calls is
  the auth headers: `InnerTube.player()` always uses `setLogin = true`
  (`innertube/.../InnerTube.kt:229`), which appends `Cookie` + `Authorization:
  SAPISIDHASH <ts>_<hash>` only when a cookie is present (InnerTube.kt:158-165).
  Logged out, neither header is sent — same client persona (WEB_REMIX).
- **Key conflict with the earlier report:** the earlier fix assumed re-login was the
  correct remedy. In fact, `YTPlayerUtils.playerResponseForPlayback` treats bot
  detection on the primary WEB_REMIX response as fatal and **throws before the
  fallback-client loop runs** (YTPlayerUtils.kt:181-188 → the loop at 228+ never
  executes). This is the single point that kills playback: the loop itself already
  handles a non-OK main response gracefully (skips it at 273-276 and tries the next
  client), so removing the throw would let logged-in playback fall through to
  anonymous fallback clients.
- **Verified client matrix** (which fallbacks stay anonymous even when logged in):
  - `VISIONOS` (default #1), `ANDROID_VR_1_65_10` (#2), `ANDROID_VR_1_43_32` (#3),
    `TVHTML5_SIMPLY` (#6): `loginSupported = false` → **no cookie, no SAPISIDHASH,
    no onBehalfOfUser** — byte-identical requests to the logged-out path that works.
  - `TVHTML5` (#5): `loginSupported = true` → also carries auth when logged in.
  - `WEB_REMIX` (main): `loginSupported = true` + `useWebPoTokens` — the flagged one.
- **Secondary suspect (poToken session mismatch):** the poToken is minted against the
  **anonymous** visitor session — `sessionId = YouTube.visitorData` (YTPlayerUtils.kt:140,
  PoTokenGenerator.getWebClientPoToken). When logged in, the same visitor-bound token +
  visitorData ride an authenticated request (cookie + dataSyncId onBehalfOfUser).
  BotGuard can flag exactly this credential/token mismatch. Logged out, the pairing is
  consistent, which also explains why the anonymous path never trips.
- `PlayerConfigStore` (remote player/cipher config) is **not** implicated: configs are
  keyed by player hash, not by auth state; and the anonymous fallback clients
  (`useSignatureTimestamp = false`) don't need cipher/decipher at all.
- `validateStatus` HEAD requests (YTPlayerUtils.kt:653-656) add the cookie only for
  uploaded/private tracks — not the main path.

**Why re-login only temporarily works:** a fresh SAPISID session passes bot detection
until YouTube flags the automated authenticated request shape (or the token/session
mismatch) again. The request shape never changes, so the prompt recurs.

**Proposed fix (not yet applied):** when bot detection fires on the authenticated
primary client, do not throw — log a warning and fall through to the anonymous fallback
clients (VISIONOS/ANDROID_VR/TVHTML5_SIMPLY). Optionally combine with preferring an
anonymous client (VISIONOS) as the primary metadata/stream client when logged in.

**Action items:**
- [ ] Implement the fall-through (or header drop) fix and smoke-test logged-in playback.
- [ ] Check whether `validateStatus` HEAD requests adding the cookie (YTPlayerUtils.kt:653-656)
      contribute to detection; the WEB_REMIX path skips HEAD validation already.
- [ ] Verify with logcat whether the thrown `BOT_DETECTED` error is the actual observed
      failure (vs. a plain 403 stream rejection) before finalizing.


## 1. Account sync: playlists not pulled on debug device (open)

**Reported by user:** signed in as `marthasmith89977` on the debug build and the
playlist pull returned nothing, even though that account has playlists (verified in
Supabase: "nf vibe" 22 tracks, "nf home" 16, "Nf best songs" 11, "Hiro" 3).

**Evidence from Render logs (2026-08-13):**
- The debug device (102.91.104.215) never successfully logged in as Martha. Its only
  session was: `POST /auth/login` 401 (wrong password) → `POST /auth/register` 201 for a
  brand-new account `ephraimibrahim608@gmail.com` → verify → pulls (`/user/liked`,
  `/user/playlists`, `/user/history`, `/user/follows` all 200, all empty because that
  fresh account has zero playlists server-side). So "login as Martha → nothing pulled"
  was actually "login into a different, empty account".
- The production device (105.113.78.124) pushed tracks/playlists all day but was never
  observed issuing any `GET /user/*` pull requests in the queried windows — it has been
  running with a persistent token, and **pulls only fire at login time**, not at app
  start (see `AuthViewModel.handleTokenResult` → `SyncRepository.onLoggedIn`).

**Action items:**
- [ ] Trigger a pull at app start (and/or on token refresh) when already signed in, not
      only after an explicit login.
- [ ] Consider surfacing the synced-in account email in the app so the user can tell
      which account they are actually on.

## 2. `/ai/generate-playlist` burst of 422s (open)

**Reported by user:** "AI playlist from search doesn't save" (toast "AI playlist
failed" repeatedly).

**Evidence from Render logs (2026-08-13 20:43-20:46 UTC):** 15 consecutive
`POST /ai/generate-playlist` → **422 Unprocessable Content** from the production device,
with no Groq call in between (validation fails before the LLM). A minute later the same
flow returned 200 and the playlist was created server-side ("nf vibe", 22 tracks), so
the failure is intermittent and request-shape dependent.

**Suspects:** the request schema requires `prompt` between 3 and 1024 chars
(`backend-auth/models/schemas.py`); a prompt shorter than 3 chars or longer than 1024
would 422. The app previously had no client-side length guard.

**Mitigation shipped 2026-08-13:** `SyncService.generateAiPlaylist` now trims the
prompt, rejects prompts < 3 chars, and truncates at 1024.

**Action items:**
- [ ] If 422s still occur, capture the exact request body server-side (log the prompt
      length) or add a middleware that logs validation errors.

## 3. Homepage cards without artist names (open / likely not a bug)

**Reported by user:** "most music on the homepage doesn't have artist name, but search
does". Releases/artists/mixes/playlists legitimately have no single artist line; only
song cards do. After checking the backend payloads and the app's parse path, no code
bug was found. If specific *song* cards show no artist, that would be a data quality
issue in the YouTube Music feed (e.g. live performances, community uploads), not a
parsing bug.

## 4. AI playlists previously saved without artist relations (fixed)

Songs saved via the AI flows and the account-sync pull path were inserted without
`ArtistEntity`/`SongArtistMap` rows, so their artist names never rendered. Fixed by
adding `DatabaseDao.insertSongWithArtists(...)` and using it in:
- `OnlineSearchResult.saveAiPlaylist` (search AI flow)
- `LibraryPlaylistsScreen.generateAiPlaylist` (library AI flow)
- `SyncRepository` pull paths (liked / playlists / history)

## 5. AI save could be cancelled by leaving the screen (fixed)

The AI playlist DB write ran in the screen-scoped coroutine; leaving the search screen
mid-save cancelled the write, so the playlist could silently fail to appear in Library.
The save is now wrapped in `NonCancellable`; navigation only happens if the screen is
still active.

## 6. AI playlist naming (fixed)

Generated playlists are now named `<Artist> AI Mix` when an artist was detected, and
otherwise fall back to the (truncated) prompt.

## 7. Admin web / crash ingestion (planned)

See `backend-auth/ADMIN_WEB_INVESTIGATION.md` for the full proposal (admin router +
static dashboard on the existing Render service, `crash_reports` table + ingest
endpoint, app-side hookup in `CrashHandler.kt`/`reportException`).

## 8. Rate-limit hammering on `POST /user/playlists/{id}/tracks` (open)

**Evidence from Render logs (2026-08-14 07:17-07:28 UTC+):** IP `102.91.72.223`
constantly hits `POST /user/playlists/0c1a7148-8fd7-475f-8c70-0236691ba588/tracks` and
trips the slowapi write limit (`_WRITE_LIMIT = "120/hour"` in `user.py`), producing
`429 Too Many Requests` every few seconds. Same IP also posts `/user/history` 200 OK.

**Root cause chain:** that playlist ("alpha 1", owned by `boichoco43@gmail.com`) has
**240 tracks** and was created 2026-08-14 05:57 UTC. `pushPlaylistSongs` adds songs one
by one; with a 240-song playlist the first 120 additions consume the entire hourly
write budget and the remaining 120 songs each get 429 + 3 retries
(`retryNetwork`, `MAX_RETRIES = 3`), so the endpoint is hammered for the rest of the
hour. Large-playlist sync will always blow through the 120/hour limit.

**Action items:**
- [ ] Increase the write limit or exempt playlist-track pushes (or batch them).
- [ ] Consider treating 429 as non-retryable (fail fast + queued offline retry) so the
      app stops hammering a rate-limited endpoint.

## 9. Backend `ReadError` exceptions from client disconnects (low priority)

**Evidence from Render logs (2026-08-15 08:15–09:49 UTC):** 16
`soundsphere-auth:Unhandled exception: ReadError` / `Exception in ASGI application`
errors, all from mobile clients on variable networks.

**Root cause:** the app closes the HTTP connection before the response finishes
(typical with background sync on flaky mobile networks). Uvicorn/Starlette logs
the broken pipe as an unhandled exception. Not a functional bug — the data still
gets written — but noisy.

**Action items:**
- [ ] Catch `ReadError` / `ConnectionResetError` in the ASGI app and log at DEBUG
      level instead of ERROR (or add a middleware that suppresses known disconnect
      exceptions).