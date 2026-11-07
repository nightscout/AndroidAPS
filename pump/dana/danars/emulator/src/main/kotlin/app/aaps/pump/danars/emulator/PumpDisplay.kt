package app.aaps.pump.danars.emulator

/**
 * Callback interface for displaying emulated pump screen content.
 *
 * On a real pump, certain operations show information on the pump's LCD.
 * During emulation, this interface replaces the physical display — implementations
 * can post notifications, show dialogs, etc.
 *
 * Lifecycle: messages are informational and don't gate the handshake.
 * The emulator auto-confirms where a real pump would wait for a button press.
 */
interface PumpDisplay {

    /** v1 pairing: pump shows "pairing request" and user confirms on pump. Emulator auto-confirms. */
    fun showPairingConfirmation()

    /**
     * RSv3 pairing: pump shows two PIN codes that user must enter in the app.
     * @param pin1 12 hex digits (6-byte pairing key)
     * @param pin2 8 hex digits (3-byte random pairing key + 1-byte checksum)
     */
    fun showPairingPinCodes(pin1: String, pin2: String)

    /** Dismiss any pump display notification. */
    fun dismiss()
}

/** No-op implementation for tests and non-interactive usage. */
object NoOpPumpDisplay : PumpDisplay {

    override fun showPairingConfirmation() {}
    override fun showPairingPinCodes(pin1: String, pin2: String) {}
    override fun dismiss() {}
}
