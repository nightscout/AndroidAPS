package app.aaps.pump.carelevo.compose.patchflow

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.LocalSnackbarHostState
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
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val isNeedleInserted by viewModel.isNeedleInsert.collectAsStateWithLifecycle()
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showNeedleCheckDialog by remember { mutableStateOf(false) }
    var showNeedleInsertedDialog by remember { mutableStateOf(false) }

    val bluetoothNotEnabledMessage = stringResource(R.string.carelevo_toast_msg_bluetooth_not_enabled)
    val notConnectedMessage = stringResource(R.string.carelevo_toast_msg_not_connected_waiting_retry)
    val profileNotSetMessage = stringResource(R.string.carelevo_toast_msg_profile_not_set)
    val needleInsertedMessage = stringResource(R.string.carelevo_toast_msg_needle_inserted)
    val needleNotInsertedMessage = stringResource(R.string.carelevo_toast_msg_needle_not_inserted)
    val needleCheckFailedMessage = stringResource(R.string.carelevo_toast_msg_needle_check_failed)
    val discardCompleteMessage = stringResource(R.string.carelevo_toast_msg_discard_complete)
    val discardFailedMessage = stringResource(R.string.carelevo_toast_msg_discard_failed)
    val setBasalCompleteMessage = stringResource(R.string.carelevo_toast_msg_set_basal_complete)
    val setBasalFailedMessage = stringResource(R.string.carelevo_toast_msg_set_basal_failed)
    val detachApplicatorGuide = stringResource(R.string.carelevo_dialog_connect_detach_applicator_guide)
    val needleCheckDescription = stringResource(R.string.carelevo_dialog_patch_needle_check_desc)
    val needleCheckRetryDescription = stringResource(R.string.carelevo_dialog_patch_needle_check_retry_desc)

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
                    snackbarHostState.showSnackbar(bluetoothNotEnabledMessage)
                }

                CarelevoConnectNeedleEvent.ShowMessageCarelevoIsNotConnected -> {
                    snackbarHostState.showSnackbar(notConnectedMessage)
                }

                CarelevoConnectNeedleEvent.ShowMessageProfileNotSet -> {
                    snackbarHostState.showSnackbar(profileNotSetMessage)
                }

                is CarelevoConnectNeedleEvent.CheckNeedleComplete -> {
                    val message = if (event.result) {
                        needleInsertedMessage
                    } else {
                        needleNotInsertedMessage
                    }
                    snackbarHostState.showSnackbar(message)
                }

                is CarelevoConnectNeedleEvent.CheckNeedleFailed -> {
                    if (event.failedCount >= MAX_NEEDLE_CHECK_COUNT) {
                        onExitFlow()
                    }
                }

                CarelevoConnectNeedleEvent.CheckNeedleError -> {
                    snackbarHostState.showSnackbar(needleCheckFailedMessage)
                }

                CarelevoConnectNeedleEvent.DiscardComplete -> {
                    showDiscardDialog = false
                    Toast.makeText(context, discardCompleteMessage, Toast.LENGTH_SHORT).show()
                    onExitFlow()
                }

                CarelevoConnectNeedleEvent.DiscardFailed -> {
                    showDiscardDialog = false
                    snackbarHostState.showSnackbar(discardFailedMessage)
                }

                CarelevoConnectNeedleEvent.SetBasalComplete -> {
                    showNeedleInsertedDialog = false
                    Toast.makeText(context, setBasalCompleteMessage, Toast.LENGTH_SHORT).show()
                    onExitFlow()
                }

                CarelevoConnectNeedleEvent.SetBasalFailed -> {
                    showNeedleInsertedDialog = false
                    snackbarHostState.showSnackbar(setBasalFailedMessage)
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

    if (showNeedleInsertedDialog) {
        CarelevoNeedleInsertedSheet(
            onDismissRequest = { showNeedleInsertedDialog = false },
            content = AnnotatedString.fromHtml(detachApplicatorGuide.replace("\n", "<br>")),
            onConfirmClick = {
                showNeedleInsertedDialog = false
                viewModel.startSetBasal()
            }
        )
    }

    if (showNeedleCheckDialog) {
        val needleFailCount = viewModel.needleFailCount() ?: 0
        val remainRetryCount = MAX_NEEDLE_CHECK_COUNT - needleFailCount
        val isRetry = needleFailCount > 0
        val descriptionText = if (isRetry) {
            needleCheckRetryDescription
        } else {
            needleCheckDescription
        }
        val buttonRes = if (isRetry) {
            R.string.carelevo_btn_retry
        } else {
            R.string.carelevo_btn_needle_insert_check
        }

        CarelevoNeedleCheckSheet(
            onDismissRequest = { showNeedleCheckDialog = false },
            description = AnnotatedString.fromHtml(descriptionText.replace("\n", "<br>")),
            retryText = if (isRetry) {
                stringResource(R.string.carelevo_dialog_patch_needle_retry_count, remainRetryCount)
            } else {
                null
            },
            confirmText = stringResource(buttonRes),
            onConfirmClick = {
                showNeedleCheckDialog = false
                viewModel.startCheckNeedle()
            },
            onCloseClick = {
                showNeedleCheckDialog = false
            }
        )
    }

    CarelevoPatchFlowStep05NeedleInsertionContent(
        isNeedleInserted = isNeedleInserted,
        onDiscardClick = { showDiscardDialog = true },
        onConfirmClick = {
            if (isNeedleInserted) {
                showNeedleInsertedDialog = true
            } else {
                showNeedleCheckDialog = true
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarelevoNeedleInsertedSheet(
    onDismissRequest: () -> Unit,
    content: AnnotatedString,
    onConfirmClick: () -> Unit
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
                text = stringResource(R.string.carelevo_dialog_patch_connect_needle_injected),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = onConfirmClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 12.dp, bottom = 8.dp)
            ) {
                Text(text = stringResource(R.string.carelevo_dialog_connect_detached))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarelevoNeedleCheckSheet(
    onDismissRequest: () -> Unit,
    description: AnnotatedString,
    retryText: String?,
    confirmText: String,
    onConfirmClick: () -> Unit,
    onCloseClick: () -> Unit
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
                text = stringResource(R.string.carelevo_dialog_patch_needle_check_title),
                style = MaterialTheme.typography.titleLarge
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (retryText != null) {
                    Text(
                        text = retryText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onCloseClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.carelevo_btn_close))
                }
                Button(
                    onClick = onConfirmClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = confirmText)
                }
            }
        }
    }
}

@Composable
private fun CarelevoPatchFlowStep05NeedleInsertionContent(
    isNeedleInserted: Boolean,
    onDiscardClick: () -> Unit,
    onConfirmClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),

        ) {
        Column(modifier = Modifier.weight(1f)) {
            Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(app.aaps.core.ui.R.drawable.ic_toast_warn),
                    contentDescription = null
                )
                Text(
                    text = stringResource(R.string.carelevo_patch_needle_insertion_desc_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
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
            Button(
                onClick = onConfirmClick,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
            ) {
                PatchFlowButtonText(text = stringResource(R.string.carelevo_btn_confirm))
            }
        }
    }
}

@Composable
private fun CarelevoPatchNeedleSection(
    stepLabel: String,
    title: String,
    description: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            Text(text = stepLabel, style = MaterialTheme.typography.titleMedium)
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        }
        Text(text = description, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true, name = "Needle Insertion")
@Composable
private fun CarelevoPatchFlowStep05NeedleInsertionPreview() {
    MaterialTheme {
        CarelevoPatchFlowStep05NeedleInsertionContent(
            isNeedleInserted = false,
            onDiscardClick = {},
            onConfirmClick = {}
        )
    }
}
