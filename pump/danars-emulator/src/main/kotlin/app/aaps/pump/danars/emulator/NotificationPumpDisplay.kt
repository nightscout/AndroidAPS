package app.aaps.pump.danars.emulator

import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager

/**
 * PumpDisplay implementation that shows emulated pump screen content as app notifications.
 *
 * Replaces the physical pump LCD during emulation. The user sees the notification
 * alongside the app's pairing dialogs (PairingHelperActivity, EnterPinActivity)
 * and can read/copy the displayed codes.
 */
class NotificationPumpDisplay(
    private val notificationManager: NotificationManager
) : PumpDisplay {

    override fun showPairingConfirmation() {
        notificationManager.post(
            id = NotificationId.PUMP_EMULATOR_DISPLAY,
            text = "Emulated Pump: Pairing confirmed automatically",
            level = NotificationLevel.INFO,
            validMinutes = 1
        )
    }

    override fun showPairingPinCodes(pin1: String, pin2: String) {
        notificationManager.post(
            id = NotificationId.PUMP_EMULATOR_DISPLAY,
            text = "Emulated Pump Display\nPIN 1: $pin1\nPIN 2: $pin2",
            level = NotificationLevel.INFO,
            validMinutes = 5
        )
    }

    override fun dismiss() {
        notificationManager.dismiss(NotificationId.PUMP_EMULATOR_DISPLAY)
    }
}
