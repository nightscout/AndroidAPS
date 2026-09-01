package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.SystemNotificationPlatform
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.image.BufferedImage

/**
 * The desktop half of notifications: the system tray.
 *
 * The shared notification manager decides which notifications exist and which alarm owns the sound.
 * This only puts them in front of the user.
 *
 * ## What it does and does not do
 *
 * - **Shows** through one tray icon, which is all AWT offers. There is no per-notification handle, so
 *   [cancel] and [cancelAll] have nothing to take back: a desktop toast is transient and gone by the
 *   time anything would cancel it. Both are recorded rather than silently ignored, so a caller
 *   expecting a notification to disappear can see that it never could.
 * - **Makes a sound** with the platform beep, and only for an alarm that carries one. It does NOT
 *   ramp the volume the way Android does. An alarm that is meant to escalate will not escalate here,
 *   which matters for anything relying on being noticed while the user is away from the machine.
 * - **Reports no dismissals.** AWT cannot tell the app that a user dismissed a toast, so the callback
 *   registered by [onDismissed] is kept but never invoked. The shared registry then only ever clears
 *   a notification from inside the app, which is correct, just less than Android manages.
 *
 * Where the tray is unavailable - a headless session, or a desktop environment without one - it falls
 * back to the log rather than throwing, because a missing tray must not take down the caller.
 */
class DesktopSystemNotificationPlatform(
    private val aapsLogger: AAPSLogger
) : SystemNotificationPlatform {

    private val trayIcon: TrayIcon? by lazy {
        runCatching {
            if (!SystemTray.isSupported()) return@runCatching null
            // A 16x16 transparent image: AWT insists on one, and the app has no desktop icon yet.
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            TrayIcon(image, "AAPS").apply { isImageAutoSize = true }
                .also { SystemTray.getSystemTray().add(it) }
        }.onFailure { aapsLogger.debug(LTag.CORE, "No system tray available: ${it.message}") }
            .getOrNull()
    }

    override fun show(notification: AapsNotification, title: String) {
        val icon = trayIcon
        if (icon == null) {
            aapsLogger.info(LTag.CORE, "Notification (no tray): $title - ${notification.text}")
            return
        }
        icon.displayMessage(title, notification.text, TrayIcon.MessageType.INFO)
    }

    override fun cancel(instanceKey: Int) {
        // A tray toast cannot be withdrawn once shown; it expires on its own.
        aapsLogger.debug(LTag.CORE, "Cannot cancel desktop notification $instanceKey: tray toasts are transient")
    }

    override fun cancelAll() {
        aapsLogger.debug(LTag.CORE, "Cannot cancel desktop notifications: tray toasts are transient")
    }

    override fun setAudibleAlarm(instanceKey: Int?, sound: AlarmSound?) {
        // Called again with the same key means "keep playing", and a single beep has already
        // finished, so there is nothing to keep. Only a new alarm that carries a sound beeps.
        if (instanceKey != null && sound != null) runCatching { Toolkit.getDefaultToolkit().beep() }
    }

    override fun onDismissed(callback: (instanceKey: Int) -> Unit) {
        // Kept for the shape of the contract. AWT gives no dismissal callback, so this never fires.
        aapsLogger.debug(LTag.CORE, "Desktop cannot report notification dismissals")
    }
}
