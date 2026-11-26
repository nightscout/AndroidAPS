package app.aaps.implementation.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmIntent
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.alerts.Alert
import app.aaps.core.interfaces.alerts.AlertLifetime
import app.aaps.core.interfaces.alerts.AlertPlatform
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.BgAlarms
import app.aaps.core.interfaces.alerts.NightscoutAnnouncements
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.ui.AndroidActivities
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.ui.CoreUiStrings
import app.aaps.implementation.androidNotification.AlarmNotificationManager
import app.aaps.implementation.androidNotification.vibrator
import app.aaps.implementation.ui.UiInteractionImpl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Android binds the shared [AlertsImpl], because it has a [AlertPlatform]. A platform without one has no pump
 * alerts, and so no driver that needs them.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object AlertsAndroidBindings {

    @Provides
    fun alerts(impl: AlertsImpl): Alerts = impl

    /** The app's own alarms are alerts too, and the shared implementation needs the same platform half. */
    @Provides
    fun uiInteraction(impl: UiInteractionImpl): UiInteraction = impl

    /** The BG alarms raise through the alerts, so they are here where the alerts are. */
    @Provides
    fun bgAlarms(impl: BgAlarmsImpl): BgAlarms = impl

    /** A Nightscout announcement is an alert, so it is here where the alerts are. */
    @Provides
    fun nightscoutAnnouncements(impl: NightscoutAnnouncementsImpl): NightscoutAnnouncements = impl
}

/**
 * The Android side of pump alerts beyond the notification list:
 * - the alarm screen is `ErrorActivity` showing the alert, started at once when AAPS is in front, and otherwise woken
 *   with the screen through [AlarmNotificationManager.scheduleScreenWakeAndLaunch], as every background alarm;
 * - a WARNING sounds once through [AlarmNotificationManager.postChime];
 * - the end of a silence is an exact alarm to [AlertWakeReceiver], because a coroutine timer runs late while the
 *   phone sleeps.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidAlertPlatform(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    // Providers: both reach Android when they are built, which must not happen while the graph is built.
    private val androidActivities: () -> AndroidActivities,
    private val alarmNotificationManager: () -> AlarmNotificationManager,
    // A provider: the alert core takes this class
    private val alerts: () -> Alerts
) : AlertPlatform {

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

    override fun showAlarmScreen(alert: Alert) {
        val intent = Intent(context, androidActivities().alarm.java).apply {
            putExtra(AlarmIntent.EXTRA_ALERT_SOURCE, alert.source)
            putExtra(AlarmIntent.EXTRA_ALERT_KEY, alert.key)
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
            // In the background Android does not let an app put a screen over another app (#5338), unless the user
            // allowed AAPS to display over other apps. Started now for that case; the screen wake below still runs,
            // because a locked phone needs it, and the screen stays owed until the alert is answered.
            if (Settings.canDrawOverlays(context)) {
                try {
                    context.startActivity(intent)
                    aapsLogger.debug(LTag.NOTIFICATION, "Alarm screen started over other apps: ${alert.source}/${alert.key}")
                } catch (e: Exception) {
                    aapsLogger.error(LTag.NOTIFICATION, "Alarm screen did not start over other apps", e)
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

    // A tablet may have no vibrator: there Vibrate is not offered, and a stored one rings
    override val canVibrate: Boolean by lazy { vibrator(context).hasVibrator() }

    override fun chime(alert: Alert, sound: AlarmSound, ringtoneUri: String?, vibrateOnly: Boolean, breakThroughDnd: Boolean) {
        alarmNotificationManager().postChime(
            chimeKey = requestCode(alert.source, alert.key),
            title = rh.gs(alert.title),
            body = alert.text?.let { rh.gs(it) } ?: "",
            // The sound the core chose: the user's for this kind on this phone, else the alert's own
            sound = sound,
            ringtoneUri = ringtoneUri,
            actions = silenceButtons(alert),
            vibrateOnly = vibrateOnly,
            breakThroughDnd = breakThroughDnd
        )
    }

    /**
     * The silence buttons of a WARNING alert that lasts (decided 2026-11-24): 5, 15 and 30 min, as the loop's carbs
     * notification had. An EVENT has none: answering it ends it, whatever the length.
     */
    private fun silenceButtons(alert: Alert): List<NotificationCompat.Action> {
        if (alert.severity != AlertSeverity.WARNING || alert.lifetime != AlertLifetime.CONDITION) return emptyList()
        return Alerts.NOTIFICATION_SILENCE_MINUTES.map { minutes ->
            val intent = Intent(context, AlertSilenceReceiver::class.java)
                .setAction(AlertSilenceReceiver.actionOf(minutes))
                .putExtra(AlertSilenceReceiver.EXTRA_SOURCE, alert.source)
                .putExtra(AlertSilenceReceiver.EXTRA_KEY, alert.key)
                .putExtra(AlertSilenceReceiver.EXTRA_MINUTES, minutes)
            NotificationCompat.Action(
                0,
                rh.gs(CoreUiStrings.alarm_silence_for, rh.gs(CoreUiStrings.format_mins, minutes)),
                PendingIntent.getBroadcast(
                    context, requestCode(alert.source, alert.key), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }
    }

    /** A silence button of [chime]'s notification was tapped: the alert is silenced, and its notification goes. */
    fun silenceFromNotification(source: String, key: String, minutes: Int) {
        aapsLogger.debug(LTag.NOTIFICATION, "Alert $source/$key silenced from its notification for $minutes min")
        alerts().silence(source, key, minutes)
        alarmNotificationManager().cancelChime(requestCode(source, key))
    }

    override fun snoozed(alert: Alert, until: Long) {
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
        if (unanswered?.getStringExtra(AlarmIntent.EXTRA_ALERT_KEY) == key &&
            unanswered?.getStringExtra(AlarmIntent.EXTRA_ALERT_SOURCE) == source
        ) unanswered = null
        alarmNotificationManager().cancelChime(requestCode(source, key))
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(wakeIntent(source, key))
    }

    /**
     * `setAlarmClock`, as the automation reminder and the "time to eat" timer always used: exact, it fires while the
     * phone is idle, and it starts the app. It needs `SCHEDULE_EXACT_ALARM` unless AAPS is excluded from battery
     * optimization (`PluginPermissionsImpl` asks for it); without it nothing is booked. [ring] is not needed here: the
     * core raises the alert when the app wakes, and its sound follows the user's choice then.
     */
    override fun book(alert: Alert, at: Long, ring: Boolean): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) {
            aapsLogger.warn(LTag.NOTIFICATION, "Exact alarms not allowed - alert ${alert.source}/${alert.key} not booked")
            return false
        }
        val show = PendingIntent.getActivity(
            context, requestCode(alert.source, alert.key),
            Intent(context, androidActivities().main.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return try {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), bookingIntent(alert.source, alert.key))
            true
        } catch (e: SecurityException) {
            // The permission was taken away between the check above and this call.
            aapsLogger.error(LTag.NOTIFICATION, "Exact alarms revoked - alert ${alert.source}/${alert.key} not booked", e)
            false
        }
    }

    override fun unbook(source: String, key: String) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(bookingIntent(source, key))
    }

    private fun wakeIntent(source: String, key: String): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode(source, key),
        Intent(context, AlertWakeReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** The same receiver as [wakeIntent], but its own action, so a booking and a silence of one alert do not replace each other. */
    private fun bookingIntent(source: String, key: String): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode(source, key),
        Intent(context, AlertWakeReceiver::class.java).setAction(AlertWakeReceiver.ACTION_BOOKING),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** One number per alert, for its chime and its pending intents; 0..65535, so the chime ids stay in their range. */
    private fun requestCode(source: String, key: String): Int = "$source/$key".hashCode() and 0xFFFF
}
