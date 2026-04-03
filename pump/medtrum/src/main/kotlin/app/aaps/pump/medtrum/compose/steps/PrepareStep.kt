package app.aaps.pump.medtrum.compose.steps

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardErrorBanner
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.code.PatchStep
import app.aaps.pump.medtrum.compose.MedtrumPatchViewModel

@Composable
fun PrepareStep(
    viewModel: MedtrumPatchViewModel,
    onCancel: () -> Unit
) {
    val patchStep by viewModel.patchStep.collectAsStateWithLifecycle()
    val setupStep by viewModel.setupStep.collectAsStateWithLifecycle()
    val reservoirLevel by viewModel.medtrumPump.reservoirFlow.collectAsStateWithLifecycle()
    val snText by viewModel.snText.collectAsStateWithLifecycle()
    val snValidationErrorResId by viewModel.snValidationErrorResId.collectAsStateWithLifecycle()

    val isConnecting = patchStep == PatchStep.PREPARE_PATCH_CONNECT
    val isFilled = setupStep == MedtrumPatchViewModel.SetupStep.FILLED
    val isError = setupStep == MedtrumPatchViewModel.SetupStep.ERROR

    // Trigger preparePatch and load insulins on initial display
    LaunchedEffect(Unit) {
        viewModel.preparePatch()
        viewModel.loadInsulins()
        viewModel.initSnText()
    }

    // When step becomes PREPARE_PATCH_CONNECT, trigger connect
    LaunchedEffect(patchStep) {
        if (patchStep == PatchStep.PREPARE_PATCH_CONNECT) {
            viewModel.preparePatchConnect()
        }
    }

    val state = when {
        patchStep == PatchStep.PREPARE_PATCH -> PrepareState.INITIAL
        isFilled                             -> PrepareState.FILLED
        isError                              -> PrepareState.ERROR
        isConnecting                         -> PrepareState.CONNECTING
        else                                 -> PrepareState.INITIAL
    }

    PrepareStepContent(
        state = state,
        snText = snText,
        snValidationErrorResId = snValidationErrorResId,
        isSnValid = viewModel.isSnValid,
        onSnTextChange = viewModel::updateSnText,
        reservoirLevel = reservoirLevel,
        pumpState = viewModel.medtrumPump.pumpState.toString(),
        onNext = {
            viewModel.saveSn()
            viewModel.moveStep(PatchStep.PREPARE_PATCH_CONNECT)
        },
        onFilled = {
            if (viewModel.showInsulinStep) viewModel.moveStep(PatchStep.SELECT_INSULIN)
            else viewModel.moveStep(PatchStep.PRIME)
        },
        onRetry = {
            viewModel.updateSetupStep(MedtrumPatchViewModel.SetupStep.INITIAL)
            viewModel.moveStep(PatchStep.PREPARE_PATCH_CONNECT)
        },
        onCancel = onCancel
    )
}

internal enum class PrepareState { INITIAL, CONNECTING, FILLED, ERROR }

@Composable
internal fun PrepareStepContent(
    state: PrepareState,
    snText: String = "",
    snValidationErrorResId: Int? = null,
    isSnValid: Boolean = true,
    onSnTextChange: (String) -> Unit = {},
    reservoirLevel: Double = 0.0,
    pumpState: String = "",
    onNext: () -> Unit,
    onFilled: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = when (state) {
            PrepareState.INITIAL    -> WizardButton(text = stringResource(app.aaps.core.ui.R.string.next), onClick = onNext, enabled = isSnValid)
            PrepareState.CONNECTING -> WizardButton(text = stringResource(app.aaps.core.ui.R.string.next), onClick = {}, loading = true)
            PrepareState.FILLED     -> WizardButton(text = stringResource(app.aaps.core.ui.R.string.next), onClick = onFilled)
            PrepareState.ERROR      -> WizardButton(text = stringResource(app.aaps.core.ui.R.string.retry), onClick = onRetry)
        },
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        when (state) {
            PrepareState.INITIAL -> {
                SerialNumberSection(
                    snText = snText,
                    snValidationErrorResId = snValidationErrorResId,
                    isSnValid = isSnValid,
                    onSnTextChange = onSnTextChange
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.patch_begin_activation).stripHtml(),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.patch_not_active_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            PrepareState.CONNECTING,
            PrepareState.FILLED,
            PrepareState.ERROR   -> {
                Text(
                    text = stringResource(R.string.connect_pump_base).stripHtml(),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.note_min_70_units),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (reservoirLevel > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.reservoir_text_and_level, reservoirLevel),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.do_not_attach_to_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                if (state == PrepareState.ERROR) {
                    Spacer(Modifier.height(8.dp))
                    WizardErrorBanner(message = stringResource(R.string.unexpected_state, pumpState))
                }
            }
        }
    }
}

@Composable
private fun SerialNumberSection(
    snText: String,
    snValidationErrorResId: Int?,
    isSnValid: Boolean,
    onSnTextChange: (String) -> Unit,
    initialEditing: Boolean = false
) {
    var isEditing by rememberSaveable { mutableStateOf(initialEditing) }
    var textBeforeEdit by rememberSaveable { mutableStateOf(snText) }

    val confirmEdit = {
        if (isSnValid) isEditing = false
    }
    val cancelEdit = {
        onSnTextChange(textBeforeEdit)
        isEditing = false
    }

    if (isEditing) {
        OutlinedTextField(
            value = snText,
            onValueChange = onSnTextChange,
            label = { Text(stringResource(R.string.sn_input_title)) },
            singleLine = true,
            isError = snValidationErrorResId != null,
            supportingText = snValidationErrorResId?.let { { Text(stringResource(it)) } },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { confirmEdit() }),
            trailingIcon = {
                Row {
                    IconButton(onClick = { cancelEdit() }, enabled = textBeforeEdit.isNotEmpty()) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(app.aaps.core.ui.R.string.cancel))
                    }
                    IconButton(onClick = { confirmEdit() }, enabled = isSnValid) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(app.aaps.core.ui.R.string.next))
                    }
                }
            }
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.sn_input_title) + ": " + snText,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                textBeforeEdit = snText
                isEditing = true
            }) {
                Text(stringResource(R.string.change_sn))
            }
        }
    }
}

@Preview(showBackground = true, name = "Prepare - Initial with SN")
@Composable
private fun PreviewInitialWithSn() {
    MaterialTheme {
        PrepareStepContent(
            state = PrepareState.INITIAL,
            snText = "48AB1234",
            isSnValid = true,
            onNext = {}, onFilled = {}, onRetry = {}, onCancel = {}
        )
    }
}

@Preview(showBackground = true, name = "Prepare - Initial no SN")
@Composable
private fun PreviewInitialNoSn() {
    MaterialTheme {
        PrepareStepContent(
            state = PrepareState.INITIAL,
            snText = "",
            isSnValid = false,
            onNext = {}, onFilled = {}, onRetry = {}, onCancel = {}
        )
    }
}

@Preview(showBackground = true, name = "Prepare - Invalid SN (editing)")
@Composable
private fun PreviewInvalidSn() {
    MaterialTheme {
        SerialNumberSection(
            snText = "00000001",
            snValidationErrorResId = R.string.sn_input_invalid,
            isSnValid = false,
            onSnTextChange = {},
            initialEditing = true
        )
    }
}

@Preview(showBackground = true, name = "Prepare - Filled")
@Composable
private fun PreviewFilled() {
    MaterialTheme {
        PrepareStepContent(state = PrepareState.FILLED, reservoirLevel = 185.0, onNext = {}, onFilled = {}, onRetry = {}, onCancel = {})
    }
}

@Preview(showBackground = true, name = "Prepare - Error")
@Composable
private fun PreviewError() {
    MaterialTheme {
        PrepareStepContent(state = PrepareState.ERROR, pumpState = "STOPPED", onNext = {}, onFilled = {}, onRetry = {}, onCancel = {})
    }
}
