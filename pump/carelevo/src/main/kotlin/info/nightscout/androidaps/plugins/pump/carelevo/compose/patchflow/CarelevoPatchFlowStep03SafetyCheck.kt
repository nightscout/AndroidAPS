package info.nightscout.androidaps.plugins.pump.carelevo.compose.patchflow

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.LocalSnackbarHostState
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.compose.dialog.CarelevoActionDialog
import info.nightscout.androidaps.plugins.pump.carelevo.presentation.model.CarelevoConnectSafetyCheckEvent
import info.nightscout.androidaps.plugins.pump.carelevo.presentation.type.CarelevoPatchStep
import info.nightscout.androidaps.plugins.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectionFlowViewModel
import info.nightscout.androidaps.plugins.pump.carelevo.presentation.viewmodel.CarelevoPatchSafetyCheckViewModel

@Composable
internal fun CarelevoPatchFlowStep03SafetyCheck(
    viewModel: CarelevoPatchSafetyCheckViewModel,
    sharedViewModel: CarelevoPatchConnectionFlowViewModel,
    onExitFlow: () -> Unit
) {
    val context = LocalContext.current
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val remainSec by viewModel.remainSec.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current
    val bluetoothNotEnabledMessage = stringResource(R.string.carelevo_toast_msg_bluetooth_not_enabled)
    val notConnectedMessage = stringResource(R.string.carelevo_toast_msg_not_connected_waiting_retry)
    val safetyCheckSuccessMessage = stringResource(R.string.carelevo_toast_msg_safety_check_success)
    val safetyCheckFailedMessage = stringResource(R.string.carelevo_toast_msg_safety_check_failed)
    val discardCompleteMessage = stringResource(R.string.carelevo_toast_msg_discard_complete)
    val discardFailedMessage = stringResource(R.string.carelevo_toast_msg_discard_failed)
    var showDiscardDialog by remember { mutableStateOf(false) }
    var safetyCheckState by remember(viewModel) {
        mutableStateOf(
            if (viewModel.isSafetyCheckPassed()) {
                SafetyCheckUiState.Success
            } else {
                SafetyCheckUiState.Ready
            }
        )
    }

    LaunchedEffect(viewModel) {
        if (!viewModel.isCreated) {
            viewModel.setIsCreated(true)
        }
        if (viewModel.isSafetyCheckPassed()) {
            viewModel.onSafetyCheckComplete()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                CarelevoConnectSafetyCheckEvent.ShowMessageBluetoothNotEnabled -> {
                    snackbarHostState.showSnackbar(bluetoothNotEnabledMessage)
                }

                CarelevoConnectSafetyCheckEvent.ShowMessageCarelevoIsNotConnected -> {
                    snackbarHostState.showSnackbar(notConnectedMessage)
                }

                CarelevoConnectSafetyCheckEvent.SafetyCheckProgress -> {
                    safetyCheckState = SafetyCheckUiState.Progress
                }

                CarelevoConnectSafetyCheckEvent.SafetyCheckComplete -> {
                    safetyCheckState = SafetyCheckUiState.Success
                    snackbarHostState.showSnackbar(safetyCheckSuccessMessage)
                }

                CarelevoConnectSafetyCheckEvent.SafetyCheckFailed -> {
                    snackbarHostState.showSnackbar(safetyCheckFailedMessage)
                }

                CarelevoConnectSafetyCheckEvent.DiscardComplete -> {
                    showDiscardDialog = false
                    Toast.makeText(context, discardCompleteMessage, Toast.LENGTH_SHORT).show()
                    onExitFlow()
                }

                CarelevoConnectSafetyCheckEvent.DiscardFailed -> {
                    showDiscardDialog = false
                    snackbarHostState.showSnackbar(discardFailedMessage)
                }

                CarelevoConnectSafetyCheckEvent.NoAction -> Unit
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
                viewModel.startDiscardProcess()
            },
            secondaryText = stringResource(R.string.carelevo_btn_cancel),
            onSecondaryClick = { showDiscardDialog = false }
        )
    }

    val titleRes = when (safetyCheckState) {
        SafetyCheckUiState.Ready,
        SafetyCheckUiState.Progress -> R.string.carelevo_patch_safety_check_start_title
        SafetyCheckUiState.Success  -> R.string.carelevo_patch_safety_check_end_title
    }
    val descRes = when (safetyCheckState) {
        SafetyCheckUiState.Ready    -> R.string.carelevo_patch_safety_check_start_desc
        SafetyCheckUiState.Progress -> R.string.carelevo_patch_safety_check_progress_desc
        SafetyCheckUiState.Success  -> R.string.carelevo_patch_safety_check_end_desc
    }

    val showNextButton = safetyCheckState != SafetyCheckUiState.Ready
    val nextEnabled = safetyCheckState == SafetyCheckUiState.Success
    val showSafetyCheckButton = safetyCheckState == SafetyCheckUiState.Ready
    val showRetrySection = safetyCheckState == SafetyCheckUiState.Success
    val showProgressDetails = safetyCheckState != SafetyCheckUiState.Ready

    CarelevoPatchFlowStep03SafetyCheckContent(
        titleRes = titleRes,
        descRes = descRes,
        progress = progress,
        remainSec = remainSec,
        showProgressDetails = showProgressDetails,
        showRetrySection = showRetrySection,
        showSafetyCheckButton = showSafetyCheckButton,
        nextEnabled = nextEnabled,
        onRetryClick = { viewModel.retryAdditionalPriming() },
        onDiscardClick = { showDiscardDialog = true },
        onSafetyCheckClick = { viewModel.startSafetyCheck() },
        onNextClick = { sharedViewModel.setPage(CarelevoPatchStep.PATCH_ATTACH) }
    )
}

@Composable
private fun CarelevoPatchFlowStep03SafetyCheckContent(
    titleRes: Int,
    descRes: Int,
    progress: Int?,
    remainSec: Long?,
    showProgressDetails: Boolean,
    showRetrySection: Boolean,
    showSafetyCheckButton: Boolean,
    nextEnabled: Boolean,
    onRetryClick: () -> Unit,
    onDiscardClick: () -> Unit,
    onSafetyCheckClick: () -> Unit,
    onNextClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(descRes),
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(
                progress = { if (showProgressDetails) (progress ?: 0) / 100f else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
            )
            if (showProgressDetails && (remainSec != null || progress != null)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = remainTimeText(remainSec),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = progressText(progress),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (showRetrySection) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Image(
                        painter = painterResource(app.aaps.core.ui.R.drawable.ic_toast_warn),
                        contentDescription = null
                    )
                    Text(
                        text = stringResource(R.string.carelevo_patch_safety_check_desc_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Text(
                    text = stringResource(R.string.carelevo_patch_safety_check_retry_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = onRetryClick,
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Text(text = stringResource(R.string.carelevo_btn_retry))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onDiscardClick,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
            ) {
                PatchFlowButtonText(text = stringResource(R.string.carelevo_btn_patch_expiration))
            }
            if (showSafetyCheckButton) {
                Button(
                    onClick = onSafetyCheckClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                ) {
                    PatchFlowButtonText(text = stringResource(R.string.carelevo_btn_safety_check))
                }
            } else {
                Button(
                    onClick = onNextClick,
                    enabled = nextEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                ) {
                    PatchFlowButtonText(text = stringResource(R.string.carelevo_btn_next))
                }
            }
        }
    }
}

private enum class SafetyCheckUiState {
    Ready,
    Progress,
    Success
}

@Composable
private fun remainTimeText(remainSeconds: Long?): String {
    if (remainSeconds == null) return ""
    val minutes = remainSeconds / 60
    val seconds = remainSeconds % 60
    return stringResource(R.string.common_unit_remain_min_sec, minutes, seconds)
}

private fun progressText(progress: Int?): String =
    if (progress == null) "" else "$progress/100"

@Preview(showBackground = true, name = "Safety Check Ready")
@Composable
private fun CarelevoPatchFlowStep03SafetyCheckReadyPreview() {
    MaterialTheme {
        CarelevoPatchFlowStep03SafetyCheckContent(
            titleRes = R.string.carelevo_patch_safety_check_start_title,
            descRes = R.string.carelevo_patch_safety_check_start_desc,
            progress = 0,
            remainSec = 180,
            showProgressDetails = false,
            showRetrySection = false,
            showSafetyCheckButton = true,
            nextEnabled = false,
            onRetryClick = {},
            onDiscardClick = {},
            onSafetyCheckClick = {},
            onNextClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Safety Check Success")
@Composable
private fun CarelevoPatchFlowStep03SafetyCheckSuccessPreview() {
    MaterialTheme {
        CarelevoPatchFlowStep03SafetyCheckContent(
            titleRes = R.string.carelevo_patch_safety_check_end_title,
            descRes = R.string.carelevo_patch_safety_check_end_desc,
            progress = 100,
            remainSec = 0,
            showProgressDetails = true,
            showRetrySection = true,
            showSafetyCheckButton = false,
            nextEnabled = true,
            onRetryClick = {},
            onDiscardClick = {},
            onSafetyCheckClick = {},
            onNextClick = {}
        )
    }
}
