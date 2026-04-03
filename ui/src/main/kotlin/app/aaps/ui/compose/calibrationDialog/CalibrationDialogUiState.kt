package app.aaps.ui.compose.calibrationDialog

import androidx.compose.runtime.Immutable
import app.aaps.core.data.model.GlucoseUnit

@Immutable
data class CalibrationDialogUiState(
    val bg: Double = 0.0,
    val units: GlucoseUnit = GlucoseUnit.MGDL,
    val bgRange: ClosedFloatingPointRange<Double> = 36.0..500.0,
    val bgStep: Double = 1.0,
    val bgDecimalPlaces: Int = 0
) {

    val isMgdl: Boolean get() = units == GlucoseUnit.MGDL
    val unitLabel: String get() = units.asText
    val hasValidBg: Boolean get() = bg > 0.0
}
