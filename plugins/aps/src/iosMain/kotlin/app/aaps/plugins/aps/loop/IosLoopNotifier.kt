package app.aaps.plugins.aps.loop

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.plugins.aps.ApsStrings
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
import platform.UserNotifications.UNUserNotificationCenter

/**
 * The loop's own system notification on iOS: "open loop has a suggestion waiting". It matters enough that the
 * interface says a silent implementation would be a safety problem, so this is a real one rather than a log line.
 * Opening the app on tap is the default behaviour and needs nothing.
 *
 * Carbs required, with its three "ignore" buttons, is not here any more: it is an alert of the core
 * (`_docs/ALARMS.md`, step 6), and the buttons became the silence buttons of every warning (`IosAlertPlatform`).
 *
 * `localOnly` is ignored. It exists on Android to stop a notification being mirrored to a watch that
 * is already showing it; iOS mirrors to a paired Apple Watch automatically and offers no
 * per-notification way to prevent it. The cost is a duplicate on the wrist, not a missed alert.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class IosLoopNotifier(
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver
) : LoopNotifier {

    // Lazy, not eager: `currentNotificationCenter()` needs an app bundle and throws
    // `bundleProxyForCurrentProcess is nil` without one, so a field initialiser makes the class
    // impossible to construct in a test binary.
    private val center by lazy { UNUserNotificationCenter.currentNotificationCenter() }

    /** Asked once, on first use, so building the graph does not touch the notification centre. */
    private val authorized: Unit by lazy {
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ -> }
    }

    override fun openLoopSuggestion(text: String, localOnly: Boolean) {
        authorized
        // localOnly has no counterpart here - see the class docs.
        post(title = rh.gs(ApsStrings.open_loop_new_suggestion), text = text)
    }

    override fun dismiss() {
        val ids = listOf(NOTIFICATION_ID)
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        center.removeDeliveredNotificationsWithIdentifiers(ids)
    }

    /**
     * One identifier, so a new suggestion replaces the old rather than stacking - the same behaviour the Android side
     * gets from reusing one notification id.
     *
     * The sound is not decoration. `AndroidLoopNotifier` posts on an `IMPORTANCE_HIGH` channel that vibrates, so on
     * Android it alerts. This posted silently once, which made "there is a suggestion waiting" easy to miss entirely.
     */
    private fun post(title: String, text: String) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(text)
            setSound(UNNotificationSound.defaultSound())
            setInterruptionLevel(UNNotificationInterruptionLevelTimeSensitive)
        }
        val request = UNNotificationRequest.requestWithIdentifier(NOTIFICATION_ID, content, null)
        center.addNotificationRequest(request) { error ->
            if (error != null) aapsLogger.error(LTag.CORE, "Cannot post loop notification: $error")
        }
    }

    internal companion object {

        const val NOTIFICATION_ID = "aaps-loop"
    }
}
