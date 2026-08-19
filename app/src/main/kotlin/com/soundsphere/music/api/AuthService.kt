/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.api

import com.soundsphere.music.data.BackendEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AuthUser(
    val id: String,
    val email: String,
    val username: String,
    val avatarUrl: String?,
    val authProvider: String,
    val isVerified: Boolean,
    val role: String,
    val createdAt: String,
)

data class AuthToken(
    val token: String,
    val user: AuthUser,
)

/**
 * Thrown when the backend rejects the stored session token (HTTP 401).
 * The app reacts by clearing the token and returning to the account gate.
 */
class UnauthorizedException(message: String) : Exception(message)

object AuthService {
    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * Runs a backend call against the current [BackendEndpoint] host. If the
     * network layer fails (IOException), the request is retried once against
     * the fallback host so a dead primary domain never blocks login/sync.
     */
    private suspend fun <T> withBackend(
        build: (base: String) -> Request,
        parse: (Response) -> Result<T>,
    ): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                executeOnce(BackendEndpoint.current(), build, parse)
            } catch (e: IOException) {
                BackendEndpoint.markFailure()
                try {
                    executeOnce(BackendEndpoint.current(), build, parse)
                } catch (e2: IOException) {
                    Result.failure(e2)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun <T> executeOnce(
        base: String,
        build: (base: String) -> Request,
        parse: (Response) -> Result<T>,
    ): Result<T> = client.newCall(build(base)).execute().use { parse(it) }

    /**
     * Parses the backend error body into a human-readable message, preferring
     * the `detail` field which FastAPI/HTTPException returns.
     */
    private fun errorMessage(body: String?, code: Int): String {
        val detail =
            try {
                JSONObject(body ?: "").optString("detail")
            } catch (e: Exception) {
                null
            }
        return if (!detail.isNullOrBlank()) detail else "HTTP $code"
    }

    suspend fun register(
        email: String,
        password: String,
        username: String,
    ): Result<Unit> =
        withBackend(
            build = { base ->
                Request
                    .Builder()
                    .url("$base/auth/register")
                    .post(
                        JSONObject()
                            .put("email", email)
                            .put("password", password)
                            .put("username", username)
                            .toString()
                            .toRequestBody(JSON),
                    )
                    .build()
            },
            parse = { response ->
                val body = response.body?.string()
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(errorMessage(body, response.code)))
                }
            },
        )

    suspend fun verify(
        email: String,
        otp: String,
    ): Result<AuthToken> =
        withBackend(
            build = { base ->
                Request
                    .Builder()
                    .url("$base/auth/verify")
                    .post(
                        JSONObject()
                            .put("email", email)
                            .put("otp", otp)
                            .toString()
                            .toRequestBody(JSON),
                    )
                    .build()
            },
            parse = { response ->
                val body = response.body?.string()
                if (response.isSuccessful) {
                    Result.success(parseToken(body))
                } else {
                    Result.failure(Exception(errorMessage(body, response.code)))
                }
            },
        )

    suspend fun resendOtp(email: String): Result<Unit> =
        withBackend(
            build = { base ->
                Request
                    .Builder()
                    .url("$base/auth/resend-otp")
                    .post(JSONObject().put("email", email).toString().toRequestBody(JSON))
                    .build()
            },
            parse = { response ->
                val body = response.body?.string()
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(errorMessage(body, response.code)))
                }
            },
        )

    suspend fun login(
        email: String,
        password: String,
    ): Result<AuthToken> =
        withBackend(
            build = { base ->
                Request
                    .Builder()
                    .url("$base/auth/login")
                    .post(
                        JSONObject()
                            .put("email", email)
                            .put("password", password)
                            .toString()
                            .toRequestBody(JSON),
                    )
                    .build()
            },
            parse = { response ->
                val body = response.body?.string()
                if (response.isSuccessful) {
                    Result.success(parseToken(body))
                } else {
                    Result.failure(Exception(errorMessage(body, response.code)))
                }
            },
        )

    suspend fun forgotPassword(email: String): Result<Unit> =
        withBackend(
            build = { base ->
                Request
                    .Builder()
                    .url("$base/auth/forgot-password")
                    .post(JSONObject().put("email", email).toString().toRequestBody(JSON))
                    .build()
            },
            parse = { response ->
                val body = response.body?.string()
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(errorMessage(body, response.code)))
                }
            },
        )

    suspend fun resetPassword(
        email: String,
        otp: String,
        newPassword: String,
    ): Result<Unit> =
        withBackend(
            build = { base ->
                Request
                    .Builder()
                    .url("$base/auth/reset-password")
                    .post(
                        JSONObject()
                            .put("email", email)
                            .put("otp", otp)
                            .put("new_password", newPassword)
                            .toString()
                            .toRequestBody(JSON),
                    )
                    .build()
            },
            parse = { response ->
                val body = response.body?.string()
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(errorMessage(body, response.code)))
                }
            },
        )

    suspend fun me(token: String): Result<AuthUser> =
        withBackend(
            build = { base ->
                Request
                    .Builder()
                    .url("$base/auth/me")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            },
            parse = { response ->
                val body = response.body?.string()
                if (response.isSuccessful) {
                    Result.success(parseUser(body))
                } else {
                    val message = errorMessage(body, response.code)
                    if (response.code == 401) {
                        Result.failure(UnauthorizedException(message))
                    } else {
                        Result.failure(Exception(message))
                    }
                }
            },
        )

    /**
     * Updates the Soundsphere account profile on the backend (username and/or
     * avatar URL). Returns the updated user on success.
     */
    suspend fun updateProfile(
        token: String,
        username: String? = null,
        avatarUrl: String? = null,
    ): Result<AuthUser> =
        withBackend(
            build = { base ->
                val body = JSONObject()
                if (username != null) body.put("username", username)
                if (avatarUrl != null) body.put("avatar_url", avatarUrl)
                Request
                    .Builder()
                    .url("$base/user/profile")
                    .header("Authorization", "Bearer $token")
                    .put(body.toString().toRequestBody(JSON))
                    .build()
            },
            parse = { response ->
                val body = response.body?.string()
                if (response.isSuccessful) {
                    Result.success(parseUser(body))
                } else {
                    val message = errorMessage(body, response.code)
                    if (response.code == 401) {
                        Result.failure(UnauthorizedException(message))
                    } else {
                        Result.failure(Exception(message))
                    }
                }
            },
        )

    private fun parseUser(body: String?): AuthUser {
        val json = JSONObject(body ?: throw Exception("Empty response"))
        return AuthUser(
            id = json.optString("id"),
            email = json.optString("email"),
            username = json.optString("username"),
            avatarUrl = json.optString("avatar_url").ifBlank { null },
            authProvider = json.optString("auth_provider"),
            isVerified = json.optBoolean("is_verified"),
            role = json.optString("role", "user"),
            createdAt = json.optString("created_at"),
        )
    }

    private fun parseToken(body: String?): AuthToken {
        val json = JSONObject(body ?: throw Exception("Empty response"))
        val token = json.optString("token")
        val userJson = json.optJSONObject("user")
            ?: throw Exception("Invalid response")
        val user =
            AuthUser(
                id = userJson.optString("id"),
                email = userJson.optString("email"),
                username = userJson.optString("username"),
                avatarUrl = userJson.optString("avatar_url").ifBlank { null },
                authProvider = userJson.optString("auth_provider"),
                isVerified = userJson.optBoolean("is_verified"),
                role = userJson.optString("role", "user"),
                createdAt = userJson.optString("created_at"),
            )
        return AuthToken(token = token, user = user)
    }
}
