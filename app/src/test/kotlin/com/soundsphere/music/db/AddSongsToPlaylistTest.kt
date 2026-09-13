/*
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.db

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.soundsphere.music.db.entities.Playlist
import com.soundsphere.music.db.entities.PlaylistEntity
import com.soundsphere.music.db.entities.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

// Note: our addSongsToPlaylist is a blocking @Transaction (not suspend like
// upstream) to avoid a Kotlin IR lowering crash, so DAO calls run on
// Dispatchers.IO / allowMainThreadQueries instead of the main thread.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AddSongsToPlaylistTest {
    private lateinit var database: InternalDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, InternalDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `bulk add inserts songs off the main thread`() =
        runBlocking {
            val playlist =
                PlaylistEntity(
                    id = "playlist",
                    name = "Playlist",
                    bookmarkedAt = LocalDateTime.of(2026, 1, 1, 0, 0),
                )
            withContext(Dispatchers.IO) {
                database.dao.insert(playlist)
                database.dao.insert(SongEntity(id = "song", title = "Song"))
            }

            assertTrue(Looper.getMainLooper().isCurrentThread)
            withContext(Dispatchers.IO) {
                database.dao.addSongsToPlaylist(
                    Playlist(playlist, 0, emptyList()),
                    listOf("song" to null),
                )
            }

            assertEquals(listOf("song"), database.dao.playlistSongIds("playlist"))
        }

    @Test
    fun `bulk add participates in an outer suspending transaction`() =
        runBlocking {
            val playlist =
                PlaylistEntity(
                    id = "playlist",
                    name = "Playlist",
                    bookmarkedAt = LocalDateTime.of(2026, 1, 1, 0, 0),
                )
            withContext(Dispatchers.IO) {
                database.dao.insert(playlist)
                database.dao.insert(SongEntity(id = "song", title = "Song"))
            }

            val result =
                runCatching {
                    MusicDatabase(database).withTransaction {
                        addSongsToPlaylist(
                            Playlist(playlist, 0, emptyList()),
                            listOf("song" to null),
                        )
                        error("roll back")
                    }
                }

            assertTrue(result.isFailure)
            assertEquals(emptyList<String>(), database.dao.playlistSongIds("playlist"))
        }
}
