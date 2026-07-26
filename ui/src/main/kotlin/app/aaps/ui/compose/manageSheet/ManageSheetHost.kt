package app.aaps.ui.compose.manageSheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.dialogs.ElementConfirmationDialog
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.navigation.NavigationRequest

/**
 * Coordinator composable that owns the manage bottom sheet visibility state, collects ViewModel state, and renders
 * the cancel-TBR / cancel-extended-bolus confirmation dialog.
 *
 * Cancel now goes through the master via [ManageViewModel.cancelTempBasal] / [ManageViewModel.cancelExtendedBolus]:
 * the master prepares + authors the confirmation, the user confirms its lines here, and [ManageViewModel.commitCancel]
 * applies it (signed round-trip on a client, local on a master).
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

    // The master's prepared cancel confirmation (element type + bolusId + its lines + round-trip label), set via the
    // ShowConfirmation side effect. Held at host level so it survives the sheet dismissing.
    var confirmation by remember { mutableStateOf<ManageViewModel.SideEffect.ShowConfirmation?>(null) }

    LaunchedEffect(Unit) {
        manageViewModel.sideEffect.collect { effect ->
            when (effect) {
                is ManageViewModel.SideEffect.ShowConfirmation -> confirmation = effect
                is ManageViewModel.SideEffect.ShowError        -> onActionsError(effect.comment, effect.elementType.deliveryErrorTitle())
            }
        }
    }

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
            showAuthorizedClients = manageState.showAuthorizedClients,
            showPairWithMaster = manageState.showPairWithMaster,
            showMutatingActions = manageState.showMutatingActions,
            showPump = manageState.showPump,
            cancelTempBasalText = manageState.cancelTempBasalText,
            cancelExtendedBolusText = manageState.cancelExtendedBolusText,
            isPatchPump = manageState.isPatchPump,
            pumpPlugin = manageState.pumpPlugin,
            customActions = manageState.customActions,
            onNavigate = onNavigate,
            onCancelTempBasalClick = { manageViewModel.cancelTempBasal() },
            onCancelExtendedBolusClick = { manageViewModel.cancelExtendedBolus() },
            onCustomActionClick = { manageViewModel.executeCustomAction(it.customActionType) }
        )
    }

    // Confirmation dialog — renders the MASTER's prepared lines; confirm commits the parked cancel exactly once.
    confirmation?.let { c ->
        ElementConfirmationDialog(
            elementType = c.elementType,
            lines = c.lines,
            onConfirm = {
                manageViewModel.commitCancel(c.bolusId, c.elementType, c.label)
                confirmation = null
            },
            onDismiss = { confirmation = null }
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

/** Alarm title for a failed cancel relay, by action — mirrors the previous per-button titles. */
private fun ElementType.deliveryErrorTitle(): String = when (this) {
    ElementType.EXTENDED_BOLUS -> "Extended bolus delivery error"
    else                       -> "Temp basal delivery error"
}

/** Handle returned by [ManageSheetHost] to trigger the sheet from outside. */
class ManageSheetState(
    val show: () -> Unit,
)
