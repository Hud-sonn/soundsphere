/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.TextField
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.soundsphere.innertube.YouTube
import com.soundsphere.innertube.utils.parseCookieString
import com.soundsphere.music.api.CloudinaryUploader
import com.soundsphere.music.LocalPlayerConnection
import com.soundsphere.music.R
import com.soundsphere.music.constants.AccountChannelHandleKey
import com.soundsphere.music.constants.AccountEmailKey
import com.soundsphere.music.constants.AccountNameKey
import com.soundsphere.music.constants.DataSyncIdKey
import com.soundsphere.music.constants.InnerTubeCookieKey
import com.soundsphere.music.constants.SoundsphereAvatarUrlKey
import com.soundsphere.music.constants.SoundsphereEmailKey
import com.soundsphere.music.constants.SoundsphereUsernameKey
import com.soundsphere.music.constants.UseLoginForBrowse
import com.soundsphere.music.constants.VisitorDataKey
import com.soundsphere.music.constants.YtmSyncKey
import com.soundsphere.music.ui.component.Material3SettingsGroup
import com.soundsphere.music.ui.component.Material3SettingsItem
import com.soundsphere.music.ui.component.TextFieldDialog
import com.soundsphere.music.ui.theme.DefaultThemeColor
import com.soundsphere.music.utils.reportException
import com.soundsphere.music.utils.rememberPreference
import com.soundsphere.music.viewmodels.AccountSettingsViewModel
import com.soundsphere.music.viewmodels.AuthViewModel
import com.soundsphere.music.viewmodels.HomeViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.compose.ui.text.input.TextFieldValue
import com.soundsphere.music.ui.component.DefaultDialog
import com.soundsphere.music.ui.component.InfoLabel
import com.yalantis.ucrop.UCrop
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    navController: NavController,
) {
    val context = LocalContext.current

    val (accountNamePref, onAccountNameChange) = rememberPreference(AccountNameKey, "")
    val (accountEmail, onAccountEmailChange) = rememberPreference(AccountEmailKey, "")
    val (accountChannelHandle, onAccountChannelHandleChange) = rememberPreference(AccountChannelHandleKey, "")
    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val (visitorData, onVisitorDataChange) = rememberPreference(VisitorDataKey, "")
    val (dataSyncId, onDataSyncIdChange) = rememberPreference(DataSyncIdKey, "")

    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(UseLoginForBrowse, true)
    val (ytmSync, onYtmSyncChange) = rememberPreference(YtmSyncKey, true)

    val homeViewModel: HomeViewModel = hiltViewModel()
    val accountSettingsViewModel: AccountSettingsViewModel = hiltViewModel()
    val authViewModel: AuthViewModel = hiltViewModel()
    val accountName by homeViewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by homeViewModel.accountImageUrl.collectAsStateWithLifecycle()

    var showToken by remember { mutableStateOf(false) }
    var showTokenEditor by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current

    Column(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text(stringResource(R.string.account_settings)) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Logout confirmation dialog
            if (showLogoutDialog) {
                DefaultDialog(
                    onDismiss = { showLogoutDialog = false },
                    title = { Text(stringResource(R.string.logout_dialog_title)) },
                    content = {
                        Text(
                            text = stringResource(R.string.logout_dialog_message),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                    },
                    buttons = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                Timber.d("[LOGOUT_CLEAR] User chose to clear data")
                                scope.launch {
                                    try {
                                        accountSettingsViewModel.logoutAndClearLibraryData(context)
                                    } catch (e: Exception) {
                                        Timber.e(e, "[LOGOUT_CLEAR] Error clearing library data")
                                        reportException(e)
                                    }
                                    onInnerTubeCookieChange("")
                                    playerConnection?.let {
                                        it.service.clearAutomix()
                                        it.player.stop()
                                        it.player.clearMediaItems()
                                    }
                                    showLogoutDialog = false
                                    navController.navigateUp()
                                }
                            }
                        ) {
                            Text(stringResource(R.string.logout_clear))
                        }
                        androidx.compose.material3.TextButton(
                            onClick = {
                                Timber.d("[LOGOUT_KEEP] User chose to keep data")
                                scope.launch {
                                    accountSettingsViewModel.logoutKeepData(context, onInnerTubeCookieChange)
                                    playerConnection?.let {
                                        it.service.clearAutomix()
                                        it.player.stop()
                                        it.player.clearMediaItems()
                                    }
                                    showLogoutDialog = false
                                    navController.navigateUp()
                                }
                            }
                        ) {
                            Text(stringResource(R.string.logout_keep))
                        }
                    }
                )
            }

            if (showTokenEditor) {
                val text = """
                    ***INNERTUBE COOKIE*** =$innerTubeCookie
                    ***VISITOR DATA*** =$visitorData
                    ***DATASYNC ID*** =$dataSyncId
                    ***ACCOUNT NAME*** =$accountNamePref
                    ***ACCOUNT EMAIL*** =$accountEmail
                    ***ACCOUNT CHANNEL HANDLE*** =$accountChannelHandle
                """.trimIndent()

                TextFieldDialog(
                    initialTextFieldValue = TextFieldValue(text),
                    onDone = { data ->
                        var cookie = ""
                        var visitorDataValue = ""
                        var dataSyncIdValue = ""
                        var accountNameValue = ""
                        var accountEmailValue = ""
                        var accountChannelHandleValue = ""

                        data.split("\n").forEach {
                            when {
                                it.startsWith("***INNERTUBE COOKIE*** =") -> cookie = it.substringAfter("=")
                                it.startsWith("***VISITOR DATA*** =") -> visitorDataValue = it.substringAfter("=")
                                it.startsWith("***DATASYNC ID*** =") -> dataSyncIdValue = it.substringAfter("=")
                                it.startsWith("***ACCOUNT NAME*** =") -> accountNameValue = it.substringAfter("=")
                                it.startsWith("***ACCOUNT EMAIL*** =") -> accountEmailValue = it.substringAfter("=")
                                it.startsWith("***ACCOUNT CHANNEL HANDLE*** =") -> accountChannelHandleValue = it.substringAfter("=")
                            }
                        }
                        accountSettingsViewModel.saveTokenAndRestart(
                            context = context,
                            cookie = cookie,
                            visitorData = visitorDataValue,
                            dataSyncId = dataSyncIdValue,
                            accountName = accountNameValue,
                            accountEmail = accountEmailValue,
                            accountChannelHandle = accountChannelHandleValue,
                        )
                    },
                    onDismiss = { showTokenEditor = false },
                    singleLine = false,
                    maxLines = 20,
                    isInputValid = { fullText ->
                        val cookieLine = fullText.lines()
                            .find { it.startsWith("***INNERTUBE COOKIE*** =") }
                        val cookieValue = cookieLine?.substringAfter("***INNERTUBE COOKIE*** =")?.trim() ?: ""
                        cookieValue.isNotEmpty() && "SAPISID" in parseCookieString(cookieValue)
                    },
                    extraContent = {
                        Spacer(Modifier.height(8.dp))
                        InfoLabel(text = stringResource(R.string.token_adv_login_description))
                    }
                )
            }

            // Profile / Account info section
            Material3SettingsGroup(
                title = stringResource(R.string.account_info),
                items = listOf(
                    Material3SettingsItem(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (accountImageUrl != null) {
                                    AsyncImage(
                                        model = accountImageUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(40.dp).clip(CircleShape)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                }
                                Column {
                                    Text(
                                        text = accountNamePref.ifBlank { stringResource(R.string.account_no_name) },
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = accountEmail.ifBlank { stringResource(R.string.account_no_email) },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onClick = { /* Edit username — future sub-phase */ }
                    )
                ),
                useLowContrast = true
            )

            Spacer(Modifier.height(8.dp))

            // YouTube account section
            Material3SettingsGroup(
                title = stringResource(R.string.youtube_account),
                items = listOf(
                    Material3SettingsItem(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isLoggedIn && accountImageUrl != null) {
                                    AsyncImage(
                                        model = accountImageUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(40.dp).clip(CircleShape)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                }
                                Text(
                                    text = if (isLoggedIn) accountName else stringResource(R.string.login),
                                )
                            }
                        },
                        icon = if (!isLoggedIn) painterResource(R.drawable.login) else null,
                        trailingContent = {
                            if (isLoggedIn) {
                                OutlinedButton(
                                    onClick = { showLogoutDialog = true },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Text(stringResource(R.string.action_logout))
                                }
                            }
                        },
                        onClick = {
                            if (isLoggedIn) {
                                navController.navigate("account")
                            } else {
                                navController.navigate("login")
                            }
                        }
                    )
                ),
                useLowContrast = true
            )

            Spacer(Modifier.height(8.dp))

            // Soundsphere account section
            val soundsphereEmail by rememberPreference(SoundsphereEmailKey, "")
            val (soundsphereUsername, onSoundsphereUsernameChange) = rememberPreference(SoundsphereUsernameKey, "")
            val (soundsphereAvatarUrl, onSoundsphereAvatarUrlChange) = rememberPreference(SoundsphereAvatarUrlKey, "")
            var showEditAccountDialog by remember { mutableStateOf(false) }
            var avatarPendingCropUri by remember { mutableStateOf<android.net.Uri?>(null) }
            var isUploadingAvatar by remember { mutableStateOf(false) }
            var avatarUploadError by remember { mutableStateOf<String?>(null) }

            val avatarCropLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
                    if (res.resultCode == android.app.Activity.RESULT_OK) {
                        val output = res.data?.let { UCrop.getOutput(it) } ?: avatarPendingCropUri
                        if (output != null) {
                            scope.launch {
                                isUploadingAvatar = true
                                avatarUploadError = null
                                val uploadFile = File(context.cacheDir, "avatar_upload_${System.currentTimeMillis()}.jpg")
                                try {
                                    // UCrop's output is a FileProvider URI; copy it to a plain
                                    // file the Cloudinary uploader can read.
                                    context.contentResolver.openInputStream(output)?.use { input ->
                                        uploadFile.outputStream().use { out -> input.copyTo(out) }
                                    } ?: throw Exception("Could not read cropped image")
                                    CloudinaryUploader.uploadAvatar(uploadFile)
                                        .onSuccess { secureUrl ->
                                            onSoundsphereAvatarUrlChange(secureUrl)
                                            authViewModel.updateAvatar(secureUrl)
                                        }
                                        .onFailure { e ->
                                            Timber.e(e, "Avatar upload failed")
                                            avatarUploadError = e.message ?: "Avatar upload failed"
                                        }
                                } finally {
                                    uploadFile.delete()
                                    isUploadingAvatar = false
                                }
                            }
                        }
                    }
                }
            val editAvatarTitle = stringResource(R.string.edit_avatar)
            val avatarStatusBarLight = !isSystemInDarkTheme()
            val avatarToolbarColor = MaterialTheme.colorScheme.surface.toArgb()
            val avatarWidgetColor = MaterialTheme.colorScheme.inverseSurface.toArgb()
            val avatarPickLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia(),
                ) { uri ->
                    uri?.let { sourceUri ->
                        val destFile = File(context.cacheDir, "avatar_crop_${System.currentTimeMillis()}.jpg")
                        val destUri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", destFile)
                        avatarPendingCropUri = destUri
                        val options =
                            UCrop.Options().apply {
                                setCompressionFormat(android.graphics.Bitmap.CompressFormat.JPEG)
                                setCompressionQuality(90)
                                setHideBottomControls(true)
                                setToolbarTitle(editAvatarTitle)
                                setStatusBarLight(avatarStatusBarLight)
                                setToolbarColor(avatarToolbarColor)
                                setToolbarWidgetColor(avatarWidgetColor)
                                setRootViewBackgroundColor(avatarToolbarColor)
                                setLogoColor(avatarToolbarColor)
                            }
                        val intent =
                            UCrop
                                .of(sourceUri, destUri)
                                .withAspectRatio(1f, 1f)
                                .withOptions(options)
                                .getIntent(context)
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        avatarCropLauncher.launch(intent)
                    }
                }

            if (showEditAccountDialog) {
                var usernameInput by remember { mutableStateOf(soundsphereUsername) }
                var isSaving by remember { mutableStateOf(false) }
                DefaultDialog(
                    onDismiss = { showEditAccountDialog = false },
                    title = { Text(stringResource(R.string.edit_account_title)) },
                    content = {
                        Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(64.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (soundsphereAvatarUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = soundsphereAvatarUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.person),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(32.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                OutlinedButton(
                                    enabled = !isUploadingAvatar,
                                    onClick = { avatarPickLauncher.launch(
                                        PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    ) },
                                ) {
                                    Text(stringResource(R.string.change_avatar))
                                }
                            }
                            if (isUploadingAvatar) {
                                Text(
                                    text = stringResource(R.string.avatar_uploading),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            } else if (avatarUploadError != null) {
                                Text(
                                    text = avatarUploadError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            TextField(
                                value = usernameInput,
                                onValueChange = { usernameInput = it },
                                label = { Text(stringResource(R.string.username)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    buttons = {
                        TextButton(onClick = { showEditAccountDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(
                            enabled = !isSaving && usernameInput.isNotBlank() &&
                                usernameInput != soundsphereUsername,
                            onClick = {
                                isSaving = true
                                scope.launch {
                                    authViewModel.updateUsername(usernameInput.trim())
                                    onSoundsphereUsernameChange(usernameInput.trim())
                                    isSaving = false
                                    showEditAccountDialog = false
                                }
                            }
                        ) {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                )
            }

            Material3SettingsGroup(
                title = stringResource(R.string.auth_account_title),
                items = listOf(
                    Material3SettingsItem(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (soundsphereAvatarUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = soundsphereAvatarUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.person),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = soundsphereUsername.ifBlank {
                                            stringResource(R.string.auth_account_title)
                                        },
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = soundsphereEmail.ifBlank {
                                            stringResource(R.string.auth_no_email)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            OutlinedButton(
                                onClick = { showEditAccountDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(stringResource(R.string.edit_account))
                            }
                        },
                        onClick = { showEditAccountDialog = true }
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.auth_account_title)) },
                        trailingContent = {
                            OutlinedButton(
                                onClick = {
                                    authViewModel.logout()
                                    navController.navigateUp()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(stringResource(R.string.action_logout))
                            }
                        },
                        onClick = {}
                    )
                ),
                useLowContrast = true
            )

            Spacer(Modifier.height(8.dp))

            // Advanced YouTube settings
            Material3SettingsGroup(
                title = stringResource(R.string.advanced_settings),
                items = listOf(
                    Material3SettingsItem(
                        title = {
                            Text(
                                when {
                                    !isLoggedIn -> stringResource(R.string.advanced_login)
                                    showToken -> stringResource(R.string.token_shown)
                                    else -> stringResource(R.string.token_hidden)
                                }
                            )
                        },
                        icon = painterResource(R.drawable.token),
                        onClick = {
                            if (!isLoggedIn) showTokenEditor = true
                            else if (!showToken) showToken = true
                            else showTokenEditor = true
                        }
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.more_content)) },
                        icon = painterResource(R.drawable.cached),
                        trailingContent = {
                            Switch(
                                enabled = isLoggedIn,
                                checked = useLoginForBrowse,
                                onCheckedChange = {
                                    YouTube.useLoginForBrowse = it
                                    onUseLoginForBrowseChange(it)
                                },
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (useLoginForBrowse) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        enabled = isLoggedIn
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.yt_sync)) },
                        icon = painterResource(R.drawable.cached),
                        trailingContent = {
                            Switch(
                                enabled = isLoggedIn,
                                checked = ytmSync,
                                onCheckedChange = onYtmSyncChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (ytmSync) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        enabled = isLoggedIn
                    )
                ),
                useLowContrast = true
            )

            Spacer(Modifier.height(8.dp))

            // Devices section (placeholder)
            Material3SettingsGroup(
                title = stringResource(R.string.devices_signed_in),
                items = listOf(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.soundsphere_account)) },
                        description = { Text(stringResource(R.string.device_active)) },
                        onClick = {}
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.youtube_account)) },
                        description = {
                            Text(
                                if (isLoggedIn) stringResource(R.string.device_active)
                                else stringResource(R.string.device_not_signed_in)
                            )
                        },
                        onClick = {}
                    )
                ),
                useLowContrast = true
            )

            Spacer(Modifier.height(8.dp))

            // Listening history
            Material3SettingsGroup(
                items = listOf(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.listening_history)) },
                        icon = painterResource(R.drawable.history),
                        onClick = {
                            navController.navigate("history")
                        }
                    )
                ),
                useLowContrast = true
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
