package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.CampusAlert

object CampusNotificationHelper {
    const val CHANNEL_ALERTS_ID = "mec_campus_alerts"
    const val CHANNEL_EVENTS_ID = "mec_campus_events"
    const val EXTRA_NAVIGATE_ROUTE = "mec_navigate_route"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                enableVibration(true)
            }
            val eventsChannel = NotificationChannel(
                CHANNEL_EVENTS_ID,
                context.getString(R.string.notification_events_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_events_channel_desc)
                enableVibration(true)
            }
            manager.createNotificationChannel(alertsChannel)
            manager.createNotificationChannel(eventsChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showCampusAlertNotification(context: Context, alert: CampusAlert) {
        val payload = PushNotificationPayload(
            title = "[${alert.category} • ${alert.department}] ${alert.title}",
            body = alert.message,
            category = alert.category,
            department = alert.department,
            targetRoute = if (alert.category.equals("Event", ignoreCase = true)) "events" else "pulse",
            notificationId = alert.id.hashCode(),
            isEventUpdate = alert.category.equals("Event", ignoreCase = true)
        )
        showPushNotification(context, payload, includeCategoryPrefix = false)
    }

    fun showPushNotification(
        context: Context,
        payload: PushNotificationPayload,
        includeCategoryPrefix: Boolean = true
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val channelId = if (payload.isEventUpdate) CHANNEL_EVENTS_ID else CHANNEL_ALERTS_ID
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NAVIGATE_ROUTE, payload.targetRoute)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            payload.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedTitle = if (includeCategoryPrefix && !payload.title.startsWith("[")) {
            "[${payload.category} • ${payload.department}] ${payload.title}"
        } else {
            payload.title
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_mec_notification)
            .setColor(ContextCompat.getColor(context, R.color.mec_notification_accent))
            .setContentTitle(formattedTitle)
            .setContentText(payload.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(payload.notificationId, notification)
        } catch (_: SecurityException) {
            // Permission revoked at runtime; ignore gracefully
        }
    }

    fun showScheduleReminderNotification(
        context: Context,
        courseCode: String,
        title: String,
        room: String,
        time: String
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NAVIGATE_ROUTE, "schedule")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (courseCode + time).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = "$title • $time at $room"
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
            .setSmallIcon(R.drawable.ic_mec_notification)
            .setColor(ContextCompat.getColor(context, R.color.mec_notification_accent))
            .setContentTitle("Upcoming MEC Class: $courseCode")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify((courseCode + time).hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission revoked at runtime; ignore gracefully
        }
    }
}
