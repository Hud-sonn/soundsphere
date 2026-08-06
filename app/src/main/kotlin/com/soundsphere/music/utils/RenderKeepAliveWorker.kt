/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.utils

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.soundsphere.music.BuildConfig
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Periodically pings the backend /health endpoint so the free Render web
 * service (soundsphere-auth.onrender.com) does not spin down after its idle
 * timeout. Runs every 15 minutes while the device has network access; a ping
 * resets Render's idle timer, keeping first login and sync snappy.
 */
class RenderKeepAliveWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val healthUrl = "${BuildConfig.API_BASE_URL}/health"
        return try {
            client.newCall(Request.Builder().url(healthUrl).build()).execute().use { response ->
                Timber.tag(TAG).d("Render health ping: HTTP ${response.code}")
                if (response.isSuccessful) Result.success() else Result.retry()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Render health ping failed")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "RenderKeepAlive"
        private const val WORK_NAME = "RenderKeepAlive"

        private val client =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

        /** Idempotent; re-enqueuing keeps the same work name. */
        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<RenderKeepAliveWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
        }
    }
}
