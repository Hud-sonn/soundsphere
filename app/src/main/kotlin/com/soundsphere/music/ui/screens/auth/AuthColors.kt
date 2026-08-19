/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens.auth

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class AuthColors(
    val background: Color,
    val surface: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val outline: Color,
)

@Composable
fun rememberEarthyAuthColors() = AuthColors(
    background = MaterialTheme.colorScheme.background,
    surface = MaterialTheme.colorScheme.surfaceContainer,
    primaryText = MaterialTheme.colorScheme.onBackground,
    secondaryText = MaterialTheme.colorScheme.onSurfaceVariant,
    accent = MaterialTheme.colorScheme.primary,
    outline = MaterialTheme.colorScheme.outline,
)
