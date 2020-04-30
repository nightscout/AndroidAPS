package info.nightscout.androidaps.utils.androidNotification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import info.nightscout.androidaps.MainActivity
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHolder @Inject constructor(
    private val resourceHelper: ResourceHelper,
    private val context: Context
) {

    val channelID = "AndroidAPS-Ongoing"
    val notificationID = 4711
    var notification: Notification

    init {
        val stackBuilder = TaskStackBuilder.create(context)
            .addParentStack(MainActivity::class.java)
            .addNextIntent(Intent(context, MainApp::class.java))
        val builder = NotificationCompat.Builder(context, channelID)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setSmallIcon(resourceHelper.getNotificationIcon())
            .setLargeIcon(resourceHelper.decodeResource(resourceHelper.getIcon()))
            .setContentTitle(resourceHelper.gs(R.string.loading))
            .setContentIntent(stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT))
        notification = builder.build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(notificationID, notification)
    }
}
