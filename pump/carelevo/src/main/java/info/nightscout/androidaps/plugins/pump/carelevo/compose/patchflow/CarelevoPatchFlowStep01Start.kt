package info.nightscout.androidaps.plugins.pump.carelevo.compose.patchflow

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.fragment.app.FragmentActivity
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.compose.dialog.CarelevoActionDialog
import info.nightscout.androidaps.plugins.pump.carelevo.compose.dialog.CarelevoInsulinAmountPickerSheet
import info.nightscout.androidaps.plugins.pump.carelevo.compose.dialog.CarelevoInsulinRefillGuideDialog
import info.nightscout.androidaps.plugins.pump.carelevo.presentation.type.CarelevoPatchStep
import info.nightscout.androidaps.plugins.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectionFlowViewModel

@Composable
internal fun CarelevoPatchFlowStep01Start(
    viewModel: CarelevoPatchConnectionFlowViewModel,
    onExitFlow: () -> Unit
) {
    val context = LocalContext.current
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }
    var showInsulinAmountPicker by remember { mutableStateOf(false) }

    if (showDiscardDialog) {
        CarelevoActionDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = stringResource(R.string.carelevo_dialog_patch_discard_message_title),
            content = stringResource(R.string.carelevo_dialog_patch_discard_message_desc),
            primaryText = stringResource(R.string.carelevo_btn_confirm),
            onPrimaryClick = {
                showDiscardDialog = false
                onExitFlow()
            },
            secondaryText = stringResource(R.string.carelevo_btn_cancel),
            onSecondaryClick = { showDiscardDialog = false }
        )
    }

    if (showGuideDialog) {
        CarelevoInsulinRefillGuideDialog(
            onDismissRequest = { showGuideDialog = false }
        )
    }

    if (showInsulinAmountPicker) {
        CarelevoInsulinAmountPickerSheet(
            initialValue = viewModel.inputInsulin,
            onDismissRequest = { showInsulinAmountPicker = false },
            onConfirm = { selected ->
                showInsulinAmountPicker = false
                if (hasPatchStartPermissions(context)) {
                    viewModel.setInputInsulin(selected)
                    viewModel.setPage(CarelevoPatchStep.PATCH_CONNECT)
                } else {
                    requestPatchStartPermissions(context as FragmentActivity)
                }
            }
        )
    }

    CarelevoPatchStartContent(
        onGuideClick = { showGuideDialog = true },
        onDiscardClick = { showDiscardDialog = true },
        onFillAmountClick = { showInsulinAmountPicker = true }
    )
}

@Composable
private fun CarelevoPatchStartContent(
    onGuideClick: () -> Unit,
    onDiscardClick: () -> Unit,
    onFillAmountClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = stringResource(R.string.carelevo_title_fill_insulin),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.carelevo_notice_fill_insulin_amount),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = onGuideClick,
                modifier = Modifier
            ) {
                Text(text = stringResource(R.string.carelevo_btn_insulin_guide))
            }
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
                onClick = onFillAmountClick,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
            ) {
                PatchFlowButtonText(text = stringResource(R.string.carelevo_btn_input_insulin_amount))
            }
        }
    }
}

@Preview(showBackground = true, name = "Patch Start")
@Composable
private fun CarelevoPatchFlowStep01StartPreview() {
    MaterialTheme {
        CarelevoPatchStartContent(
            onGuideClick = {},
            onDiscardClick = {},
            onFillAmountClick = {}
        )
    }
}
