package com.soundsphere.music.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadUtilTest {
    @Test
    fun `downloadArtworkUrls keeps distinct song and album covers`() {
        assertEquals(
            listOf("song-cover", "album-cover"),
            downloadArtworkUrls("song-cover", "album-cover"),
        )
        assertEquals(
            listOf("song-cover"),
            downloadArtworkUrls("song-cover", "song-cover"),
        )
        assertEquals(emptyList<String>(), downloadArtworkUrls("", null))
    }
}
