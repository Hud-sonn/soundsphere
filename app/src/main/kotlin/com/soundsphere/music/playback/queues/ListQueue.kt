/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.playback.queues

import androidx.media3.common.MediaItem
import com.soundsphere.music.models.MediaMetadata

class ListQueue(
    val title: String? = null,
    val items: List<MediaItem>,
    val startIndex: Int = 0,
    val position: Long = 0L,
    val sourceType: String? = null,
    val sourceId: String? = null,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    override val sourceInfo: QueueSourceInfo?
        get() = sourceId?.let { QueueSourceInfo(type = sourceType ?: "playlist", id = it, name = title) }

    override suspend fun getInitialStatus() = Queue.Status(title, items, startIndex, position)

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage() = throw UnsupportedOperationException()
}
