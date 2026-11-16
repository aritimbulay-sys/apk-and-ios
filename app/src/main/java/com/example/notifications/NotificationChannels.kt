package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.R

object NotificationChannels {
    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val alertsChannel = NotificationChannel(
            context.getString(R.string.channel_alerts_id),
            context.getString(R.string.channel_alerts_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_alerts_desc)
            enableVibration(true)
            enableLights(true)
        }

        val updatesChannel = NotificationChannel(
            context.getString(R.string.channel_updates_id),
            context.getString(R.string.channel_updates_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_updates_desc)
            enableVibration(true)
        }

        manager.createNotificationChannel(alertsChannel)
        manager.createNotificationChannel(updatesChannel)
    }
}
