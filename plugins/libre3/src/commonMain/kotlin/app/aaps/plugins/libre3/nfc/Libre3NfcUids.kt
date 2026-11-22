package app.aaps.plugins.libre3.nfc

/**
 * Constants of the ISO 15693 custom commands used by Libre 3 and Libre 3 Plus.
 *
 * A command frame on Android is built by hand and looks like this:
 * `requestFlag || commandCode || manufacturerCode || body`. On iOS the system builds the first
 * three bytes, which is why the upstream Swift code only passes the body.
 */
object Libre3NfcUids {

    /** Request flag used for every command. High data rate, no address. */
    const val REQUEST_FLAG: Byte = 0x02

    /** Manufacturer code of the sensor. A frame with any other value is not for us. */
    const val MANUFACTURER: Byte = 0x7A

    /** Read patch info. No body. */
    const val CMD_PATCH_INFO: Byte = 0xA1.toByte()

    /** Activate a fresh sensor. Body is 10 bytes. Only for sensor state `0x01`. */
    const val CMD_ACTIVATE: Byte = 0xA0.toByte()

    /** Take over a sensor that is already running. Body is 10 bytes. */
    const val CMD_SWITCH_RECEIVER: Byte = 0xA8.toByte()

    /** Sensor state that means "never activated yet". Any other state needs [CMD_SWITCH_RECEIVER]. */
    const val STATE_FRESH: Byte = 0x01
}
