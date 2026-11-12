package app.aaps.plugins.source

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.dexcomoneplus.OnePlusCalibrationOutcome
import app.aaps.plugins.dexcomoneplus.session.OnePlusCalibrationGate

/**
 * Which user text goes with a sensor calibration result. One place for the plugin and the status
 * screen, so a refusal before the confirmation and a value dropped at send time read the same.
 *
 * [OnePlusCalibrationOutcome.NotSent] and [OnePlusCalibrationOutcome.Unknown] must never share a
 * text: after NotSent nothing reached the sensor and the user may enter the value again, after
 * Unknown the value was written and must not be sent again.
 */
object DexcomOnePlusCalibrationText {

    /** Why the value may not go to the sensor now. */
    fun refusal(refusal: OnePlusCalibrationGate.Refusal): TextRef = when (refusal) {
        OnePlusCalibrationGate.Refusal.OUT_OF_RANGE      -> SourceStrings.dexcom_oneplus_calibration_refused_range
        OnePlusCalibrationGate.Refusal.TOO_OLD           -> SourceStrings.dexcom_oneplus_calibration_refused_too_old
        OnePlusCalibrationGate.Refusal.NOT_READY         -> SourceStrings.dexcom_oneplus_calibration_refused_not_ready
        OnePlusCalibrationGate.Refusal.NO_RECENT_READING -> SourceStrings.dexcom_oneplus_calibration_refused_no_reading
        OnePlusCalibrationGate.Refusal.RATE_UNKNOWN      -> SourceStrings.dexcom_oneplus_calibration_refused_rate_unknown
        OnePlusCalibrationGate.Refusal.CHANGING_TOO_FAST -> SourceStrings.dexcom_oneplus_calibration_refused_too_fast
    }

    /** The main line of the status card. */
    fun status(outcome: OnePlusCalibrationOutcome?, waiting: Boolean): TextRef = when {
        waiting || outcome is OnePlusCalibrationOutcome.Pending -> SourceStrings.dexcom_oneplus_calibration_waiting
        outcome is OnePlusCalibrationOutcome.Accepted           -> SourceStrings.dexcom_oneplus_calibration_accepted
        outcome is OnePlusCalibrationOutcome.Refused            -> SourceStrings.dexcom_oneplus_calibration_refused
        outcome is OnePlusCalibrationOutcome.Unknown            -> SourceStrings.dexcom_oneplus_calibration_unknown
        outcome is OnePlusCalibrationOutcome.NotSent            -> SourceStrings.dexcom_oneplus_calibration_dropped
        else                                                    -> SourceStrings.dexcom_oneplus_calibration_none
    }

    /** Why a value was dropped before it was written. */
    fun notSentReason(reason: OnePlusCalibrationOutcome.NotSent.Reason): TextRef =
        reason.gateRefusal?.let { refusal(it) } ?: SourceStrings.dexcom_oneplus_calibration_dropped_sensor_changed
}
