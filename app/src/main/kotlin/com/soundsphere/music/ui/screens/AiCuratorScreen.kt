/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.soundsphere.music.LocalDatabase
import com.soundsphere.music.LocalPlayerAwareWindowInsets
import com.soundsphere.music.LocalSyncRepository
import com.soundsphere.music.R
import com.soundsphere.music.constants.AiPlaylistConsentKey
import com.soundsphere.music.db.entities.PlaylistEntity
import com.soundsphere.music.ui.component.DefaultDialog
import com.soundsphere.music.ui.component.IconButton
import com.soundsphere.music.ui.utils.backToMain
import com.soundsphere.music.utils.rememberPreference
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SeedChips = listOf(
    "Analog Tape Saturation",
    "Midnight Rain & Jazz",
    "Vintage Synth & Neo-Soul",
    "Acoustic Lo-Fi Folk",
)

private val SurprisePrompts = listOf(
    "Late-night rainy drive in Tokyo, 1980s Japanese ambient, warm Fender Rhodes",
    "Sun-faded cassette dub, low-end wobble, tape hiss, sunset highway",
    "Intimate folk, brushed drums, ribbon mic, firelight room tone",
)

/**
 * Full-screen AI curator (converted from the web mock, stripped per Part R).
 * Prompt + seed chips + Surprise Me → existing generateAiPlaylist path.
 * Stripped: sliders, era pills, BPM visual, lossless toggle, glow/blur, fonts CDN.
 * Old Library dialogs still exist for fallback; this screen is the primary flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCuratorScreen(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val syncRepository = LocalSyncRepository.current
    val scope = rememberCoroutineScope()

    var prompt by rememberSaveable { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var showConsent by rememberSaveable { mutableStateOf(false) }

    val (aiConsent, onAiConsentChange) = rememberPreference(AiPlaylistConsentKey, false)

    val aiNotEnabledStr = stringResource(R.string.ai_playlist_not_enabled)
    val aiFailedStr = stringResource(R.string.ai_playlist_failed)
    val aiLimitReachedStr = stringResource(R.string.ai_playlist_limit_reached)
    val aiNoTracksStr = stringResource(R.string.ai_playlist_no_tracks)

    suspend fun doGenerate(text: String) {
        if (text.isBlank() || generating) return
        generating = true
        val result = syncRepository.generateAiPlaylist(text.trim())
        withContext(Dispatchers.Main) {
            generating = false
            result.onSuccess { tracks ->
                if (tracks.isEmpty()) {
                    Toast.makeText(context, aiNoTracksStr, Toast.LENGTH_SHORT).show()
                    return@onSuccess
                }
                val name = text.trim().let { if (it.length > 60) it.take(57).trimEnd() + "…" else it }
                val entity = PlaylistEntity(name = name, bookmarkedAt = LocalDateTime.now(), isLocal = true)
                val existingIds = tracks.mapNotNull { database.songEntity(it.id)?.id }.toHashSet()
                database.query {
                    insert(entity)
                    tracks.forEachIndexed { index, track ->
                        if (track.id !in existingIds) {
                            try { insert(track.toSongEntity()) } catch (e: Exception) { /* keep */ }
                        }
                        try {
                            insert(
                                com.soundsphere.music.db.entities.PlaylistSongMap(
                                    playlistId = entity.id,
                                    songId = track.id,
                                    position = index,
                                ),
                            )
                        } catch (e: Exception) { /* keep */ }
                    }
                }
                syncRepository.playlistCreated(entity)
                navController.navigate("local_playlist/${entity.id}")
            }.onFailure { error ->
                val message = when {
                    error.message?.contains("not enabled", ignoreCase = true) == true -> aiNotEnabledStr
                    error.message?.contains("limit", ignoreCase = true) == true -> aiLimitReachedStr
                    else -> aiFailedStr
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showConsent) {
        DefaultDialog(
            onDismiss = { showConsent = false },
            title = { Text(stringResource(R.string.ai_playlist_consent_title)) },
            buttons = {
                TextButton(onClick = { showConsent = false }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        onAiConsentChange(true)
                        withContext(Dispatchers.Main) {
                            showConsent = false
                            doGenerate(prompt)
                        }
                    }
                }) { Text(stringResource(R.string.ai_playlist_agree)) }
            },
        ) { Text(stringResource(R.string.ai_playlist_consent_text)) }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.ai_curator_title)) },
            navigationIcon = {
                IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                    androidx.compose.material3.Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
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
            item(key = "curator_hero") {
                Column {
                    Text(
                        stringResource(R.string.ai_curator_hero),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        stringResource(R.string.ai_curator_sub),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "curator_prompt") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.ai_curator_prompt_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            placeholder = { Text(stringResource(R.string.ai_playlist_prompt_hint)) },
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                prompt = SurprisePrompts.random()
                            }) { Text(stringResource(R.string.ai_curator_surprise)) }
                            Spacer(Modifier.weight(1f))
                            Button(
                                onClick = {
                                    scope.launch(Dispatchers.IO) { doGenerate(prompt) }
                                },
                                enabled = prompt.isNotBlank() && !generating,
                            ) {
                                if (generating) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.ai_curator_synthesize))
                            }
                        }
                    }
                }
            }
            item(key = "curator_seeds") {
                Column {
                    Text(
                        stringResource(R.string.ai_curator_seeds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        SeedChips.forEach { chip ->
                            AssistChip(
                                onClick = { prompt = chip },
                                label = { Text(chip) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun com.soundsphere.music.api.SyncTrack.toSongEntity() =
    com.soundsphere.music.db.entities.SongEntity(
        id = id,
        title = title,
        duration = duration,
        thumbnailUrl = artworkUrl,
        albumName = album,
    )
