package app.aaps.pump.carelevo.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.icons.IcLoopPaused
import app.aaps.core.ui.compose.pump.ActionCategory
import app.aaps.core.ui.compose.pump.PumpAction
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewScreen
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.StatusBanner
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.common.model.UiState
import app.aaps.pump.carelevo.compose.dialog.CarelevoActionDialog
import app.aaps.pump.carelevo.compose.dialog.CarelevoPumpStopDurationDialog
import app.aaps.pump.carelevo.presentation.model.CarelevoOverviewEvent
import app.aaps.pump.carelevo.presentation.type.CarelevoScreenType
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoOverviewViewModel

@Composable
fun CarelevoOverviewScreen(
    viewModel: CarelevoOverviewViewModel,
    onStartWorkflow: (CarelevoScreenType) -> Unit
) {
    val snackbarHostState = LocalSnackbarHostState.current
    val baseState by viewModel.overviewUiState.collectAsStateWithLifecycle()
    val actionState by viewModel.uiState.collectAsStateWithLifecycle()

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showResumeDialog by remember { mutableStateOf(false) }
    var showSuspendTimePicker by remember { mutableStateOf(false) }
    var selectedDurationIndex by remember { mutableIntStateOf(0) }

    val stopDurations = remember { listOf(30, 60, 90, 120, 150, 180, 210, 240) }
    val stopDurationLabels = listOf(
        stringResource(R.string.carelevo_pump_stop_duration_label_30_min),
        stringResource(R.string.carelevo_pump_stop_duration_label_1_hour),
        stringResource(R.string.carelevo_pump_stop_duration_label_1_hour_30_min),
        stringResource(R.string.carelevo_pump_stop_duration_label_2_hour),
        stringResource(R.string.carelevo_pump_stop_duration_label_2_hour_30_min),
        stringResource(R.string.carelevo_pump_stop_duration_label_3_hour),
        stringResource(R.string.carelevo_pump_stop_duration_label_3_hour_30_min),
        stringResource(R.string.carelevo_pump_stop_duration_label_4_hour),
    )
    val bluetoothNotEnabledMessage = stringResource(R.string.carelevo_toast_msg_bluetooth_not_enabled)
    val notConnectedMessage = stringResource(R.string.carelevo_toast_msg_patch_not_connected)
    val discardCompleteMessage = stringResource(R.string.carelevo_toast_msg_discard_complete)
    val discardFailedMessage = stringResource(R.string.carelevo_toast_msg_discard_failed)
    val resumeSuccessMessage = stringResource(R.string.carelevo_toast_mag_set_basal_resume_success)
    val resumeFailedMessage = stringResource(R.string.carelevo_toast_mag_set_basal_resume_fail)
    val suspendSuccessMessage = stringResource(R.string.carelevo_toast_mag_set_basal_suspend_success)
    val suspendFailedMessage = stringResource(R.string.carelevo_toast_mag_set_basal_suspend_fail)
    val connectLabel = stringResource(R.string.carelevo_overview_connect_btn_label)
    val communicationLabel = stringResource(R.string.carelevo_overview_communication_btn_label)
    val discardLabel = stringResource(R.string.carelevo_overview_pump_discard_btn_label)
    val stopLabel = stringResource(R.string.carelevo_overview_pump_stop_btn_label)
    val resumeLabel = stringResource(R.string.carelevo_overview_pump_resume_btn_label)
    val isActionLoading = actionState is UiState.Loading

    LaunchedEffect(viewModel) {
        if (!viewModel.isCreated) {
            viewModel.observePatchInfo()
            viewModel.observePatchState()
            viewModel.observeInfusionInfo()
            viewModel.observeBleState()
            viewModel.observeProfile()
            viewModel.setIsCreated(true)
        }
        viewModel.refreshPatchInfusionInfo()
    }

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                CarelevoOverviewEvent.ShowMessageBluetoothNotEnabled    -> snackbarHostState.showSnackbar(bluetoothNotEnabledMessage)
                CarelevoOverviewEvent.ShowMessageCarelevoIsNotConnected -> snackbarHostState.showSnackbar(notConnectedMessage)
                CarelevoOverviewEvent.DiscardComplete                   -> snackbarHostState.showSnackbar(discardCompleteMessage)
                CarelevoOverviewEvent.DiscardFailed                     -> snackbarHostState.showSnackbar(discardFailedMessage)
                CarelevoOverviewEvent.ResumePumpComplete                -> snackbarHostState.showSnackbar(resumeSuccessMessage)
                CarelevoOverviewEvent.ResumePumpFailed                  -> snackbarHostState.showSnackbar(resumeFailedMessage)
                CarelevoOverviewEvent.StopPumpComplete                  -> snackbarHostState.showSnackbar(suspendSuccessMessage)
                CarelevoOverviewEvent.StopPumpFailed                    -> snackbarHostState.showSnackbar(suspendFailedMessage)
                CarelevoOverviewEvent.ShowPumpResumeDialog              -> showResumeDialog = true
                CarelevoOverviewEvent.ShowPumpStopDurationSelectDialog  -> showSuspendTimePicker = true
                CarelevoOverviewEvent.ClickPumpStopResumeBtn,
                CarelevoOverviewEvent.NoAction                          -> Unit
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
                viewModel.startDiscardProcess()
            },
            secondaryText = stringResource(R.string.carelevo_btn_cancel),
            onSecondaryClick = { showDiscardDialog = false }
        )
    }

    if (showResumeDialog) {
        CarelevoActionDialog(
            title = stringResource(R.string.carelevo_pump_resume_title),
            content = stringResource(R.string.carelevo_pump_resume_description),
            onDismissRequest = { showResumeDialog = false },
            primaryText = stringResource(R.string.carelevo_btn_confirm),
            onPrimaryClick = {
                showResumeDialog = false
                viewModel.startPumpResume()
            },
            secondaryText = stringResource(R.string.carelevo_btn_cancel),
            onSecondaryClick = { showResumeDialog = false }
        )
    }

    if (showSuspendTimePicker) {
        CarelevoPumpStopDurationDialog(
            options = stopDurations,
            labels = stopDurationLabels,
            initialIndex = selectedDurationIndex,
            onDismissRequest = {
                showSuspendTimePicker = false
                selectedDurationIndex = 0
            },
            onConfirm = { duration ->
                showSuspendTimePicker = false
                viewModel.startPumpStopProcess(duration)
                selectedDurationIndex = 0
            }
        )
    }

    val patchedState = baseState.copy(
        primaryActions = baseState.primaryActions.map { action ->
            when (action.label) {
                connectLabel       -> action.copy(onClick = { onStartWorkflow(CarelevoScreenType.CONNECTION_FLOW_START) })
                communicationLabel -> action.copy(onClick = { onStartWorkflow(CarelevoScreenType.COMMUNICATION_CHECK) })
                else               -> action
            }
        },
        managementActions = baseState.managementActions.map { action ->
            when (action.label) {
                discardLabel           -> action.copy(onClick = { showDiscardDialog = true })
                stopLabel, resumeLabel -> action.copy(onClick = { viewModel.triggerEvent(CarelevoOverviewEvent.ClickPumpStopResumeBtn) })
                else                   -> action
            }
        }
    )

    Box {
        PumpOverviewScreen(
            state = patchedState,
            customContent = {
                Image(
                    painter = painterResource(id = R.drawable.ic_carelevo_128),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(128.dp),
                    contentScale = ContentScale.Fit
                )
            }
        )

        if (isActionLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

@Preview(showBackground = true, name = "Carelevo Overview Connected")
@Composable
private fun CarelevoOverviewScreenConnectedPreview() {
    MaterialTheme {
        PumpOverviewScreen(
            state = PumpOverviewUiState(
                statusBanner = StatusBanner(
                    text = stringResource(R.string.carelevo_state_connected_value),
                    level = StatusLevel.NORMAL
                ),
                infoRows = listOf(
                    PumpInfoRow(
                        label = stringResource(R.string.carelevo_bluetooth_state_key),
                        value = stringResource(R.string.carelevo_state_connected_value)
                    ),
                    PumpInfoRow(
                        label = stringResource(R.string.carelevo_serial_number_key),
                        value = "04:CD:15:D0:10:05"
                    ),
                    PumpInfoRow(
                        label = stringResource(R.string.carelevo_firmware_version_key),
                        value = "T165"
                    ),
                    PumpInfoRow(
                        label = stringResource(R.string.carelevo_boot_date_time_key),
                        value = "2026-04-13 09:00"
                    ),
                    PumpInfoRow(
                        label = stringResource(R.string.carelevo_expiration_key),
                        value = "2026-04-20 09:00"
                    ),
                    PumpInfoRow(
                        label = stringResource(R.string.carelevo_running_remain_time),
                        value = "2d 11h 20m"
                    ),
                    PumpInfoRow(
                        label = stringResource(R.string.carelevo_basal_rate_key),
                        value = stringResource(R.string.common_label_unit_value_dose_per_speed_with_space, 1.2)
                    ),
                    PumpInfoRow(
                        label = stringResource(R.string.carelevo_temp_basal_rate_key),
                        value = stringResource(R.string.common_label_unit_value_dose_per_speed_with_space, 0.5)
                    ),
                    PumpInfoRow(
                        label = stringResource(R.string.carelevo_insulin_remain_key),
                        value = "298.0 U"
                    ),
                    PumpInfoRow(
                        label = stringResource(R.string.carelevo_total_insulin_key),
                        value = stringResource(R.string.common_label_unit_value_dose_with_space, "12.50")
                    )
                ),
                primaryActions = emptyList(),
                managementActions = listOf(
                    PumpAction(
                        label = stringResource(R.string.carelevo_overview_pump_discard_btn_label),
                        icon = Icons.Filled.SwapHoriz,
                        category = ActionCategory.MANAGEMENT,
                        enabled = true,
                        visible = true,
                        onClick = {}
                    ),
                    PumpAction(
                        label = stringResource(R.string.carelevo_overview_pump_stop_btn_label),
                        icon = IcLoopPaused,
                        category = ActionCategory.MANAGEMENT,
                        enabled = true,
                        visible = true,
                        onClick = {}
                    )
                )
            ),
            customContent = {
                Image(
                    painter = painterResource(id = R.drawable.ic_carelevo_128),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(128.dp),
                    contentScale = ContentScale.Fit
                )
            }
        )
    }
}

@Preview(showBackground = true, name = "Carelevo Overview Disconnected")
@Composable
private fun CarelevoOverviewScreenDisconnectedPreview() {
    MaterialTheme {
        PumpOverviewScreen(
            state = PumpOverviewUiState(
                statusBanner = StatusBanner(
                    text = stringResource(R.string.carelevo_state_none_value),
                    level = StatusLevel.WARNING
                ),
                infoRows = listOf(
                    PumpInfoRow(
                        label = stringResource(R.string.carelevo_bluetooth_state_key),
                        value = stringResource(R.string.carelevo_state_disconnected_value)
                    )
                ),
                primaryActions = listOf(
                    PumpAction(
                        label = stringResource(R.string.carelevo_overview_connect_btn_label),
                        icon = Icons.Filled.SwapHoriz,
                        enabled = true,
                        onClick = {}
                    )
                )
            ),
            customContent = {
                Image(
                    painter = painterResource(id = R.drawable.ic_carelevo_128),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(128.dp),
                    contentScale = ContentScale.Fit
                )
            }
        )
    }
}
