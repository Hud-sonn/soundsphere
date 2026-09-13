/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.soundsphere.music.LocalDatabase
import com.soundsphere.music.LocalPlayerAwareWindowInsets
import com.soundsphere.music.LocalSyncRepository
import com.soundsphere.music.R
import com.soundsphere.music.api.SyncService
import com.soundsphere.music.ui.component.BlendIcon
import com.soundsphere.music.ui.component.IconButton
import com.soundsphere.music.ui.utils.backToMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Invite-to-Blend screen (converted from the web mock, stripped per Part N rules).
 * Member management for people already in the Blend: live member list with
 * HOST badges, owner-or-self removal (server enforces), capacity bar, and the
 * share link with copy + system sheet.
 *
 * What the mock had that is NOT here: % match scores (no engine), pending
 * invites + Resend (joins are link-based, no invite records), username/email
 * search (no user directory), collaborator carousel (no social graph), Stories
 * + QR (needs SDK/lib — QR recorded deferred), expiry countdown (tokens don't
 * expire), "Generate" CTA (nothing to generate — Done only).
 *
 * @param playlistId LOCAL playlist id (server translation happens inside the repo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlendInviteScreen(
    navController: NavController,
    playlistId: String,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val syncRepository = LocalSyncRepository.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val playlist by database.playlist(playlistId).collectAsStateWithLifecycle(initialValue = null)
    var members by remember { mutableStateOf<List<SyncService.BlendCollaborator>>(emptyList()) }
    var shareLink by remember { mutableStateOf<String?>(null) }

    fun refreshMembers() {
        scope.launch(Dispatchers.IO) {
            try {
                syncRepository.getBlendCollaborators(playlistId).onSuccess { members = it }
            } catch (e: Exception) { /* keep last */ }
        }
    }

    LaunchedEffect(playlistId) {
        refreshMembers()
        withContext(Dispatchers.IO) {
            shareLink = syncRepository.getPlaylistShareToken(playlistId).getOrNull()
        }
    }

    suspend fun showSnack(message: String) = snackbarHostState.showSnackbar(message)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.blend_invite_title)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
        )
        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            // Header: badge + title + sub.
            item(key = "invite_header") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            BlendIcon(Modifier.size(24.dp))
                            Text(
                                playlist?.playlist?.name ?: stringResource(R.string.blend),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.blend_invite_title),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.blend_invite_sub),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            // Capacity bar — real counts, 10 cap.
            item(key = "invite_capacity") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(R.string.blend_capacity, members.size),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                stringResource(R.string.blend_slots_open, (10 - members.size).coerceAtLeast(0)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { members.size / 10f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            // Members.
            items(members, key = { it.userId }) { member ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (member.avatarUrl != null) {
                                AsyncImage(
                                    model = member.avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Text(
                                    member.username.ifBlank { "?" }.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    (if (member.isSelf) member.username + " • You" else member.username)
                                        .ifBlank { member.userId.take(8) },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (member.isOwner) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Text(
                                            stringResource(R.string.blend_owner),
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                        // Owner removes anyone; anyone leaves (self). Server enforces.
                        IconButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val result = syncRepository.removeBlendMember(playlistId, member.userId)
                                    withContext(Dispatchers.Main) {
                                        if (result.isSuccess) {
                                            if (member.isSelf) navController.navigateUp()
                                            else refreshMembers()
                                        } else {
                                            showSnack(result.exceptionOrNull()?.message ?: "Couldn't remove")
                                        }
                                    }
                                }
                            },
                            onLongClick = {},
                        ) {
                            Icon(
                                painterResource(if (member.isSelf) R.drawable.logout else R.drawable.delete),
                                contentDescription = stringResource(
                                    if (member.isSelf) R.string.blend_leave else R.string.blend_remove_member,
                                ),
                            )
                        }
                    }
                }
            }
            // Share link card.
            item(key = "invite_link") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        shareLink?.let { link ->
                            Text(
                                link,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("blend", link))
                                    scope.launch { showSnack(context.getString(R.string.link_copied)) }
                                }) {
                                    Text(stringResource(R.string.copy_link))
                                }
                                Button(onClick = {
                                    context.startActivity(
                                        Intent.createChooser(
                                            Intent().apply {
                                                action = Intent.ACTION_SEND
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, link)
                                            },
                                            null,
                                        ),
                                    )
                                }) {
                                    Text(stringResource(R.string.share))
                                }
                            }
                        } ?: Text(
                            stringResource(R.string.share_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Done.
            item(key = "invite_done") {
                Button(
                    onClick = navController::navigateUp,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.blend_done),
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
