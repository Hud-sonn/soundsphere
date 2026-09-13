/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.playback.queues

import androidx.media3.common.MediaItem
import com.soundsphere.music.extensions.metadata
import com.soundsphere.music.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?

    /**
     * What container playback was started from (playlist/album/artist), used to
     * record "recently played" entries for containers, not just songs.
     * Null when the queue has no meaningful container context (search, radio...).
     */
    val sourceInfo: QueueSourceInfo?
        get() = null

    suspend fun getInitialStatus(): Status

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        fun filterExplicit(enabled: Boolean = true) =
            if (enabled) {
                copy(
                    items = items.filterExplicit(),
                )
            } else {
                this
            }

        fun filterVideoSongs(disableVideos: Boolean = false) =
            if (disableVideos) {
                copy(
                    items = items.filterVideoSongs(true),
                )
            } else {
                this
            }
    }
}

/**
 * Container context a queue was created from. [type] is one of
 * 'playlist' | 'album' | 'artist'; [id] is the local or YouTube container id.
 */
data class QueueSourceInfo(
    val type: String,
    val id: String,
    val name: String? = null,
)

fun List<MediaItem>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.metadata?.explicit == true
        }
    } else {
        this
    }

fun List<MediaItem>.filterVideoSongs(disableVideos: Boolean = false) =
    if (disableVideos) {
        filterNot { it.metadata?.isVideoSong == true }
    } else {
        this
    }
