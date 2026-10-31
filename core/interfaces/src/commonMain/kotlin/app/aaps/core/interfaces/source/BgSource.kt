package app.aaps.core.interfaces.source

interface BgSource {

    /**
     *  Sensor battery level in %
     *
     *  -1 if not supported
     */
    val sensorBatteryLevel: Int
        get() = -1

    /**
     *  Check if sensor has any error condition (expired, fault, replacement needed, signal lost)
     *
     *  @return true if sensor has error and BG values should not be displayed
     */
    fun hasSensorError(): Boolean = false

    /**
     * True when a fingerstick should go to the SENSOR instead of being fitted in the app.
     *
     * False for every source by default, and false even on a source that can do it until the user
     * has switched it on. While it is true the app must not also fit a line on top of the same
     * sensor's readings, or the correction is applied twice.
     */
    fun calibratesInSensor(): Boolean = false

    /**
     * Hand a fingerstick to the sensor's own algorithm.
     *
     * A sensor keeps a calibration it accepts for good: it cannot be edited or deleted
     * afterwards, unlike a calibration entry stored in the app. Only ever call this for an explicit
     * user action.
     *
     * @param glucoseMgdl the blood value
     * @param bloodAtMs wall clock (epoch ms) used as the time of the blood. The calibration dialog
     *   passes the moment the user confirmed the value, not the moment of the finger prick.
     */
    fun calibrateSensor(glucoseMgdl: Int, bloodAtMs: Long): SensorCalibrationResult =
        SensorCalibrationResult.NotSupported

    /**
     * Why the sensor must not get this fingerstick now, already readable by the user, or null when
     * it may. Asked before the user confirms, so a refusal is shown before anything is sent.
     * Only meaningful while [calibratesInSensor] is true.
     */
    fun sensorCalibrationRefusal(glucoseMgdl: Int, bloodAtMs: Long): String? = null
}
