package app.aaps.core.interfaces.source

import app.aaps.core.interfaces.plugin.ActivePlugin

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
     * The Android packages of the vendor app this source takes its readings from, as the CGM app catalog lists
     * them: what the CGM button opens. Empty when the source has no such app, and on platforms with no packages.
     */
    val appPackages: List<String>
        get() = emptyList()

    /** Where a calibration the user enters goes. */
    val calibrationMode: CalibrationMode
        get() = CalibrationMode.AAPS

    /**
     * The finger-stick values this source takes, in mg/dL, when it takes fewer than the calibration dialog offers, or
     * null. The dialog and the master's check then offer only these, so a value the sensor would refuse is refused
     * before the confirmation. Only asked in [CalibrationMode.SOURCE].
     */
    val calibrationRangeMgdl: ClosedFloatingPointRange<Double>?
        get() = null

    /**
     * Why a calibration cannot be taken now, or null when it can. Asked before the confirmation, so the user hears it
     * early; [calibrate] checks again. Only asked in [CalibrationMode.SOURCE].
     */
    suspend fun calibrationRefusal(): String? = null

    /**
     * A short note for the user with a calibration this source takes, for example what the receiving app must allow,
     * or null. Shown in the confirmation and in the SMS reply. Only asked in [CalibrationMode.SOURCE].
     */
    fun calibrationNote(): String? = null

    /**
     * Passes a finger-stick value on: to the sensor (a CGM driver) or to the app the readings come from (xDrip).
     * Only called in [CalibrationMode.SOURCE].
     *
     * @return null when it was taken, or why it was not, for the user to read
     */
    suspend fun calibrate(bgMgdl: Double): String? = error("${this::class.simpleName} does not take calibrations")
}

/** Where a calibration the user enters goes, for one BG source. */
enum class CalibrationMode {

    /**
     * The AAPS calibration dialog, then [BgSource.calibrate]: a CGM driver sends it to the sensor, xDrip receives it
     * by broadcast. The values that come back are calibrated already, so AAPS's calibration plugin is turned off.
     */
    SOURCE,

    /**
     * The vendor app calibrates: the calibrate button opens it ([BgSource.appPackages]). Its values are calibrated
     * already, so AAPS's calibration plugin is turned off.
     */
    OWN_APP,

    /**
     * The sensor is calibrated on the insulin pump it sends to (a Medtronic pump with a Guardian sensor). AAPS cannot
     * reach it and no phone app takes it, so there is no calibrate button. Its values are calibrated already, so
     * AAPS's calibration plugin is turned off.
     */
    PUMP,

    /**
     * Nothing else calibrates the readings (a factory calibrated sensor, or a source AAPS knows nothing about):
     * AAPS's calibration plugin may, if the user turns it on.
     */
    AAPS
}

/** The active BG source, or null before the first plugin election. Never throws, so an enforcement can read it. */
fun ActivePlugin.activeBgSourceOrNull(): BgSource? = runCatching { activeBgSource }.getOrNull()
