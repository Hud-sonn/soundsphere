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
import dagger.hilt.android.lifecycle.HiltViewModel
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
}