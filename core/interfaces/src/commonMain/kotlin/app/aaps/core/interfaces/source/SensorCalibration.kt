package app.aaps.core.interfaces.source

/**
 * What happened when a fingerstick was handed to the sensor itself.
 *
 * Only says whether the app managed to pass the value on. What the SENSOR then did with it comes
 * back later, over the radio, and belongs to the source's own status screen: the sensor is asleep
 * between its radio windows, so there is nothing to report at the moment the user taps OK.
 */
sealed interface SensorCalibrationResult {

    /** This source does not hold calibrations in the sensor, or the user has not switched it on. */
    data object NotSupported : SensorCalibrationResult

    /** Handed over. It reaches the sensor at the next radio window, normally within a few minutes. */
    data object Queued : SensorCalibrationResult

    /**
     * The app will not pass it on.
     *
     * @param reason short, already user-readable, e.g. no session, value out of range, one already
     *   waiting
     */
    data class Refused(val reason: String) : SensorCalibrationResult
}
