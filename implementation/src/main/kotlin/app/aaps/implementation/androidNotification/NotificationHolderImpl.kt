package app.aaps.implementation.androidNotification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHolderImpl @Inject constructor(
    private val rh: ResourceHelper,
    private val context: Context,
    private val iconsProvider: IconsProvider,
    private val uiInteraction: UiInteraction
) : NotificationHolder {

    override val channelID = "AndroidAPS-Ongoing"
    override val notificationID = 4711
    private var _notification: Notification? = null
    override var notification: Notification
        set(value) {
            _notification = value
        }
        get() = _notification ?: placeholderNotification()

    override fun openAppIntent(context: Context): PendingIntent? = TaskStackBuilder.create(context).run {
        addParentStack(uiInteraction.mainActivity)
        addNextIntent(Intent(context, uiInteraction.mainActivity))
        getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun placeholderNotification(): Notification {

        createNotificationChannel()

        return NotificationCompat.Builder(context, channelID)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setSmallIcon(iconsProvider.getNotificationIcon())
            .setLargeIcon(rh.decodeResource(iconsProvider.getIcon()))
            .setContentTitle(rh.gs(app.aaps.core.ui.R.string.loading))
            .setContentIntent(openAppIntent(context))
            .build()
            .also {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(notificationID, it)
            }
    }

    override fun createNotificationChannel() {
        val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelID, channelID as CharSequence, NotificationManager.IMPORTANCE_HIGH)
        mNotificationManager.createNotificationChannel(channel)
    }
}
