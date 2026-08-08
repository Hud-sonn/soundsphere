/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.playback.queues

import androidx.media3.common.MediaItem
import com.soundsphere.innertube.YouTube
import com.soundsphere.innertube.models.WatchEndpoint
import com.soundsphere.music.db.entities.AlbumWithSongs
import com.soundsphere.music.extensions.toMediaItem
import com.soundsphere.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

class LocalAlbumRadio(
    private val albumWithSongs: AlbumWithSongs,
    private val startIndex: Int = 0,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    private lateinit var playlistId: String
    private val endpoint: WatchEndpoint
        get() = WatchEndpoint(
            playlistId = playlistId
        )

    private var continuation: String? = null
    private var firstTimeLoaded: Boolean = false

    // Serializes nextPage() so rapid auto-load-more calls can't append the same page twice
    private val nextPageLock = Mutex()

    override suspend fun getInitialStatus(): Queue.Status = withContext(IO) {
        Queue.Status(
            title = albumWithSongs.album.title,
            items = albumWithSongs.songs.map { it.toMediaItem() },
            mediaItemIndex = startIndex
        )
    }

    override fun hasNextPage(): Boolean = !firstTimeLoaded || continuation != null

    override suspend fun nextPage(): List<MediaItem> = withContext(IO) {
        if (!nextPageLock.tryLock()) return@withContext emptyList()
        try {
            if (!firstTimeLoaded) {
                playlistId = YouTube.album(albumWithSongs.album.id).getOrThrow().album.playlistId
                val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
                continuation = nextResult.continuation
                firstTimeLoaded = true
                // Guard against the continuation overlapping (or being shorter than)
                // the already-loaded local album
                val from = albumWithSongs.songs.size.coerceAtMost(nextResult.items.size)
                return@withContext nextResult.items.subList(
                    from,
                    nextResult.items.size
                ).map { it.toMediaItem() }
            }
            val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
            continuation = nextResult.continuation
            nextResult.items.map { it.toMediaItem() }
        } finally {
            nextPageLock.unlock()
        }
    }
}
