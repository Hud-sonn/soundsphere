package com.soundsphere.music.utils

import com.soundsphere.music.db.entities.ArtistEntity
import com.soundsphere.music.db.entities.Song
import com.soundsphere.music.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun song(
    id: String,
    title: String,
    artist: String = "Artist",
    thumbnail: String? = "http://art",
    uploaded: Boolean = false,
    duration: Int = 180,
) = Song(
    song =
        SongEntity(
            id = id,
            title = title,
            thumbnailUrl = thumbnail,
            isUploaded = uploaded,
            duration = duration,
        ),
    artists = listOf(ArtistEntity(id = "a-$artist", name = artist)),
)

class LibraryHealthTest {
    @Test
    fun `case-insensitive duplicates grouped`() {
        val songs = listOf(song("1", "Hello"), song("2", "  hello "), song("3", "Other"))
        val groups = findDuplicateSongs(songs)
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].size)
    }

    @Test
    fun `different artists are not duplicates`() {
        val songs = listOf(song("1", "Hello", "A"), song("2", "Hello", "B"))
        assertTrue(findDuplicateSongs(songs).isEmpty())
    }

    @Test
    fun `missing art detected`() {
        val songs = listOf(song("1", "A"), song("2", "B", thumbnail = null), song("3", "C", thumbnail = ""))
        assertEquals(listOf("2", "3"), findSongsMissingArt(songs).map { it.song.id })
    }

    @Test
    fun `dead uploads detected`() {
        val songs =
            listOf(
                song("1", "Dead", uploaded = true, thumbnail = null, duration = 0),
                song("2", "Alive", uploaded = true, thumbnail = "http://x", duration = 200),
                song("3", "Local", uploaded = false, thumbnail = null, duration = 0),
            )
        assertEquals(listOf("1"), findDeadUploads(songs).map { it.song.id })
    }

    @Test
    fun `clean library reports clean`() {
        val report = buildHealthReport(listOf(song("1", "A")))
        assertTrue(report.isClean)
        assertEquals(0, report.duplicateCount)
    }
}
