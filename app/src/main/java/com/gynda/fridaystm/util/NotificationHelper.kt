package com.gynda.fridaystm.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gynda.fridaystm.MainActivity
import com.gynda.fridaystm.R

/**
 * Local notification helper for FCM-driven presensi / larkam reminders.
 *
 * Channel: [PRESENSI_CHANNEL_ID] ("Pengingat Presensi", High).
 * The channel is created lazily on every [showPresensiReminderNotification]
 * call — `createNotificationChannel` is idempotent, so no app-startup hook
 * is required.
 *
 * PendingIntent targets [MainActivity] (Dashboard). When the app is already
 * in the foreground the FCM data message still shows a heads-up notification
 * (FCM notification messages are suppressed by the system when foregrounded).
 * Extras from the FCM data payload are forwarded so the dashboard can deep-link
 * to CameraScreen if it wants (`target` = `camera`).
 */
object NotificationHelper {

    const val PRESENSI_CHANNEL_ID = "presensi_channel"
    private const val CHANNEL_NAME = "Pengingat Presensi"
    private const val CHANNEL_DESC = "Pengingat presensi & larkam Jumat pagi"

    const val NOTIFICATION_ID_PRESENSI = 1001

    /**
     * Ensures the presensi channel exists (no-op if it already does).
     * Must be called before posting any presensi notification on API 26+.
     */
    fun ensurePresensiChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(PRESENSI_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            PRESENSI_CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = CHANNEL_DESC
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Shows a high-priority presensi reminder notification.
     *
     * @param title notification title (e.g. "Jangan lupa presensi!")
     * @param body notification body (e.g. "Pembiasaan dimulai 06:30")
     * @param target optional deep-link hint forwarded to MainActivity
     *   (e.g. `"camera"` to open CameraScreen directly). Ignored when blank.
     */
    fun showPresensiReminderNotification(
        context: Context,
        title: String,
        body: String,
        target: String? = null,
    ) {
        ensurePresensiChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!target.isNullOrBlank()) {
                putExtra(EXTRA_TARGET, target)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, PRESENSI_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title.ifBlank { CHANNEL_NAME })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // On API 33+ the system silently drops the post if POST_NOTIFICATIONS
        // is not granted — the caller (HomeScreen) requests it; the service
        // just posts best-effort.
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PRESENSI, notification)
        } catch (_: SecurityException) {
            // Permission not granted on API 33+ — ignore.
        }
    }

    const val EXTRA_TARGET = "notification_target"

    // --- Test seam ---------------------------------------------------------

    /**
     * Pure extraction of title/body/target from an FCM [RemoteMessage]-like
     * map, isolated for JVM unit testing without the Firebase SDK.
     *
     * @param notificationTitle title from `remoteMessage.notification.title`
     * @param notificationBody body from `remoteMessage.notification.body`
     * @param data map from `remoteMessage.data`
     */
    fun resolveContent(
        notificationTitle: String?,
        notificationBody: String?,
        data: Map<String, String>,
    ): Triple<String, String, String?> {
        val title = notificationTitle?.takeIf { it.isNotBlank() }
            ?: data["title"]?.takeIf { it.isNotBlank() }
            ?: CHANNEL_NAME
        val body = notificationBody?.takeIf { it.isNotBlank() }
            ?: data["body"]?.takeIf { it.isNotBlank() }
            ?: data["message"]?.takeIf { it.isNotBlank() }
            ?: "Jangan lupa presensi Jumat pagi!"
        val target = data["target"]?.takeIf { it.isNotBlank() }
            ?: data["screen"]?.takeIf { it.isNotBlank() }
        return Triple(title, body, target)
    }
}
