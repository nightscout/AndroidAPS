package app.aaps.ui.compose.tempBasalDialog

import androidx.compose.runtime.Immutable

@Immutable
data class TempBasalDialogUiState(
    // User input
    val basalPercent: Double = 100.0,
    val basalAbsolute: Double = 0.0,
    val durationMinutes: Double = 0.0,
    // Config (set once during init)
    val isPercentPump: Boolean = true,
    val maxTempPercent: Double = 200.0,
    val tempPercentStep: Double = 10.0,
    val maxTempAbsolute: Double = 10.0,
    val tempAbsoluteStep: Double = 0.05,
    val tempDurationStep: Double = 60.0,
    val tempMaxDuration: Double = 720.0,
)
