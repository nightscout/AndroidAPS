package app.aaps.core.interfaces.source

import app.aaps.core.interfaces.plugin.ActivePlugin

/**
 * A BG source that talks to the sensor itself, as a pump driver talks to the pump, instead of taking the readings
 * from another app. It has its own screen, opened from Manage as the pump's screen is (`ElementType.CGM`).
 *
 * A source that only receives values from another app (xDrip, the Dexcom app, ...) is a plain [BgSource].
 */
interface CgmDriver : BgSource {

    /** How the user calibrates this sensor. */
    val calibrationMode: CgmCalibrationMode

    /**
     * Why a calibration cannot be taken now, or null when it can. Asked before the confirmation, so the user hears it
     * early; [calibrate] checks again. Only asked when [calibrationMode] is [CgmCalibrationMode.DRIVER].
     */
    suspend fun calibrationRefusal(): String? = null

    /**
     * Sends a finger-stick value to the sensor and waits for its answer. Only called when [calibrationMode] is
     * [CgmCalibrationMode.DRIVER].
     *
     * @return null when the sensor took it, or why it did not, for the user to read
     */
    suspend fun calibrate(bgMgdl: Double): String?
}

/** How the user calibrates the sensor of a [CgmDriver]. */
enum class CgmCalibrationMode {

    /** Calibrated in the factory: the user enters nothing. AAPS's own calibration plugin can still be used. */
    NONE,

    /**
     * The user enters the finger-stick value in the AAPS calibration dialog and the driver sends it to the sensor.
     * The sensor then sends values that are calibrated already, so AAPS's own calibration plugin is turned off: it
     * would correct them a second time.
     */
    DRIVER
}

/**
 * The active BG source when it is a [CgmDriver] that takes calibrations ([CgmCalibrationMode.DRIVER]), else null.
 *
 * Never throws, so a plugin enforcement can read it: `activeBgSource` throws until the first plugin election.
 */
fun ActivePlugin.calibratingCgmDriver(): CgmDriver? =
    (runCatching { activeBgSource }.getOrNull() as? CgmDriver)?.takeIf { it.calibrationMode == CgmCalibrationMode.DRIVER }
