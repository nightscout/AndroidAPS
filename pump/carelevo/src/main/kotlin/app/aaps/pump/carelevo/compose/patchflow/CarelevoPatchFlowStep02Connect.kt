package app.aaps.pump.carelevo.compose.patchflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.banner.ErrorBanner
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.compose.dialog.CarelevoActionDialog
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectPrepareEvent
import app.aaps.pump.carelevo.presentation.type.CarelevoPatchStep
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectViewModel
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectionFlowViewModel

@Composable
internal fun CarelevoPatchFlowStep02Connect(
    viewModel: CarelevoPatchConnectViewModel,
    sharedViewModel: CarelevoPatchConnectionFlowViewModel,
    onExitFlow: () -> Unit
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showConnectDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(viewModel, sharedViewModel) {
        viewModel.event.collect { event ->
            when (event) {
                CarelevoConnectPrepareEvent.ShowConnectDialog                 -> {
                    showConnectDialog = true
                }

                CarelevoConnectPrepareEvent.ShowMessageScanFailed             -> {
                    errorMessage = R.string.carelevo_toast_msg_scan_failed
                }

                CarelevoConnectPrepareEvent.ShowMessageBluetoothNotEnabled    -> {
                    errorMessage = R.string.carelevo_toast_msg_bluetooth_not_enabled
                }

                CarelevoConnectPrepareEvent.ShowMessageScanIsWorking          -> Unit

                CarelevoConnectPrepareEvent.ShowMessageNotSetUserSettingInfo  -> {
                    errorMessage = R.string.carelevo_toast_msg_profile_not_set
                }

                CarelevoConnectPrepareEvent.ShowMessageSelectedDeviceIseEmpty -> {
                    errorMessage = R.string.carelevo_toast_msg_patch_not_found
                }

                CarelevoConnectPrepareEvent.ConnectFailed                     -> {
                    showConnectDialog = false
                    errorMessage = R.string.carelevo_toast_msg_connect_failed
                }

                CarelevoConnectPrepareEvent.ConnectComplete                   -> {
                    showConnectDialog = false
                    errorMessage = null
                    sharedViewModel.setPage(CarelevoPatchStep.SAFETY_CHECK)
                }

                CarelevoConnectPrepareEvent.DiscardComplete                   -> {
                    showDiscardDialog = false
                    onExitFlow()
                }

                CarelevoConnectPrepareEvent.DiscardFailed                     -> {
                    showDiscardDialog = false
                    errorMessage = R.string.carelevo_toast_msg_discard_failed
                }

                CarelevoConnectPrepareEvent.NoAction                          -> Unit
            }
        }
    }

    if (showDiscardDialog) {
        CarelevoActionDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = stringResource(R.string.carelevo_dialog_patch_discard_message_title),
            content = stringResource(R.string.carelevo_dialog_patch_discard_message_desc),
            primaryText = stringResource(R.string.carelevo_btn_confirm),
            onPrimaryClick = {
                showDiscardDialog = false
                viewModel.startPatchDiscardProcess()
            },
            secondaryText = stringResource(R.string.carelevo_btn_cancel),
            onSecondaryClick = { showDiscardDialog = false }
        )
    }

    if (showConnectDialog) {
        CarelevoActionDialog(
            onDismissRequest = { showConnectDialog = false },
            title = stringResource(R.string.carelevo_dialog_patch_connect_message_title),
            content = "CareLevo",
            primaryText = stringResource(R.string.carelevo_btn_confirm),
            onPrimaryClick = {
                showConnectDialog = false
                viewModel.startConnect(sharedViewModel.inputInsulin)
            },
            secondaryText = stringResource(R.string.carelevo_btn_research),
            onSecondaryClick = {
                showConnectDialog = false
                viewModel.startScan()
            }
        )
    }

    CarelevoPatchConnectContent(
        errorMessage = errorMessage,
        onDiscardClick = { showDiscardDialog = true },
        onSearchClick = {
            errorMessage = null
            viewModel.startScan()
        }
    )
}

@Composable
private fun CarelevoPatchConnectContent(
    errorMessage: Int?,
    onDiscardClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(R.string.carelevo_btn_input_search_patch),
            onClick = onSearchClick
        ),
        secondaryButton = WizardButton(
            text = stringResource(R.string.carelevo_btn_patch_expiration),
            onClick = onDiscardClick
        )
    ) {
        errorMessage?.let { ErrorBanner(message = stringResource(it)) }
        CarelevoPatchConnectStepSection(
            stepLabel = stringResource(R.string.carelevo_patch_step_1),
            title = stringResource(R.string.carelevo_patch_connect_step_1_title),
            description = stringResource(R.string.carelevo_patch_connect_step_1_desc)
        )
        CarelevoPatchConnectStepSection(
            stepLabel = stringResource(R.string.carelevo_patch_step_2),
            title = stringResource(R.string.carelevo_patch_connect_step_2_title),
            description = stringResource(R.string.carelevo_patch_connect_step_2_desc)
        )
    }
}

@Composable
private fun CarelevoPatchConnectStepSection(
    stepLabel: String,
    title: String,
    description: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small), verticalAlignment = Alignment.Bottom) {
            Text(
                text = stepLabel,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        }

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(showBackground = true, name = "Patch Connect")
@Composable
private fun CarelevoPatchFlowStep02ConnectPreview() {
    MaterialTheme {
        CarelevoPatchConnectContent(
            errorMessage = null,
            onDiscardClick = {},
            onSearchClick = {}
        )
    }
}
