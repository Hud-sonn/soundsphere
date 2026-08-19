/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.lyrics

import android.content.Context
import com.soundsphere.music.constants.EnableUnisonKey
import com.soundsphere.music.utils.dataStore
import com.soundsphere.music.utils.get
import com.soundsphere.unison.Unison

object UnisonLyricsProvider : LyricsProvider {
    override val name = "Unison"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableUnisonKey] ?: true

    override suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> {
        // Primary lookup by video id, falling back to metadata when it misses.
        val byVideoId = Unison.getLyricsByVideoId(id)
        if (byVideoId.isSuccess) return byVideoId
        return Unison.getLyricsByMetadata(title, artist, duration, album)
    }
}