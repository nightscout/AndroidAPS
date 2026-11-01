package app.aaps.plugins.source

/**
 * Decides when the Libre 3 source has to write a `SENSOR_CHANGE` therapy event.
 *
 * The event matters far beyond the sensor age shown on the Overview: it is the only mark that
 * tells the rest of AAPS where one sensor stops and the next one starts. The calibration plugin
 * refuses to work without it, because a fit that ran across two sensors would mix two different
 * biases into one line.
 *
 * The rule is one event per sensor. A sensor is named by its serial number, so re-scanning or
 * re-connecting the same sensor never writes a second event and never makes a running sensor look
 * new. The decision is kept here, apart from the store and from the plugin, so it can be read and
 * tested on its own.
 */
internal object Libre3SensorChange {

    /**
     * Answers whether the start of a sensor still has to be written, and with which name to
     * remember it.
     *
     * @param loggedSerial serial of the sensor whose start was already written, or null when none
     *   was written yet.
     * @param serialNumber serial of the sensor that is running now, or null when no sensor is
     *   stored. Nothing is written for an unknown sensor: an event that cannot be tied to a serial
     *   could not be kept unique, and every reading would write one more.
     * @param activatedAtMs when that sensor was started, in phone time. Zero means the start is
     *   not known; an event at that time would land in 1970 and make the sensor look endlessly old.
     * @param nowMs the phone clock. A start in the future can only come from a wrong clock or a
     *   bad read, and must never reach the database.
     * @return the serial to remember as "already written", or null when there is nothing to write.
     */
    fun serialToLog(
        loggedSerial: String?,
        serialNumber: String?,
        activatedAtMs: Long,
        nowMs: Long,
    ): String? {
        val serial = serialNumber?.takeIf { it.isNotBlank() } ?: return null
        if (activatedAtMs <= 0L) return null
        if (activatedAtMs > nowMs) return null
        if (loggedSerial?.equals(serial, ignoreCase = true) == true) return null
        return serial
    }
}
