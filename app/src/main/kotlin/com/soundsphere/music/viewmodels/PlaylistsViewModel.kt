/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.soundsphere.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soundsphere.music.constants.AddToPlaylistSortDescendingKey
import com.soundsphere.music.constants.AddToPlaylistSortTypeKey
import com.soundsphere.music.constants.PlaylistFolderMapKey
import com.soundsphere.music.constants.PlaylistSortType
import com.soundsphere.music.db.MusicDatabase
import com.soundsphere.music.db.entities.Playlist
import com.soundsphere.music.extensions.toEnum
import com.soundsphere.music.utils.SyncUtils
import com.soundsphere.music.utils.dataStore
import com.soundsphere.music.utils.safeDataStoreEdit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allPlaylists =
        context.dataStore.data
            .map {
                it[AddToPlaylistSortTypeKey].toEnum(PlaylistSortType.CREATE_DATE) to (it[AddToPlaylistSortDescendingKey]
                    ?: true)
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending) ->
                database.playlists(sortType, descending)
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val folderJson = Json { ignoreUnknownKeys = true }

    /**
     * Playlist folders (playlistId → folder name), stored in DataStore —
     * deliberately NOT a Room table, so folders never touch the DB schema.
     * Ungrouped playlists report [UNGROUPED].
     */
    val folderMap: StateFlow<Map<String, String>> =
        context.dataStore.data
            .map { prefs ->
                prefs[PlaylistFolderMapKey]?.let { raw ->
                    runCatching {
                        folderJson.decodeFromString(
                            MapSerializer(String.serializer(), String.serializer()),
                            raw,
                        )
                    }.getOrDefault(emptyMap())
                } ?: emptyMap()
            }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    /** Playlists grouped by folder; ungrouped land under [UNGROUPED]. */
    val groupedPlaylists =
        combine(allPlaylists, folderMap) { playlists, folders ->
            playlists.groupBy { folders[it.playlist.id] ?: UNGROUPED }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    suspend fun setPlaylistFolder(playlistId: String, folder: String?) {
        val current = folderMap.value.toMutableMap()
        if (folder.isNullOrBlank()) current.remove(playlistId) else current[playlistId] = folder.trim()
        context.safeDataStoreEdit { prefs ->
            prefs[PlaylistFolderMapKey] =
                folderJson.encodeToString(
                    MapSerializer(String.serializer(), String.serializer()),
                    current,
                )
        }
    }

    // Suspend function that waits for sync to complete
    suspend fun sync() {
        syncUtils.syncSavedPlaylists()
    }

    companion object {
        const val UNGROUPED = "Ungrouped"
    }
}
