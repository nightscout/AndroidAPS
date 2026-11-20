package app.aaps.plugins.dexcomoneplus

import app.aaps.plugins.dexcomoneplus.session.OnePlusCalibrationGate

/**
 * Where a fingerstick handed to the sensor got to.
 *
 * Kept separate from the wire type so the app side never has to know about opcodes, and so the one
 * state the wire cannot express — [Pending] — has a place.
 *
 * [Unknown] is only for a value that was written to the sensor: it is not a failure and not a
 * success. The answer was lost or could not be read, and the sensor's calibration state did not
 * settle it. It must be shown as "we cannot tell", never quietly rounded to either side. A sensor
 * keeps a calibration it accepted for good, so the user is told not to send the value again.
 *
 * [NotSent] is the opposite case: the value was dropped before anything was written, so the sensor
 * never saw it and the user may enter it again.
 */
sealed interface OnePlusCalibrationOutcome {

    /** Waiting for the Control loop to reach the point where it may write. */
    data object Pending : OnePlusCalibrationOutcome

    /** The sensor said it took the value. */
    data class Accepted(val detail: String) : OnePlusCalibrationOutcome

    /** The sensor said no, and said why. */
    data class Refused(val detail: String) : OnePlusCalibrationOutcome

    /**
     * Something came back that cannot be read, or nothing came back at all.
     *
     * @param detail the raw bytes when there were any, so a field report can carry them
     */
    data class Unknown(val detail: String) : OnePlusCalibrationOutcome

    /**
     * Dropped before any write: nothing reached the sensor, so the value may be entered again.
     *
     * @param reason why it was dropped
     */
    data class NotSent(val reason: Reason) : OnePlusCalibrationOutcome {

        /**
         * Why a value was dropped before the write.
         *
         * @param gateRefusal the check of [OnePlusCalibrationGate] that failed at send time, or null
         *   when the value was cleared because the sensor or its session changed
         */
        enum class Reason(val gateRefusal: OnePlusCalibrationGate.Refusal?) {

            OUT_OF_RANGE(OnePlusCalibrationGate.Refusal.OUT_OF_RANGE),
            TOO_OLD(OnePlusCalibrationGate.Refusal.TOO_OLD),
            NOT_READY(OnePlusCalibrationGate.Refusal.NOT_READY),
            NO_RECENT_READING(OnePlusCalibrationGate.Refusal.NO_RECENT_READING),
            RATE_UNKNOWN(OnePlusCalibrationGate.Refusal.RATE_UNKNOWN),
            CHANGING_TOO_FAST(OnePlusCalibrationGate.Refusal.CHANGING_TOO_FAST),

            /** A new sensor, a new or restarted session, or a promotion cleared the waiting value. */
            SENSOR_CHANGED(null);

            companion object {

                fun of(refusal: OnePlusCalibrationGate.Refusal): Reason = entries.first { it.gateRefusal == refusal }
            }
        }
    }
}
