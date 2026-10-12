package app.aaps.implementation.androidNotification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.implementation.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class NotificationHolderImpl(
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

    /**
     * The same intent the launcher icon sends, so a tap brings a minimized AAPS back as it is.
     * It used to be built with `TaskStackBuilder`, which adds `FLAG_ACTIVITY_CLEAR_TASK`: every tap
     * destroyed the running task and the overview was built and calculated again from zero.
     */
    override fun openAppIntent(): PendingIntent? =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, uiInteraction.mainActivity.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

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
        // Same id as before, so the user's settings stay. Android renames an existing channel.
        val channel = NotificationChannel(channelID, context.getString(R.string.ongoing_channel_name), NotificationManager.IMPORTANCE_HIGH)
        mNotificationManager.createNotificationChannel(channel)
    }
}
