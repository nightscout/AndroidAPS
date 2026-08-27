package app.aaps.implementation.notifications

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.SystemNotificationPlatform
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationInterruptionLevel.UNNotificationInterruptionLevelActive
import platform.UserNotifications.UNNotificationInterruptionLevel.UNNotificationInterruptionLevelTimeSensitive
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * The system tray half of notifications on iOS.
 *
 * iOS keeps far more of this than Android does. There is no channel to create, no `PendingIntent`
 * to build, and no volume ramp to drive: a delivered notification carries its own sound, and the
 * system decides how to present it. So the interesting part here is what is *absent* compared with
 * the Android side.
 *
 * Permission is requested once, lazily, on the first notification. Asking in the constructor would
 * put the system prompt in front of the user during start up, before anything has explained why the
 * app wants it.
 */
class IosSystemNotificationPlatform(
    private val aapsLogger: AAPSLogger
) : SystemNotificationPlatform {

    private val center = UNUserNotificationCenter.currentNotificationCenter()
    private var authorizationAsked = false

    override fun show(instanceKey: Int, title: String, text: String, urgent: Boolean) {
        ensureAuthorization()
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(text)
            // Time sensitive breaks through Focus modes, which is what an urgent AAPS alarm is for.
            // The sound stays off here on purpose: audible alarms are driven by setAudibleAlarm, the
            // same split the Android side uses so a replaced alarm does not restart the sound.
            setInterruptionLevel(
                if (urgent) UNNotificationInterruptionLevelTimeSensitive
                else UNNotificationInterruptionLevelActive
            )
        }
        // A repeated identifier replaces the delivered notification rather than adding another,
        // which is the behaviour the registry expects when it reposts the same id.
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier(instanceKey),
            content = content,
            trigger = null
        )
        center.addNotificationRequest(request) { error ->
            if (error != null) aapsLogger.error(LTag.NOTIFICATION, "Cannot post notification: $error")
        }
    }

    override fun cancel(instanceKey: Int) {
        val ids = listOf(identifier(instanceKey))
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        center.removeDeliveredNotificationsWithIdentifiers(ids)
    }

    override fun cancelAll() {
        center.removeAllPendingNotificationRequests()
        center.removeAllDeliveredNotifications()
    }

    /**
     * On iOS the delivered notification carries the sound, so there is no separate player to own.
     *
     * The ramping alarm Android builds by hand has no counterpart here yet. Posting a fresh sound
     * on every change would re-alert the user each time the registry recomputes the owner, which is
     * exactly what the owner handoff exists to avoid, so this stays a no-op beyond the log line
     * until iOS grows a real alarm path (a critical alert entitlement, or an audio session).
     */
    override fun setAudibleAlarm(instanceKey: Int?, sound: AlarmSound?) {
        aapsLogger.debug(LTag.NOTIFICATION, "Audible alarm owner is now $instanceKey ($sound)")
    }

    /**
     * Not wired yet.
     *
     * Learning that the user swiped a notification away needs a `UNUserNotificationCenterDelegate`,
     * and the delegate has to be set on the shared centre by the app at start up - it cannot be
     * owned by this class without two of them fighting over the same slot. Leaving it unregistered
     * means a notification the user clears on the lock screen stays in the in-app list, which is
     * visible but harmless, rather than silently dropping something.
     */
    override fun onDismissed(callback: (instanceKey: Int) -> Unit) {
        aapsLogger.debug(LTag.NOTIFICATION, "Dismiss callbacks need a UNUserNotificationCenterDelegate, not registered")
    }

    private fun identifier(instanceKey: Int) = "aaps-$instanceKey"

    private fun ensureAuthorization() {
        if (authorizationAsked) return
        authorizationAsked = true
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        center.requestAuthorizationWithOptions(options) { granted, error ->
            if (error != null) aapsLogger.error(LTag.NOTIFICATION, "Notification permission failed: $error")
            else aapsLogger.debug(LTag.NOTIFICATION, "Notification permission granted=$granted")
        }
    }
}
