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
import org.json.JSONObject
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

    private const val BASE_URL = BuildConfig.API_BASE_URL

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
        withContext(Dispatchers.IO) {
            try {
                val jsonBody =
                    JSONObject()
                        .put("email", email)
                        .put("password", password)
                        .put("username", username)
                val request =
                    Request
                        .Builder()
                        .url("$BASE_URL/auth/register")
                        .post(jsonBody.toString().toRequestBody(JSON))
                        .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful) {
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception(errorMessage(body, response.code)))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun verify(
        email: String,
        otp: String,
    ): Result<AuthToken> =
        withContext(Dispatchers.IO) {
            try {
                val jsonBody =
                    JSONObject()
                        .put("email", email)
                        .put("otp", otp)
                val request =
                    Request
                        .Builder()
                        .url("$BASE_URL/auth/verify")
                        .post(jsonBody.toString().toRequestBody(JSON))
                        .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful) {
                        Result.success(parseToken(body))
                    } else {
                        Result.failure(Exception(errorMessage(body, response.code)))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun resendOtp(email: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val jsonBody = JSONObject().put("email", email)
                val request =
                    Request
                        .Builder()
                        .url("$BASE_URL/auth/resend-otp")
                        .post(jsonBody.toString().toRequestBody(JSON))
                        .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful) {
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception(errorMessage(body, response.code)))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun login(
        email: String,
        password: String,
    ): Result<AuthToken> =
        withContext(Dispatchers.IO) {
            try {
                val jsonBody =
                    JSONObject()
                        .put("email", email)
                        .put("password", password)
                val request =
                    Request
                        .Builder()
                        .url("$BASE_URL/auth/login")
                        .post(jsonBody.toString().toRequestBody(JSON))
                        .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful) {
                        Result.success(parseToken(body))
                    } else {
                        Result.failure(Exception(errorMessage(body, response.code)))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun forgotPassword(email: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val jsonBody = JSONObject().put("email", email)
                val request =
                    Request
                        .Builder()
                        .url("$BASE_URL/auth/forgot-password")
                        .post(jsonBody.toString().toRequestBody(JSON))
                        .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful) {
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception(errorMessage(body, response.code)))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun resetPassword(
        email: String,
        otp: String,
        newPassword: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val jsonBody =
                    JSONObject()
                        .put("email", email)
                        .put("otp", otp)
                        .put("new_password", newPassword)
                val request =
                    Request
                        .Builder()
                        .url("$BASE_URL/auth/reset-password")
                        .post(jsonBody.toString().toRequestBody(JSON))
                        .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful) {
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception(errorMessage(body, response.code)))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun me(token: String): Result<AuthUser> =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request
                        .Builder()
                        .url("$BASE_URL/auth/me")
                        .header("Authorization", "Bearer $token")
                        .get()
                        .build()
                client.newCall(request).execute().use { response ->
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
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

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
