/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soundsphere.music.api.FeedEvent
import com.soundsphere.music.api.SyncService
import com.soundsphere.music.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Data for the Events screen. Top section is upcoming concerts from artists
 * the user follows (`GET /feed/events`); bottom section is the discover
 * pool (`GET /feed/events/discover`) — tickethub listings that matched no
 * followed artist plus upcoming events cached for other users' artists.
 * Multi-artist by construction; never a single-artist page.
 */
@HiltViewModel
class EventsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _followedEvents = MutableStateFlow<List<FeedEvent>>(emptyList())
    val followedEvents = _followedEvents.asStateFlow()

    private val _discoverEvents = MutableStateFlow<List<FeedEvent>>(emptyList())
    val discoverEvents = _discoverEvents.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            val token = authRepository.getToken()
            if (token == null) {
                _isLoading.value = false
                return@launch
            }
            SyncService.getFeedEvents(token)
                .onSuccess { _followedEvents.value = it }
                .onFailure { _error.value = it.message }
            SyncService.getFeedDiscoverEvents(token)
                .onSuccess { _discoverEvents.value = it }
                .onFailure {
                    if (_error.value == null) _error.value = it.message
                }
            _isLoading.value = false
        }
    }
}
