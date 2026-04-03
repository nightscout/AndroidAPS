package app.aaps.pump.eopatch.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.ActionCategory
import app.aaps.core.ui.compose.pump.PumpAction
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewScreen
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.StatusBanner
import app.aaps.pump.eopatch.R

@Composable
fun EopatchOverviewScreen(
    viewModel: EopatchOverviewViewModel
) {
    val baseState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current

    // Dialog state
    var showSuspendConfirmDialog by remember { mutableStateOf(false) }
    var showSuspendTimePicker by remember { mutableStateOf(false) }
    var showResumeDialog by remember { mutableStateOf(false) }
    var selectedDurationIndex by remember { mutableIntStateOf(0) }

    val suspendDurations = remember { listOf(0.5f, 1.0f, 1.5f, 2.0f) }

    // Handle one-time events
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EopatchOverviewEvent.ShowToast -> {
                    snackbarHostState.showSnackbar(context.getString(event.messageResId))
                }

                is EopatchOverviewEvent.StartPatchWorkflow -> {
                    // Handled by EopatchComposeContent
                }
            }
        }
    }

    // Patch the suspend/resume action onClick to open dialog instead
    val suspendLabel = stringResource(app.aaps.core.ui.R.string.pump_suspend)
    val resumeLabel = stringResource(app.aaps.core.ui.R.string.pump_resume)
    val patchedState = baseState.copy(
        primaryActions = baseState.primaryActions.map { action ->
            when (action.label) {
                suspendLabel -> action.copy(onClick = { showSuspendConfirmDialog = true })
                resumeLabel  -> action.copy(onClick = { showResumeDialog = true })
                else         -> action
            }
        }
    )

    // Suspend confirmation dialog
    if (showSuspendConfirmDialog) {
        val suspendMessage = remember { viewModel.getSuspendDialogText() }
        AlertDialog(
            onDismissRequest = { showSuspendConfirmDialog = false },
            title = { Text(stringResource(app.aaps.core.ui.R.string.pump_suspend)) },
            text = { Text(suspendMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showSuspendConfirmDialog = false
                    showSuspendTimePicker = true
                }) {
                    Text(stringResource(app.aaps.core.ui.R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSuspendConfirmDialog = false }) {
                    Text(stringResource(app.aaps.core.ui.R.string.cancel))
                }
            }
        )
    }

    // Suspend time picker dialog
    if (showSuspendTimePicker) {
        val durationLabels = listOf(
            stringResource(R.string.time_30min),
            stringResource(R.string.time_1hr),
            stringResource(R.string.time_1hr_30min),
            stringResource(R.string.time_2hr)
        )
        AlertDialog(
            onDismissRequest = { showSuspendTimePicker = false },
            title = { Text(stringResource(R.string.string_suspend_time_insulin_delivery_title)) },
            text = {
                Column {
                    durationLabels.forEachIndexed { index, label ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedDurationIndex = index },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedDurationIndex == index,
                                onClick = { selectedDurationIndex = index }
                            )
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSuspendTimePicker = false
                    viewModel.pauseBasal(suspendDurations[selectedDurationIndex])
                    selectedDurationIndex = 0
                }) {
                    Text(stringResource(app.aaps.core.ui.R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSuspendTimePicker = false
                    selectedDurationIndex = 0
                }) {
                    Text(stringResource(app.aaps.core.ui.R.string.cancel))
                }
            }
        )
    }

    // Resume confirmation dialog
    if (showResumeDialog) {
        AlertDialog(
            onDismissRequest = { showResumeDialog = false },
            title = { Text(stringResource(R.string.string_resume_insulin_delivery_title)) },
            text = { Text(stringResource(R.string.string_resume_insulin_delivery_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showResumeDialog = false
                    viewModel.resumeBasal()
                }) {
                    Text(stringResource(app.aaps.core.ui.R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResumeDialog = false }) {
                    Text(stringResource(app.aaps.core.ui.R.string.cancel))
                }
            }
        )
    }

    PumpOverviewScreen(
        state = patchedState,
        customContent = {
            Image(
                painter = painterResource(id = app.aaps.core.ui.R.drawable.ic_eopatch2_128),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp),
                contentScale = ContentScale.Fit
            )
        }
    )
}

@Preview(showBackground = true, name = "Overview - Activated")
@Composable
private fun EopatchOverviewActivatedPreview() {
    MaterialTheme {
        PumpOverviewScreen(
            state = PumpOverviewUiState(
                infoRows = listOf(
                    PumpInfoRow(label = "Status", value = "Running"),
                    PumpInfoRow(label = "Basal Rate", value = "1.00 U/h"),
                    PumpInfoRow(label = "Reservoir", value = "185 U"),
                    PumpInfoRow(label = "Serial", value = "EO00-AB12")
                ),
                primaryActions = listOf(
                    PumpAction(label = "Suspend pump", iconRes = app.aaps.core.ui.R.drawable.ic_loop_paused, onClick = {})
                ),
                managementActions = listOf(
                    PumpAction(label = "Discard Patch", iconRes = app.aaps.core.ui.R.drawable.ic_swap_horiz, category = ActionCategory.MANAGEMENT, onClick = {})
                )
            )
        )
    }
}

@Preview(showBackground = true, name = "Overview - Not Activated")
@Composable
private fun EopatchOverviewNotActivatedPreview() {
    MaterialTheme {
        PumpOverviewScreen(
            state = PumpOverviewUiState(
                statusBanner = StatusBanner(text = "Patch not activated", level = StatusLevel.WARNING),
                primaryActions = listOf(
                    PumpAction(label = "Activate Patch", iconRes = app.aaps.core.ui.R.drawable.ic_swap_horiz, onClick = {})
                )
            )
        )
    }
}
