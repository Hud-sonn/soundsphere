/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens.library

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.clip
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
    viewType: LibraryViewType,
    onViewTypeChange: (LibraryViewType) -> Unit,
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
    initialTextFieldValue: String? = null,
    allowSyncing: Boolean = true,
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val coroutineScope = rememberCoroutineScope()
    val syncRepository = com.soundsphere.music.LocalSyncRepository.current
    var slotFreedPlaylists by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<com.soundsphere.music.db.entities.PlaylistEntity>?>(null) }
    androidx.compose.runtime.LaunchedEffect(syncRepository) {
        syncRepository.playlistSlotFreed.collect { slotFreedPlaylists = it }
    }

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

    val database = LocalDatabase.current
    val context = LocalContext.current
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
    // NOTE: the old inline name dialog + history-vs-fresh AlertDialog lived here.
    // Blend creation moved to the dedicated CreateBlendScreen ("create_blend"
    // route) — same create logic, redesigned flow. Do not re-add dialogs here.

    // NOTE: AI dialog flow moved to AiCuratorScreen ("ai_curator" route).
    // Keep generation alive here because syncRepository.generateAiPlaylist is
    // the only caller with the Groq path — the old dialogs are retained as a
    // fallback for any deep-link that still triggers them, but the FAB now
    // routes to the screen.
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
                    onViewTypeChange(viewType.toggle())
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

                    // Blend entry — theme-adaptive mark, taps straight into create flow.
                    item(
                        key = "create_blend",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        CreateBlendTile(
                            onClick = { navController.navigate("create_blend") },
                            modifier = Modifier.animateItem(),
                        )
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

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        CreateBlendTile(
                            onClick = { navController.navigate("create_blend") },
                            modifier = Modifier.animateItem(),
                        )
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

        // Blend — dedicated button like Spotify (always visible).
        // Mark is the reusable theme-adaptive BlendIcon, not a generic plus.
        FloatingActionButton(
            onClick = { navController.navigate("create_blend") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                )
                .padding(16.dp)
                .padding(bottom = 144.dp),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            com.soundsphere.music.ui.component.BlendIcon(
                modifier = Modifier.size(24.dp),
                contentDescription = stringResource(R.string.blend),
            )
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

        // AI curator — single entry point. The old dialog flow (prompt + consent
        // + generating spinner) now lives as a full screen so the mock's hero +
        // seed chips have a home; the Library no longer owns AI UI.
        if (aiPlaylistsEnabled) {
            FloatingActionButton(
                onClick = { navController.navigate("ai_curator") },
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

        // Slot-freed prompt — a synced playlist was deleted, offer to sync a local one
        slotFreedPlaylists?.let { unsynced ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { slotFreedPlaylists = null },
                title = { androidx.compose.material3.Text(stringResource(R.string.playlist_slot_freed_title)) },
                text = {
                    androidx.compose.foundation.layout.Column {
                        androidx.compose.material3.Text(
                            stringResource(R.string.playlist_slot_freed_message, unsynced.size),
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.heightIn(max = 280.dp),
                        ) {
                            items(unsynced.size) { idx ->
                                val pl = unsynced[idx]
                                androidx.compose.foundation.layout.Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                ) {
                                    androidx.compose.material3.Text(
                                        pl.name,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    androidx.compose.material3.TextButton(onClick = {
                                        syncRepository.promoteLocalPlaylist(pl)
                                        // Remove this item from the shown list; dismiss if empty
                                        val remaining = unsynced.filter { it.id != pl.id }
                                        slotFreedPlaylists = if (remaining.isEmpty()) null else remaining
                                    }) {
                                        androidx.compose.material3.Text(stringResource(R.string.playlist_slot_freed_sync))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { slotFreedPlaylists = null }) {
                        androidx.compose.material3.Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
    }
}

/**
 * Blend entry row — reusable [BlendIcon] mark (theme-adaptive, no static asset),
 * taps straight into the create-Blend dialog (same as the Blend FAB).
 */
@Composable
private fun CreateBlendTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            com.soundsphere.music.ui.component.BlendIcon(
                modifier = Modifier.size(44.dp),
                contentDescription = stringResource(R.string.blend),
            )
        }
        Spacer(Modifier.width(12.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.blend_create),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                stringResource(R.string.blend_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            painter = painterResource(R.drawable.add),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
