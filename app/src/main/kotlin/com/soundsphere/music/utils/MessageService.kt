/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.utils

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.soundsphere.music.MainActivity
import com.soundsphere.music.R
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class AnnouncementMessage(
    val id: String,
    val title: String,
    val body: String,
    val dismissable: Boolean = true,
)

/**
 * Fetches the app's announcement feed from the repo's `messages.json` file on
 * GitHub raw. Mirrors [Updater]'s caching/TTL pattern so GitHub is not hit on
 * every launch — the feed is intentionally infrequent and general (feature
 * announcements, fixes, notices), not a messaging/chat system.
 */
object MessageService {
    const val ANNOUNCEMENTS_CHANNEL_ID = "announcements"
    const val ACTION_SHOW_ANNOUNCEMENTS = "com.soundsphere.music.action.SHOW_ANNOUNCEMENTS"

    private const val NOTIFICATION_ID = 1003
    private const val REQUEST_CODE = 1003

    private val client = HttpClient()
    var lastCheckTime = -1L
        private set

    private var cachedMessages: List<AnnouncementMessage> = emptyList()

    private const val CHECK_INTERVAL_MILLIS = 2 * 60 * 60 * 1000L // 2 hours
    private const val MESSAGES_RAW_URL =
        "https://raw.githubusercontent.com/Hud-sonn/soundsphere/main/messages.json"

    /**
     * Returns the subset of announcements the user has not seen yet, ordered
     * as they appear in the feed (newest first).
     */
    fun unseenMessages(
        messages: List<AnnouncementMessage>,
        seenIds: Set<String>,
    ): List<AnnouncementMessage> = messages.filter { it.id !in seenIds }

    /**
     * Shows a single system notification for the first (newest) unseen
     * announcement. Tapping it opens the announcements feed in the app. No-op
     * when the notification permission is missing on API 33+.
     */
    fun postAnnouncementNotification(context: Context, message: AnnouncementMessage) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent =
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_SHOW_ANNOUNCEMENTS
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(context, ANNOUNCEMENTS_CHANNEL_ID)
                .setSmallIcon(R.drawable.newspaper)
                .setContentTitle(message.title)
                .setContentText(message.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked after the check; treat as dismissed.
        }
    }

    /** Removes the announcement notification, e.g. once everything is read. */
    fun cancelAnnouncementNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * Fetch the announcement list, respecting the 2-hour cache.
     * Fails silently (empty result) when the file is missing or unreachable.
     */
    suspend fun fetchMessages(forceRefresh: Boolean = false): Result<List<AnnouncementMessage>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val shouldFetch =
                    forceRefresh ||
                        (System.currentTimeMillis() - lastCheckTime) > CHECK_INTERVAL_MILLIS

                if (!shouldFetch && cachedMessages.isNotEmpty()) {
                    return@runCatching cachedMessages
                }

                val response = client.get(MESSAGES_RAW_URL).bodyAsText()
                val jsonArray = JSONArray(response)

                val messages = buildList {
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        add(
                            AnnouncementMessage(
                                id = obj.getString("id"),
                                title = obj.getString("title"),
                                body = obj.getString("body"),
                                dismissable = obj.optBoolean("dismissable", true),
                            )
                        )
                    }
                }

                cachedMessages = messages
                lastCheckTime = System.currentTimeMillis()
                messages
            }
        }
}