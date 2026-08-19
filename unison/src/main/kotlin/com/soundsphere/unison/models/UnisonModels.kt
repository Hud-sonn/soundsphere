package com.soundsphere.unison.models

import kotlinx.serialization.Serializable

/**
 * Envelope returned by Unison endpoints. Check `success` before touching `data`.
 */
@Serializable
data class UnisonResponse(
    val success: Boolean = false,
    val data: LyricsRecord? = null,
)

/**
 * A single community-synced lyrics record, keyed on the YouTube video id.
 * `lyrics` holds the synced text and `format` describes its encoding
 * (e.g. "ttml").
 */
@Serializable
data class LyricsRecord(
    val id: Long? = null,
    val videoId: String? = null,
    val song: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val isrc: String? = null,
    val lyrics: String? = null,
    val format: String? = null,
    val language: String? = null,
    val syncType: String? = null,
    val confidence: String? = null,
)