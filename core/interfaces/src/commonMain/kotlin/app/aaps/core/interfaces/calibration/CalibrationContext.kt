package app.aaps.core.interfaces.calibration

/**
 * Optional hints passed to [Calibration.calibrate].
 * Plugins that do not use a hint may ignore it.
 */
data class CalibrationContext(
    /**
     * Start of the current sensor session in epoch ms.
     * `null` means the plugin should detect the session boundary itself
     * (e.g. by walking the input data for gaps).
     */
    val sensorSessionStart: Long? = null
) {

    companion object {

        val NONE = CalibrationContext()
    }
}
