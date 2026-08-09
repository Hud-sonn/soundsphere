/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.api

import com.soundsphere.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Track metadata as stored server-side in the `tracks` table. */
data class SyncTrack(
    val id: String,
    val title: String,
    val artist: String = "",
    val album: String? = null,
    val duration: Int = 0,
    val artworkUrl: String? = null,
    val source: String = "youtube",
    val genre: String? = null,
    val year: Int? = null,
)

data class SyncPlaylistTrack(
    val position: Int,
    val addedAt: String?,
    val track: SyncTrack,
)

data class SyncPlaylist(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val trackCount: Int,
    val tracks: List<SyncPlaylistTrack>,
)

data class SyncLikedEntry(
    val likedAt: String?,
    val track: SyncTrack,
)

data class SyncHistoryEntry(
    val playedAt: String?,
    val track: SyncTrack,
)

/**
 * Client for the /user/ account-sync API. Every call requires the Bearer
 * token stored by [AuthRepository]. HTTP 401 surfaces as
 * [UnauthorizedException] so the caller can drop the session; all other
 * failures come back as plain [Exception] with a readable message.
 */
object SyncService {
    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private const val BASE_URL = BuildConfig.API_BASE_URL

    private fun errorMessage(body: String?, code: Int): String {
        val detail =
            try {
                JSONObject(body ?: "").optString("detail")
            } catch (e: Exception) {
                null
            }
        return if (!detail.isNullOrBlank()) detail else "HTTP $code"
    }

    private fun trackJson(track: SyncTrack): JSONObject =
        JSONObject()
            .put("id", track.id)
            .put("title", track.title)
            .put("artist", track.artist)
            .put("album", track.album ?: JSONObject.NULL)
            .put("duration", track.duration)
            .put("artwork_url", track.artworkUrl ?: JSONObject.NULL)
            .put("source", track.source)
            .put("genre", track.genre ?: JSONObject.NULL)
            .put("year", track.year ?: JSONObject.NULL)

    private fun parseTrack(json: JSONObject): SyncTrack =
        SyncTrack(
            id = json.optString("id"),
            title = json.optString("title"),
            artist = json.optString("artist"),
            album = json.optString("album").ifBlank { null },
            duration = json.optInt("duration", 0),
            artworkUrl = json.optString("artwork_url").ifBlank { null },
            source = json.optString("source", "youtube"),
            genre = json.optString("genre").ifBlank { null },
            year = if (json.has("year") && !json.isNull("year")) json.optInt("year") else null,
        )

    private fun parsePlaylist(json: JSONObject): SyncPlaylist {
        val tracks = mutableListOf<SyncPlaylistTrack>()
        val arr = json.optJSONArray("tracks")
        for (i in 0 until (arr?.length() ?: 0)) {
            val item = arr?.optJSONObject(i) ?: continue
            val trackJson = item.optJSONObject("track") ?: continue
            tracks.add(
                SyncPlaylistTrack(
                    position = item.optInt("position", 0),
                    addedAt = item.optString("added_at").ifBlank { null },
                    track = parseTrack(trackJson),
                ),
            )
        }
        return SyncPlaylist(
            id = json.optString("id"),
            name = json.optString("name"),
            coverUrl = json.optString("cover_url").ifBlank { null },
            createdAt = json.optString("created_at").ifBlank { null },
            updatedAt = json.optString("updated_at").ifBlank { null },
            trackCount = json.optInt("track_count", tracks.size),
            tracks = tracks,
        )
    }

    /** Executes a request, mapping 401 to [UnauthorizedException]. */
    private suspend fun execute(
        token: String,
        request: Request,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful) {
                        Result.success(body ?: "")
                    } else {
                        val message = errorMessage(body, response.code)
                        if (response.code == 401) {
                            Result.failure(UnauthorizedException(message))
                        } else {
                            Result.failure(Exception(message))
                        }
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun authRequest(
        token: String,
        method: String,
        path: String,
        jsonBody: JSONObject? = null,
    ): Request {
        val builder = Request.Builder().url("$BASE_URL$path").header("Authorization", "Bearer $token")
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete()
            else -> builder.method(method, jsonBody.toString().toRequestBody(JSON))
        }
        return builder.build()
    }

    // ===== Liked tracks =====

    suspend fun getLiked(token: String): Result<List<SyncLikedEntry>> {
        val response = execute(token, authRequest(token, "GET", "/user/liked"))
        return response.mapCatching { body ->
            val arr = JSONObject(body).optJSONArray("tracks") ?: return@mapCatching emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val track = item.optJSONObject("track") ?: continue
                    add(
                        SyncLikedEntry(
                            likedAt = item.optString("liked_at").ifBlank { null },
                            track = parseTrack(track),
                        ),
                    )
                }
            }
        }
    }

    suspend fun likeTrack(token: String, track: SyncTrack): Result<Unit> {
        val response = execute(token, authRequest(token, "POST", "/user/liked/${track.id}", trackJson(track)))
        return response.map { Unit }
    }

    suspend fun unlikeTrack(token: String, trackId: String): Result<Unit> {
        val response = execute(token, authRequest(token, "DELETE", "/user/liked/$trackId"))
        return response.map { Unit }
    }

    // ===== Playlists =====

    suspend fun getPlaylists(token: String): Result<List<SyncPlaylist>> {
        val response = execute(token, authRequest(token, "GET", "/user/playlists"))
        return response.mapCatching { body ->
            // The server returns a bare JSON array of playlist objects.
            val arr = JSONArray(body)
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(parsePlaylist(it)) }
                }
            }
        }
    }

    suspend fun createPlaylist(token: String, name: String): Result<SyncPlaylist> {
        val body = JSONObject().put("name", name)
        val response = execute(token, authRequest(token, "POST", "/user/playlists", body))
        return response.mapCatching { parsePlaylist(JSONObject(it)) }
    }

    suspend fun renamePlaylist(token: String, serverId: String, name: String): Result<Unit> {
        val body = JSONObject().put("name", name)
        val response = execute(token, authRequest(token, "PUT", "/user/playlists/$serverId", body))
        return response.map { Unit }
    }

    suspend fun deletePlaylist(token: String, serverId: String): Result<Unit> {
        val response = execute(token, authRequest(token, "DELETE", "/user/playlists/$serverId"))
        return response.map { Unit }
    }

    suspend fun addPlaylistTrack(
        token: String,
        serverId: String,
        track: SyncTrack,
        position: Int? = null,
    ): Result<Unit> {
        val body = JSONObject().put("track", trackJson(track))
        if (position != null) body.put("position", position)
        val response = execute(token, authRequest(token, "POST", "/user/playlists/$serverId/tracks", body))
        return response.map { Unit }
    }

    suspend fun removePlaylistTrack(token: String, serverId: String, trackId: String): Result<Unit> {
        val response = execute(token, authRequest(token, "DELETE", "/user/playlists/$serverId/tracks/$trackId"))
        return response.map { Unit }
    }

    // ===== History =====

    suspend fun getHistory(token: String): Result<List<SyncHistoryEntry>> {
        val response = execute(token, authRequest(token, "GET", "/user/history"))
        return response.mapCatching { body ->
            val arr = JSONObject(body).optJSONArray("history") ?: return@mapCatching emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val track = item.optJSONObject("track") ?: continue
                    add(
                        SyncHistoryEntry(
                            playedAt = item.optString("played_at").ifBlank { null },
                            track = parseTrack(track),
                        ),
                    )
                }
            }
        }
    }

    suspend fun addHistory(
        token: String,
        track: SyncTrack,
        playedAt: String,
    ): Result<Unit> {
        val body = JSONObject().put("track", trackJson(track)).put("played_at", playedAt)
        val response = execute(token, authRequest(token, "POST", "/user/history", body))
        return response.map { Unit }
    }

    // ===== Followed artists =====

    data class SyncFollowedArtist(
        val id: String,
        val name: String,
        val followedAt: String?,
    )

    suspend fun getFollowedArtists(token: String): Result<List<SyncFollowedArtist>> {
        val response = execute(token, authRequest(token, "GET", "/user/follows"))
        return response.mapCatching { body ->
            val arr = JSONObject(body).optJSONArray("artists") ?: return@mapCatching emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    add(
                        SyncFollowedArtist(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            followedAt = item.optString("followed_at").ifBlank { null },
                        ),
                    )
                }
            }
        }
    }

    suspend fun followArtist(token: String, artistId: String, artistName: String): Result<Unit> {
        val body = JSONObject().put("artist_name", artistName)
        val response = execute(token, authRequest(token, "POST", "/user/follows/$artistId", body))
        return response.map { Unit }
    }

    suspend fun unfollowArtist(token: String, artistId: String): Result<Unit> {
        val response = execute(token, authRequest(token, "DELETE", "/user/follows/$artistId"))
        return response.map { Unit }
    }

    // ===== Settings =====

    suspend fun getSettings(token: String): Result<Map<String, Any?>> {
        val response = execute(token, authRequest(token, "GET", "/user/settings"))
        return response.mapCatching { body ->
            val obj = JSONObject(body).optJSONObject("settings") ?: return@mapCatching emptyMap()
            obj.keys().asSequence().associateWith { key ->
                when {
                    obj.isNull(key) -> null
                    else -> obj.get(key)
                }
            }
        }
    }

    suspend fun updateSettings(token: String, settings: Map<String, Any?>): Result<Unit> {
        val jsonBody = JSONObject()
        settings.forEach { (k, v) -> jsonBody.put(k, v ?: JSONObject.NULL) }
        val body = JSONObject().put("settings", jsonBody)
        val response = execute(token, authRequest(token, "PUT", "/user/settings", body))
        return response.map { Unit }
    }
}
