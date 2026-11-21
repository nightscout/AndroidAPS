package app.aaps.ui.compose.treatmentsSheet

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.ui.compose.main.QuickWizardItem

/**
 * UI state for the Treatment bottom sheet
 */
@Immutable
data class TreatmentUiState(
    // Visibility states (computed from preferences)
    val showCgm: Boolean = false,
    val showCalibration: Boolean = false,
    val showTreatment: Boolean = true,
    val showInsulin: Boolean = true,
    val showCarbs: Boolean = true,
    val showCalculator: Boolean = true,

    // The active BG source's own icon for the CGM button, or null for the generic CGM icon
    val cgmIcon: ImageVector? = null,

    // QuickWizard items
    val quickWizardItems: List<QuickWizardItem> = emptyList(),

    // Settings visibility
    val showSettingsIcon: Boolean = false
)
