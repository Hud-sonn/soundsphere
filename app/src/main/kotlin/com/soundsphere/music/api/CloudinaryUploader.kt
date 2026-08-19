/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Uploads user avatar images to Cloudinary using an unsigned upload preset.
 * The cloud name and the upload preset name are public identifiers — no
 * account credentials are shipped in the app. Deletion of a replaced avatar is
 * handled server-side by the backend (see backend-auth/services/cloudinary.py).
 */
object CloudinaryUploader {

    // Cloud name and unsigned upload preset — public identifiers, not secrets.
    // The account credentials (API key/secret) live only in the backend env.
    private const val CLOUD_NAME = "diojx8vfz"
    private const val UPLOAD_PRESET = "soundsphere_avatar"
    private const val FOLDER = "soundsphere/avatars"

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

    private fun isConfigured(): Boolean =
        !CLOUD_NAME.startsWith("YOUR_") && !UPLOAD_PRESET.startsWith("YOUR_")

    /**
     * Uploads [imageFile] and returns the secure delivery URL on success.
     */
    suspend fun uploadAvatar(imageFile: File): Result<String> =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) {
                return@withContext Result.failure(Exception("Avatar upload is not configured yet"))
            }
            try {
                val request =
                    Request
                        .Builder()
                        .url("https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload")
                        .post(
                            MultipartBody
                                .Builder()
                                .setType(MultipartBody.FORM)
                                .addFormDataPart(
                                    "file",
                                    imageFile.name,
                                    imageFile.asRequestBody("image/jpeg".toMediaType()),
                                )
                                .addFormDataPart("upload_preset", UPLOAD_PRESET)
                                .addFormDataPart("folder", FOLDER)
                                .build(),
                        )
                        .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful) {
                        val secureUrl = JSONObject(body ?: "{}").optString("secure_url")
                        if (secureUrl.isNotBlank()) {
                            Result.success(secureUrl)
                        } else {
                            Result.failure(Exception("Cloudinary returned no image URL"))
                        }
                    } else {
                        Result.failure(Exception("Avatar upload failed (HTTP ${response.code})"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}