/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens.library

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.soundsphere.innertube.utils.parseCookieString
import com.soundsphere.music.LocalDatabase
import com.soundsphere.music.LocalPlayerAwareWindowInsets
import com.soundsphere.music.LocalSyncRepository
import com.soundsphere.music.R
import com.soundsphere.music.constants.AiPlaylistConsentKey
import com.soundsphere.music.constants.AiPlaylistEnabledKey
import com.soundsphere.music.constants.CONTENT_TYPE_HEADER
import com.soundsphere.music.constants.CONTENT_TYPE_PLAYLIST
import com.soundsphere.music.constants.GridItemSize
import com.soundsphere.music.constants.GridItemsSizeKey
import com.soundsphere.music.constants.GridThumbnailHeight
import com.soundsphere.music.constants.InnerTubeCookieKey
import com.soundsphere.music.constants.LibraryViewType
import com.soundsphere.music.constants.PlaylistSortDescendingKey
import com.soundsphere.music.constants.PlaylistSortType
import com.soundsphere.music.constants.PlaylistSortTypeKey
import com.soundsphere.music.constants.PlaylistViewTypeKey
import com.soundsphere.music.constants.ShowCachedPlaylistKey
import com.soundsphere.music.constants.ShowDownloadedPlaylistKey
import com.soundsphere.music.constants.ShowLikedPlaylistKey
import com.soundsphere.music.constants.ShowTopPlaylistKey
import com.soundsphere.music.constants.ShowUploadedPlaylistKey
import com.soundsphere.music.constants.YtmSyncKey
import com.soundsphere.music.db.entities.Playlist
import com.soundsphere.music.db.entities.PlaylistEntity
import com.soundsphere.music.db.entities.PlaylistSongMap
import com.soundsphere.music.db.entities.SongEntity
import com.soundsphere.music.ui.component.CreatePlaylistDialog
import com.soundsphere.music.ui.component.DefaultDialog
import com.soundsphere.music.ui.component.LibrarySearchEmptyPlaceholder
import com.soundsphere.music.ui.component.LibrarySearchHeader
import com.soundsphere.music.ui.component.LibraryPlaylistGridItem
import com.soundsphere.music.ui.component.LibraryPlaylistListItem
import com.soundsphere.music.ui.component.LocalMenuState
import com.soundsphere.music.ui.component.PlaylistGridItem
import com.soundsphere.music.ui.component.PlaylistListItem
import com.soundsphere.music.ui.component.SortHeader
import com.soundsphere.music.ui.component.TextFieldDialog
import com.soundsphere.music.extensions.matchesNormalizedQuery
import com.soundsphere.music.extensions.normalizeForSearch
import com.soundsphere.music.utils.rememberEnumPreference
import com.soundsphere.music.utils.rememberPreference
import com.soundsphere.music.viewmodels.LibraryPlaylistsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.UUID

private data class VisiblePlaylistItem(
    val key: String,
    val playlist: Playlist,
    val autoPlaylist: Boolean,
    val route: String? = null,
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryPlaylistsScreen(
    navController: NavController,
    filterContent: @Composable () -> Unit,
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
    initialTextFieldValue: String? = null,
    allowSyncing: Boolean = true,
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val coroutineScope = rememberCoroutineScope()

    var viewType by rememberEnumPreference(PlaylistViewTypeKey, LibraryViewType.GRID)
    val (sortType, onSortTypeChange) = rememberEnumPreference(
        PlaylistSortTypeKey,
        PlaylistSortType.CREATE_DATE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(
        PlaylistSortDescendingKey,
        true
    )
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()

    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val normalizedQuery = remember(searchQuery) { searchQuery.normalizeForSearch() }
    val filteredPlaylists = remember(playlists, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            playlists
        } else {
            playlists.filter { playlist ->
                matchesNormalizedQuery(normalizedQuery, playlist.playlist.name)
            }
        }
    }

    val topSize by viewModel.topValue.collectAsStateWithLifecycle(initialValue = 50)

    val likedPlaylist =
        Playlist(
            playlist = PlaylistEntity(
                id = UUID.randomUUID().toString(),
                name = stringResource(R.string.liked)
            ),
            songCount = 0,
            songThumbnails = emptyList(),
        )

    val downloadPlaylist =
        Playlist(
            playlist = PlaylistEntity(
                id = UUID.randomUUID().toString(),
                name = stringResource(R.string.offline)
            ),
            songCount = 0,
            songThumbnails = emptyList(),
        )

    val topPlaylist =
        Playlist(
            playlist = PlaylistEntity(
                id = UUID.randomUUID().toString(),
                name = stringResource(R.string.my_top) + " $topSize"
            ),
            songCount = 0,
            songThumbnails = emptyList(),
        )


    val uploadedPlaylist =
        Playlist(
            playlist = PlaylistEntity(
                id = UUID.randomUUID().toString(),
                name = stringResource(R.string.uploaded_playlist)
            ),
            songCount = 0,
            songThumbnails = emptyList(),
        )

    val cachedPlaylist =
        Playlist(
            playlist = PlaylistEntity(
                id = UUID.randomUUID().toString(),
                name = stringResource(R.string.cached_playlist)
            ),
            songCount = 0,
            songThumbnails = emptyList(),
        )

    val (showLiked) = rememberPreference(ShowLikedPlaylistKey, true)
    val (showDownloaded) = rememberPreference(ShowDownloadedPlaylistKey, true)
    val (showTop) = rememberPreference(ShowTopPlaylistKey, true)
    val (showUploaded) = rememberPreference(ShowUploadedPlaylistKey, true)
    val (showCached) = rememberPreference(ShowCachedPlaylistKey, true)
    val showLikedPlaylist = showLiked && matchesNormalizedQuery(normalizedQuery, likedPlaylist.playlist.name)
    val showDownloadedPlaylist =
        showDownloaded && matchesNormalizedQuery(normalizedQuery, downloadPlaylist.playlist.name)
    val showCachedPlaylists = showCached && matchesNormalizedQuery(normalizedQuery, cachedPlaylist.playlist.name)
    val showTopPlaylists = showTop && matchesNormalizedQuery(normalizedQuery, topPlaylist.playlist.name)
    val showUploadedPlaylists =
        showUploaded && matchesNormalizedQuery(normalizedQuery, uploadedPlaylist.playlist.name)

    val visibleResults = remember(
        filteredPlaylists,
        showLikedPlaylist,
        showDownloadedPlaylist,
        showCachedPlaylists,
        showTopPlaylists,
        showUploadedPlaylists,
        topSize,
    ) {
        buildList {
            if (showLikedPlaylist) {
                add(
                    VisiblePlaylistItem(
                        key = "likedPlaylist",
                        playlist = likedPlaylist,
                        autoPlaylist = true,
                        route = "auto_playlist/liked",
                    ),
                )
            }
            if (showDownloadedPlaylist) {
                add(
                    VisiblePlaylistItem(
                        key = "downloadedPlaylist",
                        playlist = downloadPlaylist,
                        autoPlaylist = true,
                        route = "auto_playlist/downloaded",
                    ),
                )
            }
            if (showCachedPlaylists) {
                add(
                    VisiblePlaylistItem(
                        key = "cachedPlaylist",
                        playlist = cachedPlaylist,
                        autoPlaylist = true,
                        route = "cache_playlist/cached",
                    ),
                )
            }
            if (showTopPlaylists) {
                add(
                    VisiblePlaylistItem(
                        key = "TopPlaylist",
                        playlist = topPlaylist,
                        autoPlaylist = true,
                        route = "top_playlist/$topSize",
                    ),
                )
            }
            if (showUploadedPlaylists) {
                add(
                    VisiblePlaylistItem(
                        key = "uploadedPlaylist",
                        playlist = uploadedPlaylist,
                        autoPlaylist = true,
                        route = "auto_playlist/uploaded",
                    ),
                )
            }

            filteredPlaylists
                .distinctBy { it.id }
                .forEach { playlist ->
                    add(
                        VisiblePlaylistItem(
                            key = playlist.id,
                            playlist = playlist,
                            autoPlaylist = false,
                        ),
                    )
                }
        }
    }

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsStateWithLifecycle()

    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }

    val (ytmSync) = rememberPreference(YtmSyncKey, true)

    LaunchedEffect(Unit) {
        if (ytmSync) {
            withContext(Dispatchers.IO) {
                viewModel.sync()
            }
        }
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            when (viewType) {
                LibraryViewType.LIST -> lazyListState.animateScrollToItem(0)
                LibraryViewType.GRID -> lazyGridState.animateScrollToItem(0)
            }
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            initialTextFieldValue = initialTextFieldValue,
            allowSyncing = allowSyncing,
            onPlaylistCreated = { playlistId ->
                showCreatePlaylistDialog = false
                navController.navigate("local_playlist/$playlistId")
            }
        )
    }

    val database = LocalDatabase.current
    val syncRepository = LocalSyncRepository.current
    val context = LocalContext.current

    var showAiConsentDialog by rememberSaveable { mutableStateOf(false) }
    var showAiPromptDialog by rememberSaveable { mutableStateOf(false) }
    var aiGenerating by rememberSaveable { mutableStateOf(false) }
    var aiConsent by rememberPreference(AiPlaylistConsentKey, false)
    var aiPlaylistsEnabled by rememberPreference(AiPlaylistEnabledKey, true)

    val aiNotEnabledStr = stringResource(R.string.ai_playlist_not_enabled)
    val aiFailedStr = stringResource(R.string.ai_playlist_failed)
    val aiLimitReachedStr = stringResource(R.string.ai_playlist_limit_reached)
    val aiNoTracksStr = stringResource(R.string.ai_playlist_no_tracks)

    fun generateAiPlaylist(prompt: String) {
        if (prompt.isBlank()) return
        aiGenerating = true
        coroutineScope.launch(Dispatchers.IO) {
            val result = syncRepository.generateAiPlaylist(prompt.trim())
            withContext(Dispatchers.Main) {
                aiGenerating = false
                showAiPromptDialog = false
                result.onSuccess { tracks ->
                    if (tracks.isEmpty()) {
                        Toast.makeText(context, aiNoTracksStr, Toast.LENGTH_SHORT).show()
                        return@onSuccess
                    }
                    val playlistName =
                        prompt.trim().let {
                            if (it.length > 60) it.take(57).trimEnd() + "…" else it
                        }
                    val playlistEntity =
                        PlaylistEntity(
                            name = playlistName,
                            bookmarkedAt = LocalDateTime.now(),
                            isLocal = true,
                        )
                    val existingSongIds = tracks.mapNotNull { database.songEntity(it.id)?.id }.toHashSet()
                    database.query {
                        insert(playlistEntity)
                        tracks.forEachIndexed { index, track ->
                            if (track.id !in existingSongIds) {
                                insertSongWithArtists(
                                    SongEntity(
                                        id = track.id,
                                        title = track.title,
                                        duration = track.duration,
                                        thumbnailUrl = track.artworkUrl,
                                        albumName = track.album,
                                        year = track.year,
                                        inLibrary = LocalDateTime.now(),
                                    ),
                                    listOfNotNull(track.artist.takeIf { it.isNotBlank() }),
                                )
                            }
                            insert(
                                PlaylistSongMap(
                                    playlistId = playlistEntity.id,
                                    songId = track.id,
                                    position = index,
                                ),
                            )
                        }
                    }
                    syncRepository.playlistCreated(playlistEntity)
                    navController.navigate("local_playlist/${playlistEntity.id}")
                }.onFailure { error ->
                    val message =
                        when {
                            error.message?.contains("not enabled", ignoreCase = true) == true -> aiNotEnabledStr
                            error.message?.contains("limit", ignoreCase = true) == true -> aiLimitReachedStr
                            else -> aiFailedStr
                        }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showAiConsentDialog) {
        DefaultDialog(
            onDismiss = { showAiConsentDialog = false },
            icon = { Icon(painterResource(R.drawable.ai), contentDescription = null) },
            title = { Text(text = stringResource(R.string.ai_playlist_consent_title)) },
            buttons = {
                TextButton(onClick = { showAiConsentDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        aiConsent = true
                        syncRepository.settingsChanged(mapOf("ai_playlist_consent" to true))
                        showAiConsentDialog = false
                        showAiPromptDialog = true
                    },
                ) {
                    Text(text = stringResource(R.string.ai_playlist_agree))
                }
            },
        ) {
            Text(
                text = stringResource(R.string.ai_playlist_consent_text),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (showAiPromptDialog && !aiGenerating) {
        TextFieldDialog(
            icon = { Icon(painterResource(R.drawable.ai), contentDescription = null) },
            title = { Text(text = stringResource(R.string.ai_playlist_prompt_title)) },
            placeholder = { Text(text = stringResource(R.string.ai_playlist_prompt_hint)) },
            singleLine = false,
            maxLines = 3,
            autoDismiss = false,
            onDismiss = { showAiPromptDialog = false },
            onDone = { generateAiPlaylist(it) },
        )
    }

    if (aiGenerating) {
        DefaultDialog(
            onDismiss = { aiGenerating = false },
            icon = { Icon(painterResource(R.drawable.ai), contentDescription = null) },
            title = { Text(text = stringResource(R.string.ai_playlist_prompt_title)) },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(text = stringResource(R.string.ai_playlist_generating))
            }
        }
    }

    val headerContent = @Composable {
        LibrarySearchHeader(
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onBack = {
                isSearchActive = false
                viewModel.updateSearchQuery("")
            },
            keyboardController = keyboardController,
            modifier = Modifier.padding(start = 16.dp),
        ) {
            SortHeader(
                sortType = sortType,
                sortDescending = sortDescending,
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                sortTypeText = { sortType ->
                    when (sortType) {
                        PlaylistSortType.CREATE_DATE -> R.string.sort_by_create_date
                        PlaylistSortType.NAME -> R.string.sort_by_name
                        PlaylistSortType.SONG_COUNT -> R.string.sort_by_song_count
                        PlaylistSortType.LAST_UPDATED -> R.string.sort_by_last_updated
                    }
                },
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = pluralStringResource(
                    R.plurals.n_playlist,
                    visibleResults.count { !it.autoPlaylist },
                    visibleResults.count { !it.autoPlaylist },
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            IconButton(
                onClick = { isSearchActive = true },
                modifier = Modifier.padding(start = 8.dp).size(40.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = stringResource(R.string.search),
                )
            }

            IconButton(
                onClick = {
                    viewType = viewType.toggle()
                },
                modifier = Modifier.padding(end = 8.dp).size(40.dp),
            ) {
                Icon(
                    painter =
                    painterResource(
                        when (viewType) {
                            LibraryViewType.LIST -> R.drawable.list
                            LibraryViewType.GRID -> R.drawable.grid_view
                        },
                    ),
                    contentDescription = stringResource(
                        when (viewType) {
                            LibraryViewType.LIST -> R.string.switch_to_grid_view
                            LibraryViewType.GRID -> R.string.switch_to_list_view
                        },
                    ),
                )
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        when (viewType) {
            LibraryViewType.LIST -> {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(
                        key = "filter",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        filterContent()
                    }

                    item(
                        key = "header",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        headerContent()
                    }

                    if (visibleResults.isEmpty()) {
                        item(key = "empty_placeholder") {
                            if (searchQuery.isNotBlank()) {
                                LibrarySearchEmptyPlaceholder(modifier = Modifier.animateItem())
                            } else {
                                LibrarySearchEmptyPlaceholder(
                                    modifier = Modifier.animateItem(),
                                    icon = R.drawable.playlist_play,
                                    text = stringResource(R.string.library_playlist_empty),
                                )
                            }
                        }
                    }

                    items(
                        items = visibleResults,
                        key = { it.key },
                        contentType = { CONTENT_TYPE_PLAYLIST },
                    ) { item ->
                        if (item.autoPlaylist) {
                            PlaylistListItem(
                                playlist = item.playlist,
                                autoPlaylist = true,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            item.route?.let(navController::navigate)
                                        }
                                        .animateItem(),
                            )
                        } else {
                            LibraryPlaylistListItem(
                                menuState = menuState,
                                coroutineScope = coroutineScope,
                                playlist = item.playlist,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }

            LibraryViewType.GRID -> {
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns =
                    GridCells.Adaptive(
                        minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp,
                    ),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(
                        key = "filter",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        filterContent()
                    }

                    item(
                        key = "header",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        headerContent()
                    }

                    if (visibleResults.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            if (searchQuery.isNotBlank()) {
                                LibrarySearchEmptyPlaceholder(modifier = Modifier.animateItem())
                            } else {
                                LibrarySearchEmptyPlaceholder(
                                    modifier = Modifier.animateItem(),
                                    icon = R.drawable.playlist_play,
                                    text = stringResource(R.string.library_playlist_empty),
                                )
                            }
                        }
                    }

                    items(
                        items = visibleResults,
                        key = { it.key },
                        contentType = { CONTENT_TYPE_PLAYLIST },
                    ) { item ->
                        if (item.autoPlaylist) {
                            PlaylistGridItem(
                                playlist = item.playlist,
                                fillMaxWidth = true,
                                autoPlaylist = true,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                item.route?.let(navController::navigate)
                                            },
                                        )
                                        .animateItem(),
                            )
                        } else {
                            LibraryPlaylistGridItem(
                                menuState = menuState,
                                coroutineScope = coroutineScope,
                                playlist = item.playlist,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }

        // Always visible + button (no scroll hiding)
        FloatingActionButton(
            onClick = { showCreatePlaylistDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                )
                .padding(16.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.add),
                contentDescription = stringResource(R.string.create_playlist),
            )
        }

        // AI playlist generation (server-side Groq, keys never reach the app)
        if (aiPlaylistsEnabled) {
            FloatingActionButton(
                onClick = {
                    if (aiConsent) {
                        showAiPromptDialog = true
                    } else {
                        showAiConsentDialog = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current
                            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                    )
                    .padding(16.dp)
                    .padding(bottom = 72.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ai),
                    contentDescription = stringResource(R.string.create_playlist_with_ai),
                )
            }
        }
    }
}
