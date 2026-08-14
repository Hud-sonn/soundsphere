/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens.search

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import com.soundsphere.music.LocalNavController
import com.soundsphere.music.LocalSyncRepository
import com.soundsphere.innertube.YouTube.SearchFilter.Companion.FILTER_ALBUM
import com.soundsphere.innertube.YouTube.SearchFilter.Companion.FILTER_ARTIST
import com.soundsphere.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import com.soundsphere.innertube.YouTube.SearchFilter.Companion.FILTER_EPISODE
import com.soundsphere.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import com.soundsphere.innertube.YouTube.SearchFilter.Companion.FILTER_PODCAST
import com.soundsphere.innertube.YouTube.SearchFilter.Companion.FILTER_PROFILE
import com.soundsphere.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import com.soundsphere.innertube.YouTube.SearchFilter.Companion.FILTER_VIDEO
import com.soundsphere.innertube.models.AlbumItem
import com.soundsphere.innertube.models.ArtistItem
import com.soundsphere.innertube.models.EpisodeItem
import com.soundsphere.innertube.models.PlaylistItem
import com.soundsphere.innertube.models.PodcastItem
import com.soundsphere.innertube.models.SongItem
import com.soundsphere.innertube.models.WatchEndpoint
import com.soundsphere.innertube.models.YTItem
import com.soundsphere.music.LocalDatabase
import com.soundsphere.music.LocalPlayerConnection
import com.soundsphere.music.R
import com.soundsphere.music.api.SyncArtist
import com.soundsphere.music.api.SyncTrack
import com.soundsphere.music.constants.AiPlaylistConsentKey
import com.soundsphere.music.constants.AiPlaylistEnabledKey
import com.soundsphere.music.constants.AutoRadioQueueKey
import com.soundsphere.music.constants.HideVideoSongsKey
import com.soundsphere.music.constants.MiniPlayerBottomSpacing
import com.soundsphere.music.constants.MiniPlayerHeight
import com.soundsphere.music.constants.NavigationBarHeight
import com.soundsphere.music.constants.PauseSearchHistoryKey
import com.soundsphere.music.db.entities.PlaylistEntity
import com.soundsphere.music.db.entities.PlaylistSongMap
import com.soundsphere.music.db.entities.SearchHistory
import com.soundsphere.music.db.entities.SongEntity
import com.soundsphere.music.extensions.toMediaItem
import com.soundsphere.music.models.toMediaMetadata
import com.soundsphere.music.playback.queues.ListQueue
import com.soundsphere.music.playback.queues.YouTubeQueue
import com.soundsphere.music.ui.component.ChipsRow
import com.soundsphere.music.ui.component.DefaultDialog
import com.soundsphere.music.ui.component.EmptyPlaceholder
import com.soundsphere.music.ui.component.HideOnScrollFAB
import com.soundsphere.music.ui.component.LocalMenuState
import com.soundsphere.music.ui.component.NavigationTitle
import com.soundsphere.music.ui.component.YouTubeListItem
import com.soundsphere.music.ui.component.shimmer.ListItemPlaceHolder
import com.soundsphere.music.ui.component.shimmer.ShimmerHost
import com.soundsphere.music.ui.menu.YouTubeAlbumMenu
import com.soundsphere.music.ui.menu.YouTubeArtistMenu
import com.soundsphere.music.ui.menu.YouTubePlaylistMenu
import com.soundsphere.music.ui.menu.YouTubeSongMenu
import com.soundsphere.music.utils.SearchRoutes
import com.soundsphere.music.utils.rememberPreference
import com.soundsphere.music.viewmodels.OnlineSearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.time.LocalDateTime

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchResult(
    viewModel: OnlineSearchViewModel = hiltViewModel(),
    pureBlack: Boolean = false,
    savedStateHandle: SavedStateHandle? = null
) {
    val navController = LocalNavController.current
    val database = LocalDatabase.current
    val syncRepository = LocalSyncRepository.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val scrollToTopCount by savedStateHandle
        ?.getStateFlow("scrollToTopCount", 0)
        ?.collectAsStateWithLifecycle(initialValue = 0) ?: remember { mutableIntStateOf(0) }

    var lastHandledCount by rememberSaveable { mutableIntStateOf(0) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(scrollToTopCount) {
        if (scrollToTopCount > lastHandledCount) {
            lastHandledCount = scrollToTopCount
            kotlinx.coroutines.delay(100)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (e: Exception) {
            }
            // Set focused AFTER requesting focus, not before

            isSearchFocused = true
        }
    }

    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)
    val hideVideoSongs by rememberPreference(HideVideoSongsKey, defaultValue = false)
    val autoRadioQueue by rememberPreference(AutoRadioQueueKey, defaultValue = true)

    BackHandler(enabled = isSearchFocused) {
        isSearchFocused = false
        focusManager.clearFocus()
    }

    // Extract query from navigation arguments
    val encodedQuery = navController.currentBackStackEntry?.arguments?.getString("query") ?: ""
    val decodedQuery = remember(encodedQuery) { SearchRoutes.decodeQuery(encodedQuery) }

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(decodedQuery, TextRange(decodedQuery.length)))
    }

    val onSearch: (String) -> Unit =
        remember {
            { searchQuery ->
                if (searchQuery.isNotEmpty()) {
                    isSearchFocused = false
                    focusManager.clearFocus()

                    navController.navigate(SearchRoutes.resultRoute(searchQuery)) {
                        popUpTo(SearchRoutes.ROUTE) {
                            inclusive = true
                        }

                        if (!pauseSearchHistory) {
                            coroutineScope.launch(Dispatchers.IO) {
                                database.query {
                                    insert(SearchHistory(query = searchQuery))
                                }
                            }
                        }
                    }
                }
            }
        }

    // Update query when decodedQuery changes
    LaunchedEffect(decodedQuery) {
        query = TextFieldValue(decodedQuery, TextRange(decodedQuery.length))
    }

    // ===== AI playlist from search results =====

    var aiConsent by rememberPreference(AiPlaylistConsentKey, false)
    var aiPlaylistsEnabled by rememberPreference(AiPlaylistEnabledKey, true)
    var detectedArtist by remember { mutableStateOf<SyncArtist?>(null) }
    var showAiConsentDialog by rememberSaveable { mutableStateOf(false) }
    var showArtistChoiceDialog by rememberSaveable { mutableStateOf(false) }
    var aiGenerating by rememberSaveable { mutableStateOf(false) }
    var aiDetecting by rememberSaveable { mutableStateOf(false) }

    val aiNotEnabledStr = stringResource(R.string.ai_playlist_not_enabled)
    val aiFailedStr = stringResource(R.string.ai_playlist_failed)
    val aiNoTracksStr = stringResource(R.string.ai_playlist_no_tracks)

    suspend fun saveAiPlaylist(prompt: String, tracks: List<SyncTrack>, artist: SyncArtist? = null) {
        if (tracks.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, aiNoTracksStr, Toast.LENGTH_SHORT).show()
            }
            return
        }
        val playlistName =
            if (artist != null && artist.name.isNotBlank()) {
                "${artist.name.take(50).trimEnd()} AI Mix"
            } else {
                if (prompt.length > 60) prompt.take(57).trimEnd() + "…" else prompt
            }
        val playlistEntity =
            PlaylistEntity(
                name = playlistName,
                bookmarkedAt = LocalDateTime.now(),
                isLocal = true,
            )
        val existingSongIds = tracks.mapNotNull { database.songEntity(it.id)?.id }.toHashSet()
        // Leaving the screen must not cancel the local save.
        withContext(NonCancellable) {
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
        }
        syncRepository.playlistCreated(playlistEntity)
        if (coroutineContext.isActive) {
            withContext(Dispatchers.Main) {
                navController.navigate("local_playlist/${playlistEntity.id}")
            }
        }
    }

    fun generateAiPlaylistFromSearch(artist: SyncArtist?, mixSimilar: Boolean) {
        val prompt = decodedQuery.trim()
        if (prompt.isBlank()) return
        aiGenerating = true
        coroutineScope.launch(Dispatchers.IO) {
            val result =
                syncRepository.generateAiPlaylist(
                    prompt,
                    count = 30,
                    artist = artist?.name,
                    mixSimilar = mixSimilar,
                )
            result.onSuccess { tracks -> saveAiPlaylist(prompt, tracks, artist) }
                .onFailure { error ->
                    val message =
                        when {
                            error.message?.contains("not enabled", ignoreCase = true) == true -> aiNotEnabledStr
                            else -> aiFailedStr
                        }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            withContext(Dispatchers.Main) {
                aiGenerating = false
            }
        }
    }

    fun startAiPlaylist(artist: SyncArtist?) {
        detectedArtist = artist
        if (aiConsent) {
            if (artist != null) {
                showArtistChoiceDialog = true
            } else {
                generateAiPlaylistFromSearch(artist = null, mixSimilar = false)
            }
        } else {
            showAiConsentDialog = true
        }
    }

    fun onAiCardClick() {
        val prompt = decodedQuery.trim()
        if (prompt.isBlank() || aiDetecting || aiGenerating) return
        aiDetecting = true
        coroutineScope.launch(Dispatchers.IO) {
            val result = syncRepository.detectArtist(prompt)
            withContext(Dispatchers.Main) {
                aiDetecting = false
                result.onSuccess { artist -> startAiPlaylist(artist) }
                    .onFailure {
                        Toast.makeText(context, aiFailedStr, Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    // Clear video filter if hideVideoSongs setting is enabled and filter is set to FILTER_VIDEO
    LaunchedEffect(hideVideoSongs) {
        if (hideVideoSongs && viewModel.filter.value == FILTER_VIDEO) {
            viewModel.filter.value = null
        }
    }

    val searchFilter by viewModel.filter.collectAsStateWithLifecycle()
    val searchSummary = viewModel.summaryPage
    val itemsPage by remember(searchFilter) {
        derivedStateOf {
            searchFilter?.value?.let {
                viewModel.viewStateMap[it]
            }
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (!shouldLoadMore) return@collect
            viewModel.loadMore()
        }
    }

    val ytItemContent: @Composable LazyItemScope.(YTItem) -> Unit = { item: YTItem ->
        val longClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            menuState.show {
                when (item) {
                    is SongItem -> {
                        YouTubeSongMenu(
                            song = item,
                            onDismiss = menuState::dismiss,
                        )
                    }

                    is AlbumItem -> {
                        YouTubeAlbumMenu(
                            albumItem = item,
                            onDismiss = menuState::dismiss,
                        )
                    }

                    is ArtistItem -> {
                        YouTubeArtistMenu(
                            artist = item,
                            onDismiss = menuState::dismiss,
                        )
                    }

                    is PlaylistItem -> {
                        YouTubePlaylistMenu(
                            playlist = item,
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss,
                        )
                    }

                    is PodcastItem -> {
                        YouTubePlaylistMenu(
                            playlist = item.asPlaylistItem(),
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss,
                        )
                    }

                    is EpisodeItem -> {
                        YouTubeSongMenu(
                            song = item.asSongItem(),
                            onDismiss = menuState::dismiss,
                        )
                    }
                }
            }
        }
        YouTubeListItem(
            item = item,
            isActive =
                when (item) {
                    is SongItem -> mediaMetadata?.id == item.id
                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                    is EpisodeItem -> mediaMetadata?.id == item.id
                    else -> false
                },
            isPlaying = isPlaying,
            trailingContent = {
                IconButton(
                    onClick = longClick,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null,
                    )
                }
            },
            modifier =
                Modifier
                    .combinedClickable(
                        onClick = {
                            when (item) {
                                is SongItem -> {
                                    if (item.id == mediaMetadata?.id) {
                                        playerConnection.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            if (autoRadioQueue) {
                                                YouTubeQueue(
                                                    WatchEndpoint(videoId = item.id),
                                                    item.toMediaMetadata(),
                                                )
                                            } else {
                                                ListQueue(
                                                    title = item.title,
                                                    items = listOf(item.toMediaItem())
                                                )
                                            }
                                        )
                                    }
                                }

                                is AlbumItem -> {
                                    navController.navigate("album/${item.id}")
                                }

                                is ArtistItem -> {
                                    navController.navigate("artist/${item.id}")
                                }

                                is PlaylistItem -> {
                                    navController.navigate("online_playlist/${item.id}")
                                }

                                is PodcastItem -> {
                                    navController.navigate("online_podcast/${item.id}")
                                }

                                is EpisodeItem -> {
                                    if (item.id == mediaMetadata?.id) {
                                        playerConnection.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            YouTubeQueue(
                                                WatchEndpoint(videoId = item.id),
                                                item.toMediaMetadata(),
                                            ),
                                        )
                                    }
                                }
                            }
                        },
                        onLongClick = longClick,
                    ).animateItem(),
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
    ) {
        // Google-style SearchBar with Material 3 design
        OutlinedTextField(
            value = query,
            onValueChange = { newQuery ->
                query = newQuery
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.search_yt_music),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingIcon = {
                IconButton(
                    onClick = { navController.navigateUp() },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = stringResource(R.string.dismiss),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailingIcon = {
                if (query.text.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            query = TextFieldValue("")
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            keyboardOptions =
                KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
            keyboardActions =
                KeyboardActions(
                    onSearch = {
                        onSearch(query.text)
                    },
                ),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor =
                        if (pureBlack) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    unfocusedContainerColor =
                        if (pureBlack) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            isSearchFocused = true
                        }
                    },
        )

        // Main content area below search bar
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                val visibleChips =
                    listOf(
                        null to stringResource(R.string.filter_all),
                        FILTER_SONG to stringResource(R.string.filter_songs),
                    ).let { baseChips ->
                        if (!hideVideoSongs) {
                            baseChips + (FILTER_VIDEO to stringResource(R.string.filter_videos))
                        } else {
                            baseChips
                        }
                    } +
                        listOf(
                            FILTER_ALBUM to stringResource(R.string.filter_albums),
                            FILTER_ARTIST to stringResource(R.string.filter_artists),
                            FILTER_COMMUNITY_PLAYLIST to stringResource(R.string.filter_community_playlists),
                            FILTER_FEATURED_PLAYLIST to stringResource(R.string.filter_featured_playlists),
                            FILTER_PODCAST to stringResource(R.string.filter_podcasts),
                            FILTER_EPISODE to stringResource(R.string.filter_episodes),
                            FILTER_PROFILE to stringResource(R.string.filter_profiles),
                        )

                ChipsRow(
                    chips = visibleChips,
                    currentValue = searchFilter,
                    onValueUpdate = {
                        if (viewModel.filter.value != it) {
                            viewModel.filter.value = it
                        }
                        coroutineScope.launch {
                            lazyListState.animateScrollToItem(0)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (searchFilter == null) {
                        if (searchSummary != null && aiPlaylistsEnabled) {
                            item(key = "ai_playlist_card_$decodedQuery") {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable(enabled = !aiDetecting && !aiGenerating, onClick = ::onAiCardClick)
                                            .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (aiDetecting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(R.drawable.ai),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.ai_playlist_search_result_title),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Text(
                                            text =
                                                stringResource(
                                                    R.string.ai_playlist_search_result_desc,
                                                    if (decodedQuery.length > 40) decodedQuery.take(37).trimEnd() + "…" else decodedQuery,
                                                ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (aiGenerating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                }
                            }
                        }

                        searchSummary?.summaries?.forEach { summary ->
                            item {
                                NavigationTitle(summary.title)
                            }

                            itemsIndexed(
                                items = summary.items,
                                key = { index, item -> "${summary.title}/${item.id}/$index" },
                                itemContent = { index, item -> ytItemContent(item) },
                            )
                        }

                        if (searchSummary?.summaries?.isEmpty() == true) {
                            item {
                                EmptyPlaceholder(
                                    icon = R.drawable.search,
                                    text = stringResource(R.string.no_results_found),
                                )
                            }
                        }
                    } else {
                        items(
                            items = itemsPage?.items.orEmpty().distinctBy { it.id },
                            key = { "filtered_${it.id}" },
                            itemContent = ytItemContent,
                        )

                        if (itemsPage?.continuation != null) {
                            item(key = "loading") {
                                ShimmerHost {
                                    repeat(3) {
                                        ListItemPlaceHolder()
                                    }
                                }
                            }
                        }

                        if (itemsPage?.items?.isEmpty() == true) {
                            item {
                                EmptyPlaceholder(
                                    icon = R.drawable.search,
                                    text = stringResource(R.string.no_results_found),
                                )
                            }
                        }
                    }

                    if (searchFilter == null && searchSummary == null || searchFilter != null && itemsPage == null) {
                        item {
                            ShimmerHost {
                                repeat(8) {
                                    ListItemPlaceHolder()
                                }
                            }
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(MiniPlayerHeight + MiniPlayerBottomSpacing + NavigationBarHeight))
                    }
                }
            }
            if (isSearchFocused) {
                OnlineSearchScreen(
                    query = query.text,
                    onQueryChange = { query = it },
                    onSearch = onSearch,
                    onDismiss = {
                        isSearchFocused = false
                        focusManager.clearFocus()
                    },
                    pureBlack = pureBlack,
                )
            }
            HideOnScrollFAB(
                lazyListState = lazyListState,
                icon = R.drawable.mic,
                onClick = { navController.navigate("recognition") },
            )
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
                        if (detectedArtist != null) {
                            showArtistChoiceDialog = true
                        } else {
                            generateAiPlaylistFromSearch(artist = null, mixSimilar = false)
                        }
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

    if (showArtistChoiceDialog && detectedArtist != null) {
        val artistName = detectedArtist!!.name
        DefaultDialog(
            onDismiss = { showArtistChoiceDialog = false },
            icon = { Icon(painterResource(R.drawable.ai), contentDescription = null) },
            title = { Text(text = stringResource(R.string.ai_playlist_choice_title)) },
            buttons = {
                TextButton(onClick = { showArtistChoiceDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showArtistChoiceDialog = false
                        generateAiPlaylistFromSearch(detectedArtist, mixSimilar = false)
                    },
                ) {
                    Text(text = stringResource(R.string.ai_playlist_choice_only_artist, artistName))
                }
                TextButton(
                    onClick = {
                        showArtistChoiceDialog = false
                        generateAiPlaylistFromSearch(detectedArtist, mixSimilar = true)
                    },
                ) {
                    Text(text = stringResource(R.string.ai_playlist_choice_mix, artistName))
                }
            },
        ) {
            Text(
                text = stringResource(R.string.ai_playlist_choice_body, artistName),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
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
}
