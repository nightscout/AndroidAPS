package app.aaps.plugins.aps.loop

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.notifications.IosNotificationDelegate
import app.aaps.plugins.aps.ApsStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationInterruptionLevel.UNNotificationInterruptionLevelTimeSensitive
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/**
 * The loop's own system notifications on iOS.
 *
 * These are the ones with buttons: "the loop wants carbs, ignore for N minutes" and "open loop has a
 * suggestion waiting". Both matter enough that the interface says a silent implementation would be a
 * safety problem, so this is a real one rather than a log line.
 *
 * ## What iOS does and does not carry over
 *
 * The three ignore buttons are `UNNotificationAction`s on a category, and tapping one calls
 * `Loop.disableCarbSuggestions` directly - iOS has no intents, so where Android goes through a
 * broadcast receiver this simply calls the same method. Opening the app on tap is the default
 * behaviour and needs nothing.
 *
 * `localOnly` is ignored. It exists on Android to stop a notification being mirrored to a watch that
 * is already showing it; iOS mirrors to a paired Apple Watch automatically and offers no
 * per-notification way to prevent it. The cost is a duplicate on the wrist, not a missed alert.
 *
 * The delegate is registered through [IosNotificationDelegate] rather than set here. There is one
 * delegate slot for the whole app, so a second owner would silently stop the dismissal callbacks the
 * notification platform relies on.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosLoopNotifier @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    private val loop: Loop
) : LoopNotifier {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    /** Registered once, on first use, so building the graph does not touch the notification centre. */
    private val registered: Unit by lazy {
        IosNotificationDelegate.register(setOf(carbsCategory())) { actionId, _ ->
            val minutes = IGNORE_MINUTES.firstOrNull { actionId == ignoreActionId(it) }
            if (minutes == null) return@register false
            aapsLogger.debug(LTag.CORE, "Carb suggestions ignored for $minutes minutes")
            loop.disableCarbSuggestions(minutes)
            true
        }
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ -> }
    }

    override fun carbsRequired(text: String) {
        registered
        post(title = rh.gs(ApsStrings.carbs_suggestion), text = text, category = CATEGORY_CARBS)
    }

    override fun openLoopSuggestion(text: String, localOnly: Boolean) {
        registered
        // localOnly has no counterpart here - see the class docs.
        post(title = rh.gs(ApsStrings.open_loop_new_suggestion), text = text, category = null)
    }

    override fun dismiss() {
        val ids = listOf(NOTIFICATION_ID)
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        center.removeDeliveredNotificationsWithIdentifiers(ids)
    }

    /**
     * One identifier for both, so a new notification replaces the old rather than stacking - the
     * same behaviour the Android side gets from reusing one notification id.
     */
    private fun post(title: String, text: String, category: String?) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(text)
            setInterruptionLevel(UNNotificationInterruptionLevelTimeSensitive)
            category?.let { setCategoryIdentifier(it) }
        }
        val request = UNNotificationRequest.requestWithIdentifier(NOTIFICATION_ID, content, null)
        center.addNotificationRequest(request) { error ->
            if (error != null) aapsLogger.error(LTag.CORE, "Cannot post loop notification: $error")
        }
    }

    private fun carbsCategory(): UNNotificationCategory =
        UNNotificationCategory.categoryWithIdentifier(
            identifier = CATEGORY_CARBS,
            actions = IGNORE_MINUTES.map { minutes ->
                UNNotificationAction.actionWithIdentifier(
                    identifier = ignoreActionId(minutes),
                    title = rh.gs(ignoreLabel(minutes)),
                    options = 0u
                )
            },
            intentIdentifiers = emptyList<Any>(),
            options = 0u
        )

    private fun ignoreActionId(minutes: Int) = "$IGNORE_PREFIX$minutes"

    private fun ignoreLabel(minutes: Int) = when (minutes) {
        5    -> ApsStrings.ignore5m
        15   -> ApsStrings.ignore15m
        else -> ApsStrings.ignore30m
    }

    internal companion object {

        /** Same three the Android notification offers. */
        val IGNORE_MINUTES = listOf(5, 15, 30)

        const val NOTIFICATION_ID = "aaps-loop"
        const val CATEGORY_CARBS = "aaps-carbs-required"
        const val IGNORE_PREFIX = "aaps-ignore-"
    }
}
