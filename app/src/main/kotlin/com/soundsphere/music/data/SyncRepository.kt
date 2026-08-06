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
import org.json.JSONObject
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background account-data sync with the Soundsphere backend (/user/* API).
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

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    private val _serverPlaylistIds = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        scope.launch {
            val json = context.dataStore.data.first()[PLAYLIST_MAP_KEY]
            _serverPlaylistIds.value = parsePlaylistMap(json)
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
        val token = authRepository.getToken() ?: return
        _isSyncing.value = true
        try {
            pullLikes(token)
            pullPlaylists(token)
            pullHistory(token)
        } finally {
            _isSyncing.value = false
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
        for (entry in result.getOrThrow()) {
            val remote = entry.track
            val local = database.dao.songEntity(remote.id)
            if (local == null) {
                database.dao.insert(remote.toSongEntity(liked = true, likedDate = now))
            } else if (!local.liked) {
                database.dao.update(local.copy(liked = true, likedDate = local.likedDate ?: now))
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
        val localPlaylists = database.dao.playlistEntitiesByNameAsc()
        for (server in result.getOrThrow()) {
            val existing = server.id.let { mapped -> _serverPlaylistIds.value[mapped] }
                ?.let { localId -> localPlaylists.firstOrNull { it.id == localId } }
                ?: localPlaylists.firstOrNull { it.name == server.name }
            val local: PlaylistEntity
            if (existing == null) {
                local = PlaylistEntity(name = server.name, isLocal = true)
                database.dao.insert(local)
            } else {
                local = existing
                if (local.name != server.name) {
                    database.dao.update(local.copy(name = server.name, lastUpdateTime = LocalDateTime.now()))
                }
            }
            savePlaylistMapping(local.id, server.id)

            val existingSongIds = database.dao.playlistSongIds(local.id).toSet()
            for (playlistTrack in server.tracks) {
                val remote = playlistTrack.track
                if (database.dao.songEntity(remote.id) == null) {
                    database.dao.insert(remote.toSongEntity(liked = false, likedDate = null))
                }
                if (remote.id !in existingSongIds) {
                    database.dao.insertPlaylistSongMap(
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
            if (database.dao.songEntity(remote.id) == null) {
                database.dao.insert(remote.toSongEntity(liked = false, likedDate = null))
            }
            database.dao.insert(
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
            reportError("Push like failed for ${song.id}", result.exceptionOrNull())
        }
    }

    private suspend fun pushHistory(songId: String) {
        val token = authRepository.getToken() ?: return
        val song = database.dao.songEntity(songId) ?: return
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
        val songIds = database.dao.playlistSongIds(localId)
        for (songId in songIds) {
            val song = database.dao.songEntity(songId) ?: continue
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
        val song = database.dao.songEntity(songId) ?: return
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

    // ===== Helpers =====

    private suspend fun <T> retryNetwork(block: suspend () -> Result<T>): Result<T> {
        var attempt = 0
        while (true) {
            val result = block()
            if (result.isSuccess || attempt >= MAX_RETRIES) return result
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

    private companion object {
        const val MAX_RETRIES = 3
        const val RETRY_DELAY_MS = 1_000L
        val PLAYLIST_MAP_KEY = stringPreferencesKey("sync_playlist_map")
    }
}
