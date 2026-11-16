package com.example.notifications

import android.Manifest
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
import com.example.data.AppPreferencesRepository
import com.example.data.NotificationCategory
import com.example.data.NotificationRecord
import java.util.concurrent.atomic.AtomicInteger

object HotsMonNotificationHelper {

    const val EXTRA_TARGET_URL = "extra_target_url"
    private val notificationIdCounter = AtomicInteger(1000)

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    suspend fun dispatchPushNotification(
        context: Context,
        repository: AppPreferencesRepository,
        title: String,
        message: String,
        targetUrl: String = AppPreferencesRepository.BASE_URL,
        category: NotificationCategory = NotificationCategory.WEB_PUSH,
        highPriority: Boolean = category == NotificationCategory.ALERT
    ): Boolean {
        NotificationChannels.ensureCreated(context)

        repository.addNotificationRecord(
            NotificationRecord(
                title = title,
                message = message,
                targetUrl = targetUrl,
                category = category,
                isRead = false
            )
        )

        if (!hasNotificationPermission(context)) {
            return false
        }

        val channelId = if (highPriority) {
            context.getString(R.string.channel_alerts_id)
        } else {
            context.getString(R.string.channel_updates_id)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_URL, targetUrl)
        }

        val requestCode = notificationIdCounter.incrementAndGet()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.hotsmon_icon_fg)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(
                if (highPriority) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
