package app.aaps.pump.carelevo.compose.patchflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.banner.ErrorBanner
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.compose.dialog.CarelevoActionDialog
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectSafetyCheckEvent
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectionFlowViewModel
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchSafetyCheckViewModel

@Composable
internal fun CarelevoPatchFlowStep03SafetyCheck(
    viewModel: CarelevoPatchSafetyCheckViewModel,
    sharedViewModel: CarelevoPatchConnectionFlowViewModel,
    onExitFlow: () -> Unit
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val remainSec by viewModel.remainSec.collectAsStateWithLifecycle()
    var showDiscardDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<Int?>(null) }
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
                CarelevoConnectSafetyCheckEvent.ShowMessageBluetoothNotEnabled    -> {
                    errorMessage = R.string.carelevo_toast_msg_bluetooth_not_enabled
                }

                CarelevoConnectSafetyCheckEvent.ShowMessageCarelevoIsNotConnected -> {
                    errorMessage = R.string.carelevo_toast_msg_not_connected_waiting_retry
                }

                CarelevoConnectSafetyCheckEvent.SafetyCheckProgress               -> {
                    errorMessage = null
                    safetyCheckState = SafetyCheckUiState.Progress
                }

                CarelevoConnectSafetyCheckEvent.SafetyCheckComplete               -> {
                    errorMessage = null
                    safetyCheckState = SafetyCheckUiState.Success
                }

                CarelevoConnectSafetyCheckEvent.SafetyCheckFailed                 -> {
                    errorMessage = R.string.carelevo_toast_msg_safety_check_failed
                    safetyCheckState = SafetyCheckUiState.Ready
                }

                CarelevoConnectSafetyCheckEvent.DiscardComplete                   -> {
                    showDiscardDialog = false
                    onExitFlow()
                }

                CarelevoConnectSafetyCheckEvent.DiscardFailed                     -> {
                    showDiscardDialog = false
                    errorMessage = R.string.carelevo_toast_msg_discard_failed
                }

                CarelevoConnectSafetyCheckEvent.NoAction                          -> Unit
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

    val nextEnabled = safetyCheckState == SafetyCheckUiState.Success
    val showSafetyCheckButton = safetyCheckState == SafetyCheckUiState.Ready
    val showRetrySection = safetyCheckState == SafetyCheckUiState.Success
    val showProgressDetails = safetyCheckState != SafetyCheckUiState.Ready
    // While the ~100-210 s check is streaming, block the discard escape hatch: tapping it would
    // enqueue a CmdDiscard behind the still-running CmdSafetyCheck — a confusing interleaving
    // that never reflects what the user sees on screen. (The global Loading overlay is deliberately
    // NOT used here — it would hide this step's live progress display.)
    val discardEnabled = safetyCheckState != SafetyCheckUiState.Progress

    CarelevoPatchFlowStep03SafetyCheckContent(
        titleRes = titleRes,
        descRes = descRes,
        errorMessage = errorMessage,
        progress = progress,
        remainSec = remainSec,
        showProgressDetails = showProgressDetails,
        showRetrySection = showRetrySection,
        showSafetyCheckButton = showSafetyCheckButton,
        nextEnabled = nextEnabled,
        discardEnabled = discardEnabled,
        onRetryClick = {
            errorMessage = null
            viewModel.retryAdditionalPriming()
        },
        onDiscardClick = { showDiscardDialog = true },
        onSafetyCheckClick = {
            errorMessage = null
            viewModel.startSafetyCheck()
        },
        onNextClick = { sharedViewModel.advanceFromSafetyCheck() }
    )
}

@Composable
private fun CarelevoPatchFlowStep03SafetyCheckContent(
    titleRes: Int,
    descRes: Int,
    errorMessage: Int?,
    progress: Int?,
    remainSec: Long?,
    showProgressDetails: Boolean,
    showRetrySection: Boolean,
    showSafetyCheckButton: Boolean,
    nextEnabled: Boolean,
    discardEnabled: Boolean = true,
    onRetryClick: () -> Unit,
    onDiscardClick: () -> Unit,
    onSafetyCheckClick: () -> Unit,
    onNextClick: () -> Unit
) {
    WizardStepLayout(
        primaryButton = if (showSafetyCheckButton) {
            WizardButton(
                text = stringResource(R.string.carelevo_btn_safety_check),
                onClick = onSafetyCheckClick
            )
        } else {
            WizardButton(
                text = stringResource(R.string.carelevo_btn_next),
                onClick = onNextClick,
                enabled = nextEnabled
            )
        },
        secondaryButton = WizardButton(
            text = stringResource(R.string.carelevo_btn_patch_expiration),
            onClick = onDiscardClick,
            enabled = discardEnabled
        )
    ) {
        errorMessage?.let { ErrorBanner(message = stringResource(it)) }
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
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.carelevo_patch_safety_check_desc_warning),
                    style = MaterialTheme.typography.bodyMedium
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

@Composable
private fun progressText(progress: Int?): String =
    if (progress == null) "" else stringResource(R.string.carelevo_progress_of_100, progress)

@Preview(showBackground = true, name = "Safety Check Ready")
@Composable
private fun CarelevoPatchFlowStep03SafetyCheckReadyPreview() {
    MaterialTheme {
        CarelevoPatchFlowStep03SafetyCheckContent(
            titleRes = R.string.carelevo_patch_safety_check_start_title,
            descRes = R.string.carelevo_patch_safety_check_start_desc,
            errorMessage = null,
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
            errorMessage = null,
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
