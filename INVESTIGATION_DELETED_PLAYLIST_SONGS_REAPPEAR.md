# Investigation: Deleted playlist songs reappear the next day

**Date:** 2026-09-01
**Status:** Investigation complete — no code changed
**Why:** User-reported bug: removing songs from a playlist appears to succeed locally but songs reappear the next day, consistently

---

## Summary verdict

**The bug is real and is the same class as the prior likes/history union-merge, but for playlist tracks it is *worse* because playlist tracks are the only entity that is supposed to support deletes.**

* **`pullPlaylists()` is add-only** — it never deletes a local `PlaylistSongMap` that no longer exists server-side. This is by design (`SyncRepository.kt:64-70` documented `pulls only ADD remote data locally … they never delete local rows`).
* **Two of the three UI code paths that delete a song from a playlist never call the backend `DELETE` at all** — they delete locally and stop. The server row stays, so the next pull (on any device, next day) sees `remote.id !in existingSongIds` and re-inserts it.
* The one correct path (`LocalPlaylistScreen` swipe) does call the backend but **fire-and-forget with no persistent retry** — if the network is offline, the token is missing, or the playlist has no `serverId` mapping, the `DELETE` is silently dropped. Same end result: server still holds the track, next pull re-adds it.

**So the root cause is Failure Mode A (local delete never reaches server) — for 2/3 UI entry points always, and for the 3rd entry point sometimes (offline/unsynced cases).** Failure Mode B (stale cache/race causing a *successful* server delete to be undone) is *not* the primary cause, but a secondary race exists.

---

## 1. What happens app-side when a user removes a song from a playlist

There are **three distinct UI entry points** for the same logical action, with different backend behavior:

### Primitive used by all three

* Entity: `app/src/main/kotlin/com/soundsphere/music/db/entities/PlaylistSongMap.kt:30-37` (`playlistId`, `songId`, `position`, `setVideoId`, `addedByUserId`)
* DAO delete: `app/src/main/kotlin/com/soundsphere/music/db/DatabaseDao.kt:1953` `@Delete fun delete(playlistSongMap: PlaylistSongMap)`
* Position fixup: `DatabaseDao.kt:1620-1624` `@Query UPDATE playlist_song_map SET position = CASE … fun move(…)`
* Pattern everywhere: `database.transaction { move(playlistId, position, Int.MAX_VALUE); delete(map.copy(position=Int.MAX_VALUE)) }` — synchronously, gaplessly, inside `database.transaction` (Room `WRITE_AHEAD_LOGGING`).

### Backend primitive (when called)

* Client: `app/src/main/kotlin/com/soundsphere/music/api/SyncService.kt:328-331`
  ```kotlin
  suspend fun removePlaylistTrack(token:String, serverId:String, trackId:String): Result<Unit>
    = execute(token,"DELETE","/user/playlists/$serverId/tracks/$trackId")
  ```
* Repo wrapper: `app/src/main/kotlin/com/soundsphere/music/data/SyncRepository.kt:253-255` `fun playlistTrackRemoved(playlistId,songId){ scope.launch{ pushPlaylistTrackRemoved() } }` and `SyncRepository.kt:604-615` `private suspend fun pushPlaylistTrackRemoved`:
  ```kotlin
  val token = authRepository.getToken() ?: return
  val serverId = _serverPlaylistIds.value[playlistId] ?: return
  val result = retryNetwork { SyncService.removePlaylistTrack(token, serverId, songId) }
  // MAX_RETRIES=3, REPORT_ERROR on failure, Unauthorized->clearToken
  ```
  Scope: `CoroutineScope(SupervisorJob()+Dispatchers.IO)` — **never awaited by UI**, error → `Timber.w` + `_lastSyncError` (`StateFlow<String?>`) **never shown as UI toast/snackbar for this op**.
* Backend handler: `backend-auth/routers/user.py:630-645` `@router.delete("/playlists/{playlist_id}/tracks/{track_id}")` → `_get_accessible_playlist` (owner OR `playlist_collaborators`, so Blends allowed) → `delete().eq("playlist_id",playlist_id).eq("track_id",track_id).execute()` → `_recount_playlist`.

### Three UI paths — only one is correct

| Path | File:Line | UI trigger | Local DB | YouTube (`browseId != null`) | Backend (`serverId` + token) | When it appears |
|------|-----------|------------|----------|------------------------------|------------------------------|-----------------|
| **A — LocalPlaylistScreen swipe (correct)** | `app/src/main/kotlin/com/soundsphere/music/ui/screens/playlist/LocalPlaylistScreen.kt:570-592` `fun deleteFromPlaylist()` called from `594-613` swipe handler | ✅ `database.transaction{ move+delete }` sync | ✅ `syncUtils.scheduleRemoveFromPlaylist(browseId, songId, playlistId){setVideoId}` fire-and-forget `SyncUtils.kt:1747-1785` (Mutex + 500 ms throttle, `YouTube.removeFromPlaylist` `WEB_REMIX` `InnerTube` `utils/SyncUtils.kt:2980`) if `playlist?.playlist?.browseId != null` | ✅ `syncRepository.playlistTrackRemoved(playlistId,songId)` fire-and-forget `DELETE /user/playlists/{serverId}/tracks/{trackId}` if `token && serverId != null` | `!locked && !inSelectMode && swipeRemoveEnabledPref` (`748-760` `if(locked||inSelectMode||!swipeRemoveEnabled) Box else SwipeToDismissBox`), plus `SwipeToRemoveSongKey` pref (`594`) |
| **B — SongMenu single “Remove from playlist” (BUG — backend missing)** | `app/src/main/kotlin/com/soundsphere/music/ui/menu/SongMenu.kt:829-863` `if(playlistSong!=null) add(Material3MenuItemData(title={Text(R.string.remove_from_playlist)}))` `onClick{ … }` at `839-860` | ✅ `database.transaction{ move+delete }` | ✅ conditional `playlistBrowseId?.let{ syncUtils.scheduleRemoveFromPlaylist(...) }` `850-857` | ❌ **Missing entirely** — no `syncRepository.playlistTrackRemoved` call (contrast `LocalPlaylistScreen.kt:591` which has it). Called from `LocalPlaylistScreen.kt:641-647` `SongMenu(playlistSong=song, playlistBrowseId=playlist?.playlist?.browseId)` — only non-null there | Any long-press on a song row → overflow → `Remove from playlist`. `isLocal=true`/Blends (`browseId==null`) correctly skip YT, but still should hit backend |
| **C — Bulk selection “Delete” (BUG — both YT and backend missing)** | `app/src/main/kotlin/com/soundsphere/music/ui/menu/SelectionSongsMenu.kt:616-639` `if(songPosition?.isNotEmpty()==true) add(Material3MenuItemData(title={Text(R.string.delete)}))` `onClick{ database.query{ loop move+delete } }` `629-635` | ✅ `database.query{ songPosition.forEach{ move(cur.position - i, MAX); delete(cur.copy(MAX)); i++ } }` | ❌ **Missing** — never calls `scheduleRemoveFromPlaylist` | ❌ **Missing** — never calls `playlistTrackRemoved` | `songPosition.isNotEmpty()` — only `LocalPlaylistScreen.kt:858-871` TopAppBar selection mode passes `songPosition = selection.mapNotNull{mapId->songs.find{it.map.id==mapId}?.map}`. Other screens pass `null` so item doesn’t appear |

**Condition matrix that matters for “why it reappears”:**

* **Brand-new local playlist under 20-sync limit, not yet pushed:** `_serverPlaylistIds[playlistId]==null` until `pushPlaylist` succeeds (`POST /user/playlists` → `savePlaylistMapping`). All three paths correctly result in local-only delete (backend no-op is *intended* until first push). After first push, **A** will backend-sync, **B/C** remain broken.
* **Blends (`isCollaborative=true`, `browseId==null`):** YT branch correctly skipped, backend is the *only* remote. **B/C leave ghost rows server-side** (`playlist_tracks` row remains, collaborators still see track, `notifications` poll never clears it).
* **YT playlists (`browseId` like `VL…` + local copy):** Two remotes. **A** updates both, **B** updates only YT, **C** updates neither.

**All three:** local `transaction` is synchronous, remote calls are `scope.launch` fire-and-forget. **No UI feedback on remote failure** (`SyncUtils` `Timber.e`, `SyncRepository` `reportError`/`_lastSyncError` never observed for track removal; only playlist sync limit uses `_lastSyncError`). User sees immediate local disappearance; silent divergence if network/auth fails.

---

## 2. What `pullPlaylists()` does with server tracks

* **Function:** `app/src/main/kotlin/com/soundsphere/music/data/SyncRepository.kt:337-413` `private suspend fun pullPlaylists(token:String)`
* **Contract:** `SyncRepository.kt:64-70` `Semantics are local-first with a union merge: … pulls only ADD remote data locally (likes/playlists/history), they never delete local rows…` and `SyncRepository.kt:257` `// ===== Pull (server -> local, union merge) =====`
* **Evidence — add-only, inside the loop `SyncRepository.kt:392-410`:**

  ```kotlin
  val existingSongIds = database.playlistSongIds(local.id).toSet() // 392
  for (playlistTrack in server.tracks) { // 393
    if (database.songEntity(remote.id)==null) insertSongWithArtists(...) // 394-402
    if (remote.id !in existingSongIds) { // 403 — only INSERT
      database.insert(PlaylistSongMap(playlistId=local.id, songId=remote.id, position=playlistTrack.position, addedByUserId=...)) // 402-408
    }
  }
  ```
  No `delete(PlaylistSongMap)`, no `DELETE FROM playlist_song_map`, no `clearPlaylist` exists in the function (grep `delete.*playlist` in `SyncRepository.kt` only hits `pushPlaylistDeleted` for whole-playlist delete, not tracks). `DatabaseDao.kt:1953` `delete(PlaylistSongMap)` and `DatabaseDao.kt:1627` `@Query DELETE` are available but **never invoked during pull**.

* **Same pattern as prior audits:** `pullLikes()` `SyncRepository.kt:312-335` inserts/`liked=true` only, `pullHistory()` `SyncRepository.kt:415-440` `if(remote.id in existing) continue` then `insert(Event)`, `pullFollowedArtists()` `SyncRepository.kt:815-836` insert/`bookmarkedAt` only. The **only** delete-capable pull is `pullRecentlyPlayed()` `SyncRepository.kt:450-475` (`clearRecentlyPlayed() + insert` — explicitly documented `Replace-with-latest`).
* **Playlist list merge:** `SyncRepository.kt:298-413` fetches `SyncService.getPlaylists(token)` (`SyncService.kt:271-282` `GET /user/playlists`, fallback host retry), then for each `server` playlist does `serverIdToLocalId` mapping → name fallback (collision-safe) → `insert` or `update(name)` → `savePlaylistMapping` → then the track loop above. Existing local playlists not on server are **kept** (no delete of whole playlists either, except via `_deletedServerPlaylistIds` tombstone for whole-playlist delete `SyncRepository.kt:353,638` — not for tracks).

**Result:** If server still has track `T` (because local `DELETE` never reached server), next pull on *any* device sees `T !in existingSongIds` (since locally deleted) and re-inserts it. Local order is preserved, but deleted track reappears at its server `position`.

---

## 3. Which failure mode is actually happening — A vs B

**Primary: Failure Mode A — local delete never calls the backend (for 2/3 entry points) or calls it but it is silently dropped.**

* **Always-A for paths B and C:** No code path exists to call `DELETE /user/playlists/{serverId}/tracks/{trackId}` from `SongMenu.kt:839-860` or `SelectionSongsMenu.kt:616-639`. Even when `_serverPlaylistIds[playlistId]` and `token` are valid, the server row stays. Any subsequent pull (next day = `onLoggedIn()->pullAll()` `SyncRepository.kt:198-199` behind `pullMutex:264`, or any `pullPlaylists` on another device) will re-add. **This is deterministically reproducible for “Remove from playlist” overflow and bulk Delete, and for Blends it leaves a ghost visible to collaborators.**

* **Sometimes-A for the “correct” path A:** `LocalPlaylistScreen.kt:591` *does* call `playlistTrackRemoved`, but `SyncRepository.kt:604-615` silently returns if `token==null` (not logged into Soundsphere) or `serverId==null` (playlist never synced — e.g., brand-new playlist before first `pushPlaylist` `POST /user/playlists` succeeds, or 20-sync-limit playlists where `savePlaylistMapping` never happened). Those are intended local-only cases, but from the user’s perspective the playlist *looks* synced (it appears in Library) so the reappearance is still surprising. When conditions *are* met, the call is `retryNetwork` `MAX_RETRIES=3` `1s*attempt` `SyncService.kt:328`, but on final failure it only `reportError` → `Timber.w` + `_lastSyncError` — **not retried later** (contrast `likes` which have `_pendingLikePushes` `SyncRepository.kt:111,722,293` persisted `PENDING_LIKES_KEY:986` and `retryPendingLikes`). Offline swipe-delete is then permanently lost; server wins on next pull.

* **How to prove A for a specific reported playlist:** Query the backend directly (service role) for that `playlist_id` after the user reports the delete locally: `db.table("playlists").select("*, playlist_tracks(track_id, position, tracks(*))").eq("id", playlist_id).execute()` or via admin `GET /admin/...` or direct `GET /user/playlists` with that user’s token. If `playlist_tracks` still contains `track_id == deletedSongId`, it is **A** (server never updated). All B/C deletions will show this.

**Not primarily Failure Mode B (successful server delete undone by stale pull/race).** The `DELETE` handler `backend-auth/routers/user.py:630-645` does `delete().eq("playlist_id",playlist_id).eq("track_id",track_id).execute()` then `_recount_playlist` — no cache. No `ETag`/`If-None-Match`, no `playlist_tracks` materialized view, no `SELECT` cache in `user.py`. The client does not cache `getPlaylists` response either (`SyncService.kt:271` `JSONArray(body)` fresh each call). A race where `pushPlaylistTrackRemoved` and a concurrent `pullPlaylists` interleave could theoretically re-add a track that was deleted on server *after* pull read `server.tracks` but *before* `delete` executed, but the pull is only triggered via `onLoggedIn()->pullAll()` (behind `pullMutex`) and not on a timer ( `notifications` poll `SyncRepository.kt:184-189` every 30s does **not** trigger a pull, and `LocalPlaylistScreen` does not auto-pull on resume). So **B would be rare and require manual timing, while A is 100% reproducible via B/C menu paths.** If a future `pullPlaylists` is made periodic, B becomes more likely, but today the data points to A.

---

## 4. Does “in a playlist” vs elsewhere matter and are there multiple removal code paths

**Yes — “in a playlist” is not one path, it is three, and only one is correct.**

* **`playlistSong != null` vs `null`:** `SongMenu.kt:829` `if(playlistSong!=null)` gates `Remove from playlist`. Only `LocalPlaylistScreen.kt:641` passes `playlistSong=song` (where `song: PlaylistSong` includes `map: PlaylistSongMap`). Any other caller that reuses `SongMenu` without `playlistSong` (e.g., `TopPlaylistScreen`, `AlbumScreen`) will not show the item at all — so no “elsewhere” path that should call backend but doesn’t. The bug is that the *same* screen has two different ways to remove from the *same* playlist (swipe vs overflow) and they differ.
* **`playlistBrowseId` param:** `SongMenu.kt:850` `playlistBrowseId?.let{ syncUtils.scheduleRemoveFromPlaylist(...) }` — correctly threads `browseId` from `LocalPlaylistScreen.kt:641` `playlist?.playlist?.browseId`. `SelectionSongsMenu.kt:616` has **no `playlistBrowseId` param at all** (`fun SelectionSongMenu(songSelection, onDismiss, clearAction, songPosition:List<PlaylistSongMap>?, isUploadedPlaylist)`) — so even if someone added `playlistTrackRemoved` there, they’d need to plumb `browseId` through from `LocalPlaylistScreen.kt:858` `SelectionSongMenu(songPosition=selection.map…)` — currently not available, which is why YT bulk delete is also missing.
* **No other “remove from library” path that should also remove from playlist membership:** `SongMenu.kt` `Remove from library` / `Un-like` (`likeChanged`) only touches `SongEntity.liked` + `liked_tracks`, not `PlaylistSongMap`. That is correct — removing from library should *not* also remove from playlist membership unless the user explicitly removes from that playlist.

**Fix can be written directly from this report (no re-investigation):**

* **Path B fix:** `SongMenu.kt:839-860` after `database.transaction{…}` add `com.soundsphere.music.LocalSyncRepository.current.playlistTrackRemoved(ps.map.playlistId, ps.map.songId)` (inject `LocalSyncRepository` as `LocalPlaylistScreen` already does at `LocalPlaylistScreen.kt:173` `val syncRepository = LocalSyncRepository.current`; `SongMenu` will need same `val syncRepository = LocalSyncRepository.current` hoisted to composable scope at `SongMenu.kt:57` `val database = LocalDatabase.current` — currently `onClick` at `202:92` illegally calls `LocalSyncRepository.current` inside lambda, which is `@Composable` only in composable scope — move to top).
* **Path C fix:** `SelectionSongsMenu.kt:626-637` after `database.query{…}` loop, inject `LocalSyncUtils` + `LocalSyncRepository` (or plumb `playlistBrowseId:String?` param like `SongMenu` does) and for each `cur in songPosition` call `if(browseId!=null) syncUtils.scheduleRemoveFromPlaylist(browseId, cur.songId, cur.playlistId){cur.setVideoId}` and always `syncRepository.playlistTrackRemoved(cur.playlistId, cur.songId)`. Requires threading `playlistBrowseId` from `LocalPlaylistScreen.kt:858` `SelectionSongMenu(…, playlistBrowseId=playlist?.playlist?.browseId)`.
* **Reference correct implementation to copy:** `LocalPlaylistScreen.kt:583-591` already handles both `browseId` nullability and fire-and-forget correctly.

**One pre-existing latent bug that will amplify the fix if not handled:** No per-track persistent retry queue (unlike `likes` `PENDING_LIKES_KEY`). After fixing B/C to call `playlistTrackRemoved`, offline bulk deletes will still be fire-and-forget `MAX_RETRIES=3` then lost. Consider adding `_pendingPlaylistTrackRemoves` `StateFlow<Set<Pair<playlistId,songId>>>` persisted `DataStore` similar to `likes`, and `pullPlaylists` should *not* re-add `remote.id` that is in `pendingRemoves`.

