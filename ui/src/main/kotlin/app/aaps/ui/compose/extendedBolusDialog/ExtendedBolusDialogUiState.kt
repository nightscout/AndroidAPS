package app.aaps.ui.compose.extendedBolusDialog

import androidx.compose.runtime.Immutable

@Immutable
data class ExtendedBolusDialogUiState(
    // User input
    val insulin: Double = 0.0,
    val durationMinutes: Double = 0.0,
    // Config (set once during init)
    val maxInsulin: Double = 0.0,
    val extendedStep: Double = 0.1,
    val extendedDurationStep: Double = 30.0,
    val extendedMaxDuration: Double = 720.0,
    val showLoopStopWarning: Boolean = false,
    val loopStopWarningAccepted: Boolean = false,
)
