package app.aaps.plugins.aps.loop

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.content.Context
import androidx.core.app.NotificationCompat
import app.aaps.core.data.configuration.Constants
import app.aaps.core.interfaces.notifications.DoNotDisturbRule
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.CarbSuggestionActions
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.aps.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import android.app.NotificationManager as AndroidNotificationManager

/**
 * The Android half of [LoopNotifier]. All of this used to sit inside `LoopPlugin`, and it was the only
 * thing keeping a ~1000-line loop algorithm out of `commonMain`.
 *
 * One notification id for both shapes, as before: the carbs and open-loop notifications replace each
 * other rather than stacking, and [dismiss] takes down whichever is showing.
 *
 * The notification is `CATEGORY_ALARM`, so Do Not Disturb lets it ring when the profile allows alarms.
 * While Do Not Disturb is on and "Override Do Not Disturb mode" is off it goes to a channel that only
 * vibrates, as every alarm does, see [DoNotDisturbRule].
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidLoopNotifier(
    private val context: Context,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val notificationHolder: NotificationHolder,
    private val carbSuggestionActions: CarbSuggestionActions
) : LoopNotifier {

    private val notificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager

    // `by lazy`, not in an init block: this class is built when the graph resolves, and reaching a
    // system service there breaks the plain-JVM graph tests. First use is always a real notification.
    private val channel: Unit by lazy {
        // Since Android 8 only the channel decides the vibration, and an app cannot change it on an
        // existing channel. The old channel did not vibrate, so this one has a new id (#5217).
        notificationManager.deleteNotificationChannel(OLD_CHANNEL_ID)
        @SuppressLint("WrongConstant") val channel = NotificationChannel(
            CHANNEL_ID,
            rh.gs(R.string.loop_notification_channel_name),
            AndroidNotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            vibrationPattern = VIBRATE_PATTERN
        }
        notificationManager.createNotificationChannel(channel)
        // The same without a sound, for Do Not Disturb with the override off
        @SuppressLint("WrongConstant") val silentChannel = NotificationChannel(
            SILENT_CHANNEL_ID,
            rh.gs(R.string.loop_notification_channel_silent_name),
            AndroidNotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = VIBRATE_PATTERN
        }
        notificationManager.createNotificationChannel(silentChannel)
    }

    override fun carbsRequired(text: String) {
        channel
        val builder = baseBuilder(rh.gs(R.string.carbs_suggestion), text)
        // Request codes 1..3 must stay distinct, or the three intents collide and Android hands back
        // the first one for all of them.
        builder
            .addAction(ignoreAction(R.string.ignore5m, "Ignore 5m", minutes = 5, requestCode = 1))
            .addAction(ignoreAction(R.string.ignore15m, "Ignore 15m", minutes = 15, requestCode = 2))
            .addAction(ignoreAction(R.string.ignore30m, "Ignore 30m", minutes = 30, requestCode = 3))
        notificationManager.notify(Constants.NOTIFICATION_ID, builder.build())
    }

    override fun openLoopSuggestion(text: String, localOnly: Boolean) {
        channel
        val builder = baseBuilder(rh.gs(R.string.open_loop_new_suggestion), text)
        if (localOnly) builder.setLocalOnly(true)
        builder.setContentIntent(notificationHolder.openAppIntent())
        notificationManager.notify(Constants.NOTIFICATION_ID, builder.build())
    }

    override fun dismiss() {
        notificationManager.cancel(Constants.NOTIFICATION_ID)
    }

    private fun baseBuilder(title: String, text: String): NotificationCompat.Builder =
        NotificationCompat.Builder(
            context,
            if (DoNotDisturbRule.staysQuiet(preferences.get(BooleanKey.AlertOverrideDoNotDisturb), notificationManager.currentInterruptionFilter)) SILENT_CHANNEL_ID
            else CHANNEL_ID
        )
            .setSmallIcon(app.aaps.core.ui.R.drawable.notif_icon)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    private fun ignoreAction(label: Int, fallback: String, minutes: Int, requestCode: Int) =
        NotificationCompat.Action(
            app.aaps.core.ui.R.drawable.ic_notif_aaps,
            rh.gs(label, fallback),
            carbSuggestionActions.ignoreFor(minutes = minutes, requestCode = requestCode)
        )

    private companion object {

        const val OLD_CHANNEL_ID = "AAPS-OpenLoop"
        const val CHANNEL_ID = "AAPS-OpenLoop-v2"
        const val SILENT_CHANNEL_ID = "AAPS-OpenLoop-v2-silent"
        val VIBRATE_PATTERN = longArrayOf(1000, 1000, 1000, 1000, 1000)
    }
}
