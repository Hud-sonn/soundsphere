/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.soundsphere.music.utils.joinByBullet
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.soundsphere.music.LocalPlayerAwareWindowInsets
import com.soundsphere.music.LocalPlayerConnection
import com.soundsphere.music.R
import com.soundsphere.music.api.SharedPlaylist
import com.soundsphere.music.api.SyncTrack
import com.soundsphere.music.extensions.toMediaItem
import com.soundsphere.music.models.MediaMetadata
import com.soundsphere.music.playback.queues.ListQueue
import com.soundsphere.music.ui.utils.backToMain
import com.soundsphere.music.ui.utils.resize
import com.soundsphere.music.viewmodels.SharedPlaylistViewModel
import kotlinx.coroutines.launch

/**
 * Recipient view for a playlist shared through a Soundsphere share link.
 * Shows the branded preview (cover, name, owner, track count) and lets the
 * recipient play any track or the whole playlist — no account relationship
 * with the owner is needed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SharedPlaylistScreen(
    navController: NavController,
    viewModel: SharedPlaylistViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isJoining by viewModel.isJoining.collectAsStateWithLifecycle()
    val joinError by viewModel.joinError.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            if (playlist == null) {
                if (isLoading) {
                    item(key = "loading_placeholder") {
                        Box(
                            modifier =
                                Modifier
                                    .fillParentMaxSize()
                                    .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            ContainedLoadingIndicator()
                        }
                    }
                } else if (error != null) {
                    item(key = "error_placeholder") {
                        Column(
                            modifier =
                                Modifier
                                    .fillParentMaxSize()
                                    .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.shared_playlist_load_error),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = { viewModel.fetch() }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            } else {
                playlist?.let { shared ->
                    item(key = "shared_playlist_header") {
                        SharedPlaylistHeader(
                            playlist = shared,
                            isJoining = isJoining,
                            joinError = joinError,
                            onJoinBlend = {
                                // Single navigation path: the ViewModel only calls this
                                // after the join AND all local writes completed.
                                viewModel.joinBlend { joinedPlaylistId ->
                                    navController.navigate("local_playlist/$joinedPlaylistId")
                                }
                            },
                            onPlayAll = {
                                coroutineScope.launch {
                                    val items = shared.tracks.map { it.track.toMediaMetadata().toMediaItem() }
                                    if (items.isNotEmpty()) {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = shared.name,
                                                items = items,
                                            ),
                                        )
                                    }
                                }
                            },
                        )
                    }

                    itemsIndexed(shared.tracks) { index, entry ->
                        val track = entry.track
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch {
                                            val items = shared.tracks.map { it.track.toMediaMetadata().toMediaItem() }
                                            if (items.isNotEmpty()) {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = shared.name,
                                                        items = items,
                                                        startIndex = index,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model =
                                    ImageRequest
                                        .Builder(LocalContext.current)
                                        .data(track.artworkUrl?.resize(144, 144))
                                        .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                            Spacer(modifier = Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (track.artist.isNotBlank()) {
                                    Text(
                                        text = track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.shared_playlist_screen_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
        )
    }
}

@Composable
private fun SharedPlaylistHeader(
    playlist: SharedPlaylist,
    onPlayAll: () -> Unit,
    onJoinBlend: (() -> Unit)? = null,
    isJoining: Boolean = false,
    joinError: String? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (playlist.isCollaborative) {
            // Inviter banner — owner identity comes from the public share response.
            if (playlist.owner.username.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (playlist.owner.avatarUrl != null) {
                            AsyncImage(
                                model = playlist.owner.avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        } else {
                            Text(
                                playlist.owner.username.take(1).uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.blend_invited_by, playlist.owner.username),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            // Emblem: cover art, else the reusable Blend mark (never blank).
            Surface(
                modifier = Modifier.size(200.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (playlist.coverUrl != null) {
                        AsyncImage(
                            model = ImageRequest
                                .Builder(LocalContext.current)
                                .data(playlist.coverUrl.resize(1080, 1080))
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        com.soundsphere.music.ui.component.BlendIcon(
                            modifier = Modifier.size(120.dp),
                            contentDescription = stringResource(R.string.blend),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        } else {
            Surface(
                modifier =
                    Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
            ) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalContext.current)
                            .data(playlist.coverUrl?.resize(1080, 1080))
                            .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        Text(
            text = playlist.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (playlist.isCollaborative) {
            // Real metrics: track count + member capacity (no vibe scores).
            Text(
                text = joinByBullet(
                    pluralStringResource(R.plurals.n_song, playlist.trackCount, playlist.trackCount),
                    stringResource(R.string.blend_capacity, playlist.memberCount),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Honest 2-step card — only claims that exist.
            androidx.compose.material3.Card(
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        stringResource(R.string.blend_how_it_works),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    BlendJoinStep(
                        number = "1",
                        title = stringResource(R.string.blend_join_step1_title),
                        desc = stringResource(R.string.blend_join_step1_desc),
                    )
                    BlendJoinStep(
                        number = "2",
                        title = stringResource(R.string.blend_join_step2_title),
                        desc = stringResource(R.string.blend_join_step2_desc),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Accept — guarded join path (black-screen fix lives in the ViewModel).
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Button(
                    onClick = { onJoinBlend?.invoke() },
                    enabled = !isJoining,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (isJoining) stringResource(R.string.joining) else stringResource(R.string.blend_accept_join),
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                if (joinError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(joinError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onPlayAll) { Text(stringResource(R.string.shared_playlist_play_all)) }
            }
        } else {
            val meta = buildString {
                if (playlist.owner.username.isNotBlank()) {
                    append(stringResource(R.string.shared_playlist_by_owner, playlist.owner.username))
                    append(" · ")
                }
                append(stringResource(R.string.shared_playlist_song_count, playlist.trackCount))
            }
            Text(
                text = meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                onClick = onPlayAll,
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = stringResource(R.string.shared_playlist_play_all),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BlendJoinStep(
    number: String,
    title: String,
    desc: String,
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun SyncTrack.toMediaMetadata() =
    MediaMetadata(
        id = id,
        title = title,
        artists =
            listOfNotNull(
                artist.takeIf { it.isNotBlank() }?.let {
                    MediaMetadata.Artist(id = null, name = it)
                },
            ),
        duration = duration,
        thumbnailUrl = artworkUrl,
        album =
            album?.takeIf { it.isNotBlank() }?.let {
                MediaMetadata.Album(id = "", title = it)
            },
    )