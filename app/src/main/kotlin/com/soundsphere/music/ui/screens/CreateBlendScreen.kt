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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.soundsphere.music.LocalDatabase
import com.soundsphere.music.LocalPlayerAwareWindowInsets
import com.soundsphere.music.LocalSyncRepository
import com.soundsphere.music.R
import com.soundsphere.music.db.entities.PlaylistEntity
import com.soundsphere.music.ui.component.BlendIcon
import com.soundsphere.music.ui.component.IconButton
import com.soundsphere.music.ui.utils.backToMain
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Create-Blend flow as a full screen (converted from the web design mock).
 * Stripped from the mock before conversion — see FEASIBILITY_PREMIUM_FEATURES
 * Part N for the strip list: no Duo/Group/Mood pills (no backend concept),
 * no public/followers toggle (no followers system), no daily-refresh or taste
 * claims (don't exist), no web header/nav (app owns chrome).
 *
 * Flow kept identical to the old dialog path: name → history-or-fresh choice
 * → local insert + sync push → navigate into the new Blend.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBlendScreen(navController: NavController) {
    val database = LocalDatabase.current
    val syncRepository = LocalSyncRepository.current
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf("") }
    var pendingName by rememberSaveable { mutableStateOf<String?>(null) }

    fun createBlend(blendName: String, fillFromHistory: Boolean) {
        scope.launch(Dispatchers.IO) {
            val entity = PlaylistEntity(
                name = blendName,
                bookmarkedAt = LocalDateTime.now(),
                isCollaborative = true,
            )
            // Direct DAO calls (not fire-and-forget query{}) so the tracks below
            // land BEFORE playlistCreated pushes — ordering matters.
            database.insert(entity)
            if (fillFromHistory) {
                // Top 25 by all-time play time. addedBy stays null locally; the
                // server stamps the owner on push and the pull backfill heals the
                // local rows on next sync (see backfillTrackAddedBy).
                database.topSongsByPlayTime(25).forEachIndexed { index, song ->
                    try {
                        database.insert(
                            com.soundsphere.music.db.entities.PlaylistSongMap(
                                playlistId = entity.id,
                                songId = song.id,
                                position = index,
                                addedByUserId = null,
                            ),
                        )
                    } catch (e: Exception) { /* keep what landed */ }
                }
            }
            syncRepository.playlistCreated(entity)
            withContext(Dispatchers.Main) {
                navController.navigate("local_playlist/${entity.id}")
            }
        }
    }

    pendingName?.let { blendName ->
        AlertDialog(
            onDismissRequest = { pendingName = null },
            title = { Text(blendName) },
            text = { Text(stringResource(R.string.blend_start_choice)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingName = null
                    createBlend(blendName, fillFromHistory = true)
                }) { Text(stringResource(R.string.blend_fill_history)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingName = null
                    createBlend(blendName, fillFromHistory = false)
                }) { Text(stringResource(R.string.blend_start_fresh)) }
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.blend_create)) },
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
            modifier = Modifier.fillMaxSize(),
        ) {
            // Hero: ambient rings + reusable Blend mark + title + subhead.
            item(key = "blend_hero") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)),
                        )
                        Box(
                            modifier = Modifier
                                .size(170.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f)),
                        )
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            BlendIcon(
                                modifier = Modifier.size(80.dp),
                                contentDescription = stringResource(R.string.blend),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.blend_hero_title),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.blend_hero_sub),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 48.dp),
                    )
                }
            }
            // How it works: only the two honest steps.
            item(key = "blend_how") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(
                            stringResource(R.string.blend_how_it_works),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        HowStep(
                            number = "1",
                            title = stringResource(R.string.blend_step1_title),
                            desc = stringResource(R.string.blend_step1_desc),
                        )
                        HowStep(
                            number = "2",
                            title = stringResource(R.string.blend_step2_title),
                            desc = stringResource(R.string.blend_step2_desc),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            // Name input.
            item(key = "blend_name") {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        stringResource(R.string.blend_name_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text(stringResource(R.string.blend_name_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
            // CTA.
            item(key = "blend_cta") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    Button(
                        onClick = { if (name.isNotBlank()) pendingName = name.trim() },
                        enabled = name.isNotBlank(),
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.blend_create_invite),
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.blend_footer),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun HowStep(
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
        Spacer(Modifier.width(12.dp))
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
