package app.aaps.ui.compose.treatmentDialog

import androidx.compose.runtime.Immutable

@Immutable
data class TreatmentDialogUiState(
    // User input
    val insulin: Double = 0.0,
    val carbs: Int = 0,
    // Config (set once during init)
    val maxInsulin: Double = 0.0,
    val maxCarbs: Int = 0,
    val bolusStep: Double = 0.1,
    val isAapsClient: Boolean = false
)
