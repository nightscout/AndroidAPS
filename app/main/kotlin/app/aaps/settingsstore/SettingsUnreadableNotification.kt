package app.aaps.settingsstore

import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.aaps.R
import app.aaps.core.interfaces.notifications.NotificationManager
import android.app.NotificationManager as AndroidNotificationManager
import app.aaps.implementation.R as ImplementationR

/**
 * The notification that says AAPS is not running because its settings cannot be opened.
 *
 * It cannot be swiped away: with the app closed, it is the only sign that the loop is not running. It goes
 * through the channel of the app's other urgent notifications, and opens [SettingsUnreadableActivity].
 */
object SettingsUnreadableNotification {

    /** Far from the small numbers the app's own notifications use. */
    private const val ID = 7_412_001

    fun show(context: Context, smallIcon: Int) {
        val manager = context.getSystemService(AndroidNotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(NotificationManager.CHANNEL_ID, context.getString(ImplementationR.string.notifications_channel_name), AndroidNotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            context, ID, Intent(context, SettingsUnreadableActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = context.getString(R.string.settings_unreadable_notification)
        val notification = NotificationCompat.Builder(context, NotificationManager.CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(context.getString(R.string.settings_unreadable_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        manager.notify(ID, notification)
    }

    /** Called on every normal start: the notification outlives the process that posted it. */
    fun cancel(context: Context) {
        context.getSystemService(AndroidNotificationManager::class.java).cancel(ID)
    }
}
