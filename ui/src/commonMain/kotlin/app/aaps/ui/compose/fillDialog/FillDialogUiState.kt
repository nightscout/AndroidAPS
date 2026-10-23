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

    // Date/Time. The time the dialog opened, and the offset from it in minutes.
    val eventTime: Long = 0L,
    val eventTimeOriginal: Long = 0L,
    val timeOffsetMinutes: Int = 0,

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
    /** False on a pump that fills and primes itself (patch pumps): no prime amount, no presets, no prime. */
    val primeAvailable: Boolean = true,
    val showNotesFromPreferences: Boolean = false,
    val simpleMode: Boolean = true,
    val concentrationEnabled: Boolean = false
) {

    val eventTimeChanged: Boolean
        get() = eventTime != eventTimeOriginal

    /** Whether a prime will be delivered */
    val hasPrime: Boolean
        get() = primeAvailable && insulinAfterConstraints > 0

    /** Whether any actionable item is selected */
    val hasAction: Boolean
        get() = hasPrime || siteChange || insulinCartridgeChange

    /** Show insulin change section when: cartridge change checked, multiple insulins, time not changed (now) */
    val showInsulinChange: Boolean
        get() = insulinCartridgeChange && availableInsulins.size > 1 && !eventTimeChanged

    /** Whether user selected a different insulin than currently active */
    val insulinChanged: Boolean
        get() = selectedInsulin != null && selectedInsulin.insulinLabel != activeInsulinLabel
}
