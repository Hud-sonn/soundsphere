/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soundsphere.music.R
import com.soundsphere.music.ui.screens.settings.MarkdownText
import com.soundsphere.music.utils.ReleaseInfo
import com.soundsphere.music.utils.Updater

/** What the sheet is being shown for: deciding whether to download, or re-checking before installing. */
enum class UpdateSheetMode {
    /** Update found but not downloaded yet — offers the "Download update" action. */
    AVAILABLE,

    /** Download finished — offers the "Install now" action. */
    READY_TO_INSTALL,
}

/**
 * Renders the release notes of an update as formatted markdown so the user
 * can decide whether to download it. Reused from the "update available"
 * notification, the "ready to install" notification and the settings screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateChangelogSheet(
    mode: UpdateSheetMode,
    release: ReleaseInfo?,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    var loadedRelease by remember(release) { mutableStateOf(release) }

    // The release info is cached in memory by Updater; if the process was
    // restarted (e.g. via a notification tap) the cache is empty, so fetch it.
    LaunchedEffect(release) {
        if (loadedRelease == null) {
            loadedRelease = Updater.getLatestRelease().getOrNull()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.whats_new),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            val releaseInfo = loadedRelease
            if (releaseInfo != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = releaseInfo.versionName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                MarkdownText(releaseInfo.description)
            } else {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.update_changelog_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    when (mode) {
                        UpdateSheetMode.AVAILABLE -> onDownload()
                        UpdateSheetMode.READY_TO_INSTALL -> onInstall()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (mode == UpdateSheetMode.AVAILABLE) {
                            R.string.download_update
                        } else {
                            R.string.install_now
                        },
                    ),
                )
            }
        }
    }
}
