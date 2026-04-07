/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import com.metrolist.music.constants.EnableLyricsPlus
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

@Serializable
private data class AgentInfo(
    val type: String? = null,
    val name: String? = null,
    val alias: String? = null, // "v1", "v2", etc.
)

@Serializable
private data class SongPart(
    val name: String? = null,
    val time: Long? = null,
    val duration: Long? = null,
)

@Serializable
private data class LyricsMetadata(
    val agents: Map<String, AgentInfo>? = null,
    val songParts: List<SongPart>? = null,
    val songWriters: List<String>? = null,
    val title: String? = null,
    val language: String? = null,
    val totalDuration: String? = null,
)

@Serializable
private data class Translation(
    val lang: String? = null,
    val text: String? = null,
)

@Serializable
private data class LyricWord(
    val time: Long = 0,       // milliseconds
    val duration: Long = 0,   // milliseconds
    val text: String = "",
    val isBackground: Boolean = false,
)

@Serializable
private data class Transliteration(
    val lang: String? = null,
    val text: String? = null,
    val syllabus: List<LyricWord>? = null,
)

@Serializable
private data class LineElement(
    val key: String? = null,
    val singer: String? = null,       // already-resolved alias, e.g. "v1"
    val songPartIndex: Int? = null,
)

@Serializable
private data class LyricLine(
    val time: Long = 0,               // milliseconds
    val duration: Long = 0,           // milliseconds
    val text: String = "",
    val syllabus: List<LyricWord>? = null,
    val element: LineElement? = null,
    val translation: Translation? = null,
    val transliteration: Transliteration? = null,
)

@Serializable
private data class LyricsPlusResponse(
    val type: String? = null,
    val metadata: LyricsMetadata? = null,
    val lyrics: List<LyricLine>? = null,
    val cached: String? = null,
)

object LyricsPlusProvider : LyricsProvider {
    override val name = "LyricsPlus"

    private val baseUrls = listOf(
        "https://lyricsplus.binimum.org", //binimum's alternate server
        "https://lyricsplus.atomix.one/", //meow's mirror
        "https://lyricsplus.prjktla.my.id", //main server
        "https://lyricsplus-seven.vercel.app", //jigen's mirror
        //"https://lyricsplus.prjktla.workers.dev", //ibra's cf workers (disabled due it has 100000 request per day limit)
        //"https://lyrics-plus-backend.vercel.app", //ibra's vercel (disabled due it's disabled)
    )

    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }

            expectSuccess = false
        }
    }

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableLyricsPlus] ?: false

    private suspend fun fetchFromUrl(
        url: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): LyricsPlusResponse? = runCatching {
        val response = client.get("$url/v2/lyrics/get") {
            parameter("title", title)
            parameter("artist", artist)
            if (duration > 0) parameter("duration", duration / 1000)  // omit if invalid
            if (!album.isNullOrBlank()) parameter("album", album)
            parameter("source", "apple,lyricsplus,qq,musixmatch,musixmatch-word")
        }
        if (response.status == HttpStatusCode.OK) response.body<LyricsPlusResponse>() else null
    }.getOrNull()

    private suspend fun fetchLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): LyricsPlusResponse? {
        if (title.isBlank() || artist.isBlank()) {
            Timber.tag("LyricsPlus").d("Skipping fetch: missing title or artist")
            return null
        }

        for (baseUrl in baseUrls) {
            try {
                val result = fetchFromUrl(baseUrl, title, artist, duration, album)
                if (result != null && !result.lyrics.isNullOrEmpty()) return result
            } catch (e: Exception) {
                Timber.tag("LyricsPlus").d(e, "Failed to fetch from $baseUrl")
            }
        }
        return null
    }

    /**
     * Converts a LyricsPlus JSON response to
     * Metrolist's extended LRC:
     *
     *   [mm:ss.cc]{agent:v1}line text     ← multi-voice agent tag
     *   <word:startSec:endSec|word:...>   ← word-sync block (Word mode only)
     *   [mm:ss.cc]{bg}bg vocal text       ← first in a consecutive bg run
     *   <word:startSec:endSec|...>
     */
    private fun convertToLrc(response: LyricsPlusResponse?): String? {
        val lyrics = response?.lyrics?.takeIf { it.isNotEmpty() } ?: return null
        val isWordSync = response.type.equals("Word", ignoreCase = true)

        // Agent mapping
        // The JSON aliases (v1, v2, v1000) are used directly. Others get mapped
        // to the next free v1/v2 slot, falling back to v1.
        val agentMap = linkedMapOf<String, String>() // raw alias -> lrc id
        lyrics.forEach { line ->
            val raw = line.element?.singer?.lowercase() ?: return@forEach
            if (raw !in agentMap) {
                agentMap[raw] = when {
                    raw == "v1" || raw == "v2" || raw == "v1000" -> raw
                    else -> {
                        val taken = agentMap.values.toSet()
                        listOf("v1", "v2").firstOrNull { it !in taken } ?: "v1"
                    }
                }
            }
        }
        val isMultiAgent = agentMap.size > 1 ||
            (agentMap.size == 1 && !agentMap.containsKey("v1"))

        val sb = StringBuilder(lyrics.size * 128)
        var lastWasBg = false

        for (line in lyrics) {
            val mainWords = line.syllabus?.filter { !it.isBackground } ?: emptyList()
            val bgWords   = line.syllabus?.filter {  it.isBackground } ?: emptyList()

            val isFullBgLine = line.syllabus != null &&
                mainWords.isEmpty() && bgWords.isNotEmpty()

            val mainText = when {
                isWordSync && mainWords.isNotEmpty() -> buildText(mainWords)
                isFullBgLine                         -> ""
                else                                 -> line.text.trim()
            }

            // main line
            if (mainText.isNotBlank()) {
                lastWasBg = false
                val agentId  = agentMap[line.element?.singer?.lowercase()]
                val agentTag = if (isMultiAgent && agentId != null) "{agent:$agentId}" else ""
                sb.appendLrcLine(line.time, agentTag, mainText)
                if (isWordSync && mainWords.isNotEmpty()) sb.appendWordBlock(mainWords)
            }

            // background vocals
            val bgToEmit = when {
                bgWords.isNotEmpty() -> bgWords
                else                 -> emptyList()
            }
            if (bgToEmit.isNotEmpty()) {
                val bgText = if (isWordSync) buildText(bgToEmit) else line.text.trim()
                if (bgText.isNotBlank()) {
                    val bgTime = bgToEmit.minOf { it.time }
                    val bgTag  = if (lastWasBg) "" else "{bg}"
                    sb.appendLrcLine(bgTime, bgTag, bgText)
                    lastWasBg = true
                    if (isWordSync) sb.appendWordBlock(bgToEmit)
                }
            }
        }

        return sb.toString().trimEnd().ifBlank { null }
    }

    /** Joins word texts as-is (spaces are embedded in each text value by the API). */
    private fun buildText(words: List<LyricWord>): String =
        words.joinToString("") { it.text }.trim()

    /** Appends `[mm:ss.cc]<tag>text\n` */
    private fun StringBuilder.appendLrcLine(timeMs: Long, tag: String, text: String) {
        append(formatLrcTime(timeMs))
        append(tag)
        append(text)
        append('\n')
    }

    /** Appends `<word:startSec:endSec|...>\n` */
    private fun StringBuilder.appendWordBlock(words: List<LyricWord>) {
        val valid = words.filter { it.text.isNotBlank() }
        if (valid.isEmpty()) return
        append('<')
        valid.forEachIndexed { i, w ->
            val startSec = w.time / 1000.0
            val endSec   = (w.time + w.duration) / 1000.0
            append(w.text.trim())
            append(':').append(startSec)
            append(':').append(endSec)
            if (i < valid.lastIndex) append('|')
        }
        append(">\n")
    }

    private fun formatLrcTime(timeMs: Long): String {
        val m = timeMs / 60000
        val s = (timeMs % 60000) / 1000
        val c = (timeMs % 1000) / 10
        return buildString {
            append('[')
            if (m < 10) append('0')
            append(m).append(':')
            if (s < 10) append('0')
            append(s).append('.')
            if (c < 10) append('0')
            append(c).append(']')
        }
    }

    override suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = runCatching {
        val response = fetchLyrics(title, artist, duration, album)
        convertToLrc(response) ?: throw IllegalStateException("Lyrics unavailable")
    }

    override suspend fun getAllLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        getLyrics(context, id, title, artist, duration, album).onSuccess { callback(it) }
    }
}
