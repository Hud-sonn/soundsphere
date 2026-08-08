/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.data

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.soundsphere.music.api.SyncHistoryEntry
import com.soundsphere.music.api.SyncPlaylist
import com.soundsphere.music.api.SyncService
import com.soundsphere.music.api.SyncTrack
import com.soundsphere.music.api.UnauthorizedException
import com.soundsphere.music.db.MusicDatabase
import com.soundsphere.music.db.entities.Event
import com.soundsphere.music.db.entities.PlaylistEntity
import com.soundsphere.music.db.entities.PlaylistSongMap
import com.soundsphere.music.db.entities.SongEntity
import com.soundsphere.music.utils.dataStore
import com.soundsphere.music.utils.safeDataStoreEdit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background account-data sync with the Soundsphere backend (/user endpoints).
 *
 * This is intentionally decoupled from the YouTube/Metrolist sync pipeline
 * (`SyncUtils`): it only touches its own DataStore key and the backend JWT,
 * never the YouTube session cookies or account keys.
 *
 * Semantics are local-first with a union merge:
 *  - local changes are pushed to the server immediately (write-through),
 *    retried a few times with backoff, and never block the UI;
 *  - pulls only ADD remote data locally (likes/playlists/history), they never
 *    delete local rows, so a device that is offline never loses state;
 *  - on HTTP 401 the stored token is cleared and the account gate reappears
 *    (the MainActivity auth effect reacts to [AuthRepository.isLoggedIn]).
 */
@Singleton
class SyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val database: MusicDatabase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Serializes full pulls so concurrent triggers (like toggles, periodic sync)
    // can't interleave or leave isSyncing stuck mid-flight.
    private val pullMutex = Mutex()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    private val _serverPlaylistIds = MutableStateFlow<Map<String, String>>(emptyMap())

    // Songs whose like/unlike state failed to reach the server. Pulls skip these so
    // an offline like/unlike is never silently reverted by stale server data; they
    // are re-pushed on the next full sync (see retryPendingLikes).
    private val _pendingLikePushes = MutableStateFlow<Set<String>>(emptySet())

    init {
        scope.launch {
            val json = context.dataStore.data.first()[PLAYLIST_MAP_KEY]
            _serverPlaylistIds.value = parsePlaylistMap(json)
        }
        scope.launch {
            val json = context.dataStore.data.first()[PENDING_LIKES_KEY]
            _pendingLikePushes.value = parseIdSet(json)
        }
    }

    // ===== Public API (fire-and-forget, called from UI / service) =====

    /** Full pull after login or app start: likes, playlists and history. */
    fun onLoggedIn() {
        scope.launch { pullAll() }
    }

    /** Called from every like/unlike path (funnelled through SyncUtils.likeSong). */
    fun likeChanged(song: SongEntity) {
        scope.launch { pushLike(song) }
    }

    /** Called when a playback event is recorded locally. */
    fun historyAdded(songId: String) {
        scope.launch { pushHistory(songId) }
    }

    fun playlistCreated(playlist: PlaylistEntity) {
        scope.launch { pushPlaylist(playlist) }
    }

    fun playlistRenamed(playlist: PlaylistEntity) {
        scope.launch { pushPlaylist(playlist) }
    }

    fun playlistDeleted(playlist: PlaylistEntity) {
        scope.launch { pushPlaylistDeleted(playlist) }
    }

    fun playlistTrackAdded(playlistId: String, songId: String) {
        scope.launch { pushPlaylistTrackAdded(playlistId, songId) }
    }

    fun playlistTrackRemoved(playlistId: String, songId: String) {
        scope.launch { pushPlaylistTrackRemoved(playlistId, songId) }
    }

    // ===== Pull (server -> local, union merge) =====

    private suspend fun pullAll() {
        if (!pullMutex.tryLock()) return
        try {
            _isSyncing.value = true
            val token = authRepository.getToken() ?: return
            pullLikes(token)
            pullPlaylists(token)
            pullHistory(token)
            retryPendingLikes(token)
        } finally {
            _isSyncing.value = false
            pullMutex.unlock()
        }
    }

    /** Re-pushes like/unlike changes that failed while offline. */
    private suspend fun retryPendingLikes(token: String) {
        for (songId in _pendingLikePushes.value) {
            val song = database.songEntity(songId) ?: continue
            val result =
                retryNetwork {
                    if (song.liked) {
                        SyncService.likeTrack(token, song.toSyncTrack())
                    } else {
                        SyncService.unlikeTrack(token, song.id)
                    }
                }
            if (result.isSuccess) {
                clearPendingLike(songId)
            } else {
                handleFailure(result.exceptionOrNull())
            }
        }
    }

    private suspend fun pullLikes(token: String) {
        val result = retryNetwork { SyncService.getLiked(token) }
        if (result.isFailure) {
            if (handleFailure(result.exceptionOrNull())) return
            reportError("Pull likes failed", result.exceptionOrNull())
            return
        }
        val now = LocalDateTime.now()
        val pendingLikes = _pendingLikePushes.value
        for (entry in result.getOrThrow()) {
            val remote = entry.track
            // Local offline intent wins until it reaches the server
            if (remote.id in pendingLikes) continue
            val local = database.songEntity(remote.id)
            if (local == null) {
                database.insert(remote.toSongEntity(liked = true, likedDate = now))
            } else if (!local.liked) {
                database.update(local.copy(liked = true, likedDate = local.likedDate ?: now))
            }
        }
    }

    private suspend fun pullPlaylists(token: String) {
        val result = retryNetwork { SyncService.getPlaylists(token) }
        if (result.isFailure) {
            if (handleFailure(result.exceptionOrNull())) return
            reportError("Pull playlists failed", result.exceptionOrNull())
            return
        }
        val localPlaylists = database.playlistEntitiesByNameAsc()
        for (server in result.getOrThrow()) {
            val existing = server.id.let { mapped -> _serverPlaylistIds.value[mapped] }
                ?.let { localId -> localPlaylists.firstOrNull { it.id == localId } }
                ?: localPlaylists.firstOrNull { it.name == server.name }
            val local: PlaylistEntity
            if (existing == null) {
                local = PlaylistEntity(name = server.name, isLocal = true)
                database.insert(local)
            } else {
                local = existing
                if (local.name != server.name) {
                    database.update(local.copy(name = server.name, lastUpdateTime = LocalDateTime.now()))
                }
            }
            savePlaylistMapping(local.id, server.id)

            val existingSongIds = database.playlistSongIds(local.id).toSet()
            for (playlistTrack in server.tracks) {
                val remote = playlistTrack.track
                if (database.songEntity(remote.id) == null) {
                    database.insert(remote.toSongEntity(liked = false, likedDate = null))
                }
                if (remote.id !in existingSongIds) {
                    database.insert(
                        PlaylistSongMap(
                            playlistId = local.id,
                            songId = remote.id,
                            position = playlistTrack.position,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun pullHistory(token: String) {
        val result = retryNetwork { SyncService.getHistory(token) }
        if (result.isFailure) {
            if (handleFailure(result.exceptionOrNull())) return
            reportError("Pull history failed", result.exceptionOrNull())
            return
        }
        val existingSongIds = database.events().first().map { it.event.songId }.toSet()
        for (entry in result.getOrThrow()) {
            val remote = entry.track
            if (remote.id in existingSongIds) continue
            if (database.songEntity(remote.id) == null) {
                database.insert(remote.toSongEntity(liked = false, likedDate = null))
            }
            database.insert(
                Event(
                    songId = remote.id,
                    timestamp = parseTimestamp(entry.playedAt) ?: LocalDateTime.now(),
                    playTime = 0L,
                ),
            )
        }
    }

    // ===== Push (local -> server, write-through) =====

    private suspend fun pushLike(song: SongEntity) {
        val token = authRepository.getToken() ?: return
        val result =
            retryNetwork {
                if (song.liked) {
                    SyncService.likeTrack(token, song.toSyncTrack())
                } else {
                    SyncService.unlikeTrack(token, song.id)
                }
            }
        if (result.isFailure) {
            if (handleFailure(result.exceptionOrNull())) return
            markPendingLike(song.id)
            reportError("Push like failed for ${song.id}", result.exceptionOrNull())
        } else {
            clearPendingLike(song.id)
        }
    }

    private suspend fun pushHistory(songId: String) {
        val token = authRepository.getToken() ?: return
        val song = database.songEntity(songId) ?: return
        val result =
            retryNetwork {
                SyncService.addHistory(token, song.toSyncTrack(), LocalDateTime.now().toString())
            }
        if (result.isFailure) {
            if (handleFailure(result.exceptionOrNull())) return
            reportError("Push history failed for $songId", result.exceptionOrNull())
        }
    }

    private suspend fun pushPlaylist(playlist: PlaylistEntity) {
        val token = authRepository.getToken() ?: return
        val serverId = _serverPlaylistIds.value[playlist.id]
        if (serverId != null) {
            val result = retryNetwork { SyncService.renamePlaylist(token, serverId, playlist.name) }
            if (result.isFailure) {
                if (handleFailure(result.exceptionOrNull())) return
                reportError("Push playlist failed for ${playlist.id}", result.exceptionOrNull())
            }
            return
        }
        val created = retryNetwork { SyncService.createPlaylist(token, playlist.name) }
        if (created.isFailure) {
            if (handleFailure(created.exceptionOrNull())) return
            reportError("Create playlist failed for ${playlist.id}", created.exceptionOrNull())
            return
        }
        savePlaylistMapping(playlist.id, created.getOrThrow().id)
        pushPlaylistSongs(token, playlist.id, created.getOrThrow().id)
    }

    private suspend fun pushPlaylistSongs(token: String, localId: String, serverId: String) {
        val songIds = database.playlistSongIds(localId)
        for (songId in songIds) {
            val song = database.songEntity(songId) ?: continue
            val result =
                retryNetwork {
                    SyncService.addPlaylistTrack(token, serverId, song.toSyncTrack())
                }
            if (result.isFailure) {
                if (handleFailure(result.exceptionOrNull())) return
                reportError("Push playlist song failed for $songId", result.exceptionOrNull())
            }
        }
    }

    private suspend fun pushPlaylistTrackAdded(playlistId: String, songId: String) {
        val token = authRepository.getToken() ?: return
        val serverId = _serverPlaylistIds.value[playlistId] ?: return
        val song = database.songEntity(songId) ?: return
        val result =
            retryNetwork {
                SyncService.addPlaylistTrack(token, serverId, song.toSyncTrack())
            }
        if (result.isFailure) {
            if (handleFailure(result.exceptionOrNull())) return
            reportError("Push playlist track failed for $songId", result.exceptionOrNull())
        }
    }

    private suspend fun pushPlaylistTrackRemoved(playlistId: String, songId: String) {
        val token = authRepository.getToken() ?: return
        val serverId = _serverPlaylistIds.value[playlistId] ?: return
        val result =
            retryNetwork {
                SyncService.removePlaylistTrack(token, serverId, songId)
            }
        if (result.isFailure) {
            if (handleFailure(result.exceptionOrNull())) return
            reportError("Remove playlist track failed for $songId", result.exceptionOrNull())
        }
    }

    private suspend fun pushPlaylistDeleted(playlist: PlaylistEntity) {
        val token = authRepository.getToken() ?: return
        val serverId = _serverPlaylistIds.value[playlist.id] ?: return
        val result = retryNetwork { SyncService.deletePlaylist(token, serverId) }
        if (result.isFailure) {
            if (handleFailure(result.exceptionOrNull())) return
            reportError("Delete playlist failed for ${playlist.id}", result.exceptionOrNull())
            return
        }
        removePlaylistMapping(playlist.id)
    }

    // ===== Playlist id mapping (local "LP..." id <-> server uuid) =====

    private suspend fun savePlaylistMapping(localId: String, serverId: String) {
        val updated = _serverPlaylistIds.value + (localId to serverId)
        _serverPlaylistIds.value = updated
        context.safeDataStoreEdit { it[PLAYLIST_MAP_KEY] = mapToJson(updated) }
    }

    private suspend fun removePlaylistMapping(localId: String) {
        val updated = _serverPlaylistIds.value - localId
        _serverPlaylistIds.value = updated
        context.safeDataStoreEdit { it[PLAYLIST_MAP_KEY] = mapToJson(updated) }
    }

    private suspend fun markPendingLike(songId: String) {
        val updated = _pendingLikePushes.value + songId
        _pendingLikePushes.value = updated
        context.safeDataStoreEdit { it[PENDING_LIKES_KEY] = setIdToJson(updated) }
    }

    private suspend fun clearPendingLike(songId: String) {
        val updated = _pendingLikePushes.value - songId
        _pendingLikePushes.value = updated
        context.safeDataStoreEdit { it[PENDING_LIKES_KEY] = setIdToJson(updated) }
    }

    // ===== Helpers =====

    private suspend fun <T> retryNetwork(block: suspend () -> Result<T>): Result<T> {
        var attempt = 0
        while (true) {
            val result = block()
            if (result.isSuccess || attempt >= MAX_RETRIES) return result
            // A 401 will never succeed by retrying; fail fast and let handleFailure
            // clear the session instead of waiting out the backoff
            if (result.exceptionOrNull() is UnauthorizedException) return result
            attempt++
            delay(RETRY_DELAY_MS * attempt)
        }
    }

    /** True when the failure was an expired/revoked token (session was cleared). */
    private fun handleFailure(error: Throwable?): Boolean {
        if (error is UnauthorizedException) {
            authRepository.clearToken()
            return true
        }
        return false
    }

    private fun reportError(message: String, error: Throwable?) {
        Timber.w(error, message)
        _lastSyncError.value = error?.message ?: message
    }

    private fun SongEntity.toSyncTrack(): SyncTrack =
        SyncTrack(
            id = id,
            title = title,
            artist = "",
            album = albumName,
            duration = duration.coerceAtLeast(0),
            artworkUrl = thumbnailUrl,
            source = "youtube",
            year = year,
        )

    private fun SyncTrack.toSongEntity(
        liked: Boolean,
        likedDate: LocalDateTime?,
    ): SongEntity =
        SongEntity(
            id = id,
            title = title,
            duration = duration,
            thumbnailUrl = artworkUrl,
            albumName = album,
            year = year,
            liked = liked,
            likedDate = likedDate,
        )

    private fun parseTimestamp(value: String?): LocalDateTime? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalDateTime.parse(value.replace("Z", ""))
        } catch (e: Exception) {
            try {
                java.time.OffsetDateTime.parse(value).toLocalDateTime()
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun mapToJson(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun parsePlaylistMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse playlist map")
            emptyMap()
        }
    }

    private fun setIdToJson(ids: Set<String>): String {
        val array = org.json.JSONArray()
        ids.forEach { array.put(it) }
        return array.toString()
    }

    private fun parseIdSet(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            val array = org.json.JSONArray(json)
            buildSet { for (i in 0 until array.length()) add(array.getString(i)) }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse pending like set")
            emptySet()
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
        const val RETRY_DELAY_MS = 1_000L
        val PLAYLIST_MAP_KEY = stringPreferencesKey("sync_playlist_map")
        val PENDING_LIKES_KEY = stringPreferencesKey("sync_pending_like_pushes")
    }
}
