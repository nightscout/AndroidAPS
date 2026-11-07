package app.aaps.plugins.libre3

/**
 * Hides most of a sensor serial or a Bluetooth address before it is written to a log line.
 *
 * Users send `AndroidAPS.log` to other people when they ask for help, so a log line must not carry
 * the full serial or the full address of their sensor. The last few characters are kept: that is
 * enough to tell two sensors apart in one log, and not enough to find or claim the sensor.
 *
 * Use this for log text only. What is stored, compared or shown to the user stays the full value.
 */
object Libre3LogMask {

    /** Characters kept at the end of a Bluetooth address, for example `***EE:FF`. */
    const val MAC_KEPT = 5

    /** Characters kept at the end of a sensor serial, for example `***7ABC`. */
    const val SERIAL_KEPT = 4

    /** A Bluetooth address for a log line, for example `AA:BB:CC:DD:EE:FF` becomes `***EE:FF`. */
    fun mac(address: String?): String = mask(address?.trim()?.uppercase(), MAC_KEPT)

    /** A sensor serial for a log line, for example `0D00012ABC` becomes `***2ABC`. */
    fun serial(serial: String?): String = mask(serial?.trim(), SERIAL_KEPT)

    private fun mask(value: String?, kept: Int): String = when {
        value.isNullOrEmpty() -> "none"
        // A value this short would be shown whole, so nothing of it is kept.
        value.length <= kept  -> "***"
        else                  -> "***${value.takeLast(kept)}"
    }
}
