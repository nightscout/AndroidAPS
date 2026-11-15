package app.aaps.implementation.alerts

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.CoreUiStrings
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
 * The "time to eat" alarm as a scheduled local notification, like `IosReminderScheduler`.
 *
 * The system holds the notification, so it rings at the time even when the app was terminated since. It has
 * one fixed identifier: scheduling again replaces the pending one, and [cancel] takes it back.
 *
 * Today the iOS app is a client, and a client never sets this alarm (the master owns the timer). It is still
 * a real implementation, so the shared timer is never silent on this platform.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class IosEatReminderAlarm(
    private val rh: TextResolver,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) : EatReminderAlarm {

    // Lazy for the same reason as in IosReminderScheduler: it needs an app bundle, which a test binary has not.
    private val center by lazy { UNUserNotificationCenter.currentNotificationCenter() }
    private var authorizationAsked = false

    override fun schedule(startedAt: Long, endsAt: Long): Boolean {
        val seconds = (endsAt - dateUtil.now()) / 1000.0
        // iOS refuses a non-positive interval.
        if (seconds <= 0) {
            aapsLogger.error(LTag.AUTOMATION, "Eat reminder end is not in the future")
            return false
        }
        requestAuthorizationOnce()
        val content = UNMutableNotificationContent().apply {
            setBody(rh.gs(CoreUiStrings.eat_reminder_title))
            setSound(UNNotificationSound.defaultSound())
            // Breaks through Focus, which is what an alarm the user set is for.
            setInterruptionLevel(UNNotificationInterruptionLevelTimeSensitive)
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(timeInterval = seconds, repeats = false)
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(IDENTIFIER, content, trigger)) { error ->
            if (error != null) aapsLogger.error(LTag.AUTOMATION, "Cannot schedule eat reminder: $error")
        }
        return true
    }

    override fun cancel() {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(IDENTIFIER))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(IDENTIFIER))
    }

    private fun requestAuthorizationOnce() {
        if (authorizationAsked) return
        authorizationAsked = true
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
            if (!granted) aapsLogger.error(LTag.AUTOMATION, "Notifications not permitted, the eat reminder will not appear")
        }
    }

    private companion object {

        const val IDENTIFIER = "aaps-eat-reminder"
    }
}
