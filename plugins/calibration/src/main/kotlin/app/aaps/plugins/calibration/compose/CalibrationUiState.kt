package app.aaps.plugins.calibration.compose

import androidx.compose.runtime.Immutable
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.plugins.calibration.CalibrationFit
import app.aaps.plugins.calibration.db.CalibrationEntry

@Immutable
data class CalibrationUiState(
    val sessionStart: Long? = null,
    val warmUpEndsAt: Long? = null,
    val isInWarmUp: Boolean = false,
    val entries: List<CalibrationEntry> = emptyList(),
    val fit: CalibrationFit? = null,
    val now: Long = 0L,
    val selectedEntryId: Long? = null,
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MGDL
)
