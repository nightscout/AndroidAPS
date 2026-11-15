package app.aaps.ui.compose.calibrationDialog

import androidx.compose.runtime.Immutable
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.calibration.AddEntryResult

@Immutable
data class CalibrationDialogUiState(
    val bg: Double = 0.0,
    val units: GlucoseUnit = GlucoseUnit.MGDL,
    val bgRange: ClosedFloatingPointRange<Double> = 36.0..500.0,
    val bgStep: Double = 1.0,
    val bgDecimalPlaces: Int = 0,
    val preconditions: AddEntryResult? = null,
    val submitting: Boolean = false
) {

    val isMgdl: Boolean get() = units == GlucoseUnit.MGDL
    val unitLabel: String get() = units.displayLabel
    val hasValidBg: Boolean get() = bg > 0.0
    val canSubmit: Boolean get() = hasValidBg && preconditions is AddEntryResult.Accepted && !submitting
    val blockingPreconditions: AddEntryResult.Rejected? get() = preconditions as? AddEntryResult.Rejected
    val canMarkSensorChange: Boolean get() = !submitting
}
