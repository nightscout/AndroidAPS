package app.aaps.implementation.pump.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmIntent
import app.aaps.core.interfaces.pump.alerts.PumpAlert
import app.aaps.core.interfaces.pump.alerts.PumpAlertPlatform
import app.aaps.core.interfaces.pump.alerts.PumpAlerts
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.ui.AndroidActivities
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.implementation.androidNotification.AlarmNotificationManager
import app.aaps.implementation.ui.UiInteractionImpl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Android binds the shared [PumpAlertsImpl], because it has a [PumpAlertPlatform]. A platform without one has no pump
 * alerts, and so no driver that needs them.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object PumpAlertsAndroidBindings {

    @Provides
    fun pumpAlerts(impl: PumpAlertsImpl): PumpAlerts = impl

    /** The app's own alarms are alerts too, and the shared implementation needs the same platform half. */
    @Provides
    fun uiInteraction(impl: UiInteractionImpl): UiInteraction = impl
}

/**
 * The Android side of pump alerts beyond the notification list:
 * - the alarm screen is `ErrorActivity` showing the alert, started at once when AAPS is in front, and otherwise woken
 *   with the screen through [AlarmNotificationManager.scheduleScreenWakeAndLaunch], as every background alarm;
 * - a WARNING sounds once through [AlarmNotificationManager.postChime];
 * - the end of a silence is an exact alarm to [PumpAlertWakeReceiver], because a coroutine timer runs late while the
 *   phone sleeps.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidPumpAlertPlatform(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    // Providers: both reach Android when they are built, which must not happen while the graph is built.
    private val androidActivities: () -> AndroidActivities,
    private val alarmNotificationManager: () -> AlarmNotificationManager
) : PumpAlertPlatform {

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /**
     * The alarm screen that was asked for while AAPS was not on the screen, and has not been shown since.
     *
     * Read by `ComposeMainActivity` when the app comes to the front, so an alarm that rang in the user's pocket is
     * the first thing they see (#5338). It used to live in `AlarmNotificationManager.unansweredAlarm`, set by
     * `postFullScreenAlarm` - the path `UiInteraction.runAlarm` took before every alarm became an alert.
     *
     * Cleared by [withdrawn], which every way of answering an alert ends in: the alert is resolved or silenced, and
     * either way its screen is no longer wanted. Guarded because it is written from the main thread and read from
     * whichever thread the activity resumes on.
     */
    @Volatile private var unanswered: Intent? = null

    override fun showAlarmScreen(alert: PumpAlert) {
        val intent = Intent(context, androidActivities().alarm.java).apply {
            putExtra(AlarmIntent.EXTRA_PUMP_ALERT_SOURCE, alert.source)
            putExtra(AlarmIntent.EXTRA_PUMP_ALERT_KEY, alert.key)
            putExtra(AlarmIntent.EXTRA_TITLE, rh.gs(alert.title))
            putExtra(AlarmIntent.EXTRA_STATUS, alert.text?.let { rh.gs(it) } ?: "")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        // ProcessLifecycleOwner is read on the main thread only.
        mainHandler.post {
            if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                try {
                    context.startActivity(intent)
                    unanswered = null
                    return@post
                } catch (e: Exception) {
                    aapsLogger.error(LTag.NOTIFICATION, "Pump alert screen did not start, waking the screen instead", e)
                }
            }
            // Not shown, so it is owed to the user the next time they open AAPS.
            unanswered = intent
            val pendingIntent = PendingIntent.getActivity(
                context, requestCode(alert.source, alert.key), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmNotificationManager().scheduleScreenWakeAndLaunch(pendingIntent)
        }
    }

    /** A copy, so the caller starting it cannot change what is held here. Null when nothing is owed. */
    fun unansweredAlarmIntent(): Intent? = unanswered?.let { Intent(it) }

    override fun chime(alert: PumpAlert) {
        alarmNotificationManager().postChime(
            chimeKey = requestCode(alert.source, alert.key),
            title = rh.gs(alert.title),
            body = alert.text?.let { rh.gs(it) } ?: ""
        )
    }

    override fun snoozed(alert: PumpAlert, until: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val wake = wakeIntent(alert.source, alert.key)
        try {
            if (alarmManager.canScheduleExactAlarms()) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, wake)
            else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, wake)
        } catch (e: SecurityException) {
            // The permission went away between the check and the call. The timer of the shared code still runs.
            aapsLogger.error(LTag.NOTIFICATION, "Pump alert silence end not scheduled", e)
        }
    }

    override fun withdrawn(source: String, key: String) {
        // Answered, resolved or silenced: whatever happened, its screen is no longer owed.
        if (unanswered?.getStringExtra(AlarmIntent.EXTRA_PUMP_ALERT_KEY) == key &&
            unanswered?.getStringExtra(AlarmIntent.EXTRA_PUMP_ALERT_SOURCE) == source
        ) unanswered = null
        alarmNotificationManager().cancelChime(requestCode(source, key))
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(wakeIntent(source, key))
    }

    private fun wakeIntent(source: String, key: String): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode(source, key),
        Intent(context, PumpAlertWakeReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** One number per alert, for its chime and its pending intents; 0..65535, so the chime ids stay in their range. */
    private fun requestCode(source: String, key: String): Int = "$source/$key".hashCode() and 0xFFFF
}
