/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.soundsphere.music.R

/**
 * Reusable bottom-sheet / dialog content that shows a user's public info.
 *
 * Currently enabled only for Blend track attribution (Added by), but designed
 * to be reusable for any future user-profile surface (comments, activity feed, etc.).
 *
 * @param username Display name (or email prefix fallback)
 * @param avatarUrl Remote avatar URL, may be null
 * @param userId Optional user ID (for future copy / view profile actions)
 * @param subtitle Optional second line (e.g. "Collaborator • Added 3 songs")
 */
@Composable
fun UserInfoContent(
    username: String,
    avatarUrl: String?,
    userId: String? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = username.ifBlank { stringResource(R.string.unknown_user) },
                style = MaterialTheme.typography.titleMedium,
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (userId != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = userId.take(8) + "…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun UserInfoSheet(
    username: String,
    avatarUrl: String?,
    userId: String? = null,
    subtitle: String? = null,
    onDismiss: () -> Unit,
) {
    BottomSheetContent(
        onDismiss = onDismiss,
        title = { Text(stringResource(R.string.user_info)) },
    ) {
        UserInfoContent(
            username = username,
            avatarUrl = avatarUrl,
            userId = userId,
            subtitle = subtitle,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// Internal helper to avoid duplicating BottomSheet boilerplate if BottomSheetContent doesn't exist
@Composable
private fun BottomSheetContent(
    onDismiss: () -> Unit,
    title: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            title()
        }
        content()
    }
}
