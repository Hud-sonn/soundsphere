/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.soundsphere.music.constants.SoundsphereAvatarUrlKey
import com.soundsphere.music.constants.SoundsphereEmailKey
import com.soundsphere.music.constants.SoundsphereUsernameKey
import com.soundsphere.music.utils.rememberPreference

/**
 * Navigation drawer — pure navigation only. Profile header at top (identity
 * context, not tappable), followed by navigation items: Profile, Stats,
 * Listen Together, App Settings, Integrations, Updates. Footer shows app version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundsphereSidebar(
    currentRoute: String?,
    accountName: String,
    accountImageUrl: String?,
    listenTogetherInBottomNav: Boolean,
    updateAvailable: Boolean,
    announcementsUnseen: Boolean,
    isLoggedIn: Boolean,
    onNavigate: (String) -> Unit,
    onShowAnnouncements: () -> Unit,
) {
    val email by rememberPreference(AccountEmailKey, "")
    val soundsphereEmail by rememberPreference(SoundsphereEmailKey, "")
    val soundsphereUsername by rememberPreference(SoundsphereUsernameKey, "")
    val soundsphereAvatarUrl by rememberPreference(SoundsphereAvatarUrlKey, "")

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
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // Profile header — identity context only, not tappable
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopStart,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
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
                    } else if (soundsphereAvatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = soundsphereAvatarUrl,
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
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                // Red dot over the profile avatar when there are unread announcements
                if (announcementsUnseen) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 4.dp)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                                .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLow, CircleShape),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Spacer(modifier = Modifier.height(8.dp))

        // Navigation items
        DrawerItem(
            icon = R.drawable.person,
            label = R.string.profile,
            selected = currentRoute == "account_settings",
            onClick = { onNavigate("account_settings") },
        )
        DrawerItem(
            icon = R.drawable.stats,
            label = R.string.stats,
            selected = currentRoute == "stats",
            onClick = { onNavigate("stats") },
        )
        if (!listenTogetherInBottomNav) {
            DrawerItem(
                icon = R.drawable.group_outlined,
                label = R.string.together,
                selected = currentRoute == "listen_together_from_topbar",
                onClick = { onNavigate("listen_together_from_topbar") },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Spacer(modifier = Modifier.height(8.dp))

        DrawerItem(
            icon = R.drawable.settings,
            label = R.string.settings,
            selected = currentRoute == "settings",
            onClick = { onNavigate("settings") },
        )
        DrawerItem(
            icon = R.drawable.integration,
            label = R.string.integrations,
            selected = currentRoute == "settings/integrations",
            onClick = { onNavigate("settings/integrations") },
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
            icon = R.drawable.newspaper,
            label = R.string.announcements,
            selected = false,
            onClick = onShowAnnouncements,
            badge = {
                if (announcementsUnseen) {
                    Badge()
                }
            },
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
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        badge = badge,
    )
}
