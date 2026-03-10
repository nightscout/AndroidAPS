package app.aaps.ui.compose.fillDialog

import androidx.compose.runtime.Immutable
import app.aaps.core.data.model.TE

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

    // Site rotation
    val siteRotationEnabled: Boolean = false,
    val siteLocation: TE.Location = TE.Location.NONE,
    val siteArrow: TE.Arrow = TE.Arrow.NONE,
    val lastSiteLocationString: String? = null,
    val selectedSiteLocationString: String? = null,

    // Config
    val showBolus: Boolean = true,
    val showNotesFromPreferences: Boolean = false,
    val simpleMode: Boolean = true
)
