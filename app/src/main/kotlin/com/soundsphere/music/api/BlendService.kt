package com.soundsphere.music.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client for the second backend (`soundsphere-blend`) when double-project
 * isolation is enabled. Same JWT as the primary backend — same `JWT_SECRET`
 * on both hosts, so `AuthRepository.getToken()` works on both. See
 * `INVESTIGATION_BLEND.md` § "How a Second Backend Would Work".
 *
 * v1 fallback: if `BlendEndpoint.isConfigured()` is false or the second
 * project is still `INACTIVE` (e.g. `yuukseizasygckaeonrh` — keysheild),
 * callers should use `SyncService` (single-project) instead. This object
 * itself will just 404 until the second host is live — check `isConfigured()`
 * before calling.
 */
object BlendService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private fun errorMessage(body: String?, code: Int): String {
        return try { JSONObject(body ?: "").optString("detail") } catch (e: Exception) { null }
            ?.takeIf { it.isNotBlank() } ?: "HTTP $code"
    }

    private fun authRequest(token: String, method: String, path: String, jsonBody: JSONObject? = null, base: String = BlendEndpoint.current): Request {
        val builder = Request.Builder().url("$base$path").header("Authorization", "Bearer $token")
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete()
            else -> builder.method(method, jsonBody.toString().toRequestBody(JSON))
        }
        return builder.build()
    }

    private suspend fun execute(token: String, method: String, path: String, jsonBody: JSONObject? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            client.newCall(authRequest(token, method, path, jsonBody, BlendEndpoint.current)).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful) Result.success(body ?: "")
                else {
                    val msg = errorMessage(body, response.code)
                    if (response.code == 401) Result.failure(UnauthorizedException(msg)) else Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getCollaborators(token: String, playlistId: String): Result<List<SyncService.BlendCollaborator>> {
        val response = execute(token, "GET", "/user/playlists/$playlistId/collaborators")
        return response.mapCatching { body ->
            val arr = JSONObject(body).optJSONArray("collaborators") ?: return@mapCatching emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        SyncService.BlendCollaborator(
                            userId = obj.optString("user_id"),
                            username = obj.optString("username"),
                            avatarUrl = obj.optString("avatar_url").ifBlank { null },
                            isOwner = obj.optBoolean("is_owner", false),
                        ),
                    )
                }
            }
        }
    }
}
