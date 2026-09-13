/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.component

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.soundsphere.music.LocalDatabase
import com.soundsphere.music.LocalSyncRepository
import com.soundsphere.music.LocalSyncUtils
import com.soundsphere.music.R
import com.soundsphere.music.constants.InnerTubeCookieKey
import com.soundsphere.music.db.entities.PlaylistEntity
import com.soundsphere.music.extensions.isSyncEnabled
import com.soundsphere.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    initialTextFieldValue: String? = null,
    allowSyncing: Boolean = true,
    onPlaylistCreated: ((String) -> Unit)? = null,
) {
    val database = LocalDatabase.current
    val syncRepository = LocalSyncRepository.current
    val syncUtils = LocalSyncUtils.current
    val coroutineScope = rememberCoroutineScope()
    var syncedPlaylist by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isSignedIn = innerTubeCookie.isNotEmpty()

    val notLoggedInYoutubeStr = stringResource(R.string.not_logged_in_youtube)
    val syncDisabledStr = stringResource(R.string.sync_disabled)
    val playlistCreatedLocallyStr = stringResource(R.string.playlist_created_locally)

    var showSyncCapDialog by remember { mutableStateOf(false) }
    var pendingPlaylistName by remember { mutableStateOf<String?>(null) }

    if (showSyncCapDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSyncCapDialog = false },
            title = { androidx.compose.material3.Text(stringResource(R.string.playlist_sync_limit_title)) },
            text = { androidx.compose.material3.Text(stringResource(R.string.playlist_sync_limit_message)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showSyncCapDialog = false
                    val name = pendingPlaylistName ?: return@TextButton
                    syncUtils.createPlaylist(
                        playlist = PlaylistEntity(name = name, bookmarkedAt = LocalDateTime.now(), isEditable = true),
                        syncWithYouTube = syncedPlaylist,
                    ) { playlistId, remoteCreated ->
                        if (syncedPlaylist && !remoteCreated) {
                            Toast.makeText(context, playlistCreatedLocallyStr, Toast.LENGTH_LONG).show()
                        }
                        coroutineScope.launch(Dispatchers.IO) {
                            database.playlist(playlistId).first()?.playlist?.let { syncRepository.playlistCreated(it) }
                        }
                        onPlaylistCreated?.invoke(playlistId)
                    }
                    onDismiss()
                }) { androidx.compose.material3.Text(stringResource(R.string.playlist_sync_limit_keep_local)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showSyncCapDialog = false }) {
                    androidx.compose.material3.Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    TextFieldDialog(
        icon = { Icon(painter = painterResource(R.drawable.add), contentDescription = null) },
        title = { Text(text = stringResource(R.string.create_playlist)) },
        initialTextFieldValue = TextFieldValue(initialTextFieldValue ?: ""),
        onDismiss = onDismiss,
        onDone = { playlistName ->
            // Soundsphere sync cap — 20/account. If full, prompt before creating.
            if (syncRepository.isLoggedIn && !syncRepository.canSyncNewPlaylist()) {
                pendingPlaylistName = playlistName
                showSyncCapDialog = true
                return@TextFieldDialog
            }
            syncUtils.createPlaylist(
                playlist = PlaylistEntity(
                    name = playlistName,
                    bookmarkedAt = LocalDateTime.now(),
                    isEditable = true,
                ),
                syncWithYouTube = syncedPlaylist,
            ) { playlistId, remoteCreated ->
                if (syncedPlaylist && !remoteCreated) {
                    Toast.makeText(context, playlistCreatedLocallyStr, Toast.LENGTH_LONG).show()
                }
                coroutineScope.launch(Dispatchers.IO) {
                    database.playlist(playlistId).first()?.playlist?.let { syncRepository.playlistCreated(it) }
                }
                onPlaylistCreated?.invoke(playlistId)
            }
        },
        extraContent = {
            if (allowSyncing) {
                Row(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 40.dp),
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.sync_playlist),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.allows_for_sync_witch_youtube),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(0.7f),
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Switch(
                            checked = syncedPlaylist,
                            onCheckedChange = {
                                coroutineScope.launch {
                                    val isYtmSyncEnabled = withContext(Dispatchers.IO) { context.isSyncEnabled() }
                                    if (!isSignedIn && !syncedPlaylist) {
                                        Toast
                                            .makeText(
                                                context,
                                                notLoggedInYoutubeStr,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    } else if (!isYtmSyncEnabled) {
                                        Toast
                                            .makeText(
                                                context,
                                                syncDisabledStr,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    } else {
                                        syncedPlaylist = !syncedPlaylist
                                    }
                                }
                            },
                        )
                    }
                }
            }
        },
    )
}
