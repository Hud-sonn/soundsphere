/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens.auth

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

val EarthyAuthColors =
    AuthColors(
        background = Color(0xFF0A0908),
        surface = Color(0xFF22333B),
        primaryText = Color(0xFFEAE0D5),
        secondaryText = Color(0xFFC6AC8F),
        accent = Color(0xFF5E503F),
        outline = Color(0xFF979087),
    )
