package app.aaps.ui.compose.main

import androidx.compose.runtime.Immutable
import app.aaps.core.data.model.RM

/**
 * State of the TempTarget chip in Overview
 */
enum class TempTargetChipState {

    /** No temp target, showing profile default */
    None,

    /** No temp target, but APS adjusted the target */
    Adjusted,

    /** Active temp target */
    Active
}

@Immutable
data class MainUiState(
    val isDrawerOpen: Boolean = false,
    val isSimpleMode: Boolean = true,
    val isProfileLoaded: Boolean = false,
    val showAboutDialog: Boolean = false,
    val showMaintenanceSheet: Boolean = false,
    // Profile state for top bar chip
    val profileName: String = "",
    val isProfileModified: Boolean = false,
    val profileProgress: Float = 0f, // 0-1 progress for temporary profile switch
    // TempTarget state for chip
    val tempTargetText: String = "",
    val tempTargetState: TempTargetChipState = TempTargetChipState.None,
    val tempTargetProgress: Float = 0f, // 0-1 progress for active temp target
    val tempTargetReason: String? = null, // TT reason for icon coloring
    // Running mode state for chip
    val runningMode: RM.Mode = RM.Mode.DISABLED_LOOP,
    val runningModeText: String = "",
    val runningModeProgress: Float = 0f, // 0-1 progress for temporary modes
    // QuickWizard entries for treatment bottom sheet
    val quickWizardItems: List<QuickWizardItem> = emptyList()
)

@Immutable
data class QuickWizardItem(
    val guid: String,
    val buttonText: String,
    val detail: String? = null,
    val isEnabled: Boolean = false,
    val disabledReason: String? = null
)
