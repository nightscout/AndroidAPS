package info.nightscout.androidaps.utils.androidNotification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import info.nightscout.androidaps.MainActivity
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.interfaces.IconsProvider
import info.nightscout.androidaps.interfaces.NotificationHolder
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHolderImpl @Inject constructor(
    resourceHelper: ResourceHelper,
    context: Context,
    iconsProvider: IconsProvider
) : NotificationHolder {

    override val channelID = "AndroidAPS-Ongoing"
    override val notificationID = 4711
    override var notification: Notification = NotificationCompat.Builder(context, channelID)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setSmallIcon(iconsProvider.getNotificationIcon())
        .setLargeIcon(resourceHelper.decodeResource(iconsProvider.getIcon()))
        .setContentTitle(resourceHelper.gs(R.string.loading))
        .setContentIntent(openAppIntent(context))
        .build()
        .also {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(notificationID, it)
        }

    override fun openAppIntent(context: Context): PendingIntent? = TaskStackBuilder.create(context).run {
        addParentStack(MainActivity::class.java)
        addNextIntent(Intent(context, MainActivity::class.java))
        getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
