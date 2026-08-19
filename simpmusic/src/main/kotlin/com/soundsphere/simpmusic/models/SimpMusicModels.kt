package com.soundsphere.simpmusic.models

import kotlinx.serialization.Serializable

/**
 * Envelope returned by all SimpMusic endpoints. `success` is the authoritative
 * flag; `type` is informational and ignored. On failure `data` is absent and an
 * `error` object is present instead.
 */
@Serializable
data class SimpMusicResponse(
    val success: Boolean = false,
    val data: List<LyricItem> = emptyList(),
)

/**
 * A single lyrics record, keyed on the YouTube video id.
 * `syncedLyrics` is standard LRC, `richSyncLyrics` is word-level synced,
 * and `plainLyric` is the unstamped text.
 */
@Serializable
data class LyricItem(
    val id: String? = null,
    val videoId: String? = null,
    val songTitle: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val durationSeconds: Int? = null,
    val plainLyric: String? = null,
    val syncedLyrics: String? = null,
    val richSyncLyrics: String? = null,
    val trackType: String? = null,
    val vote: Int? = null,
    val contributor: String? = null,
    val contributorEmail: String? = null,
)