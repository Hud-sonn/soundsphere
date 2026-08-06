/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.utils

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.soundsphere.music.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Downloads the update APK in the background using WorkManager.
 *
 * Runs as a foreground service (data sync) so the download survives the app
 * being backgrounded, streams the APK into [Context.externalCacheDir] and
 * reports throttled progress via [setProgress] so that both the notification
 * and the Settings screen can observe it.
 *
 * On completion the job does NOT auto-install; it posts a "ready to install"
 * notification and waits for the user to tap it.
 */
class AppUpdateDownloadJob(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val downloadUrl = inputData.getString(KEY_DOWNLOAD_URL) ?: return Result.failure()
        val versionName = inputData.getString(KEY_VERSION_NAME).orEmpty()

        return try {
            val file = downloadApk(downloadUrl, versionName)
            postReadyToInstallNotification(file, versionName)
            Result.success(workDataOf(KEY_OUTPUT_FILE_PATH to file.absolutePath))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Update download failed")
            reportException(e)
            postFailureNotification(downloadUrl, versionName)
            Result.failure()
        }
    }

    /**
     * Called by WorkManager whenever it needs to (re)create the foreground
     * notification, e.g. after the process was killed. The notification must
     * be shown immediately on start, not after the first progress tick.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo(0)

    private suspend fun downloadApk(downloadUrl: String, versionName: String): File {
        // The foreground service must have a visible notification right away.
        setForeground(createForegroundInfo(0))

        val fileName = downloadUrl.substringAfterLast('/', downloadUrl.substringAfterLast('.'))
        val targetFile = File(context.externalCacheDir, fileName)
        // A previous (replaced) job may have left a partial file behind.
        targetFile.delete()

        val request = Request.Builder().url(downloadUrl).build()
        var lastProgressUpdate = 0L
        var lastReportedPercent = -1

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} while downloading update")
            }
            val contentLength = response.body?.contentLength() ?: -1L
            response.body?.byteStream()?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped) throw CancellationException("Worker stopped")
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val percent =
                            if (contentLength > 0) {
                                (totalRead * 100 / contentLength).toInt().coerceIn(0, 100)
                            } else {
                                -1 // unknown length, show indeterminate progress
                            }
                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate >= PROGRESS_THROTTLE_MILLIS || percent >= 100) {
                            lastProgressUpdate = now
                            lastReportedPercent = percent
                            reportProgress(percent, versionName)
                        }
                    }
                }
            }
        }

        if (targetFile.length() == 0L) {
            throw IOException("Downloaded update is empty")
        }
        // Make sure the final progress tick is visible.
        if (lastReportedPercent != 100) {
            reportProgress(100, versionName)
        }
        return targetFile
    }

    private suspend fun reportProgress(percent: Int, versionName: String) {
        setProgress(workDataOf(KEY_PROGRESS to percent))
        postNotification(
            DOWNLOAD_NOTIFICATION_ID,
            buildDownloadNotification(percent, versionName),
        )
    }

    private fun createForegroundInfo(percent: Int): ForegroundInfo {
        val notification = buildDownloadNotification(percent, versionName())
        return ForegroundInfo(
            DOWNLOAD_NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun buildDownloadNotification(percent: Int, versionName: String): Notification {
        val indeterminate = percent < 0
        val text =
            if (indeterminate) {
                versionName
            } else {
                context.getString(R.string.update_download_progress, percent)
            }
        return NotificationCompat
            .Builder(context, UPDATES_CHANNEL_ID)
            .setSmallIcon(R.drawable.update)
            .setContentTitle(context.getString(R.string.update_downloading_title))
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent.coerceAtLeast(0), indeterminate)
            .build()
    }

    private fun postReadyToInstallNotification(file: File, versionName: String) {
        // Remove the downloading notification; the ready-to-install
        // notification takes over with its own ID. The foreground service
        // stops automatically when the worker finishes.
        NotificationManagerCompat.from(context).cancel(DOWNLOAD_NOTIFICATION_ID)

        val installPending =
            PendingIntent.getBroadcast(
                context,
                INSTALL_REQUEST_CODE,
                Intent(context, AppUpdateInstallReceiver::class.java).apply {
                    action = AppUpdateInstallReceiver.ACTION_INSTALL
                    putExtra(AppUpdateInstallReceiver.EXTRA_FILE_PATH, file.absolutePath)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        // Secondary action: re-check what changed before installing.
        val changelogPending =
            PendingIntent.getActivity(
                context,
                CHANGELOG_REQUEST_CODE,
                Intent(context, com.soundsphere.music.MainActivity::class.java).apply {
                    action = ACTION_SHOW_UPDATE_CHANGELOG
                    putExtra(EXTRA_UPDATE_SHEET_MODE_READY, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, UPDATES_CHANNEL_ID)
                .setSmallIcon(R.drawable.update)
                .setContentTitle(context.getString(R.string.update_download_ready_title))
                .setContentText(context.getString(R.string.update_download_ready_desc, versionName))
                .setContentIntent(installPending)
                .addAction(R.drawable.info, context.getString(R.string.whats_new), changelogPending)
                .setAutoCancel(true)
                .build()
        postNotification(READY_NOTIFICATION_ID, notification)
    }

    private fun postFailureNotification(downloadUrl: String, versionName: String) {
        NotificationManagerCompat.from(context).cancel(DOWNLOAD_NOTIFICATION_ID)
        val intent =
            Intent(context, AppUpdateDownloadReceiver::class.java).apply {
                action = AppUpdateDownloadReceiver.ACTION_START_DOWNLOAD
                putExtra(AppUpdateDownloadReceiver.EXTRA_URL, downloadUrl)
                putExtra(AppUpdateDownloadReceiver.EXTRA_VERSION_NAME, versionName)
            }
        val pending =
            PendingIntent.getBroadcast(
                context,
                RETRY_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, UPDATES_CHANNEL_ID)
                .setSmallIcon(R.drawable.update)
                .setContentTitle(context.getString(R.string.update_download_failed_title))
                .setContentText(context.getString(R.string.update_download_failed_desc))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
        postNotification(READY_NOTIFICATION_ID, notification)
    }

    private fun versionName(): String = inputData.getString(KEY_VERSION_NAME).orEmpty()

    private fun postNotification(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    companion object {
        private const val UPDATES_CHANNEL_ID = "updates"
        private const val DOWNLOAD_NOTIFICATION_ID = 1002
        const val READY_NOTIFICATION_ID = 1003
        private const val INSTALL_REQUEST_CODE = 1002
        private const val RETRY_REQUEST_CODE = 1003
        private const val CHANGELOG_REQUEST_CODE = 1004
        private const val PROGRESS_THROTTLE_MILLIS = 200L

        const val UPDATE_AVAILABLE_NOTIFICATION_ID = 1001

        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_VERSION_NAME = "version_name"
        const val KEY_OUTPUT_FILE_PATH = "output_file_path"
        const val KEY_PROGRESS = "progress"

        /** Used both as the unique work name and as the tag for observation. */
        const val WORK_NAME = "AppUpdateDownload"

        /** Opens the "what's new" sheet from a notification tap. */
        const val ACTION_SHOW_UPDATE_CHANGELOG = "com.soundsphere.music.action.SHOW_UPDATE_CHANGELOG"
        const val EXTRA_UPDATE_SHEET_MODE_READY = "update_sheet_mode_ready"
    }
}

/**
 * Enqueues update downloads. Re-enqueueing replaces any in-flight job, so at
 * most one download can run at a time.
 */
object AppUpdateDownloader {

    fun enqueue(context: Context, downloadUrl: String, versionName: String = "") {
        // Once the download starts, the "update available" and any stale
        // "ready to install" notifications are no longer relevant.
        NotificationManagerCompat.from(context).cancel(AppUpdateDownloadJob.UPDATE_AVAILABLE_NOTIFICATION_ID)
        NotificationManagerCompat.from(context).cancel(AppUpdateDownloadJob.READY_NOTIFICATION_ID)
        val request =
            OneTimeWorkRequestBuilder<AppUpdateDownloadJob>()
                .setInputData(
                    workDataOf(
                        AppUpdateDownloadJob.KEY_DOWNLOAD_URL to downloadUrl,
                        AppUpdateDownloadJob.KEY_VERSION_NAME to versionName,
                    ),
                )
                .addTag(AppUpdateDownloadJob.WORK_NAME)
                .build()
        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(
                AppUpdateDownloadJob.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
    }
}

/**
 * Starts the update download. Used as the retry target of the failure
 * notification.
 */
class AppUpdateDownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_START_DOWNLOAD) {
            val url = intent.getStringExtra(EXTRA_URL) ?: return
            AppUpdateDownloader.enqueue(context, url, intent.getStringExtra(EXTRA_VERSION_NAME).orEmpty())
        }
    }

    companion object {
        const val ACTION_START_DOWNLOAD = "com.soundsphere.music.action.START_UPDATE_DOWNLOAD"
        const val EXTRA_URL = "url"
        const val EXTRA_VERSION_NAME = "version_name"
    }
}

/**
 * Launches the APK install via the FileProvider when the user taps the
 * "ready to install" notification. The download itself never auto-installs;
 * this only hands the APK to Android's own installer.
 */
class AppUpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL) return
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: return
        installApk(context, filePath)
    }

    companion object {
        const val ACTION_INSTALL = "com.soundsphere.music.action.INSTALL_UPDATE"
        const val EXTRA_FILE_PATH = "file_path"

        /** Launches the system package installer for the given APK path. */
        fun installApk(context: Context, filePath: String) {
            val file = File(filePath)
            if (!file.exists()) return

            val uri =
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.FileProvider",
                    file,
                )
            val installIntent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(installIntent)
        }
    }
}
