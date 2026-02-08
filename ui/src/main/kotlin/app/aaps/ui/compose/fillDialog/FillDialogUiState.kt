package app.aaps.ui.compose.fillDialog

import androidx.compose.runtime.Immutable

enum class FillPreselect {
    NONE,
    SITE_CHANGE,
    CARTRIDGE_CHANGE
}

@Immutable
data class FillDialogUiState(
    val insulin: Double = 0.0,
    val siteChange: Boolean = false,
    val insulinCartridgeChange: Boolean = false,
    val notes: String = "",

    // Date/Time
    val eventTime: Long = System.currentTimeMillis(),
    val eventTimeChanged: Boolean = false,

    // Config values (set once on init)
    val maxInsulin: Double = 0.0,
    val bolusStep: Double = 0.1,
    val presetButton1: Double = 0.0,
    val presetButton2: Double = 0.0,
    val presetButton3: Double = 0.0,

    // Computed after constraints
    val insulinAfterConstraints: Double = 0.0,
    val constraintApplied: Boolean = false,

    // Computed after concentration
    val insulinAfterConcentration: Double = 0.0,
    val concentrationApplied: Boolean = false,

    // Config
    val showBolus: Boolean = true,
    val showNotesFromPreferences: Boolean = false,
    val simpleMode: Boolean = true
)
