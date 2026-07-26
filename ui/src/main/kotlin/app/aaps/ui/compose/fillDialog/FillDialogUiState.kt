package app.aaps.ui.compose.fillDialog

import androidx.compose.runtime.Immutable
import app.aaps.core.data.model.ICfg
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

    // Insulin change section
    val availableInsulins: List<ICfg> = emptyList(),
    val selectedInsulin: ICfg? = null,
    val activeInsulinLabel: String? = null,

    // Pump units warning (non-null when concentration != U100)
    val pumpUnitsWarning: String? = null,

    // Site rotation
    val siteRotationEnabled: Boolean = false,
    val siteLocation: TE.Location = TE.Location.NONE,
    val siteArrow: TE.Arrow = TE.Arrow.NONE,
    val lastSiteLocationString: String? = null,
    val selectedSiteLocationString: String? = null,

    // Config
    val showBolus: Boolean = true,
    val showNotesFromPreferences: Boolean = false,
    val simpleMode: Boolean = true,
    val concentrationEnabled: Boolean = false
) {

    /** Whether any actionable item is selected */
    val hasAction: Boolean
        get() = insulinAfterConstraints > 0 || siteChange || insulinCartridgeChange

    /** Show insulin change section when: cartridge change checked, multiple insulins, time not changed (now) */
    val showInsulinChange: Boolean
        get() = insulinCartridgeChange && availableInsulins.size > 1 && !eventTimeChanged

    /** Whether user selected a different insulin than currently active */
    val insulinChanged: Boolean
        get() = selectedInsulin != null && selectedInsulin.insulinLabel != activeInsulinLabel
}
