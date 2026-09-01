package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.plugins.aps.loop.LoopNotifier
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The loop's two notifications on desktop, through the shared registry.
 *
 * Android posts these straight to the system notification manager; here they go through the AAPS
 * registry, which puts them in the app's own notification list **and** shows a tray toast. That is
 * the better trade on a desktop: the tray toast disappears on its own and cannot be brought back, so
 * the in-app list is where a user who was away from the machine actually finds the message.
 *
 * ## What is missing, and it is worth knowing
 *
 * **The "ignore for 5 / 15 / 30 minutes" buttons do not exist here.** Android puts three actions on
 * the carbs notification, and iOS registers the same three as notification categories; AWT's tray
 * has no notion of an action at all, so there is nothing to attach them to. The suggestion is still
 * delivered - the user reads it and acts in the app - but dismissing the reminder for a while is not
 * offered. That is logged rather than left silent, because a user who knows the Android app will
 * look for those buttons.
 *
 * **Tapping does not open anything.** `openLoopSuggestion` on Android carries an intent that brings
 * the app up. A tray toast has no equivalent, and the app is already on screen or in the taskbar.
 *
 * Both messages use one notification id on purpose, and that matches Android: it posts both under
 * the same `Constants.NOTIFICATION_ID`, so the newer one replaces the older and a single [dismiss]
 * clears whichever is showing.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DesktopLoopNotifier @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val notificationManager: NotificationManager
) : LoopNotifier {

    override fun carbsRequired(text: String) {
        aapsLogger.debug(LTag.CORE, "Carbs required: $text (no ignore actions on desktop)")
        notificationManager.post(NotificationId.CARBS_REQUIRED, text)
    }

    override fun openLoopSuggestion(text: String, localOnly: Boolean) {
        // localOnly means "do not mirror this to the watch". There is no watch paired to a desktop,
        // so the flag makes no difference to what happens here.
        aapsLogger.debug(LTag.CORE, "Open loop suggestion: $text")
        notificationManager.post(NotificationId.CARBS_REQUIRED, text)
    }

    override fun dismiss() {
        notificationManager.dismiss(NotificationId.CARBS_REQUIRED)
    }
}
