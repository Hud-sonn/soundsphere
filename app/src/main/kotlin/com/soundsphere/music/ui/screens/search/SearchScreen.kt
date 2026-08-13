/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens.search

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import com.soundsphere.music.LocalDatabase
import com.soundsphere.music.LocalIsPlayerExpanded
import com.soundsphere.music.LocalNavController
import com.soundsphere.music.LocalPlayerAwareWindowInsets
import com.soundsphere.music.LocalPlayerConnection
import com.soundsphere.music.LocalSyncRepository
import com.soundsphere.innertube.models.WatchEndpoint
import com.soundsphere.innertube.utils.YouTubeUrlParser
import com.soundsphere.music.R
import com.soundsphere.music.constants.AiPlaylistConsentKey
import com.soundsphere.music.constants.PauseSearchHistoryKey
import com.soundsphere.music.constants.SearchSource
import com.soundsphere.music.constants.SearchSourceKey
import com.soundsphere.music.db.entities.PlaylistEntity
import com.soundsphere.music.db.entities.PlaylistSongMap
import com.soundsphere.music.db.entities.SearchHistory
import com.soundsphere.music.db.entities.SongEntity
import com.soundsphere.music.playback.queues.YouTubeQueue
import com.soundsphere.music.ui.component.DefaultDialog
import com.soundsphere.music.ui.component.HideOnScrollFAB
import com.soundsphere.music.utils.SearchRoutes
import com.soundsphere.music.utils.rememberEnumPreference
import com.soundsphere.music.utils.rememberPreference
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    pureBlack: Boolean,
    savedStateHandle: SavedStateHandle,
) {
    val navController = LocalNavController.current
    val database = LocalDatabase.current
    val syncRepository = LocalSyncRepository.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val playerConnection = LocalPlayerConnection.current
    val isPlayerExpanded = LocalIsPlayerExpanded.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lazyListState = rememberLazyListState()
    var isHandlingScrollToTop by remember { mutableStateOf(false) }

    val scrollToTopCount by savedStateHandle.getStateFlow("scrollToTopCount", 0).collectAsStateWithLifecycle(initialValue = 0)

    var lastHandledCount by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        if (!isPlayerExpanded) {
            kotlinx.coroutines.delay(100)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (e: Exception) {
            }
        }
    }
    LaunchedEffect(scrollToTopCount) {
        if (scrollToTopCount > lastHandledCount) {
            lastHandledCount = scrollToTopCount
            isHandlingScrollToTop = true

            kotlinx.coroutines.delay(100)

            if (!isPlayerExpanded) {
                focusManager.clearFocus(force = true)
                kotlinx.coroutines.delay(50)
                try {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                } catch (e: Exception) {
                }
            }

            kotlinx.coroutines.delay(500)
            isHandlingScrollToTop = false
        }
    }

    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)
    var aiConsent by rememberPreference(AiPlaylistConsentKey, false)
    var showAiSearchOption by rememberSaveable { mutableStateOf(false) }
    var pendingAiQuery by rememberSaveable { mutableStateOf("") }
    var showAiConsentDialog by rememberSaveable { mutableStateOf(false) }
    var aiGenerating by rememberSaveable { mutableStateOf(false) }

    fun handleSearch(searchQuery: String) {
        if (searchQuery.isEmpty()) {
            return
        }

        focusManager.clearFocus()

        when (val parsedUrl = YouTubeUrlParser.parse(searchQuery)) {
            is YouTubeUrlParser.ParsedUrl.Video -> {
                playerConnection?.playQueue(
                    YouTubeQueue(
                        WatchEndpoint(videoId = parsedUrl.id),
                    ),
                )
            }

            is YouTubeUrlParser.ParsedUrl.Playlist -> {
                navController.navigate("online_playlist/${parsedUrl.id}")
            }

            is YouTubeUrlParser.ParsedUrl.Album -> {
                navController.navigate("album/MPREb_${parsedUrl.id}")
            }

            is YouTubeUrlParser.ParsedUrl.Artist -> {
                navController.navigate("artist/${parsedUrl.id}")
            }

            null -> {
                if (searchSource == SearchSource.ONLINE) {
                    pendingAiQuery = searchQuery
                    showAiSearchOption = true
                } else {
                    navController.navigate(SearchRoutes.resultRoute(searchQuery))
                }
            }
        }

        if (!pauseSearchHistory) {
            coroutineScope.launch(Dispatchers.IO) {
                database.query {
                    insert(SearchHistory(query = searchQuery))
                }
            }
        }
    }

    val onSearch: (String) -> Unit = { searchQuery -> handleSearch(searchQuery) }

    val onSearchFromSuggestion: (String) -> Unit = { searchQuery -> handleSearch(searchQuery) }

    val aiNotEnabledStr = stringResource(R.string.ai_playlist_not_enabled)
    val aiFailedStr = stringResource(R.string.ai_playlist_failed)
    val aiNoTracksStr = stringResource(R.string.ai_playlist_no_tracks)

    fun generateAiPlaylistFromSearch() {
        val prompt = pendingAiQuery.trim()
        if (prompt.isBlank()) return
        aiGenerating = true
        coroutineScope.launch(Dispatchers.IO) {
            val result = syncRepository.generateAiPlaylist(prompt, count = 30)
            withContext(Dispatchers.Main) {
                aiGenerating = false
                result.onSuccess { tracks ->
                    if (tracks.isEmpty()) {
                        Toast.makeText(context, aiNoTracksStr, Toast.LENGTH_SHORT).show()
                        return@onSuccess
                    }
                    val playlistName =
                        if (prompt.length > 60) prompt.take(57).trimEnd() + "…" else prompt
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
                                insert(
                                    SongEntity(
                                        id = track.id,
                                        title = track.title,
                                        duration = track.duration,
                                        thumbnailUrl = track.artworkUrl,
                                        albumName = track.album,
                                        year = track.year,
                                        inLibrary = LocalDateTime.now(),
                                    ),
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
                            else -> aiFailedStr
                        }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun startAiPlaylistFromSearch() {
        showAiSearchOption = false
        if (aiConsent) {
            generateAiPlaylistFromSearch()
        } else {
            showAiConsentDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                            textStyle =
                                TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (query.text.isEmpty()) {
                                    Text(
                                        text =
                                            stringResource(
                                                when (searchSource) {
                                                    SearchSource.LOCAL -> R.string.search_library
                                                    SearchSource.ONLINE -> R.string.search_yt_music
                                                },
                                            ),
                                        style =
                                            TextStyle(
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                fontSize = 16.sp,
                                            ),
                                    )
                                }
                                innerTextField()
                            },
                            keyboardOptions =
                                KeyboardOptions(
                                    imeAction = ImeAction.Search,
                                ),
                            keyboardActions =
                                KeyboardActions(
                                    onSearch = { onSearch(query.text) },
                                ),
                        )

                        Row {
                            if (query.text.isNotEmpty()) {
                                IconButton(onClick = { query = TextFieldValue("") }) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    searchSource =
                                        if (searchSource == SearchSource.ONLINE) {
                                            SearchSource.LOCAL
                                        } else {
                                            SearchSource.ONLINE
                                        }
                                },
                            ) {
                                Icon(
                                    painter =
                                        painterResource(
                                            when (searchSource) {
                                                SearchSource.LOCAL -> R.drawable.library_music
                                                SearchSource.ONLINE -> R.drawable.language
                                            },
                                        ),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.dismiss),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
        },
        containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .padding(top = paddingValues.calculateTopPadding())
                    .fillMaxSize(),
        ) {
            when (searchSource) {
                SearchSource.LOCAL -> {
                    LocalSearchScreen(
                        query = query.text,
                        onDismiss = { navController.navigateUp() },
                        pureBlack = pureBlack,
                    )
                }

                SearchSource.ONLINE -> {
                    OnlineSearchScreen(
                        query = query.text,
                        onQueryChange = { query = it },
                        onSearch = onSearchFromSuggestion,
                        onDismiss = { /* Don't dismiss when searching from suggestions */ },
                        pureBlack = pureBlack,
                    )
                }
            }

            HideOnScrollFAB(
                lazyListState = lazyListState,
                icon = R.drawable.mic,
                onClick = { navController.navigate("recognition") },
            )
        }
    }

    if (showAiSearchOption) {
        DefaultDialog(
            onDismiss = {
                showAiSearchOption = false
                navController.navigate(SearchRoutes.resultRoute(pendingAiQuery))
            },
            icon = { Icon(painterResource(R.drawable.ai), contentDescription = null) },
            title = { Text(text = stringResource(R.string.ai_playlist_search_title)) },
            buttons = {
                TextButton(
                    onClick = {
                        showAiSearchOption = false
                        navController.navigate(SearchRoutes.resultRoute(pendingAiQuery))
                    },
                ) {
                    Text(text = stringResource(R.string.ai_playlist_search_just_search))
                }
                TextButton(onClick = { startAiPlaylistFromSearch() }) {
                    Text(text = stringResource(R.string.ai_playlist_search_action))
                }
            },
        ) {
            Text(
                text =
                    stringResource(
                        R.string.ai_playlist_search_text,
                        if (pendingAiQuery.length > 40) pendingAiQuery.take(37).trimEnd() + "…" else pendingAiQuery,
                    ),
                style = MaterialTheme.typography.bodyMedium,
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
                        generateAiPlaylistFromSearch()
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

    // Handle lifecycle events to manage keyboard visibility
    DisposableEffect(lifecycleOwner, isPlayerExpanded) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        if (isHandlingScrollToTop) return@LifecycleEventObserver
                        // Always hide keyboard when resuming if player is expanded
                        if (isPlayerExpanded) {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    }

                    Lifecycle.Event.ON_PAUSE -> {
                        if (isHandlingScrollToTop) return@LifecycleEventObserver
                        // Clear focus when pausing to prevent keyboard from showing on resume
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }

                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Initial check - hide keyboard if player is expanded
        if (isPlayerExpanded) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}