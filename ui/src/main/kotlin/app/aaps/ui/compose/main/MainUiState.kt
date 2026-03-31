package app.aaps.ui.compose.main

import androidx.compose.runtime.Immutable
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.overview.graph.TbrState

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
    val rawProfileName: String = "", // Profile name without remaining time (for scene badge comparison)
    val profilePercentage: Int = 100, // Current profile percentage (for scene badge comparison)
    val isProfileModified: Boolean = false,
    val profileProgress: Float = 0f, // 0-1 progress for temporary profile switch
    // TempTarget state for chip
    val tempTargetText: String = "",
    val tempTargetState: TempTargetChipState = TempTargetChipState.None,
    val tempTargetProgress: Float = 0f, // 0-1 progress for active temp target
    val tempTargetReason: TT.Reason? = null, // TT reason for icon coloring
    val tempTargetRecordId: Long = 0, // DB record ID (for scene override detection)
    // Running mode state for chip
    val runningMode: RM.Mode = RM.Mode.DISABLED_LOOP,
    val runningModeText: String = "",
    val runningModeProgress: Float = 0f, // 0-1 progress for temporary modes
    val runningModeRecordId: Long = 0, // DB record ID (for scene override detection)
    // Running TBR state for chip (HIGH / LOW / NONE)
    val tbrState: TbrState = TbrState.NONE,
    // QuickWizard entries for treatment bottom sheet
    val quickWizardItems: List<QuickWizardItem> = emptyList(),
    // Navigation-triggered dialogs
    val showAuthFailedDialog: Boolean = false
)

@Immutable
data class QuickWizardItem(
    val guid: String,
    val buttonText: String,
    val detail: String? = null,
    val isEnabled: Boolean = false,
    val disabledReason: String? = null,
    val mode: Int = 0  // QuickWizardMode.value — 0=WIZARD, 1=INSULIN, 2=CARBS
)

/**
 * Confirmation dialog data for actions that need a confirm step before executing.
 * Shared by toolbar quick actions, automation bottom sheet, and TT presets.
 */
@Immutable
data class ActionConfirmation(
    val title: String,
    val message: String,
    val onConfirmAction: ConfirmableAction
)

/**
 * The action to execute when a confirmation dialog is confirmed.
 */
sealed class ConfirmableAction {

    data class ExecuteAutomation(val automationId: String) : ConfirmableAction()
    data class ActivateTempTargetPreset(val presetId: String) : ConfirmableAction()
    data class ActivateProfile(
        val profileName: String,
        val percentage: Int,
        val durationMinutes: Int
    ) : ConfirmableAction()

    data class ActivateScene(val sceneId: String, val durationMinutes: Int) : ConfirmableAction()
    data object DeactivateScene : ConfirmableAction()
}
