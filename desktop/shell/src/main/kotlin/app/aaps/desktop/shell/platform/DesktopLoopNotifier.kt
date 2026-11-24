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
 * The loop's open loop suggestion on desktop, through the shared registry.
 *
 * Android posts it straight to the system notification manager; here it goes through the AAPS registry, which puts
 * it in the app's own notification list **and** shows a tray toast. That is the better trade on a desktop: the tray
 * toast disappears on its own and cannot be brought back, so the in-app list is where a user who was away from the
 * machine actually finds the message.
 *
 * **Tapping does not open anything.** `openLoopSuggestion` on Android carries an intent that brings the app up. A
 * tray toast has no equivalent, and the app is already on screen or in the taskbar.
 *
 * Carbs required is not here any more: it is an alert of the core (`_docs/ALARMS.md`, step 6).
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DesktopLoopNotifier(
    private val aapsLogger: AAPSLogger,
    private val notificationManager: NotificationManager
) : LoopNotifier {

    override fun openLoopSuggestion(text: String, localOnly: Boolean) {
        // localOnly means "do not mirror this to the watch". There is no watch paired to a desktop,
        // so the flag makes no difference to what happens here.
        aapsLogger.debug(LTag.CORE, "Open loop suggestion: $text")
        notificationManager.post(NotificationId.OPEN_LOOP_SUGGESTION, text)
    }

    override fun dismiss() {
        notificationManager.dismiss(NotificationId.OPEN_LOOP_SUGGESTION)
    }
}
