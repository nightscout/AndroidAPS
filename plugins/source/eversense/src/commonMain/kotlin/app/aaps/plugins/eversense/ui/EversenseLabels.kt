package app.aaps.plugins.eversense.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.plugins.eversense.EversenseStrings
import app.aaps.plugins.eversense.enums.CalibrationPhase
import app.aaps.plugins.eversense.enums.SignalStrength

/** The signal in words. The status tab and the placement guide say the same, on the official app's steps. */
internal val SignalStrength.label: TextRef
    get() = when (this) {
        SignalStrength.EXCELLENT -> EversenseStrings.eversense_signal_excellent
        SignalStrength.GOOD      -> EversenseStrings.eversense_signal_good
        SignalStrength.LOW       -> EversenseStrings.eversense_signal_fair
        SignalStrength.VERY_LOW  -> EversenseStrings.eversense_signal_weak
        SignalStrength.POOR      -> EversenseStrings.eversense_signal_poor
        SignalStrength.NO_SIGNAL -> EversenseStrings.eversense_signal_none
    }

/** What the placement guide tells the user to do at this signal. */
internal val SignalStrength.instruction: TextRef
    get() = when (this) {
        SignalStrength.EXCELLENT -> EversenseStrings.eversense_placement_instruction_excellent
        SignalStrength.GOOD      -> EversenseStrings.eversense_placement_instruction_good
        SignalStrength.LOW       -> EversenseStrings.eversense_placement_instruction_fair
        SignalStrength.VERY_LOW  -> EversenseStrings.eversense_placement_instruction_weak
        SignalStrength.POOR,
        SignalStrength.NO_SIGNAL -> EversenseStrings.eversense_placement_instruction_poor
    }

/** Lit bars of the placement guide, 0 to 5: the step's place in the scale. */
internal val SignalStrength.bars: Int get() = ordinal

/** The signal row of the status tab: good is fine, fair needs a look, weaker needs a move. */
internal val SignalStrength.level: StatusLevel
    get() = when (this) {
        SignalStrength.EXCELLENT, SignalStrength.GOOD -> StatusLevel.NORMAL
        SignalStrength.LOW                            -> StatusLevel.WARNING
        else                                          -> StatusLevel.CRITICAL
    }

/** The colour of the lit bars: good is fine, fair needs a look, weaker needs a move. */
@Composable
internal fun SignalStrength.color(): Color = when (this) {
    SignalStrength.EXCELLENT, SignalStrength.GOOD -> AapsTheme.generalColors.statusNormal
    SignalStrength.LOW                            -> AapsTheme.generalColors.statusWarning
    else                                          -> AapsTheme.generalColors.statusCritical
}

internal val CalibrationPhase.label: TextRef
    get() = when (this) {
        CalibrationPhase.WARMING_UP         -> EversenseStrings.eversense_phase_warming_up
        CalibrationPhase.INITIALIZATION     -> EversenseStrings.eversense_phase_initialization
        CalibrationPhase.DAILY_CALIBRATION  -> EversenseStrings.eversense_phase_daily
        CalibrationPhase.WEEKLY_CALIBRATION -> EversenseStrings.eversense_phase_weekly
        CalibrationPhase.SUSPICIOUS         -> EversenseStrings.eversense_phase_suspicious
        CalibrationPhase.UNKNOWN            -> EversenseStrings.eversense_phase_unknown
        CalibrationPhase.DEBUG              -> EversenseStrings.eversense_phase_debug
        CalibrationPhase.DROPOUT            -> EversenseStrings.eversense_phase_dropout
    }
