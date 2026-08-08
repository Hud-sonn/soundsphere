/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.playback.queues

import androidx.media3.common.MediaItem
import com.soundsphere.innertube.YouTube
import com.soundsphere.innertube.models.WatchEndpoint
import com.soundsphere.music.extensions.toMediaItem
import com.soundsphere.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

class YouTubeAlbumRadio(
    private var playlistId: String,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    private val endpoint: WatchEndpoint
        get() = WatchEndpoint(
            playlistId = playlistId
        )

    private var albumSongCount = 0
    private var continuation: String? = null
    private var firstTimeLoaded: Boolean = false

    // Serializes nextPage() so rapid auto-load-more calls can't append the same page twice
    private val nextPageLock = Mutex()

    override suspend fun getInitialStatus(): Queue.Status = withContext(IO) {
        val albumSongs = YouTube.albumSongs(playlistId).getOrThrow()
        albumSongCount = albumSongs.size
        Queue.Status(
            title = albumSongs.firstOrNull()?.album?.name.orEmpty(),
            items = albumSongs.map { it.toMediaItem() },
            mediaItemIndex = 0
        )
    }

    override fun hasNextPage(): Boolean = !firstTimeLoaded || continuation != null

    override suspend fun nextPage(): List<MediaItem> = withContext(IO) {
        if (!nextPageLock.tryLock()) return@withContext emptyList()
        try {
            val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
            continuation = nextResult.continuation
            if (!firstTimeLoaded) {
                firstTimeLoaded = true
                // Guard against the continuation overlapping (or being shorter than)
                // the already-loaded initial page
                val from = albumSongCount.coerceAtMost(nextResult.items.size)
                nextResult.items.subList(from, nextResult.items.size).map { it.toMediaItem() }
            } else {
                nextResult.items.map { it.toMediaItem() }
            }
        } finally {
            nextPageLock.unlock()
        }
    }
}
