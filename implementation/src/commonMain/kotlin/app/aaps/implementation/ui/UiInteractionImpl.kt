package app.aaps.implementation.ui

import app.aaps.core.interfaces.alerts.Alert
import app.aaps.core.interfaces.alerts.AlertAction
import app.aaps.core.interfaces.alerts.AlertLifetime
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.CoreAlert
import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAlarmDismissed
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.TextRef.Companion.withArgs
import app.aaps.core.ui.CoreUiStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The app's own alarms, as [Alerts] with [AlertLifetime.EVENT].
 *
 * Shared code, because there is nothing platform specific left in it. The card, the sound, the alarm screen, the
 * silence, the re-ring and the announcement all belong to [Alerts] and its platform half, which Android and iOS both
 * have. What this class adds is only the shape of an app alarm: the source, the severity, and the "+5 min" of the
 * "time to eat" timer.
 *
 * Before this, `runAlarm` was Android-only, 150 lines of activity starts, notification channels and an in-process
 * re-ring timer, and `IosUiInteraction.runAlarm` wrote a line in the log and did nothing. So a bolus failure
 * announced itself on Android and was silent on iOS, from the same shared caller
 * (`CommandQueueImplementation`). See `_docs/PUMP_DESIGN.md`, section 8.
 *
 * Not contributed from here: each platform binds it once it has a `AlertPlatform`, the same rule
 * `AlertsImpl` follows, so a platform without one has no alarms rather than alarms that do nothing.
 */
@SingleIn(AppScope::class)
@Inject
class UiInteractionImpl(
    private val aapsLogger: AAPSLogger,
    private val alerts: Alerts,
    // A provider: the registry builds the notification platform, which on Android reaches back here.
    private val notificationManager: () -> NotificationManager,
    private val dateUtil: DateUtil,
    private val rxBus: RxBus,
    // A provider: EatReminder needs UiInteraction back, to silence its own alarm.
    private val eatReminder: () -> EatReminder
) : UiInteraction {

    override fun runAlarm(key: String, status: String, title: String, sound: AlarmSound?) {
        aapsLogger.debug(LTag.CORE, "Alarm $key: $title - $status (sound=$sound)")
        alerts.raise(alarm(key, status, title, sound))
    }

    override fun runEatReminderAlarm(status: String, title: String) {
        aapsLogger.debug(LTag.CORE, "Eat reminder alarm: $title - $status")
        alerts.raise(eatReminderAlarm(status, title))
    }

    override fun scheduleAlarm(at: Long, key: String, status: String, title: String, sound: AlarmSound?): Boolean =
        alerts.schedule(alarm(key, status, title, sound), at)

    override fun scheduleEatReminderAlarm(at: Long, status: String, title: String): Boolean =
        alerts.schedule(eatReminderAlarm(status, title), at)

    override fun cancelScheduledAlarm(key: String) = alerts.unschedule(UiInteraction.SOURCE, key)

    private fun alarm(key: String, status: String, title: String, sound: AlarmSound?) =
        alert(key, status, title, sound, action = null, canMute = true, tellWatch = true)

    /**
     * The "time to eat" timer's alarm, with "+5 min" as the alert's own action.
     *
     * That button moves the **timer**, it does not delay the alarm: a user who needs five more minutes before eating
     * wants the reminder five minutes later, not the same reminder again. `extend()` silences this alarm itself and
     * starts the new one, and when it fails nothing has changed, so the alarm goes on ringing - which is why an
     * action that fails leaves an event alert exactly as it was.
     */
    private fun eatReminderAlarm(status: String, title: String) =
        alert(
            key = UiInteraction.KEY_EAT_REMINDER,
            status = status,
            title = title,
            sound = AlarmSound.EAT_REMINDER,
            action = AlertAction(
                label = CoreUiStrings.eat_reminder_extend.withArgs(EatReminder.EXTEND_MINUTES)
            ) { eatReminder().extend() },
            // "+5 min" already is the delay, so a "Mute 5 min" beside it would be two buttons five minutes apart.
            canMute = false,
            // The watch rings this one itself (`EatReminderWatch`), so telling it as well would show two reminders.
            tellWatch = false
        )

    /** One of the app's own alarms, raised now or booked; a booked one gets the time of its booking. */
    private fun alert(
        key: String,
        status: String,
        title: String,
        sound: AlarmSound?,
        action: AlertAction?,
        canMute: Boolean,
        tellWatch: Boolean
    ): Alert =
            Alert(
                // The app's own kind for this alarm; it is filed as the app's, not under the pump
                kind = CoreAlert.ofRunAlarmKey(key),
                key = key,
                // Always critical: every caller of runAlarm is telling the user that something they rely on did
                // not happen, and the alarm screen and the ringing card are exactly what they always got.
                severity = AlertSeverity.CRITICAL,
                // The caller's title is the headline on the alarm screen and on the notification, and its status is
                // the message under it - which is how this looked before every alarm became an alert.
                title = TextRef.Literal(title),
                text = TextRef.Literal(status),
                raisedAt = dateUtil.now(),
                action = action,
                lifetime = AlertLifetime.EVENT,
                notificationTitle = title,
                canMute = canMute,
                tellWatch = tellWatch,
                // Null has always meant the default alarm sound here, not silence: the Android path resolved it to
                // ERROR in the channel and in the player, and only a test ever passed null.
                sound = sound ?: AlarmSound.ERROR
            )

    /**
     * Silence whatever is ringing.
     *
     * Still the registry's `muteAllAlarms`, which is what this has always been, because the surfaces that reach here
     * - the watch, the Mute action, a swipe - cannot name an alarm. What is different is what happens to the alerts
     * it takes off the screen: [Alerts] reads a card of its own going away as the user's answer, so one of the
     * app's own alarms is over and a pump alert goes quiet for its silence window and comes back. Nothing has to be
     * told apart here.
     */
    override fun stopAlarm(reason: String) {
        aapsLogger.debug(LTag.CORE, "stopAlarm: $reason")
        notificationManager().muteAllAlarms()
    }

    override fun dismissAlarm(reason: String) {
        stopAlarm(reason)
        // The alarm screen is its own activity on Android: it is told, and closes itself. On iOS the alert leaving
        // `active` is what closes it, so this event is Android's only.
        rxBus.send(EventAlarmDismissed())
    }

    /**
     * Quiet now, ringing again in [minutes].
     *
     * A snooze of the one alert that is ringing, not a mute of everything: the card goes back without its sound, so
     * the phone is quiet, and the stored silence brings it back - after a restart too, and on iOS through a
     * notification scheduled for that moment. A second alarm that is also ringing keeps ringing, which is the point
     * of silencing one rather than all.
     *
     * When the sounding card is not an alert at all - a driver that still posts its own alarm, until step 3 reaches
     * it - there is nothing to snooze, so this silences everything instead. The user gets quiet either way; what
     * they do not get is the alarm coming back, and the log says so.
     */
    override fun muteAlarm(minutes: Int, reason: String) {
        val alert = alerts.sounding
        if (alert == null) {
            aapsLogger.debug(LTag.CORE, "muteAlarm: nothing ringing is an alert, silencing everything ($reason)")
            stopAlarm(reason)
            return
        }
        aapsLogger.debug(LTag.CORE, "muteAlarm ${alert.source}/${alert.key} for $minutes min: $reason")
        alerts.snooze(alert.source, alert.key, minutes)
    }

    override fun stopEatReminderAlarm() {
        aapsLogger.debug(LTag.CORE, "stopEatReminderAlarm")
        alerts.silence(UiInteraction.SOURCE, UiInteraction.KEY_EAT_REMINDER)
    }
}
