package com.soundsphere.music.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.soundsphere.music.db.entities.SongEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SkipCountTest {
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
    fun `skip count increments twice`() =
        runBlocking {
            val song = SongEntity(id = "song-1", title = "Test", totalPlayTime = 0, inLibrary = LocalDateTime.now())
            database.dao.insert(song)
            database.dao.incrementSkipCount("song-1")
            database.dao.incrementSkipCount("song-1")
            val loaded = database.dao.getSongById("song-1")
            assertEquals(2, loaded?.song?.skipCount)
        }

    @Test
    fun `untouched song has zero skips`() =
        runBlocking {
            val song = SongEntity(id = "song-2", title = "Test 2")
            database.dao.insert(song)
            val loaded = database.dao.getSongById("song-2")
            assertEquals(0, loaded?.song?.skipCount)
        }
}
