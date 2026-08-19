package com.soundsphere.simpmusic

import com.soundsphere.simpmusic.models.LyricItem
import com.soundsphere.simpmusic.models.SimpMusicResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * SimpMusic Lyrics API
 * Community-driven lyrics database indexed by YouTube video id.
 * See https://api-lyrics.simpmusic.org (SimpMusic app by maxrave-dev).
 */
object SimpMusic {
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
                url("https://api-lyrics.simpmusic.org")
            }

            expectSuccess = false
        }
    }

    /**
     * Fetch lyrics for a YouTube video id.
     * Prefers synced LRC, falls back to rich-synced, then plain text.
     */
    suspend fun getLyrics(videoId: String): Result<String> = runCatching {
        if (videoId.isBlank()) throw IllegalStateException("Video id is blank")

        val response = client.get("/v1/$videoId")
        if (response.status != HttpStatusCode.OK) {
            throw IllegalStateException("SimpMusic returned HTTP ${response.status.value}")
        }

        val envelope = response.body<SimpMusicResponse>()
        if (!envelope.success) throw IllegalStateException("SimpMusic returned an error response")

        envelope.data.firstOrNull()
            ?.toLyrics()
            ?: throw IllegalStateException("No lyrics found for video id")
    }

    private fun LyricItem.toLyrics(): String =
        syncedLyrics?.takeIf { it.isNotBlank() }
            ?: richSyncLyrics?.takeIf { it.isNotBlank() }
            ?: plainLyric?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Lyrics record is empty")
}