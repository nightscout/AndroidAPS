package app.aaps.ui.compose.eversenseCalibrationDialog

import androidx.compose.runtime.Immutable
import app.aaps.core.data.model.GlucoseUnit

@Immutable
data class EversenseCalibrationDialogUiState(
    val bg: Double = 0.0,
    val units: GlucoseUnit = GlucoseUnit.MGDL,
    val bgRange: ClosedFloatingPointRange<Double> = 40.0..400.0,
    val bgStep: Double = 1.0,
    val bgDecimalPlaces: Int = 0,
    val notConnected: Boolean = false,
    val submitting: Boolean = false
) {

    val isMgdl: Boolean get() = units == GlucoseUnit.MGDL
    val unitLabel: String get() = units.asText
    val hasValidBg: Boolean get() = bg > 0.0
    val canSubmit: Boolean get() = hasValidBg && !submitting
}
