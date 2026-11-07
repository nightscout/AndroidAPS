package app.aaps.core.interfaces.source

/**
 * Abstraction over Eversense's BLE calibration flow, exposed to modules (e.g. `ui`) that must not
 * take a direct dependency on the `plugins:eversense`/`plugins:source` modules — same pattern as
 * [XDripSource]/[DexcomBoyda].
 */
interface EversenseCalibrationSource {

    /** True when Eversense is the currently active/enabled BG source. */
    fun isEnabled(): Boolean

    /** True when currently connected to the transmitter over BLE. */
    fun isConnected(): Boolean

    /**
     * Sends a fingerstick calibration value (mg/dL) to the transmitter, connecting first (with a
     * timeout) if not already connected. Returns true on success.
     */
    suspend fun calibrate(bgMgDl: Int): Boolean
}
