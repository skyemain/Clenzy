package com.skyemain.clenzy

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

internal object ScanNotifications {
    const val channel = "ai_scan_results"
    const val notificationId = 1946

    fun ensureChannel(context: Context): NotificationManager {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channel, "AI scan results", NotificationManager.IMPORTANCE_DEFAULT))
        return manager
    }

    fun enabled(context: Context): Boolean {
        val manager = ensureChannel(context)
        return manager.areNotificationsEnabled() && manager.getNotificationChannel(channel).importance != NotificationManager.IMPORTANCE_NONE
    }

    fun completed(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (!enabled(context)) return
        val open = PendingIntent.getActivity(context, notificationId, Intent(context, SmartActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("AI scan complete")
            .setContentText("Tap to review your results.")
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }
}
