package app.aaps.pump.carelevo.compose.patchflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.banner.ErrorBanner
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.compose.dialog.CarelevoActionDialog
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectNeedleEvent
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchNeedleInsertionViewModel

private const val MAX_NEEDLE_CHECK_COUNT = 3

@Composable
internal fun CarelevoPatchFlowStep05NeedleInsertion(
    viewModel: CarelevoPatchNeedleInsertionViewModel,
    onExitFlow: () -> Unit
) {
    val isNeedleInserted by viewModel.isNeedleInsert.collectAsStateWithLifecycle()
    var showDiscardDialog by remember { mutableStateOf(false) }
    var failCount by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(viewModel) {
        if (!viewModel.isCreated) {
            viewModel.observePatchInfo()
            viewModel.setIsCreated(true)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                CarelevoConnectNeedleEvent.ShowMessageBluetoothNotEnabled -> {
                    errorMessage = R.string.carelevo_toast_msg_bluetooth_not_enabled
                }

                CarelevoConnectNeedleEvent.ShowMessageCarelevoIsNotConnected -> {
                    errorMessage = R.string.carelevo_toast_msg_not_connected_waiting_retry
                }

                CarelevoConnectNeedleEvent.ShowMessageProfileNotSet -> {
                    errorMessage = R.string.carelevo_toast_msg_profile_not_set
                }

                is CarelevoConnectNeedleEvent.CheckNeedleComplete -> {
                    errorMessage = null
                }

                is CarelevoConnectNeedleEvent.CheckNeedleFailed -> {
                    failCount = event.failedCount
                    if (event.failedCount >= MAX_NEEDLE_CHECK_COUNT) {
                        onExitFlow()
                    }
                }

                CarelevoConnectNeedleEvent.CheckNeedleError -> {
                    errorMessage = R.string.carelevo_toast_msg_needle_check_failed
                }

                CarelevoConnectNeedleEvent.DiscardComplete -> {
                    showDiscardDialog = false
                    onExitFlow()
                }

                CarelevoConnectNeedleEvent.DiscardFailed -> {
                    showDiscardDialog = false
                    errorMessage = R.string.carelevo_toast_msg_discard_failed
                }

                CarelevoConnectNeedleEvent.SetBasalComplete -> {
                    onExitFlow()
                }

                CarelevoConnectNeedleEvent.SetBasalFailed -> {
                    errorMessage = R.string.carelevo_toast_msg_set_basal_failed
                }

                CarelevoConnectNeedleEvent.NoAction -> Unit
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

    CarelevoPatchFlowStep05NeedleInsertionContent(
        isNeedleInserted = isNeedleInserted,
        failCount = failCount,
        errorMessage = errorMessage,
        onCheckClick = {
            errorMessage = null
            viewModel.startCheckNeedle()
        },
        onStartDeliveryClick = {
            errorMessage = null
            viewModel.startSetBasal()
        },
        onDiscardClick = { showDiscardDialog = true }
    )
}

@Composable
private fun CarelevoPatchFlowStep05NeedleInsertionContent(
    isNeedleInserted: Boolean,
    failCount: Int,
    errorMessage: Int?,
    onCheckClick: () -> Unit,
    onStartDeliveryClick: () -> Unit,
    onDiscardClick: () -> Unit
) {
    val deactivateButton = WizardButton(
        text = stringResource(R.string.carelevo_btn_patch_expiration),
        onClick = onDiscardClick
    )

    if (isNeedleInserted) {
        // Needle detected — guide the user to detach the applicator, then start delivery.
        WizardStepLayout(
            primaryButton = WizardButton(
                text = stringResource(R.string.carelevo_dialog_connect_detached),
                onClick = onStartDeliveryClick
            ),
            secondaryButton = deactivateButton
        ) {
            errorMessage?.let { ErrorBanner(message = stringResource(it)) }
            Text(
                text = stringResource(R.string.carelevo_dialog_patch_connect_needle_injected),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = AnnotatedString.fromHtml(
                    stringResource(R.string.carelevo_dialog_connect_detach_applicator_guide).replace("\n", "<br>")
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    // Needle not detected yet — show insertion instructions and let the user run the check.
    val isRetry = failCount > 0
    WizardStepLayout(
        primaryButton = WizardButton(
            text = if (isRetry) {
                stringResource(R.string.carelevo_btn_retry)
            } else {
                stringResource(R.string.carelevo_btn_needle_insert_check)
            },
            onClick = onCheckClick
        ),
        secondaryButton = deactivateButton
    ) {
        errorMessage?.let { ErrorBanner(message = stringResource(it)) }
        CarelevoPatchNeedleSection(
            stepLabel = stringResource(R.string.carelevo_patch_step_1),
            title = stringResource(R.string.carelevo_patch_needle_insertion_step1_title),
            description = stringResource(R.string.carelevo_patch_needle_insertion_step1_desc)
        )
        CarelevoPatchNeedleSection(
            stepLabel = stringResource(R.string.carelevo_patch_step_2),
            title = stringResource(R.string.carelevo_patch_needle_insertion_step2_title),
            description = stringResource(R.string.carelevo_patch_needle_insertion_step2_desc)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = stringResource(R.string.carelevo_patch_needle_insertion_desc_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        if (isRetry) {
            Text(
                text = stringResource(
                    R.string.carelevo_dialog_patch_needle_retry_count,
                    (MAX_NEEDLE_CHECK_COUNT - failCount).coerceAtLeast(0)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun CarelevoPatchNeedleSection(
    stepLabel: String,
    title: String,
    description: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small), verticalAlignment = Alignment.Bottom) {
            Text(text = stepLabel, style = MaterialTheme.typography.titleMedium)
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        }
        Text(text = description, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true, name = "Needle Insertion - not detected")
@Composable
private fun CarelevoPatchFlowStep05NotDetectedPreview() {
    MaterialTheme {
        CarelevoPatchFlowStep05NeedleInsertionContent(
            isNeedleInserted = false,
            failCount = 0,
            errorMessage = null,
            onCheckClick = {},
            onStartDeliveryClick = {},
            onDiscardClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Needle Insertion - detected")
@Composable
private fun CarelevoPatchFlowStep05DetectedPreview() {
    MaterialTheme {
        CarelevoPatchFlowStep05NeedleInsertionContent(
            isNeedleInserted = true,
            failCount = 0,
            errorMessage = null,
            onCheckClick = {},
            onStartDeliveryClick = {},
            onDiscardClick = {}
        )
    }
}
