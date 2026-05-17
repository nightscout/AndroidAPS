package app.aaps.ui.compose.manageSheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.navigation.NavigationRequest

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
    onNavigate: (NavigationRequest) -> Unit,
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
            showBatteryChange = manageState.showBatteryChange,
            showFill = manageState.showFill,
            cancelTempBasalText = manageState.cancelTempBasalText,
            cancelExtendedBolusText = manageState.cancelExtendedBolusText,
            isPatchPump = manageState.isPatchPump,
            pumpPlugin = manageState.pumpPlugin,
            customActions = manageState.customActions,
            onNavigate = onNavigate,
            onCancelTempBasalClick = {
                manageViewModel.cancelTempBasal { success, comment ->
                    if (!success) {
                        onActionsError(comment, "Temp basal delivery error")
                    }
                }
            },
            onCancelExtendedBolusClick = {
                manageViewModel.cancelExtendedBolus { success, comment ->
                    if (!success) {
                        onActionsError(comment, "Extended bolus delivery error")
                    }
                }
            },
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
