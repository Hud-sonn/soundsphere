/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.soundsphere.music.R
import com.soundsphere.music.constants.AccountEmailKey
import com.soundsphere.music.constants.SoundsphereEmailKey
import com.soundsphere.music.constants.SoundsphereUsernameKey
import com.soundsphere.music.utils.rememberPreference

/**
 * Navigation drawer for the home experience: profile header, top-level
 * navigation shortcuts and the app-level entries (settings, changelog,
 * updates, about).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundsphereSidebar(
    currentRoute: String?,
    accountName: String,
    accountImageUrl: String?,
    listenTogetherInTopBar: Boolean,
    updateAvailable: Boolean,
    isLoggedIn: Boolean,
    onNavigate: (String) -> Unit,
    onOpenAccount: () -> Unit,
    onOpenChangelog: () -> Unit,
) {
    val email by rememberPreference(AccountEmailKey, "")
    val soundsphereEmail by rememberPreference(SoundsphereEmailKey, "")
    val soundsphereUsername by rememberPreference(SoundsphereUsernameKey, "")

    // Prefer the YouTube account name; fall back to the Soundsphere account
    // username when signed in; only show "Guest" when fully signed out.
    val displayName =
        when {
            accountName != "Guest" -> accountName
            soundsphereUsername.isNotBlank() -> soundsphereUsername
            isLoggedIn -> stringResource(R.string.soundsphere_account)
            else -> "Guest"
        }
    val subtitle =
        when {
            isLoggedIn ->
                soundsphereEmail.ifBlank { email }.ifBlank {
                    stringResource(R.string.sidebar_signed_in)
                }
            else -> stringResource(R.string.sidebar_sign_in_hint)
        }

    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
    ) {
        // Profile header
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAccount)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (accountImageUrl != null) {
                    AsyncImage(
                        model = accountImageUrl,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.person),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider()

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.sidebar_section_music),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        )

        DrawerItem(
            icon = R.drawable.stats,
            label = R.string.stats,
            selected = currentRoute == "stats",
            onClick = { onNavigate("stats") },
        )
        DrawerItem(
            icon = R.drawable.history,
            label = R.string.history,
            selected = currentRoute == "history",
            onClick = { onNavigate("history") },
        )
        if (listenTogetherInTopBar) {
            DrawerItem(
                icon = R.drawable.group_outlined,
                label = R.string.together,
                selected = currentRoute == "listen_together_from_topbar",
                onClick = { onNavigate("listen_together_from_topbar") },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()

        Text(
            text = stringResource(R.string.sidebar_section_app),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        )

        DrawerItem(
            icon = R.drawable.settings,
            label = R.string.settings,
            selected = currentRoute == "settings",
            onClick = { onNavigate("settings") },
        )
        DrawerItem(
            icon = R.drawable.newspaper,
            label = R.string.changelog,
            selected = false,
            onClick = onOpenChangelog,
        )
                DrawerItem(
            icon = R.drawable.update,
            label = R.string.updates,
            selected = currentRoute == "settings/updater",
            onClick = { onNavigate("settings/updater") },
            badge = {
                if (updateAvailable) {
                    Badge()
                }
            },
        )
        DrawerItem(
            icon = R.drawable.info,
            label = R.string.about,
            selected = currentRoute == "settings/about",
            onClick = { onNavigate("settings/about") },
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = stringResource(R.string.app_version_value, com.soundsphere.music.BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun DrawerItem(
    icon: Int,
    label: Int,
    selected: Boolean,
    onClick: () -> Unit,
    badge: (@Composable () -> Unit)? = null,
) {
    NavigationDrawerItem(
        label = { Text(text = stringResource(label)) },
        icon = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
            )
        },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
        colors = NavigationDrawerItemDefaults.colors(),
        badge = badge,
    )
}
