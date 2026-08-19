package com.soundsphere.unison

import com.soundsphere.music.betterlyrics.TTMLParser
import com.soundsphere.unison.models.LyricsRecord
import com.soundsphere.unison.models.UnisonResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Unison — public database of community-synced lyrics, the source the
 * Better Lyrics API reads from. Each entry is keyed on a YouTube video id.
 * See https://unison.boidu.dev/docs
 */
object Unison {
    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                    },
                )
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
            }

            defaultRequest {
                url("https://unison.boidu.dev")
                headers {
                    append("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    append("Accept", "application/json")
                }
            }

            expectSuccess = false
        }
    }

    /** Fetch the top-ranked lyrics for a YouTube video id. */
    suspend fun getLyricsByVideoId(videoId: String): Result<String> = runCatching {
        if (videoId.isBlank()) throw IllegalStateException("Video id is blank")
        client.get("/lyrics") {
            parameter("v", videoId)
        }.toLyrics()
    }

    /** Fetch lyrics by metadata (title/artist/album/duration) as a fallback. */
    suspend fun getLyricsByMetadata(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ): Result<String> = runCatching {
        if (title.isBlank() || artist.isBlank()) {
            throw IllegalStateException("Title and artist are required")
        }
        client.get("/lyrics") {
            parameter("song", title)
            parameter("artist", artist)
            if (!album.isNullOrBlank()) parameter("album", album)
            if (duration > 0) parameter("duration", duration)
        }.toLyrics()
    }

    private suspend fun io.ktor.client.statement.HttpResponse.toLyrics(): String {
        if (status != HttpStatusCode.OK) {
            throw IllegalStateException("Unison returned HTTP ${status.value}")
        }
        val envelope = body<UnisonResponse>()
        if (!envelope.success) throw IllegalStateException("Unison returned an error response")
        val record = envelope.data ?: throw IllegalStateException("No lyrics found")
        return record.toLyricsText()
    }

    private fun LyricsRecord.toLyricsText(): String {
        val raw = lyrics?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Lyrics record is empty")

        // TTML is the native encoding; convert it to the app's LRC format.
        // Any other format is passed through untouched.
        return if (format.equals("ttml", ignoreCase = true)) {
            val parsedLines = TTMLParser.parseTTML(raw)
            if (parsedLines.isEmpty()) {
                throw IllegalStateException("Failed to parse TTML lyrics")
            }
            TTMLParser.toLRC(parsedLines)
        } else {
            raw
        }
    }
}