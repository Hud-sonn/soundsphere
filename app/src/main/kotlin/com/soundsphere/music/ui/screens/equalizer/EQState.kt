package com.soundsphere.music.ui.screens.equalizer

import com.soundsphere.music.eq.data.SavedEQProfile

/**
 * UI State for EQ Screen
 */
data class EQState(
    val profiles: List<SavedEQProfile> = emptyList(),
    val activeProfileId: String? = null,
    val importStatus: String? = null,
    val error: String? = null,
    /** OutputRoute.name → profile id for per-output auto-switch. */
    val routeMap: Map<String, String> = emptyMap(),
)