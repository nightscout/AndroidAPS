package app.aaps.ui.compose.extendedBolusDialog

import androidx.compose.runtime.Immutable

@Immutable
data class ExtendedBolusDialogUiState(
    // User input
    val insulin: Double = 0.0,
    val durationMinutes: Double = 0.0,
    // Config (set once during init)
    val maxInsulin: Double = 0.0,
    val minInsulin: Double = 0.1,
    val extendedStep: Double = 0.1,
    val extendedDurationStep: Double = 30.0,
    val extendedMaxDuration: Double = 720.0,
    val showLoopStopWarning: Boolean = false,
    val loopStopWarningAccepted: Boolean = false,
    // True while a prepare round-trip is in flight; disables the Confirm button to prevent a double prepare.
    val isPreparing: Boolean = false,
)
