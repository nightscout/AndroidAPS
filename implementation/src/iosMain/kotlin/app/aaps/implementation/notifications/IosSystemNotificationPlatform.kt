package app.aaps.implementation.notifications

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.SystemNotificationPlatform
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.darwin.NSObject
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionCustomDismissAction
import platform.UserNotifications.UNNotificationDismissActionIdentifier
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
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

    /**
     * Resolved on first use, not in the constructor.
     *
     * `currentNotificationCenter()` needs an app bundle and throws
     * `bundleProxyForCurrentProcess is nil` without one, so constructing this class eagerly made it
     * impossible to build outside a running app - including in a test. Nothing here needs the centre
     * until something is actually posted.
     */
    private val center by lazy { UNUserNotificationCenter.currentNotificationCenter() }
    private var authorizationAsked = false

    /** Strong reference: the centre holds its delegate weakly. */
    private var delegate: DismissDelegate? = null

    /**
     * iOS shows every notification, unlike Android.
     *
     * The two fields Android needs to decide with - `sound` and `actions` - are ignored here on
     * purpose. There is no "post the alarm silently" split because there is no separate ramping
     * player to hand the audio to (see [setAudibleAlarm]), and no preference gating whether a system
     * notification appears, because on iOS that choice belongs to the user in Settings.
     */
    override fun show(notification: AapsNotification, title: String) {
        ensureAuthorization()
        val urgent = notification.level == NotificationLevel.URGENT
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(notification.text)
            // Time sensitive breaks through Focus modes, which is what an urgent AAPS alarm is for.
            // The sound stays off here on purpose: audible alarms are driven by setAudibleAlarm, the
            // same split the Android side uses so a replaced alarm does not restart the sound.
            // Without this the dismiss callback never fires - see onDismissed.
            setCategoryIdentifier(CATEGORY)
            setInterruptionLevel(
                if (urgent) UNNotificationInterruptionLevelTimeSensitive
                else UNNotificationInterruptionLevelActive
            )
        }
        // A repeated identifier replaces the delivered notification rather than adding another,
        // which is the behaviour the registry expects when it reposts the same id.
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier(notification.instanceKey),
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
     * Learn about notifications the user swiped away outside the app.
     *
     * Two things are needed, and missing either one makes this silently never fire:
     *
     * 1. A delegate on the shared centre. The slot is app wide and **weak**, so the delegate is held
     *    in a property here - a local would be collected and the callbacks would simply stop.
     * 2. A category carrying `customDismissAction`. Without it iOS reports taps but not dismissals,
     *    which is the trap: the code looks right and nothing ever arrives.
     */
    override fun onDismissed(callback: (instanceKey: Int) -> Unit) {
        center.setNotificationCategories(setOf(dismissibleCategory()))
        delegate = DismissDelegate { identifier ->
            instanceKeyOf(identifier)?.let(callback)
        }
        center.setDelegate(delegate)
    }

    private fun dismissibleCategory(): UNNotificationCategory =
        UNNotificationCategory.categoryWithIdentifier(
            identifier = CATEGORY,
            actions = emptyList<Any>(),
            intentIdentifiers = emptyList<Any>(),
            options = UNNotificationCategoryOptionCustomDismissAction
        )

    /**
     * Only reacts to a dismissal, and hands every other response straight back to iOS.
     *
     * A tap is deliberately not treated as a dismissal: the notification does go away, but the user
     * asked to *see* the thing, so it must stay in the in-app list.
     */
    private class DismissDelegate(
        private val onDismiss: (String) -> Unit
    ) : NSObject(), UNUserNotificationCenterDelegateProtocol {

        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            didReceiveNotificationResponse: UNNotificationResponse,
            withCompletionHandler: () -> Unit
        ) {
            if (didReceiveNotificationResponse.actionIdentifier == UNNotificationDismissActionIdentifier) {
                onDismiss(didReceiveNotificationResponse.notification.request.identifier)
            }
            withCompletionHandler()
        }
    }

    internal fun identifier(instanceKey: Int) = "$IDENTIFIER_PREFIX$instanceKey"

    /** The reverse of [identifier]. Null for anything this class did not post. */
    internal fun instanceKeyOf(identifier: String): Int? =
        if (identifier.startsWith(IDENTIFIER_PREFIX)) identifier.removePrefix(IDENTIFIER_PREFIX).toIntOrNull()
        else null

    private fun ensureAuthorization() {
        if (authorizationAsked) return
        authorizationAsked = true
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        center.requestAuthorizationWithOptions(options) { granted, error ->
            if (error != null) aapsLogger.error(LTag.NOTIFICATION, "Notification permission failed: $error")
            else aapsLogger.debug(LTag.NOTIFICATION, "Notification permission granted=$granted")
        }
    }

    companion object {

        private const val IDENTIFIER_PREFIX = "aaps-"

        /** Named once: it has to match between the posted content and the registered category. */
        private const val CATEGORY = "aaps-notification"
    }
}
