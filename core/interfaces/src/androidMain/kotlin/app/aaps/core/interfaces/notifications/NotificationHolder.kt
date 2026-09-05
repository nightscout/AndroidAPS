package app.aaps.core.interfaces.notifications

import android.app.Notification
import android.app.PendingIntent

interface NotificationHolder {

    val channelID: String
    val notificationID: Int
    var notification: Notification

    fun openAppIntent(): PendingIntent?
    fun createNotificationChannel()
}