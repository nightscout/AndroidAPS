package app.aaps.implementation.alerts

import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.alerts.Alert
import app.aaps.core.interfaces.alerts.AlertLifetime
import app.aaps.core.interfaces.alerts.AlertPlatform
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.BgAlarms
import app.aaps.core.interfaces.alerts.NightscoutAnnouncements
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.IosNotificationDelegate
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.ui.CoreUiStrings
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
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationInterruptionLevel.UNNotificationInterruptionLevelTimeSensitive
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNNotificationTrigger
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS binds the shared [AlertsImpl], because it has a [AlertPlatform]. The Android counterpart is
 * `AlertsAndroidBindings`. Both exist so that a platform without a platform half has no pump alerts at all,
 * rather than alerts that quietly do nothing.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object AlertsIosBindings {

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
 * A silence lasts [Alerts.SILENCE_MINUTES] and the alert rings again when it ends. On Android that is an exact
 * alarm, which wakes the phone and runs code. iOS gives an app no way to run at a chosen time: a suspended process has
 * no timers and cannot un-suspend itself, so the coroutine timer inside [AlertsImpl] only fires if the app happens
 * to be alive. A local notification is the one primitive that is delivered on time whether or not the process is
 * running, so [snoozed] schedules one and it *is* the re-ring the user hears.
 *
 * What it cannot do is run [AlertsImpl.checkSilences], because a delivered notification does not start the app. So
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
class IosAlertPlatform(
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    /**
     * A provider, because [AlertsImpl] takes this class: asking for it directly would be a dependency cycle. The
     * same shape `AndroidAlertPlatform` uses for its two Android collaborators.
     */
    private val alerts: () -> AlertsImpl,
    @ApplicationScope private val appScope: CoroutineScope
) : AlertPlatform {

    private val _alarmScreen = MutableStateFlow<AlertId?>(null)

    /** The alert whose alarm screen should be up, or null. The app host shows it; nothing here draws anything. */
    val alarmScreen: StateFlow<AlertId?> = _alarmScreen.asStateFlow()

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
            if (inForeground) appScope.launch {
                alerts().checkSilences()
                // A booking whose notification rang while the app was not running becomes the real alert now
                alerts().fireDue()
            }
        }
    }

    override fun showAlarmScreen(alert: Alert) {
        aapsLogger.debug(LTag.NOTIFICATION, "Pump alert screen asked for: ${alert.source}/${alert.key}")
        _alarmScreen.value = AlertId(alert.source, alert.key)
    }

    /** The alarm screen was closed by the user. The alert stays; only the request goes. */
    fun alarmScreenClosed(source: String, key: String) {
        if (_alarmScreen.value == AlertId(source, key)) _alarmScreen.value = null
    }

    // The system sound: iOS cannot play an mp3 as a notification sound, so the chosen [sound] is the alarm's only.
    // [ringtoneUri] is never set on iOS: an app can play only the sounds it ships.
    override fun chime(alert: Alert, sound: AlarmSound, ringtoneUri: String?) {
        // Logged, so a chime raised at a start a moment before a tap that started the app silences it can be seen.
        // Accepted as it is (decided 2026-11-25): the core does not hold an alert back (ALARMS.md, decision 9).
        aapsLogger.debug(LTag.NOTIFICATION, "Pump alert chime: ${alert.source}/${alert.key}")
        post(chimeId(alert.source, alert.key), alert, trigger = null, category = categoryOf(alert))
    }

    override fun snoozed(alert: Alert, until: Long) {
        val seconds = secondsFromNow(until)
        if (seconds < 1.0) {
            // Already over, or so close that iOS would refuse the trigger. checkSilences ends it the ordinary way.
            aapsLogger.debug(LTag.NOTIFICATION, "Pump alert silence of ${alert.source}/${alert.key} is already over, nothing scheduled")
            return
        }
        aapsLogger.debug(LTag.NOTIFICATION, "Pump alert ${alert.source}/${alert.key} rings again in ${seconds.toLong()}s")
        // The booking is for a suspended app only. A running app ends the silence on time itself and takes the booking
        // back (`withdrawn`); booked for the same second, both rang, and the tray had the card, the chime and the
        // booking (2026-11-25). So it is booked a little later: a suspended app re-rings that much late.
        post(
            snoozeId(alert.source, alert.key), alert,
            UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds + REBOOK_LATER_SECONDS, repeats = false),
            category = categoryOf(alert)
        )
    }

    override fun withdrawn(source: String, key: String) {
        alarmScreenClosed(source, key)
        val ids = listOf(chimeId(source, key), snoozeId(source, key))
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        center.removeDeliveredNotificationsWithIdentifiers(ids)
    }

    /**
     * A notification at [at], for the same reason as [snoozed]: iOS cannot run the app at a time, but it delivers a
     * notification then. When the app runs at that time the core raises the alert itself and [unbook] takes this one
     * back, which may come a moment after it was shown - twice is better than never. A kind the user made silent
     * ([ring] false) books a notification without sound.
     */
    override fun book(alert: Alert, at: Long, ring: Boolean): Boolean {
        val seconds = secondsFromNow(at)
        // iOS refuses a trigger that is not in the future. The core's own timer raises one that is due now.
        if (seconds < 1.0) return true
        requestAuthorizationOnce()
        post(bookingId(alert.source, alert.key), alert, UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false), ring)
        aapsLogger.debug(LTag.NOTIFICATION, "Alert ${alert.source}/${alert.key} booked in ${seconds.toLong()}s")
        return true
    }

    override fun unbook(source: String, key: String) {
        val ids = listOf(bookingId(source, key))
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        center.removeDeliveredNotificationsWithIdentifiers(ids)
    }

    /**
     * A booking can be the first thing that posts (the "time to eat" timer, an automation reminder), before any card
     * made `IosSystemNotificationPlatform` ask, and without permission it would be booked and never seen.
     */
    private fun requestAuthorizationOnce() {
        if (authorizationAsked) return
        authorizationAsked = true
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
            if (!granted) aapsLogger.error(LTag.NOTIFICATION, "Notifications not permitted, booked alerts will not appear")
        }
    }

    private var authorizationAsked = false

    /**
     * One notification for this alert, now ([trigger] null) or at a time.
     *
     * It carries the alert's own title and text, because that is what the user needs to read whether it is the one
     * sound of a WARNING or the re-ring at the end of a silence. The time-sensitive level is the only one delivered
     * through a Focus mode, and the closest match to the `IMPORTANCE_HIGH` channel Android uses for the same thing.
     * Critical alerts, which would also break through silent mode, need an entitlement from Apple that this project
     * does not have - a decision for the project rather than something to work around here.
     *
     * Permission is not asked for here. Every alert posts its card first ([AlertsImpl] shows the card before it
     * calls this), and that goes through `IosSystemNotificationPlatform`, which asks once. If it was refused this
     * fails with the reason in the log rather than disappearing.
     */
    private fun post(identifier: String, alert: Alert, trigger: UNNotificationTrigger?, sound: Boolean = true, category: String? = null) {
        val content = UNMutableNotificationContent().apply {
            setTitle(rh.gs(alert.title))
            alert.text?.let { setBody(rh.gs(it)) }
            if (sound) setSound(UNNotificationSound.defaultSound())
            setInterruptionLevel(UNNotificationInterruptionLevelTimeSensitive)
            category?.let { setCategoryIdentifier(it) }
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

    private fun chimeId(source: String, key: String) = chimeIdOf(source, key)
    private fun snoozeId(source: String, key: String) = snoozeIdOf(source, key)
    private fun bookingId(source: String, key: String) = "$BOOKING_PREFIX$source/$key"

    /**
     * Registers the category of a WARNING alert that lasts: its notification has the silence buttons (5, 15, 30 min,
     * [Alerts.NOTIFICATION_SILENCE_MINUTES], decided 2026-11-24). A button silences the alert named by the
     * notification's identifier.
     *
     * The app host calls it at every start, once the language is set (the button titles are resolved here) and before
     * [IosNotificationDelegate.install]. `install` replaces the whole set of categories with what is registered so
     * far, so a category registered only with the first warning was missing after every relaunch: a warning still in
     * Notification Centre lost its buttons, and a tap that launched the app found no handler. Registering only
     * records, so a test binary can still build this class. Called again, it does nothing.
     */
    fun registerSilenceButtons() {
        if (silenceButtonsRegistered) return
        silenceButtonsRegistered = true
        IosNotificationDelegate.register(setOf(warningCategory())) { actionId, notificationId ->
            val minutes = silenceMinutesOf(actionId) ?: return@register false
            val alert = alertOf(notificationId) ?: return@register false
            aapsLogger.debug(LTag.NOTIFICATION, "Alert ${alert.source}/${alert.key} silenced from its notification for $minutes min")
            alerts().silence(alert.source, alert.key, minutes)
            true
        }
    }

    private var silenceButtonsRegistered = false

    private fun warningCategory(): UNNotificationCategory =
        UNNotificationCategory.categoryWithIdentifier(
            identifier = CATEGORY_WARNING,
            actions = Alerts.NOTIFICATION_SILENCE_MINUTES.map { minutes ->
                UNNotificationAction.actionWithIdentifier(
                    identifier = "$SILENCE_PREFIX$minutes",
                    title = rh.gs(CoreUiStrings.alarm_silence_for, rh.gs(CoreUiStrings.format_mins, minutes)),
                    options = 0u
                )
            },
            intentIdentifiers = emptyList<Any>(),
            options = 0u
        )

    /** The category of [alert]'s notification: the silence buttons for a WARNING that lasts, else none. */
    private fun categoryOf(alert: Alert): String? =
        if (alert.severity == AlertSeverity.WARNING && alert.lifetime == AlertLifetime.CONDITION) CATEGORY_WARNING else null

    companion object {

        /** Named once: the point of all three is that they do not look like an `IosSystemNotificationPlatform` id. */
        private const val CHIME_PREFIX = "pump-alert-chime-"
        private const val SNOOZE_PREFIX = "pump-alert-snooze-"
        internal const val BOOKING_PREFIX = "alert-booking-"

        /** How much later than the end of a silence its booked re-ring fires; see [snoozed]. */
        private const val REBOOK_LATER_SECONDS = 5.0

        internal const val CATEGORY_WARNING = "aaps-alert-warning"
        internal const val SILENCE_PREFIX = "aaps-alert-silence-"

        /** The minutes of a silence button, or null when [actionId] is not one of ours. */
        internal fun silenceMinutesOf(actionId: String): Int? =
            Alerts.NOTIFICATION_SILENCE_MINUTES.firstOrNull { actionId == "$SILENCE_PREFIX$it" }

        /** The alert a chime or a ring after a silence is for, read from its notification's identifier. */
        internal fun alertOf(notificationId: String): AlertId? {
            val rest = listOf(CHIME_PREFIX, SNOOZE_PREFIX).firstOrNull { notificationId.startsWith(it) }
                ?.let { notificationId.removePrefix(it) } ?: return null
            val slash = rest.indexOf('/')
            if (slash <= 0 || slash == rest.length - 1) return null
            return AlertId(rest.substring(0, slash), rest.substring(slash + 1))
        }

        /** Test access to the identifier of a chime and of a ring after a silence. */
        internal fun chimeIdOf(source: String, key: String) = "$CHIME_PREFIX$source/$key"
        internal fun snoozeIdOf(source: String, key: String) = "$SNOOZE_PREFIX$source/$key"
    }
}
