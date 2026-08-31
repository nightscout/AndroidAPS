package app.aaps.plugins.aps.loop

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.aaps.core.data.configuration.Constants
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.CarbSuggestionActions
import app.aaps.core.interfaces.ui.UiInteraction
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
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AndroidLoopNotifier @Inject constructor(
    private val context: Context,
    private val rh: ResourceHelper,
    private val uiInteraction: UiInteraction,
    private val carbSuggestionActions: CarbSuggestionActions
) : LoopNotifier {

    private val notificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager

    // `by lazy`, not in an init block: this class is built when the graph resolves, and reaching a
    // system service there breaks the plain-JVM graph tests. First use is always a real notification.
    private val channel: Unit by lazy {
        @SuppressLint("WrongConstant") val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_ID,
            AndroidNotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)
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
            .setVibrate(VIBRATE_PATTERN)
        notificationManager.notify(Constants.NOTIFICATION_ID, builder.build())
    }

    override fun openLoopSuggestion(text: String, localOnly: Boolean) {
        channel
        val builder = baseBuilder(rh.gs(R.string.open_loop_new_suggestion), text)
        if (localOnly) builder.setLocalOnly(true)
        builder.setContentIntent(openTheApp())
        builder.setVibrate(VIBRATE_PATTERN)
        notificationManager.notify(Constants.NOTIFICATION_ID, builder.build())
    }

    override fun dismiss() {
        notificationManager.cancel(Constants.NOTIFICATION_ID)
    }

    private fun baseBuilder(title: String, text: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
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

    /**
     * An artificial back stack, so navigating back out of the opened screen leaves the app rather than
     * landing somewhere the user never navigated to.
     */
    private fun openTheApp(): PendingIntent {
        val resultIntent = Intent(context, uiInteraction.mainActivity.java)
        val stackBuilder = TaskStackBuilder.create(context)
        stackBuilder.addParentStack(uiInteraction.mainActivity.java)
        stackBuilder.addNextIntent(resultIntent)
        return stackBuilder.getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {

        const val CHANNEL_ID = "AAPS-OpenLoop"
        val VIBRATE_PATTERN = longArrayOf(1000, 1000, 1000, 1000, 1000)
    }
}
