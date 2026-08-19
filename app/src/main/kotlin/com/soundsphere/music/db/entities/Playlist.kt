/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

@Immutable
data class Playlist(
    @Embedded
    val playlist: PlaylistEntity,
    val songCount: Int,
    @Relation(
        entity = SongEntity::class,
        entityColumn = "id",
        parentColumn = "id",
        projection = ["thumbnailUrl"],
        associateBy =
        Junction(
            value = PlaylistSongMapPreview::class,
            parentColumn = "playlistId",
            entityColumn = "songId",
        ),
    )
    val songThumbnails: List<String?>,
) : LocalItem() {
    override val id: String
        get() = playlist.id
    override val title: String
        get() = playlist.name
    override val thumbnailUrl: String?
        get() = null
    
    val thumbnails: List<String>
        get() {
            val customThumbnail = playlist.thumbnailUrl
            val songCovers = songThumbnails.filterNotNull()
            // A user-picked cover (gallery file or uploaded custom thumbnail) is
            // the definitive artwork — always show it alone.
            val isCustomCover =
                customThumbnail != null &&
                    (customThumbnail.contains("content://com.soundsphere.music") ||
                        customThumbnail.contains("studio_square_thumbnail"))
            return when {
                isCustomCover -> listOf(customThumbnail!!)
                songCovers.isNotEmpty() -> songCovers
                customThumbnail != null -> listOf(customThumbnail)
                else -> emptyList()
            }
        }
}
