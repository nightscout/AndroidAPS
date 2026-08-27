package app.aaps.implementation.androidNotification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
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

    override fun openAppIntent(): PendingIntent? = TaskStackBuilder.create(context).run {
        addParentStack(uiInteraction.mainActivity.java)
        addNextIntent(Intent(context, uiInteraction.mainActivity.java))
        getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun placeholderNotification(): Notification {

        createNotificationChannel()

        return NotificationCompat.Builder(context, channelID)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setSmallIcon(iconsProvider.getNotificationIcon())
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, iconsProvider.getIcon()))
            .setContentTitle(rh.gs(app.aaps.core.ui.R.string.loading))
            .setContentIntent(openAppIntent())
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
