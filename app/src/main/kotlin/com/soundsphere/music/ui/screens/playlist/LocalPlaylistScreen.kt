/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens.playlist

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.util.fastSumBy
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.soundsphere.innertube.YouTube
import com.soundsphere.innertube.models.PlaylistItem
import com.soundsphere.music.LocalDatabase
import com.soundsphere.music.LocalDownloadUtil
import com.soundsphere.music.LocalNavController
import com.soundsphere.music.LocalPlayerAwareWindowInsets
import com.soundsphere.music.LocalPlayerConnection
import com.soundsphere.music.LocalSyncRepository
import com.soundsphere.music.LocalSyncUtils
import com.soundsphere.music.R
import com.soundsphere.music.constants.DarkModeKey
import com.soundsphere.music.constants.PlaylistEditLockKey
import com.soundsphere.music.constants.PlaylistSongSortDescendingKey
import com.soundsphere.music.constants.PlaylistSongSortType
import com.soundsphere.music.constants.PlaylistSongSortTypeKey
import com.soundsphere.music.constants.SwipeToRemoveSongKey
import com.soundsphere.music.db.entities.Playlist
import com.soundsphere.music.db.entities.PlaylistSong
import com.soundsphere.music.db.entities.PlaylistSongMap
import com.soundsphere.music.extensions.move
import com.soundsphere.music.extensions.toMediaItem
import com.soundsphere.music.playback.ExoDownloadService
import com.soundsphere.music.playback.queues.ListQueue
import com.soundsphere.music.ui.component.ActionPromptDialog
import com.soundsphere.music.ui.component.DefaultDialog
import com.soundsphere.music.ui.component.DraggableScrollbar
import com.soundsphere.music.ui.component.EmptyPlaceholder
import com.soundsphere.music.ui.component.ExpandableText
import com.soundsphere.music.ui.component.IconButton
import com.soundsphere.music.ui.component.LocalMenuState
import com.soundsphere.music.ui.component.OverlayEditButton
import com.soundsphere.music.ui.component.SongListItem
import com.soundsphere.music.ui.component.SortHeader
import com.soundsphere.music.ui.component.TextFieldDialog
import com.soundsphere.music.ui.menu.CustomThumbnailMenu
import com.soundsphere.music.ui.menu.LocalPlaylistMenu
import com.soundsphere.music.ui.menu.SelectionSongMenu
import com.soundsphere.music.ui.menu.SongMenu
import com.soundsphere.music.ui.screens.settings.DarkMode
import com.soundsphere.music.ui.utils.backToMain
import com.soundsphere.music.utils.makeTimeString
import com.soundsphere.music.utils.rememberEnumPreference
import com.soundsphere.music.utils.rememberPreference
import com.soundsphere.music.utils.reportException
import com.soundsphere.music.viewmodels.LocalPlaylistViewModel
import com.yalantis.ucrop.UCrop
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDateTime

@SuppressLint("RememberReturnType")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistScreen(
    navController: NavController,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val syncRepository = LocalSyncRepository.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val onlinePlaylist by viewModel.onlinePlaylist.collectAsStateWithLifecycle()
    // Blend members hoisted here (not in the header) so BOTH the member strip
    // and per-track "Added by" rows resolve ids to names from one fetch.
    // Polls every 30s while a Blend is open — same cadence as the notification
    // poll — so the owner sees a joiner appear without reopening the screen.
    var blendMembers by remember { mutableStateOf<List<com.soundsphere.music.api.SyncService.BlendCollaborator>>(emptyList()) }
    // Per-member track counts power the contributions bar (header). Refreshed with members.
    var blendMemberTrackCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val blendMembersPlaylistId = playlist?.playlist?.takeIf { it.isCollaborative }?.id
    androidx.compose.runtime.LaunchedEffect(blendMembersPlaylistId) {
        if (blendMembersPlaylistId == null) {
            blendMembers = emptyList()
            blendMemberTrackCounts = emptyMap()
            return@LaunchedEffect
        }
        while (true) {
            try {
                syncRepository.getBlendCollaborators(blendMembersPlaylistId).onSuccess { members ->
                    blendMembers = members
                    blendMemberTrackCounts = members.associate { m ->
                        m.userId to database.countTracksAddedBy(blendMembersPlaylistId, m.userId)
                    }
                }
            } catch (e: Exception) { /* keep last good list */ }
            delay(30_000)
        }
    }
    // Contributor filter — null = everyone. Nulls (pre-attribution rows) always show.
    var contributorFilter by rememberSaveable { mutableStateOf<String?>(null) }
    if (blendMembersPlaylistId == null && contributorFilter != null) contributorFilter = null
    val blendMemberNames = remember(blendMembers) {
        blendMembers.associate { it.userId to it.username.ifBlank { it.userId.take(8) } }
    }
    val mutableSongs = remember { mutableStateListOf<PlaylistSong>() }
    val playlistLength =
        remember(songs) {
            songs.fastSumBy { it.song.song.duration }
        }
    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            PlaylistSongSortTypeKey,
            PlaylistSongSortType.CUSTOM,
        )
    val (sortDescending, onSortDescendingChange) =
        rememberPreference(
            PlaylistSongSortDescendingKey,
            true,
        )
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = true)

    val coroutineScope = rememberCoroutineScope()
    val syncUtils = LocalSyncUtils.current
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearching by rememberSaveable { mutableStateOf(false) }

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    val filteredSongs =
        remember(songs, query) {
            if (query.text.isEmpty()) {
                songs
            } else {
                songs.filter { song ->
                    song.song.song.title
                        .contains(query.text, ignoreCase = true) ||
                        song.song.artists
                            .fastAny { it.name.contains(query.text, ignoreCase = true) }
                }
            }
        }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    var inSelectMode by remember { mutableStateOf(false) }
    val selection =
        remember {
            mutableStateListOf<Int>()
        }
    var selectionAnchorMapId by remember { mutableStateOf<Int?>(null) }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
        selectionAnchorMapId = null
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    val editable: Boolean = playlist?.playlist?.isEditable == true

    LaunchedEffect(songs) {
        selection.fastForEachReversed { mapId ->
            if (songs.find { it.map.id == mapId } == null) {
                selection.remove(Integer.valueOf(mapId))
            }
        }

        if (selectionAnchorMapId != null && songs.none { it.map.id == selectionAnchorMapId }) {
            selectionAnchorMapId = songs.firstOrNull { it.map.id in selection }?.map?.id
        }
    }

    LaunchedEffect(songs) {
        mutableSongs.apply {
            clear()
            addAll(songs)
        }
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.song.id]?.state == Download.STATE_QUEUED ||
                            downloads[it.song.id]?.state == Download.STATE_DOWNLOADING ||
                            downloads[it.song.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    var showEditDialog by remember {
        mutableStateOf(false)
    }

    if (showEditDialog) {
        playlist?.playlist?.let { playlistEntity ->
            TextFieldDialog(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.edit),
                        contentDescription = null,
                    )
                },
                title = { Text(text = stringResource(R.string.edit_playlist)) },
                onDismiss = { showEditDialog = false },
                initialTextFieldValue =
                    TextFieldValue(
                        playlistEntity.name,
                        TextRange(playlistEntity.name.length),
                    ),
                onDone = { name ->
                    val renamed =
                        playlistEntity.copy(
                            name = name,
                            lastUpdateTime = LocalDateTime.now(),
                        )
                    database.query {
                        update(renamed)
                    }
                    syncRepository.playlistRenamed(renamed)
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        playlistEntity.browseId?.let { YouTube.renamePlaylist(it, name) }
                    }
                },
            )
        }
    }

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text =
                        stringResource(
                            R.string.remove_download_playlist_confirm,
                            playlist?.playlist?.name.orEmpty(),
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = { showRemoveDownloadDialog = false },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        if (!editable) {
                            database.transaction {
                                playlist?.id?.let { clearPlaylist(it) }
                            }
                        }
                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.song.id,
                                false,
                            )
                        }
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    var showDeletePlaylistDialog by remember {
        mutableStateOf(false)
    }
    if (showDeletePlaylistDialog) {
        DefaultDialog(
            onDismiss = { showDeletePlaylistDialog = false },
            content = {
                Text(
                    text =
                        stringResource(
                            R.string.delete_playlist_confirm,
                            playlist?.playlist?.name.orEmpty(),
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                    },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        database.query {
                            playlist?.let { delete(it.playlist) }
                        }
                        syncRepository.playlistDeleted(playlist?.playlist ?: return@TextButton)
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            playlist?.playlist?.browseId?.let { YouTube.deletePlaylist(it) }
                        }
                        navController.popBackStack()
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    val headerItems = 2
    val lazyListState = rememberLazyListState()
    var dragInfo by remember {
        mutableStateOf<Pair<Int, Int>?>(null)
    }
    val reorderableState =
        rememberReorderableLazyListState(
            lazyListState = lazyListState,
            scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) { from, to ->
            if (to.index >= headerItems && from.index >= headerItems) {
                val currentDragInfo = dragInfo
                dragInfo =
                    if (currentDragInfo == null) {
                        (from.index - headerItems) to (to.index - headerItems)
                    } else {
                        currentDragInfo.first to (to.index - headerItems)
                    }

                mutableSongs.move(from.index - headerItems, to.index - headerItems)
            }
        }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            dragInfo?.let { (from, to) ->
                viewModel.viewModelScope.launch(Dispatchers.IO) {
                    database.withTransaction {
                        move(viewModel.playlistId, from, to)
                    }

                    // Sync order with YT Music
                    val browseId = viewModel.playlist.value?.playlist?.browseId
                    if (browseId != null) {
                        val playlistSongMap = database.playlistSongMaps(viewModel.playlistId, 0)
                        val setVideoId = playlistSongMap.getOrNull(to)?.setVideoId
                        val successorSetVideoId = playlistSongMap.getOrNull(to + 1)?.setVideoId

                        if (setVideoId != null) {
                            YouTube.moveSongPlaylist(
                                browseId,
                                setVideoId,
                                successorSetVideoId,
                            )
                        }
                    }
                }

                dragInfo = null
            }
        }
    }

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
        ) {
            playlist?.let { playlist ->
                if (playlist.songCount == 0 && playlist.playlist.remoteSongCount == 0) {
                    item(key = "empty_placeholder") {
                        EmptyPlaceholder(
                            icon = R.drawable.music_note,
                            text = stringResource(R.string.playlist_is_empty),
                            modifier = Modifier.animateItem(),
                        )
                    }
                } else {
                    if (!isSearching) {
                        item(key = "playlist_header") {
                            LocalPlaylistHeader(
                                playlist = playlist,
                                songs = songs,
                                onlinePlaylist = onlinePlaylist,
                                onShowEditDialog = { showEditDialog = true },
                                onShowRemoveDownloadDialog = { showRemoveDownloadDialog = true },
                                onshowDeletePlaylistDialog = { showDeletePlaylistDialog = true },
                                onStartSearch = { isSearching = true },
                                snackbarHostState = snackbarHostState,
                                modifier = Modifier.animateItem(),
                                blendMembers = blendMembers,
                                blendMemberTrackCounts = blendMemberTrackCounts,
                            )
                        }
                    }

                    item(key = "controls_row") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .padding(start = 16.dp)
                                    .animateItem(),
                        ) {
                            SortHeader(
                                sortType = sortType,
                                sortDescending = sortDescending,
                                onSortTypeChange = onSortTypeChange,
                                onSortDescendingChange = onSortDescendingChange,
                                sortTypeText = { sortType ->
                                    when (sortType) {
                                        PlaylistSongSortType.CUSTOM -> R.string.sort_by_custom
                                        PlaylistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                        PlaylistSongSortType.NAME -> R.string.sort_by_name
                                        PlaylistSongSortType.ARTIST -> R.string.sort_by_artist
                                        PlaylistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            if (editable) {
                                IconButton(
                                    onClick = { locked = !locked },
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(if (locked) R.drawable.lock else R.drawable.lock_open),
                                        contentDescription = null,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tracklist section title (mock) + contributor filter (Blends only).
            if (blendMembersPlaylistId != null && !isSearching) {
                item(key = "tracklist_title") {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.tracklist),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 8.dp)
                            .animateItem(),
                    )
                }
            }
            // Filter by contributor (Blends only) — null = everyone.
            if (blendMembersPlaylistId != null && blendMembers.isNotEmpty() && !isSearching) {
                item(key = "contributor_filter") {
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .animateItem(),
                    ) {
                        androidx.compose.material3.FilterChip(
                            selected = contributorFilter == null,
                            onClick = { contributorFilter = null },
                            label = { androidx.compose.material3.Text(stringResource(R.string.filter_all)) },
                        )
                        blendMembers.forEach { member ->
                            androidx.compose.material3.FilterChip(
                                selected = contributorFilter == member.userId,
                                onClick = {
                                    contributorFilter = if (contributorFilter == member.userId) null else member.userId
                                },
                                label = {
                                    androidx.compose.material3.Text(
                                        member.username.ifBlank { member.userId.take(8) },
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // Contributor filter stacks on top of search: unknown-attribution rows
            // always stay visible so the filter never hides pre-attribution history.
            val contributorFiltered =
                if (!isSearching && contributorFilter != null) {
                    mutableSongs.filter { it.map.addedByUserId == null || it.map.addedByUserId == contributorFilter }
                } else {
                    mutableSongs
                }
            val displayedSongs = if (isSearching) filteredSongs else contributorFiltered

            itemsIndexed(
                items = displayedSongs,
                key = { _, song -> song.map.id },
            ) { index, song ->
                ReorderableItem(
                    state = reorderableState,
                    key = song.map.id,
                ) {
                    val currentItem by rememberUpdatedState(song)

                    fun deleteFromPlaylist() {
                        // Capture values before deletion — DB entry will be gone afterwards
                        val browseId = playlist?.playlist?.browseId
                        val setVideoId = currentItem.map.setVideoId
                        val songId = currentItem.map.songId
                        val playlistId = currentItem.map.playlistId

                        database.transaction {
                            move(playlistId, currentItem.map.position, Int.MAX_VALUE)
                            delete(currentItem.map.copy(position = Int.MAX_VALUE))
                        }

                        if (browseId != null) {
                            syncUtils.scheduleRemoveFromPlaylist(
                                browseId,
                                songId,
                                playlistId
                            ) {
                                setVideoId
                            }
                        }
                        syncRepository.playlistTrackRemoved(playlistId, songId)
                    }

                    val swipeRemoveEnabled by rememberPreference(SwipeToRemoveSongKey, defaultValue = false)
                    val dismissBoxState =
                        rememberSwipeToDismissBoxState(
                            positionalThreshold = { totalDistance -> totalDistance },
                        )
                    var processedDismiss by remember { mutableStateOf(false) }
                    LaunchedEffect(dismissBoxState.currentValue) {
                        val dv = dismissBoxState.currentValue
                        if (swipeRemoveEnabled && !processedDismiss && (
                                dv == SwipeToDismissBoxValue.StartToEnd ||
                                    dv == SwipeToDismissBoxValue.EndToStart
                            )
                        ) {
                            processedDismiss = true
                            deleteFromPlaylist()
                        }
                        if (dv == SwipeToDismissBoxValue.Settled) {
                            processedDismiss = false
                        }
                    }

                    val onCheckedChange: (Boolean) -> Unit = {
                        if (it) {
                            selection.add(song.map.id)
                        } else {
                            selection.remove(Integer.valueOf(song.map.id))
                        }
                    }

                    val isBlend = playlist?.playlist?.isCollaborative == true
                    val addedBy = song.map.addedByUserId
                    val content: @Composable () -> Unit = {
                        androidx.compose.foundation.layout.Column {
                        SongListItem(
                            song = song.song,
                            isActive = song.song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            showInLibraryIcon = true,
                            trailingContent = {
                                if (inSelectMode) {
                                    Checkbox(
                                        checked = selection.contains(song.map.id),
                                        onCheckedChange = onCheckedChange,
                                    )
                                } else {
                                    // Per-row duration (mock track rows show it).
                                    androidx.compose.material3.Text(
                                        text = makeTimeString(song.song.song.duration * 1000L),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 4.dp),
                                    )
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song.song,
                                                    playlistSong = song,
                                                    playlistBrowseId = playlist?.playlist?.browseId,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null,
                                        )
                                    }

                                    if (sortType == PlaylistSongSortType.CUSTOM && !locked && !inSelectMode && !isSearching && editable) {
                                        IconButton(
                                            onClick = { },
                                            modifier = Modifier.draggableHandle(),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.drag_handle),
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                }
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (inSelectMode) {
                                                onCheckedChange(!selection.contains(song.map.id))
                                            } else if (song.song.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = playlist?.playlist?.name.orEmpty(),
                                                        items = songs.map { it.song.toMediaItem() },
                                                        startIndex = songs.indexOfFirst { it.map.id == song.map.id },
                                                        sourceType = "playlist",
                                                        sourceId = playlist?.playlist?.id,
                                                    ),
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            if (!inSelectMode) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                inSelectMode = true
                                                onCheckedChange(true)
                                                selectionAnchorMapId = song.map.id
                                            } else {
                                                val anchorIndex =
                                                    selectionAnchorMapId?.let { anchorMapId ->
                                                        displayedSongs.indexOfFirst { it.map.id == anchorMapId }
                                                    } ?: -1

                                                if (anchorIndex == -1) {
                                                    onCheckedChange(true)
                                                    selectionAnchorMapId = song.map.id
                                                } else {
                                                    val range = if (anchorIndex <= index) anchorIndex..index else index..anchorIndex
                                                    for (rangeIndex in range) {
                                                        val rangeMapId = displayedSongs[rangeIndex].map.id
                                                        if (rangeMapId !in selection) {
                                                            selection.add(rangeMapId)
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    ),
                        )
                            // Blend attribution chip — pill with avatar dot + name, merged
                            // into the row card (mock style). Taps to the member profile.
                            val addedByName = addedBy?.let { blendMemberNames[it] ?: it.take(8) }
                            if (isBlend && addedBy != null && addedByName != null) {
                                androidx.compose.foundation.layout.Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 56.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                ) {
                                    androidx.compose.foundation.layout.Row(
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                            .clickable {
                                                navController.navigate("user/$addedBy?playlistId=${playlist?.playlist?.id}")
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                    ) {
                                        androidx.compose.foundation.layout.Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondaryContainer),
                                            contentAlignment = androidx.compose.ui.Alignment.Center,
                                        ) {
                                            androidx.compose.material3.Text(
                                                text = addedByName.ifBlank { "?" }.take(1).uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                        androidx.compose.material3.Text(
                                            text = stringResource(R.string.blend_added_by, addedByName),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        } // Column
                    }

                    if (locked || inSelectMode || !swipeRemoveEnabled) {
                        Box(modifier = Modifier.animateItem()) {
                            BlendRowCard(isBlend) { content() }
                        }
                    } else {
                        SwipeToDismissBox(
                            state = dismissBoxState,
                            backgroundContent = {},
                            modifier = Modifier.animateItem(),
                        ) {
                            BlendRowCard(isBlend) { content() }
                        }
                    }
                }
            }
        }

        DraggableScrollbar(
            modifier =
                Modifier
                    .padding(
                        LocalPlayerAwareWindowInsets.current
                            .union(WindowInsets.ime)
                            .asPaddingValues(),
                    ).align(Alignment.CenterEnd),
            scrollState = lazyListState,
            headerItems = 2,
        )

        TopAppBar(
            title = {
                if (inSelectMode) {
                    Text(pluralStringResource(R.plurals.n_selected, selection.size, selection.size))
                } else if (isSearching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                    )
                } else if (showTopBarTitle) {
                    Text(playlist?.playlist?.name.orEmpty())
                }
            },
            navigationIcon = {
                if (inSelectMode) {
                    IconButton(onClick = onExitSelectionMode) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (isSearching) {
                                isSearching = false
                                query = TextFieldValue()
                            } else {
                                navController.navigateUp()
                            }
                        },
                        onLongClick = {
                            if (!isSearching) {
                                navController.backToMain()
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                }
            },
            actions = {
                if (inSelectMode) {
                    Checkbox(
                        checked = selection.size == songs.size && selection.isNotEmpty(),
                        onCheckedChange = {
                            if (selection.size == songs.size) {
                                selection.clear()
                            } else {
                                selection.clear()
                                selection.addAll(songs.map { it.map.id })
                            }
                        },
                    )
                    IconButton(
                        enabled = selection.isNotEmpty(),
                        onClick = {
                            menuState.show {
                                SelectionSongMenu(
                                    songSelection =
                                        selection.mapNotNull { mapId ->
                                            songs.find { it.map.id == mapId }?.song
                                        },
                                    songPosition =
                                        selection.mapNotNull { mapId ->
                                            songs.find { it.map.id == mapId }?.map
                                        },
                                    playlistBrowseId = playlist?.playlist?.browseId,
                                    onDismiss = menuState::dismiss,
                                    clearAction = onExitSelectionMode,
                                )
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                } else if (!isSearching) {
                    // Only search button remains in TopAppBar
                    IconButton(
                        onClick = { isSearching = true },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null,
                        )
                    }
                }
            },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime))
                    .align(Alignment.BottomCenter),
        )
    }
}

@Composable
fun LocalPlaylistHeader(
    playlist: Playlist,
    songs: List<PlaylistSong>,
    onlinePlaylist: PlaylistItem?,
    onShowEditDialog: () -> Unit,
    onShowRemoveDownloadDialog: () -> Unit,
    onshowDeletePlaylistDialog: () -> Unit,
    onStartSearch: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier,
    blendMembers: List<com.soundsphere.music.api.SyncService.BlendCollaborator> = emptyList(),
    // Local track counts per member id — powers the contributions bar.
    blendMemberTrackCounts: Map<String, Int> = emptyMap(),
) {
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val syncUtils = LocalSyncUtils.current
    val syncRepository = LocalSyncRepository.current
    val scope = rememberCoroutineScope()
    val editPlaylistCoverStr = stringResource(R.string.edit_playlist_cover)
    val playlistSyncedStr = stringResource(R.string.playlist_synced)

    val playlistLength =
        remember(songs) {
            songs.fastSumBy { it.song.song.duration }
        }

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    val liked = playlist.playlist.bookmarkedAt != null
    val editable: Boolean = playlist.playlist.isEditable

    // Seeded from the DB so an uploaded cover survives reopen — previously this
    // reset to null every time and the header fell back to song art even though
    // Home/Library showed the custom cover.
    val overrideThumbnail = remember(playlist.playlist.thumbnailUrl) { mutableStateOf(playlist.playlist.thumbnailUrl) }
    var isCustomThumbnail: Boolean =
        playlist.thumbnails.firstOrNull()?.let {
            it.contains("studio_square_thumbnail") || it.contains("content://com.soundsphere.music")
        } ?: false

    val result = remember { mutableStateOf<Uri?>(null) }
    var pendingCropDestUri by remember { mutableStateOf<Uri?>(null) }
    var showEditNoteDialog by remember { mutableStateOf(false) }

    val cropLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == android.app.Activity.RESULT_OK) {
                val output = res.data?.let { UCrop.getOutput(it) } ?: pendingCropDestUri
                if (output != null) result.value = output
            }
        }

    val (darkMode, _) =
        rememberEnumPreference(
            DarkModeKey,
            defaultValue = DarkMode.AUTO,
        )

    val cropColor = MaterialTheme.colorScheme
    val darkTheme = darkMode == DarkMode.ON || (darkMode == DarkMode.AUTO && isSystemInDarkTheme())

    val pickLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            uri?.let { sourceUri ->
                val destFile = java.io.File(context.cacheDir, "playlist_cover_crop_${System.currentTimeMillis()}.jpg")
                val destUri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", destFile)
                pendingCropDestUri = destUri

                val options =
                    UCrop.Options().apply {
                        setCompressionFormat(Bitmap.CompressFormat.JPEG)
                        setCompressionQuality(90)
                        setHideBottomControls(true)
                        setToolbarTitle(editPlaylistCoverStr)

                        setStatusBarLight(!darkTheme)

                        setToolbarColor(cropColor.surface.toArgb())
                        setToolbarWidgetColor(cropColor.inverseSurface.toArgb())
                        setRootViewBackgroundColor(cropColor.surface.toArgb())
                        setLogoColor(cropColor.surface.toArgb())
                    }

                val intent =
                    UCrop
                        .of(sourceUri, destUri)
                        .withAspectRatio(1f, 1f)
                        .withOptions(options)
                        .getIntent(context)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                cropLauncher.launch(intent)
            }
        }

    LaunchedEffect(result.value) {
        val uri = result.value ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            when {
                playlist.playlist.isCollaborative -> {
                    // Blend: upload to Cloudinary, sync cover_url across devices
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext
                        val bytes = inputStream.readBytes()
                        inputStream.close()
                        val tmpFile = java.io.File.createTempFile("blend_cover_", ".jpg", context.cacheDir)
                        tmpFile.writeBytes(bytes)
                        val uploadResult = com.soundsphere.music.api.CloudinaryUploader.uploadBlendCover(tmpFile)
                        tmpFile.delete()
                        uploadResult.onSuccess { secureUrl ->
                            overrideThumbnail.value = secureUrl
                            isCustomThumbnail = true
                            database.query { update(playlist.playlist.copy(thumbnailUrl = secureUrl)) }
                            // Sync to backend — push cover_url via SyncRepository
                            try {
                                syncRepository.playlistCoverChanged(playlist.playlist.id, secureUrl)
                            } catch (e: Exception) { reportException(e) }
                        }.onFailure { reportException(it); snackbarHostState.showSnackbar("Cover upload failed") }
                    } catch (e: Exception) { reportException(e) }
                }
                playlist.playlist.browseId == null -> {
                    overrideThumbnail.value = uri.toString()
                    isCustomThumbnail = true

                    // Update the database with the new thumbnail
                    database.query {
                        update(playlist.playlist.copy(thumbnailUrl = uri.toString()))
                    }
                }

                else -> {
                    val bytes = uriToByteArray(context, uri)
                    if (bytes == null) {
                        android.util.Log.w("LocalPlaylistScreen", "Failed to read selected thumbnail")
                        return@withContext
                    }
                    YouTube
                        .uploadCustomThumbnailLink(
                            playlist.playlist.browseId,
                            bytes,
                        ).onSuccess { newThumbnailUrl ->
                            overrideThumbnail.value = newThumbnailUrl
                            isCustomThumbnail = true

                            // Update the database with the new thumbnail URL
                            database.query {
                                update(playlist.playlist.copy(thumbnailUrl = newThumbnailUrl))
                            }
                        }.onFailure {
                            if (it is ClientRequestException) {
                                snackbarHostState.showSnackbar("${it.response.status.value} ${it.response.status.description}")
                            }
                            reportException(it)
                        }
                }
            }
        }
    }

    LaunchedEffect(songs) {
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.song.id]?.state == Download.STATE_QUEUED ||
                            downloads[it.song.id]?.state == Download.STATE_DOWNLOADING ||
                            downloads[it.song.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showEditNoteDialog) {
            ActionPromptDialog(
                title = stringResource(R.string.edit_playlist_cover),
                onDismiss = { showEditNoteDialog = false },
                onConfirm = {
                    showEditNoteDialog = false
                    pickLauncher.launch(
                        PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onCancel = { showEditNoteDialog = false },
            ) {
                if (playlist.playlist.browseId != null) {
                    Text(
                        text = stringResource(R.string.edit_playlist_cover_note),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = stringResource(R.string.edit_playlist_cover_note_wait),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
        // Playlist Thumbnail(s) - Large centered with shadow.
        // Blends get the ambient glow + reusable mark as default art (mock hero).
        Box(
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (playlist.playlist.isCollaborative) {
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)),
                )
            }
            when (playlist.thumbnails.size) {
                0 -> {
                    // Blends use the card radius from the mock; regular playlists unchanged.
                    val artShape = if (playlist.playlist.isCollaborative) RoundedCornerShape(12.dp) else RoundedCornerShape(3.dp)
                    Surface(
                        modifier =
                            Modifier
                                .size(240.dp)
                                .shadow(
                                    elevation = 16.dp,
                                    shape = artShape,
                                ),
                        shape = artShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (playlist.playlist.isCollaborative) {
                                com.soundsphere.music.ui.component.BlendIcon(
                                    modifier = Modifier.size(120.dp),
                                    contentDescription = stringResource(R.string.blend),
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.queue_music),
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                1 -> {
                    val artShape1 = if (playlist.playlist.isCollaborative) RoundedCornerShape(12.dp) else RoundedCornerShape(3.dp)
                    Surface(
                        modifier =
                            Modifier
                                .size(240.dp)
                                .shadow(
                                    elevation = 24.dp,
                                    shape = artShape1,
                                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                ),
                        shape = artShape1,
                    ) {
                        AsyncImage(
                            model = overrideThumbnail.value ?: playlist.thumbnails[0],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (editable) {
                        OverlayEditButton(
                            visible = true,
                            alignment = Alignment.BottomEnd,
                            onClick = {
                                if (isCustomThumbnail) {
                                    menuState.show(
                                        {
                                            CustomThumbnailMenu(
                                                onEdit = {
                                                    pickLauncher.launch(
                                                        PickVisualMediaRequest(
                                                            mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly,
                                                        ),
                                                    )
                                                },
                                                onRemove = {
                                                    when {
                                                        playlist.playlist.browseId == null -> {
                                                            overrideThumbnail.value = null
                                                            database.query {
                                                                update(playlist.playlist.copy(thumbnailUrl = null))
                                                            }
                                                        }

                                                        else -> {
                                                            scope.launch(Dispatchers.IO) {
                                                                YouTube.removeThumbnailPlaylist(playlist.playlist.browseId).onSuccess { newThumbnailUrl ->
                                                                    overrideThumbnail.value = newThumbnailUrl
                                                                    database.query {
                                                                        update(playlist.playlist.copy(thumbnailUrl = newThumbnailUrl))
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    isCustomThumbnail = false
                                                },
                                                onDismiss = menuState::dismiss,
                                            )
                                        },
                                    )
                                } else {
                                    showEditNoteDialog = true
                                }
                            },
                        )
                    }
                }

                else -> {
                    Surface(
                        modifier =
                            Modifier
                                .size(240.dp)
                                .shadow(
                                    elevation = 24.dp,
                                    shape = RoundedCornerShape(3.dp),
                                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                ),
                        shape = RoundedCornerShape(3.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            listOf(
                                Alignment.TopStart,
                                Alignment.TopEnd,
                                Alignment.BottomStart,
                                Alignment.BottomEnd,
                            ).fastForEachIndexed { index, alignment ->
                                AsyncImage(
                                    model = playlist.thumbnails.getOrNull(index),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .align(alignment)
                                            .size(120.dp),
                                )
                            }
                        }
                    }
                    if (editable) {
                        OverlayEditButton(
                            visible = true,
                            alignment = Alignment.BottomEnd,
                            onClick = {
                                if (isCustomThumbnail) {
                                    menuState.show(
                                        {
                                            CustomThumbnailMenu(
                                                onEdit = {
                                                    pickLauncher.launch(
                                                        PickVisualMediaRequest(
                                                            mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly,
                                                        ),
                                                    )
                                                },
                                                onRemove = {
                                                    when {
                                                        playlist.playlist.browseId == null -> {
                                                            overrideThumbnail.value = null
                                                            database.query {
                                                                update(playlist.playlist.copy(thumbnailUrl = null))
                                                            }
                                                        }

                                                        else -> {
                                                            scope.launch(Dispatchers.IO) {
                                                                YouTube.removeThumbnailPlaylist(playlist.playlist.browseId).onSuccess { newThumbnailUrl ->
                                                                    overrideThumbnail.value = newThumbnailUrl
                                                                    database.query {
                                                                        update(playlist.playlist.copy(thumbnailUrl = newThumbnailUrl))
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    isCustomThumbnail = false
                                                },
                                                onDismiss = menuState::dismiss,
                                            )
                                        },
                                    )
                                } else {
                                    showEditNoteDialog = true
                                }
                            },
                        )
                    }
                }
            }
        }

        // Playlist Name
        Text(
            text = playlist.playlist.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Metadata - Song Count • Duration
        val songCount =
            if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
                playlist.playlist.remoteSongCount
            } else {
                playlist.songCount
            }
        val nSongs = pluralStringResource(R.plurals.n_song, songCount, songCount)
        val durationText = if (playlistLength > 0) makeTimeString(playlistLength * 1000L) else null
        val metadataString = buildString {
            // Honest version of the mock's "Updated Daily" — our Blends update live.
            if (playlist.playlist.isCollaborative) {
                append(stringResource(R.string.blend_updated_live))
                append(" • ")
            }
            append(nSongs)
            if (durationText != null) {
                append(" ")
                append(durationText)
            }
        }
        Text(
            text = metadataString,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )

        if (playlist.playlist.isCollaborative) {
            // "Curated for A, B & C" — real member names, no fake taste claims.
            if (blendMembers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(
                        R.string.blend_curated_for,
                        blendMembers.take(3).joinToString(", ") { it.username.ifBlank { it.userId.take(8) } },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable {
                        scope.launch {
                            snackbarHostState.showSnackbar("Share this Blend via Invite to Blend in menu")
                        }
                    },
            ) {
                Icon(painter = painterResource(R.drawable.add), contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("Blend • Collaborative", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            // Member avatars strip — fed by the screen-level poll (see LocalPlaylistScreen),
            // so the strip, the count, and track attribution all share one member list.
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((-8).dp),
            ) {
                val list = blendMembers
                if (list.isEmpty()) {
                    // Placeholder until first fetch succeeds — shows 1 avatar + Invite
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painter = painterResource(R.drawable.person), contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                } else {
                    list.take(5).forEach { member ->
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .clickable {
                                    navController.navigate("user/${member.userId}?playlistId=${playlist.playlist.id}")
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (member.avatarUrl != null) {
                                coil3.compose.AsyncImage(
                                    model = member.avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Text(
                                    member.username.ifBlank { "?" }.take(1).uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    if (list.size > 5) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) { Text("+${list.size - 5}", style = MaterialTheme.typography.labelSmall) }
                    }
                    // Member count — owner included, so "nf home" reads 2 members.
                    Text(
                        stringResource(R.string.blend_members, list.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.material3.AssistChip(
                    // Opens the full Invite screen (members, capacity, link, share).
                    onClick = { navController.navigate("blend_invite/${playlist.playlist.id}") },
                    label = { Text("Invite") },
                    leadingIcon = { Icon(painter = painterResource(R.drawable.add), contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
            // Contributions bar — real per-member track shares (no genre labels:
            // we don't store genre anywhere). Only when counts are known.
            val contributionTotal = songs.size
            if (blendMembers.isNotEmpty() && contributionTotal > 0 && blendMemberTrackCounts.isNotEmpty()) {
                val barColors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.tertiary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.blend_contributions),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                ) {
                    blendMembers.forEachIndexed { index, member ->
                        val fraction = (blendMemberTrackCounts[member.userId] ?: 0).toFloat() / contributionTotal
                        if (fraction > 0f) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .weight(fraction)
                                    .fillMaxHeight()
                                    .background(barColors[index % barColors.size]),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                blendMembers.forEachIndexed { index, member ->
                    val count = blendMemberTrackCounts[member.userId] ?: 0
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(barColors[index % barColors.size]),
                        )
                        Text(
                            "${member.username.ifBlank { member.userId.take(8) }} ($count)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        val onlineAuthor = onlinePlaylist?.author
        if (onlineAuthor != null) {
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier.combinedClickable(
                        onClick = {
                            if (onlineAuthor.id != null) {
                                navController.navigate("artist/${onlineAuthor.id}")
                            }
                        },
                    ),
            ) {
                if (onlinePlaylist.authorAvatarUrl != null) {
                    AsyncImage(
                        model = onlinePlaylist.authorAvatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape),
                    )
                }
                Text(
                    text = onlineAuthor.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        val description = onlinePlaylist?.description
        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))

            ExpandableText(
                text = description,
                modifier = Modifier.padding(horizontal = 32.dp),
                collapsedMaxLines = 3,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons Row
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Shuffle Button - Smaller secondary button
            Surface(
                onClick = {
                    playerConnection.playQueue(
                        ListQueue(
                            title = playlist.playlist.name,
                            items = songs.shuffled().map { it.song.toMediaItem() },
                            sourceType = "playlist",
                            sourceId = playlist.playlist.id,
                        ),
                    )
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.shuffle),
                        contentDescription = stringResource(R.string.shuffle),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Play Button - Larger primary circular button
            Surface(
                onClick = {
                    playerConnection.playQueue(
                        ListQueue(
                            title = playlist.playlist.name,
                            items = songs.map { it.song.toMediaItem() },
                            sourceType = "playlist",
                            sourceId = playlist.playlist.id,
                        ),
                    )
                },
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = stringResource(R.string.play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            // Menu Button - Smaller secondary button
            Surface(
                onClick = {
                    menuState.show {
                        LocalPlaylistMenu(
                            playlist = playlist,
                            songs = songs,
                            context = context,
                            downloadState = downloadState,
                            coroutineScope = scope,
                            onEdit = onShowEditDialog,
                            onSync = {
                                scope.launch(Dispatchers.IO) {
                                    syncUtils.syncPlaylistSuspend(
                                        playlist.playlist.browseId!!,
                                        playlist.id,
                                    )
                                    withContext(Dispatchers.Main) {
                                        snackbarHostState.showSnackbar(playlistSyncedStr)
                                    }
                                }
                            },
                            onDelete = onshowDeletePlaylistDialog,
                            onDownload = {
                                when (downloadState) {
                                    Download.STATE_COMPLETED -> {
                                        onShowRemoveDownloadDialog()
                                    }

                                    Download.STATE_DOWNLOADING -> {
                                        songs.forEach { song ->
                                            DownloadService.sendRemoveDownload(
                                                context,
                                                ExoDownloadService::class.java,
                                                song.song.id,
                                                false,
                                            )
                                        }
                                    }

                                    else -> {
                                        songs.forEach { song ->
                                            downloadUtil.download(song.song)
                                        }
                                    }
                                }
                            },
                            onQueue = {
                                playerConnection.addToQueue(
                                    items = songs.map { it.song.toMediaItem() },
                                )
                            },
                            onDismiss = { menuState.dismiss() },
                        )
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataChip(
    icon: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

fun uriToByteArray(
    context: Context,
    uri: Uri,
): ByteArray? =
    try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
        if (e is SecurityException || e is java.io.IOException) {
            null
        } else {
            throw e
        }
    }

/**
 * Blend track-row card (mock tracklist style). Non-Blends render bare content
 * so regular playlists keep their exact current look.
 */
@Composable
private fun BlendRowCard(
    isBlend: Boolean,
    content: @Composable () -> Unit,
) {
    if (!isBlend) {
        content()
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        content()
    }
}
