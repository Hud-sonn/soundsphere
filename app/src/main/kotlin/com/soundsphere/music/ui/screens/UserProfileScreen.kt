/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.soundsphere.music.LocalDatabase
import com.soundsphere.music.LocalPlayerAwareWindowInsets
import com.soundsphere.music.LocalSyncRepository
import com.soundsphere.music.R
import com.soundsphere.music.api.SyncService
import com.soundsphere.music.ui.component.IconButton
import com.soundsphere.music.ui.utils.backToMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Public member profile, opened by tapping a Blend member avatar or an
 * "Added by" attribution row. Only reachable from a shared Blend, so every
 * viewer is already a co-member — no stranger directory.
 *
 * @param userId member id to display.
 * @param playlistId LOCAL playlist id of the Blend for context (role, added
 * count, avatar). Null shows a bare id fallback, never a crash.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    navController: NavController,
    userId: String,
    playlistId: String?,
) {
    val database = LocalDatabase.current
    val syncRepository = LocalSyncRepository.current

    var member by remember { mutableStateOf<SyncService.BlendCollaborator?>(null) }
    var addedCount by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(userId, playlistId) {
        if (playlistId == null) {
            loaded = true
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                member = syncRepository.getBlendCollaborators(playlistId).getOrNull()
                    ?.firstOrNull { it.userId == userId }
                addedCount = database.countTracksAddedBy(playlistId, userId)
            } catch (e: Exception) { /* keep fallbacks */ }
        }
        loaded = true
    }

    val displayName = member?.username?.ifBlank { null } ?: userId.take(8)
    val avatarUrl = member?.avatarUrl

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(displayName) },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "profile_header") {
                Spacer(Modifier.height(32.dp))
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            displayName.ifBlank { "?" }.take(1).uppercase(),
                            style = MaterialTheme.typography.displaySmall,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(displayName, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                if (member != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                stringResource(if (member!!.isOwner) R.string.blend_owner else R.string.blend_member),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        pluralStringResource(R.plurals.n_song, addedCount, addedCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    member!!.addedAt?.take(10)?.let { since ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            since,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (loaded) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        userId.take(8),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
