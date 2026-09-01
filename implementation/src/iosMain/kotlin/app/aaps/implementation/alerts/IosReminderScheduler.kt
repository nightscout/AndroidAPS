package app.aaps.implementation.alerts

import app.aaps.core.interfaces.alerts.ReminderScheduler
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationInterruptionLevel.UNNotificationInterruptionLevelTimeSensitive
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

/**
 * Automation's alarm action, as a scheduled local notification.
 *
 * The interface warns that Android's `setAlarmClock` is exact, fires while the device is idle and
 * survives backgrounding, and that a best-effort scheduler would quietly weaken the feature. A
 * `UNTimeIntervalNotificationTrigger` meets that bar: iOS delivers it at the requested time even if
 * the app has been terminated since, because the notification is registered with the system rather
 * than kept in the process.
 *
 * This is worth contrasting with `SceneExpiryScheduler`, which iOS deliberately does **not**
 * implement. The difference is what has to happen at the deadline. A reminder only has to *ring*,
 * which a notification does. Scene expiry has to *run code* - revert an SMB toggle and a profile
 * switch - and a notification cannot do that. Same-looking problem, opposite answer.
 *
 * ## Limits worth knowing
 *
 * - It rings as a notification, so it obeys the user's notification settings for the app. Android's
 *   alarm clock is louder by nature.
 * - Each reminder gets its own identifier, so several can be outstanding, as on Android.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosReminderScheduler @Inject constructor(
    private val aapsLogger: AAPSLogger
) : ReminderScheduler {

    // Lazy, not eager: `currentNotificationCenter()` needs an app bundle and throws
    // `bundleProxyForCurrentProcess is nil` without one, so a field initialiser makes the class
    // impossible to construct in a test binary.
    private val center by lazy { UNUserNotificationCenter.currentNotificationCenter() }
    private var nextId = 0

    override fun scheduleReminder(seconds: Int, text: String) {
        // iOS refuses a non-positive interval, and a reminder in the past is meaningless anyway.
        if (seconds <= 0) {
            aapsLogger.error(LTag.AUTOMATION, "Reminder interval must be positive, was $seconds seconds")
            return
        }
        requestAuthorizationOnce()

        val content = UNMutableNotificationContent().apply {
            setBody(text)
            setSound(UNNotificationSound.defaultSound())
            // Breaks through Focus, which is what an alarm the user set is for.
            setInterruptionLevel(UNNotificationInterruptionLevelTimeSensitive)
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
            timeInterval = seconds.toDouble(),
            repeats = false
        )
        val request = UNNotificationRequest.requestWithIdentifier("$IDENTIFIER_PREFIX${nextId++}", content, trigger)
        center.addNotificationRequest(request) { error ->
            // The interface says implementations report their own failure; a caller gets no result
            // because there is nothing useful it could do with one.
            if (error != null) aapsLogger.error(LTag.AUTOMATION, "Cannot schedule reminder: $error")
            else aapsLogger.debug(LTag.AUTOMATION, "Reminder scheduled in ${seconds}s: $text")
        }
    }

    private fun requestAuthorizationOnce() {
        if (authorizationAsked) return
        authorizationAsked = true
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
            // Worth logging loudly: without permission the reminder is scheduled and never seen.
            if (!granted) aapsLogger.error(LTag.AUTOMATION, "Notifications not permitted, reminders will not appear")
        }
    }

    private var authorizationAsked = false

    internal companion object {

        const val IDENTIFIER_PREFIX = "aaps-reminder-"
    }
}
