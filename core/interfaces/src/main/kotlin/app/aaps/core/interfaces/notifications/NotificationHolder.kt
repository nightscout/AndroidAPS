package app.aaps.core.interfaces.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context

interface NotificationHolder {

    val channelID: String
    val notificationID: Int
    var notification: Notification

    fun openAppIntent(context: Context): PendingIntent?
    fun createNotificationChannel()
}