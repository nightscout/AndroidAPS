package app.aaps.pump.carelevo.common

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.R
import app.aaps.pump.carelevo.CarelevoStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** An Android notification on its own Carelevo channel. A tap opens AAPS at its launch screen. */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AndroidCarelevoSystemNotifier @Inject constructor(
    private val context: Context,
    private val rh: ResourceHelper
) : CarelevoSystemNotifier {

    private val channelId = "carelevo_alarm_channel"

    override fun show(alarmId: String, title: String, text: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Made on each call: Android keeps one channel per id, so after the first time this changes nothing.
        // Its name and description are what the user sees in the system notification settings.
        notificationManager.createNotificationChannel(
            NotificationChannel(channelId, rh.gs(CarelevoStrings.carelevo_alarm_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = rh.gs(CarelevoStrings.carelevo_alarm_channel_description)
            }
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.notif_icon)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAapsIntent())
            .setAutoCancel(true)
            .build()
        notificationManager.notify(alarmId.hashCode(), notification)
    }

    private fun openAapsIntent(): PendingIntent {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) }
        return PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
