/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.utils

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Material You spring animation spec for lightweight transitions.
 * Uses stiff spring for responsive feel without heavy computation.
 */
object MaterialYouAnimation {
    /**
     * Standard spring for list items, cards, and general UI elements.
     */
    @Composable
    fun <T> springSpec() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /**
     * Gentle spring for subtle transitions like color changes.
     */
    @Composable
    fun <T> gentleSpringSpec() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )
}

/**
 * Standard padding for settings screen content.
 */
val SettingsContentPadding = Modifier.padding(horizontal = 16.dp)
