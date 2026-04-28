package app.aaps.pump.carelevo.compose.patchflow

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.LocalSnackbarHostState
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
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scanFailedMessage = stringResource(R.string.carelevo_toast_msg_scan_failed)
    val bluetoothNotEnabledMessage = stringResource(R.string.carelevo_toast_msg_bluetooth_not_enabled)
    val scanInProgressMessage = stringResource(R.string.carelevo_toast_msg_scan_in_progress)
    val profileNotSetMessage = stringResource(R.string.carelevo_toast_msg_profile_not_set)
    val noPatchFoundMessage = stringResource(R.string.carelevo_toast_msg_patch_not_found)
    val connectFailedMessage = stringResource(R.string.carelevo_toast_msg_connect_failed)
    val connectCompleteMessage = stringResource(R.string.carelevo_toast_msg_connect_complete)
    val discardCompleteMessage = stringResource(R.string.carelevo_toast_msg_discard_complete)
    val discardFailedMessage = stringResource(R.string.carelevo_toast_msg_discard_failed)
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showConnectDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel, sharedViewModel) {
        viewModel.event.collect { event ->
            when (event) {
                CarelevoConnectPrepareEvent.ShowConnectDialog                 -> {
                    showConnectDialog = true
                }

                CarelevoConnectPrepareEvent.ShowMessageScanFailed             -> {
                    snackbarHostState.showSnackbar(scanFailedMessage)
                }

                CarelevoConnectPrepareEvent.ShowMessageBluetoothNotEnabled    -> {
                    snackbarHostState.showSnackbar(bluetoothNotEnabledMessage)
                }

                CarelevoConnectPrepareEvent.ShowMessageScanIsWorking          -> {
                    snackbarHostState.showSnackbar(scanInProgressMessage)
                }

                CarelevoConnectPrepareEvent.ShowMessageNotSetUserSettingInfo  -> {
                    snackbarHostState.showSnackbar(profileNotSetMessage)
                }

                CarelevoConnectPrepareEvent.ShowMessageSelectedDeviceIseEmpty -> {
                    snackbarHostState.showSnackbar(noPatchFoundMessage)
                }

                CarelevoConnectPrepareEvent.ConnectFailed                     -> {
                    showConnectDialog = false
                    snackbarHostState.showSnackbar(connectFailedMessage)
                }

                CarelevoConnectPrepareEvent.ConnectComplete                   -> {
                    showConnectDialog = false
                    Toast.makeText(context, connectCompleteMessage, Toast.LENGTH_SHORT).show()
                    sharedViewModel.setPage(CarelevoPatchStep.SAFETY_CHECK)
                }

                CarelevoConnectPrepareEvent.DiscardComplete                   -> {
                    showDiscardDialog = false
                    Toast.makeText(context, discardCompleteMessage, Toast.LENGTH_SHORT).show()
                    onExitFlow()
                }

                CarelevoConnectPrepareEvent.DiscardFailed                     -> {
                    showDiscardDialog = false
                    snackbarHostState.showSnackbar(discardFailedMessage)
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
        CarelevoPatchConnectSheet(
            onDismissRequest = { showConnectDialog = false },
            onConfirmClick = {
                showConnectDialog = false
                viewModel.startConnect(sharedViewModel.inputInsulin)
            },
            onResearchClick = {
                showConnectDialog = false
                viewModel.startScan()
            }
        )
    }

    CarelevoPatchConnectContent(
        onDiscardClick = { showDiscardDialog = true },
        onSearchClick = { viewModel.startScan() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarelevoPatchConnectSheet(
    onDismissRequest: () -> Unit,
    onConfirmClick: () -> Unit,
    onResearchClick: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.carelevo_dialog_patch_connect_message_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "CareLevo",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onResearchClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.carelevo_btn_research))
                }
                Button(
                    onClick = onConfirmClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.carelevo_btn_confirm))
                }
            }
        }
    }
}

@Composable
private fun CarelevoPatchConnectContent(
    onDiscardClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onDiscardClick,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
            ) {
                PatchFlowButtonText(text = stringResource(R.string.carelevo_btn_patch_expiration))
            }
            Button(
                onClick = onSearchClick,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
            ) {
                PatchFlowButtonText(text = stringResource(R.string.carelevo_btn_input_search_patch))
            }
        }
    }
}

@Composable
private fun CarelevoPatchConnectStepSection(
    stepLabel: String,
    title: String,
    description: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
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
            onDiscardClick = {},
            onSearchClick = {}
        )
    }
}
