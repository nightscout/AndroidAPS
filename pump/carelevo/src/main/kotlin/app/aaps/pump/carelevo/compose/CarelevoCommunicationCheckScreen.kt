package app.aaps.pump.carelevo.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.common.model.UiState
import app.aaps.pump.carelevo.compose.dialog.CarelevoActionDialog
import app.aaps.pump.carelevo.presentation.model.CarelevoCommunicationCheckEvent
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoCommunicationCheckViewModel

@Composable
internal fun CarelevoCommunicationCheckScreen(
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onExit: () -> Unit
) {
    val viewModel: CarelevoCommunicationCheckViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current
    val title = stringResource(R.string.carelevo_comm_check_title)
    val bluetoothDisabledMessage = stringResource(R.string.carelevo_toast_msg_bluetooth_not_enabled)
    val patchAddressInvalidMessage = stringResource(R.string.carelevo_comm_check_patch_address_invalid)
    val communicationFailedMessage = stringResource(R.string.carelevo_comm_check_failed)
    val discardFailedMessage = stringResource(R.string.carelevo_toast_msg_discard_failed)
    var showDiscardDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        onExit()
    }

    LaunchedEffect(title) {
        setToolbarConfig(
            ToolbarConfig(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(app.aaps.core.ui.R.string.back)
                        )
                    }
                },
                actions = {}
            )
        )
    }

    LaunchedEffect(viewModel) {
        if (!viewModel.isCreated) {
            viewModel.setIsCreated(true)
        }
        viewModel.event.collect { event ->
            when (event) {
                CarelevoCommunicationCheckEvent.ShowMessageBluetoothNotEnabled -> {
                    snackbarHostState.showSnackbar(bluetoothDisabledMessage)
                }

                CarelevoCommunicationCheckEvent.ShowMessagePatchAddressInvalid -> {
                    snackbarHostState.showSnackbar(patchAddressInvalidMessage)
                }

                CarelevoCommunicationCheckEvent.CommunicationCheckComplete     -> {
                    onExit()
                }

                CarelevoCommunicationCheckEvent.CommunicationCheckFailed       -> {
                    snackbarHostState.showSnackbar(communicationFailedMessage)
                }

                CarelevoCommunicationCheckEvent.DiscardComplete                -> {
                    showDiscardDialog = false
                    onExit()
                }

                CarelevoCommunicationCheckEvent.DiscardFailed                  -> {
                    showDiscardDialog = false
                    snackbarHostState.showSnackbar(discardFailedMessage)
                }

                CarelevoCommunicationCheckEvent.NoAction                       -> Unit
            }
        }
    }

    if (showDiscardDialog) {
        CarelevoActionDialog(
            title = stringResource(R.string.carelevo_dialog_patch_discard_message_title),
            content = stringResource(R.string.carelevo_dialog_patch_discard_message_desc),
            onDismissRequest = { showDiscardDialog = false },
            primaryText = stringResource(R.string.carelevo_btn_confirm),
            onPrimaryClick = {
                showDiscardDialog = false
                viewModel.startForceDiscard()
            },
            secondaryText = stringResource(R.string.carelevo_btn_cancel),
            onSecondaryClick = { showDiscardDialog = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.carelevo_comm_check_description),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { showDiscardDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                ) {
                    Text(text = stringResource(R.string.alarm_feat_title_warning_expired_patch))
                }
                Button(
                    onClick = { viewModel.startReconnect() },
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                ) {
                    Text(text = stringResource(R.string.carelevo_overview_communication_btn_label))
                }
            }
        }

        if (uiState is UiState.Loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(app.aaps.core.ui.R.string.loading),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
