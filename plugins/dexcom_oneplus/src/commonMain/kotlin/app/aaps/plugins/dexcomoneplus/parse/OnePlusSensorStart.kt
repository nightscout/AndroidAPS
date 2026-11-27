package app.aaps.plugins.dexcomoneplus.parse

/** When a sensor session started, as the sensor itself reports it. */
object OnePlusSensorStart {

    /**
     * From one glucose message: the receive time minus the session age, or null.
     *
     * Only the G7 / ONE+ message (0x4e) counts. Its session age is the time since the sensor was
     * activated, the value Trio's G7SensorKit takes for the activation date. The older 0x4f message
     * counts from the transmitter, which is not the age of the sensor.
     */
    fun fromEgv(opcode: Int, sessionAgeSeconds: Int?, nowMs: Long): Long? =
        if (opcode == OnePlusGlucoseParser.OPCODE_EGV2 && sessionAgeSeconds != null && sessionAgeSeconds > 0) {
            nowMs - sessionAgeSeconds * 1000L
        } else {
            null
        }
}
