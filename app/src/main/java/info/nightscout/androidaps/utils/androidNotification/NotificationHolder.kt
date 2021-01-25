package info.nightscout.androidaps.utils.androidNotification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.NotificationHolderInterface
import info.nightscout.androidaps.utils.resources.IconsProvider
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHolder @Inject constructor(
    resourceHelper: ResourceHelper,
    context: Context,
    iconsProvider: IconsProvider
) : NotificationHolderInterface {

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
}
