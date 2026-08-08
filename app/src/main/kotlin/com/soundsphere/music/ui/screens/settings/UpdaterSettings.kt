/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.navigation.NavController
import com.soundsphere.music.BuildConfig
import com.soundsphere.music.LocalPlayerAwareWindowInsets
import com.soundsphere.music.R
import com.soundsphere.music.constants.CheckForUpdatesKey
import com.soundsphere.music.constants.UpdateNotificationsEnabledKey
import com.soundsphere.music.ui.component.IconButton
import com.soundsphere.music.ui.component.Material3SettingsGroup
import com.soundsphere.music.ui.component.Material3SettingsItem
import com.soundsphere.music.ui.utils.backToMain
import com.soundsphere.music.ui.component.UpdateChangelogSheet
import com.soundsphere.music.ui.component.UpdateSheetMode
import com.soundsphere.music.utils.AppUpdateDownloadJob
import com.soundsphere.music.utils.AppUpdateDownloader
import com.soundsphere.music.utils.Updater
import com.soundsphere.music.utils.installUpdateApk
import com.soundsphere.music.utils.rememberPreference
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdaterScreen(
    navController: NavController
) {
    val (checkForUpdates, onCheckForUpdatesChange) = rememberPreference(CheckForUpdatesKey, true)
    val (updateNotifications, onUpdateNotificationsChange) = rememberPreference(UpdateNotificationsEnabledKey, true)

    val context = LocalContext.current
    var isChecking by remember { mutableStateOf(false) }
    var updateAvailable by remember { mutableStateOf(false) }
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var showChangelogSheet by remember { mutableStateOf(false) }
    var checkError by remember { mutableStateOf<String?>(null) }
    var downloadUrl by remember { mutableStateOf<String?>(null) }
    val failedToCheckUpdatesTemplate = stringResource(R.string.failed_to_check_updates)

    val coroutineScope = rememberCoroutineScope()

    // Mirrors the download state surfaced by the update notification, so the
    // in-app UI and the notification always report the same progress.
    val updateWorkInfos by
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(AppUpdateDownloadJob.WORK_NAME)
            .collectAsState(initial = emptyList())
    val activeDownload = updateWorkInfos.firstOrNull {
        it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
    }
    // A completed download is only installable if the APK is still on disk and
    // matches the release currently offered. Otherwise the stale SUCCEEDED work
    // (e.g. cache file evicted, or a newer release dropped) would take over the
    // "Install now" branch and leave the "Download update" branch unreachable.
    val downloadedRelease = updateWorkInfos.firstOrNull {
        it.state == WorkInfo.State.SUCCEEDED &&
            it.outputData.getString(AppUpdateDownloadJob.KEY_VERSION_NAME) == latestVersion &&
            it.outputData
                .getString(AppUpdateDownloadJob.KEY_OUTPUT_FILE_PATH)
                ?.let(::File)
                ?.exists() == true
    }

    fun performManualCheck() {
        coroutineScope.launch {
            isChecking = true
            checkError = null
            withContext(Dispatchers.IO) {
                Updater
                    .checkForUpdate(forceRefresh = true)
                    .onSuccess { (releaseInfo, hasUpdate) ->
                        if (releaseInfo != null) {
                            latestVersion = releaseInfo.versionName
                            updateAvailable = hasUpdate
                            downloadUrl = Updater.getDownloadUrlForCurrentVariant(releaseInfo)
                        }
                    }.onFailure {
                        checkError = String.format(failedToCheckUpdatesTemplate, it.message ?: "Unknown error")
                    }
            }
            isChecking = false
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top,
                ),
            ),
        )

        Spacer(Modifier.height(4.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.current_version),
            items =
                listOf(
                    Material3SettingsItem(
                        title = {
                            Text(stringResource(R.string.version_format, BuildConfig.VERSION_NAME))
                        },
                        description = {
                            val arch = BuildConfig.ARCHITECTURE
                            val variant = if (BuildConfig.CAST_AVAILABLE) "GMS" else "FOSS"
                            Text("$arch - $variant")
                        },
                    ),
                ),
        )

        Spacer(Modifier.height(16.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.update_settings),
            items =
                buildList {
                    add(
                        Material3SettingsItem(
                            title = { Text(stringResource(R.string.check_for_updates)) },
                            icon = painterResource(R.drawable.update),
                            trailingContent = {
                                Switch(
                                    checked = checkForUpdates,
                                    onCheckedChange = onCheckForUpdatesChange,
                                )
                            },
                            onClick = { onCheckForUpdatesChange(!checkForUpdates) },
                        ),
                    )

                    if (checkForUpdates) {
                        add(
                            Material3SettingsItem(
                                title = { Text(stringResource(R.string.update_notifications)) },
                                icon = painterResource(R.drawable.notification),
                                trailingContent = {
                                    Switch(
                                        checked = updateNotifications,
                                        onCheckedChange = onUpdateNotificationsChange,
                                    )
                                },
                                onClick = { onUpdateNotificationsChange(!updateNotifications) },
                            ),
                        )
                    }
                },
        )

        Spacer(Modifier.height(16.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.check_for_updates_title),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.refresh),
                        title = {
                            if (isChecking) {
                                Text(stringResource(R.string.checking_for_updates))
                            } else if (latestVersion != null) {
                                Text(stringResource(R.string.latest_version_format, latestVersion!!))
                            } else {
                                Text(stringResource(R.string.check_for_updates_button))
                            }
                        },
                        trailingContent = {
                            if (isChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 16.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        },
                        onClick = { if (!isChecking) performManualCheck() },
                    ),
                ),
        )

        checkError?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (latestVersion != null && !updateAvailable) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.update_up_to_date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (updateAvailable && latestVersion != null) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showChangelogSheet = true },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
            ) {
                Text(stringResource(R.string.view_changelog))
            }

            when {
                activeDownload != null -> {
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text =
                                    stringResource(
                                        R.string.update_download_progress,
                                        activeDownload.progress.getInt(AppUpdateDownloadJob.KEY_PROGRESS, 0),
                                    ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = {
                                activeDownload.progress.getInt(AppUpdateDownloadJob.KEY_PROGRESS, 0) / 100f
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                downloadedRelease != null -> {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            downloadedRelease.outputData
                                .getString(AppUpdateDownloadJob.KEY_OUTPUT_FILE_PATH)
                                ?.let { installUpdateApk(context, it) }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                    ) {
                        Text(stringResource(R.string.install_now))
                    }
                }

                downloadUrl != null -> {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            AppUpdateDownloader.enqueue(context, downloadUrl!!, latestVersion.orEmpty())
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                    ) {
                        Text(stringResource(R.string.download_update))
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    if (showChangelogSheet) {
        UpdateChangelogSheet(
            mode = UpdateSheetMode.AVAILABLE,
            release = Updater.getCachedLatestRelease(),
            onDownload = {
                downloadUrl?.let { AppUpdateDownloader.enqueue(context, it, latestVersion.orEmpty()) }
                showChangelogSheet = false
            },
            onInstall = {},
            onDismiss = { showChangelogSheet = false },
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.updater)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}
