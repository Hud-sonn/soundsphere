/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soundsphere.music.api.SharedPlaylist
import com.soundsphere.music.api.SyncService
import com.soundsphere.music.data.AuthRepository
import com.soundsphere.music.db.MusicDatabase
import com.soundsphere.music.db.entities.PlaylistEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Loads a playlist shared by another user through the public share link.
 * Unlike [OnlinePlaylistViewModel], the data comes from the Soundsphere
 * backend (`GET /share/playlists/{token}`) instead of InnerTube, so the
 * recipient does not need any YouTube relationship to the owner.
 */
@HiltViewModel
class SharedPlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val database: MusicDatabase,
) : ViewModel() {
    private val shareToken = savedStateHandle.get<String>("token")!!

    private val _playlist = MutableStateFlow<SharedPlaylist?>(null)
    val playlist = _playlist.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        fetch()
    }

    fun fetch() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            SyncService.getSharedPlaylist(shareToken)
                .onSuccess { _playlist.value = it }
                .onFailure { _error.value = it.message ?: "Failed to load shared playlist" }
            _isLoading.value = false
        }
    }

    private val _isJoining = MutableStateFlow(false)
    val isJoining = _isJoining.asStateFlow()
    private val _joinError = MutableStateFlow<String?>(null)
    val joinError = _joinError.asStateFlow()
    // NOTE: an observable joinedPlaylistId + LaunchedEffect navigator lived here.
    // Removed as dead flow — navigation runs once via the onJoined callback.
    // The guarded join body below (try/catch + awaited transaction) is the
    // black-screen fix: do NOT reintroduce unguarded writes or early navigate.

    fun joinBlend(onJoined: (String) -> Unit) {
        val shared = _playlist.value ?: return
        if (!shared.isCollaborative) return
        viewModelScope.launch(Dispatchers.IO) {
            _isJoining.value = true
            _joinError.value = null
            val token = authRepository.getToken()
            if (token == null) {
                _joinError.value = "Please log in to join a Blend"
                _isJoining.value = false
                return@launch
            }
            // Everything below is guarded: an uncaught throw here used to kill the
            // process mid-join (black screen, join never completes). Any failure now
            // surfaces as joinError text instead.
            try {
                val result = SyncService.joinBlend(token, shareToken)
                if (result.isFailure) {
                    _joinError.value = result.exceptionOrNull()?.message ?: "Failed to join Blend"
                    _isJoining.value = false
                    return@launch
                }
                // Save to local library as a Blend — shows in Library + Home.
                // Suspended transaction (not fire-and-forget query{}) so navigation
                // below only happens after every row actually landed.
                val entity = PlaylistEntity(
                    id = shared.id,
                    name = shared.name,
                    thumbnailUrl = shared.coverUrl,
                    bookmarkedAt = LocalDateTime.now(),
                    isCollaborative = true,
                    isLocal = false,
                )
                database.withTransaction {
                    // Upsert playlist
                    try {
                        insert(entity)
                    } catch (e: Exception) {
                        update(entity)
                    }
                    // Save tracks with attribution (map insert is IGNORE → re-join safe)
                    shared.tracks.forEachIndexed { idx, entry ->
                        val song = entry.track
                        if (songEntity(song.id) == null) {
                            insertSongWithArtists(
                                song.toSongEntity(),
                                listOfNotNull(song.artist.takeIf { it.isNotBlank() }),
                            )
                        }
                        insert(
                            com.soundsphere.music.db.entities.PlaylistSongMap(
                                playlistId = entity.id,
                                songId = song.id,
                                position = entry.position,
                                addedByUserId = entry.addedByUserId,
                            ),
                        )
                    }
                }
                _isJoining.value = false
                launch(Dispatchers.Main) { onJoined(entity.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _joinError.value = e.message ?: "Failed to save Blend"
                _isJoining.value = false
            }
        }
    }

    private fun com.soundsphere.music.api.SyncTrack.toSongEntity() =
        com.soundsphere.music.db.entities.SongEntity(
            id = id,
            title = title,
            duration = duration,
            thumbnailUrl = artworkUrl,
            albumName = album,
        )
}