/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * A bounded "recently played" recency entry: either a song that was actually
 * listened to or a container (playlist/album/artist) playback was started from.
 *
 * Unlike [Event] this table follows a replace-with-latest sync model: pulls
 * replace the whole local contents with the server's most recent snapshot,
 * and old entries fall away instead of accumulating forever.
 */
@Immutable
@Entity(
    tableName = "recently_played",
    indices = [
        Index(value = ["sourceType", "sourceId"], unique = true),
    ],
)
data class RecentlyPlayedEntity(
    @PrimaryKey val id: String,
    // 'song' | 'playlist' | 'album' | 'artist'
    @ColumnInfo(name = "sourceType") val sourceType: String,
    @ColumnInfo(name = "sourceId") val sourceId: String,
    @ColumnInfo(name = "sourceName") val sourceName: String?,
    @ColumnInfo(name = "sourceThumbnail") val sourceThumbnail: String?,
    val playedAt: LocalDateTime,
)
