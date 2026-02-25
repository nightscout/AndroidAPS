package app.aaps.ui.compose.overview.manage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Coordinator composable that owns the manage bottom sheet visibility state,
 * collects ViewModel state, and handles cancel-action error callbacks.
 *
 * Eliminates ~10 manage-only callback parameters from MainScreen.
 */
@Composable
fun ManageSheetHost(
    manageViewModel: ManageViewModel,
    isSimpleMode: Boolean,
    // Shared callbacks (also used by OverviewScreen)
    onProfileManagementClick: () -> Unit,
    onTempTargetClick: () -> Unit,
    // Manage-only callbacks
    onTempBasalClick: () -> Unit,
    onExtendedBolusClick: () -> Unit,
    onBgCheckClick: () -> Unit,
    onNoteClick: () -> Unit,
    onExerciseClick: () -> Unit,
    onQuestionClick: () -> Unit,
    onAnnouncementClick: () -> Unit,
    onSiteRotationClick: () -> Unit,
    onQuickWizardClick: () -> Unit,
    onActionsError: (String, String) -> Unit,
): ManageSheetState {
    var visible by remember { mutableStateOf(false) }

    if (visible) {
        val manageState by manageViewModel.uiState.collectAsStateWithLifecycle()
        ManageBottomSheet(
            onDismiss = { visible = false },
            isSimpleMode = isSimpleMode,
            showTempTarget = manageState.showTempTarget,
            showTempBasal = manageState.showTempBasal,
            showCancelTempBasal = manageState.showCancelTempBasal,
            showExtendedBolus = manageState.showExtendedBolus,
            showCancelExtendedBolus = manageState.showCancelExtendedBolus,
            cancelTempBasalText = manageState.cancelTempBasalText,
            cancelExtendedBolusText = manageState.cancelExtendedBolusText,
            customActions = manageState.customActions,
            onProfileManagementClick = onProfileManagementClick,
            onTempTargetClick = onTempTargetClick,
            onTempBasalClick = onTempBasalClick,
            onCancelTempBasalClick = {
                manageViewModel.cancelTempBasal { success, comment ->
                    if (!success) {
                        onActionsError(comment, "Temp basal delivery error")
                    }
                }
            },
            onExtendedBolusClick = onExtendedBolusClick,
            onCancelExtendedBolusClick = {
                manageViewModel.cancelExtendedBolus { success, comment ->
                    if (!success) {
                        onActionsError(comment, "Extended bolus delivery error")
                    }
                }
            },
            onBgCheckClick = onBgCheckClick,
            onNoteClick = onNoteClick,
            onExerciseClick = onExerciseClick,
            onQuestionClick = onQuestionClick,
            onAnnouncementClick = onAnnouncementClick,
            onSiteRotationClick = onSiteRotationClick,
            onQuickWizardClick = onQuickWizardClick,
            onCustomActionClick = { manageViewModel.executeCustomAction(it.customActionType) }
        )
    }

    return remember {
        ManageSheetState(
            show = {
                manageViewModel.refreshState()
                visible = true
            }
        )
    }
}

/** Handle returned by [ManageSheetHost] to trigger the sheet from outside. */
class ManageSheetState(
    val show: () -> Unit,
)
