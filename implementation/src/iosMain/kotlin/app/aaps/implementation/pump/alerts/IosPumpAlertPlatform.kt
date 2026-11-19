package app.aaps.implementation.pump.alerts

import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.alerts.PumpAlert
import app.aaps.core.interfaces.pump.alerts.PumpAlertPlatform
import app.aaps.core.interfaces.pump.alerts.PumpAlerts
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.implementation.lifecycle.observeAppForeground
import app.aaps.implementation.ui.UiInteractionImpl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSinceNow
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationInterruptionLevel.UNNotificationInterruptionLevelTimeSensitive
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNNotificationTrigger
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS binds the shared [PumpAlertsImpl], because it has a [PumpAlertPlatform]. The Android counterpart is
 * `PumpAlertsAndroidBindings`. Both exist so that a platform without a platform half has no pump alerts at all,
 * rather than alerts that quietly do nothing.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object PumpAlertsIosBindings {

    @Provides
    fun pumpAlerts(impl: PumpAlertsImpl): PumpAlerts = impl

    /** The app's own alarms are alerts too, and the shared implementation needs the same platform half. */
    @Provides
    fun uiInteraction(impl: UiInteractionImpl): UiInteraction = impl
}

/** Which alert the alarm screen should be showing. */
data class PumpAlertId(val source: String, val key: String)

/**
 * The iOS side of pump alerts beyond the notification list, which already owns the card and the ramping sound.
 *
 * ## The alarm screen is a request, not a window
 *
 * Android starts an activity and can wake the screen for it. iOS has neither, so [showAlarmScreen] only records which
 * alert wants the screen in [alarmScreen], and the Compose host in the app shows it while that is set. The practical
 * difference is that an alert raised while the app is in the background puts its screen up the moment the user opens
 * AAPS, instead of lighting the phone up at the time. The ring itself does not wait for that: the card carries the
 * system sound in the background, see `IosSystemNotificationPlatform.onAppForeground`.
 *
 * ## Why the silence end is a notification and not a timer
 *
 * A silence lasts [PumpAlerts.SILENCE_MINUTES] and the alert rings again when it ends. On Android that is an exact
 * alarm, which wakes the phone and runs code. iOS gives an app no way to run at a chosen time: a suspended process has
 * no timers and cannot un-suspend itself, so the coroutine timer inside [PumpAlertsImpl] only fires if the app happens
 * to be alive. A local notification is the one primitive that is delivered on time whether or not the process is
 * running, so [snoozed] schedules one and it *is* the re-ring the user hears.
 *
 * What it cannot do is run [PumpAlertsImpl.checkSilences], because a delivered notification does not start the app. So
 * the card in the list can still read as silenced for a while after the phone has already rung. [checkSilences] is
 * therefore called on every way back in - the app coming to the front, which includes a Bluetooth event that wakes it
 * - and brings the card in line then. The user hears the alarm on time either way, which is the part that matters.
 *
 * ## The identifier prefix matters
 *
 * `IosSystemNotificationPlatform` shares this notification centre and its `cancelAll` removes everything whose
 * identifier it recognises as its own (`aaps-<instanceKey>`). The prefixes below sit outside that filter, so "mute all
 * alarms" cannot sweep away a scheduled re-ring of an alert that is still on the pump.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IosPumpAlertPlatform(
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    /**
     * A provider, because [PumpAlertsImpl] takes this class: asking for it directly would be a dependency cycle. The
     * same shape `AndroidPumpAlertPlatform` uses for its two Android collaborators.
     */
    private val pumpAlerts: () -> PumpAlertsImpl,
    @ApplicationScope private val appScope: CoroutineScope
) : PumpAlertPlatform {

    private val _alarmScreen = MutableStateFlow<PumpAlertId?>(null)

    /** The alert whose alarm screen should be up, or null. The app host shows it; nothing here draws anything. */
    val alarmScreen: StateFlow<PumpAlertId?> = _alarmScreen.asStateFlow()

    /**
     * Resolved on first use, never in the constructor.
     *
     * `currentNotificationCenter()` needs an app bundle and raises `bundleProxyForCurrentProcess is nil` without one,
     * so this class can be built in a test binary but not post from it. The raise is an Objective-C exception, which
     * `runCatching` does not catch - it ends the process - so there is no guarding it either. That is why the state
     * this class keeps is changed before any call through here, and why [alarmScreenClosed] rather than [withdrawn]
     * is what a test exercises.
     */
    private val center by lazy { UNUserNotificationCenter.currentNotificationCenter() }

    init {
        observeAppForeground { inForeground ->
            if (inForeground) appScope.launch { pumpAlerts().checkSilences() }
        }
    }

    override fun showAlarmScreen(alert: PumpAlert) {
        aapsLogger.debug(LTag.NOTIFICATION, "Pump alert screen asked for: ${alert.source}/${alert.key}")
        _alarmScreen.value = PumpAlertId(alert.source, alert.key)
    }

    /** The alarm screen was closed by the user. The alert stays; only the request goes. */
    fun alarmScreenClosed(source: String, key: String) {
        if (_alarmScreen.value == PumpAlertId(source, key)) _alarmScreen.value = null
    }

    override fun chime(alert: PumpAlert) = post(chimeId(alert.source, alert.key), alert, trigger = null)

    override fun snoozed(alert: PumpAlert, until: Long) {
        val seconds = secondsFromNow(until)
        if (seconds < 1.0) {
            // Already over, or so close that iOS would refuse the trigger. checkSilences ends it the ordinary way.
            aapsLogger.debug(LTag.NOTIFICATION, "Pump alert silence of ${alert.source}/${alert.key} is already over, nothing scheduled")
            return
        }
        aapsLogger.debug(LTag.NOTIFICATION, "Pump alert ${alert.source}/${alert.key} rings again in ${seconds.toLong()}s")
        post(snoozeId(alert.source, alert.key), alert, UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false))
    }

    override fun withdrawn(source: String, key: String) {
        alarmScreenClosed(source, key)
        val ids = listOf(chimeId(source, key), snoozeId(source, key))
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        center.removeDeliveredNotificationsWithIdentifiers(ids)
    }

    /**
     * One notification for this alert, now ([trigger] null) or at a time.
     *
     * It carries the alert's own title and text, because that is what the user needs to read whether it is the one
     * sound of a WARNING or the re-ring at the end of a silence. The time-sensitive level is the only one delivered
     * through a Focus mode, and the closest match to the `IMPORTANCE_HIGH` channel Android uses for the same thing.
     * Critical alerts, which would also break through silent mode, need an entitlement from Apple that this project
     * does not have - a decision for the project rather than something to work around here.
     *
     * Permission is not asked for here. Every alert posts its card first ([PumpAlertsImpl] shows the card before it
     * calls this), and that goes through `IosSystemNotificationPlatform`, which asks once. If it was refused this
     * fails with the reason in the log rather than disappearing.
     */
    private fun post(identifier: String, alert: PumpAlert, trigger: UNNotificationTrigger?) {
        val content = UNMutableNotificationContent().apply {
            setTitle(rh.gs(alert.title))
            alert.text?.let { setBody(rh.gs(it)) }
            setSound(UNNotificationSound.defaultSound())
            setInterruptionLevel(UNNotificationInterruptionLevelTimeSensitive)
        }
        val request = UNNotificationRequest.requestWithIdentifier(identifier, content, trigger)
        center.addNotificationRequest(request) { error ->
            if (error != null) aapsLogger.error(LTag.NOTIFICATION, "Cannot post pump alert notification $identifier: $error")
        }
    }

    /**
     * How long from now until [until] (epoch ms), in seconds, which is what a `UNTimeIntervalNotificationTrigger`
     * takes.
     *
     * Through Foundation rather than `DateUtil`, so this class does not carry a 65-member interface it would use once
     * - and so it can be built in a test binary, where there is no Mockito to stand one in. It is the same clock
     * either way.
     */
    internal fun secondsFromNow(until: Long): Double = NSDate.dateWithTimeIntervalSince1970(until / 1000.0).timeIntervalSinceNow

    private fun chimeId(source: String, key: String) = "$CHIME_PREFIX$source/$key"
    private fun snoozeId(source: String, key: String) = "$SNOOZE_PREFIX$source/$key"

    companion object {

        /** Named once: the point of both is that they do not look like an `IosSystemNotificationPlatform` id. */
        private const val CHIME_PREFIX = "pump-alert-chime-"
        private const val SNOOZE_PREFIX = "pump-alert-snooze-"
    }
}
