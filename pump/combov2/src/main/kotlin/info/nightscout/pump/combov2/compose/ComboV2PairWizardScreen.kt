package info.nightscout.pump.combov2.compose

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.pump.BluetoothPermissionsHost
import app.aaps.core.ui.compose.pump.KeepScreenOnEffect
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import info.nightscout.comboctl.base.PAIRING_PIN_SIZE
import info.nightscout.pump.combov2.ComboV2Plugin
import info.nightscout.pump.combov2.R

@Composable
fun ComboV2PairWizardScreen(
    viewModel: ComboV2PairWizardViewModel,
    combov2Plugin: ComboV2Plugin,
    onFinish: () -> Unit
) {
    KeepScreenOnEffect()

    // System discovery activity hookup: the plugin calls back with an Intent
    // when it needs the user to grant discovery permission. We launch it via
    // the ActivityResult API and, on RESULT_CANCELED, cancel the pairing.
    val discoveryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_CANCELED) {
            viewModel.confirmCancelPairing()
        }
    }
    DisposableEffect(Unit) {
        combov2Plugin.customDiscoveryActivityStartCallback = { intent ->
            discoveryLauncher.launch(intent)
        }
        onDispose {
            combov2Plugin.customDiscoveryActivityStartCallback = null
        }
    }

    BluetoothPermissionsHost(
        deniedContent = { requestAgain ->
            PermissionsDeniedSection(onRetry = requestAgain, onGoBack = onFinish)
        },
        content = {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(viewModel) {
                viewModel.events.collect { event ->
                    when (event) {
                        is PairWizardEvent.Finish -> onFinish()
                    }
                }
            }

            WizardBody(state = state, viewModel = viewModel)
        }
    )
}

@Composable
private fun WizardBody(
    state: ComboV2PairWizardUiState,
    viewModel: ComboV2PairWizardViewModel
) {
    when (state.phase) {
        PairWizardPhase.DriverNotInitialized -> DriverNotInitializedSection(
            onGoBack = viewModel::onDriverNotInitializedGoBack
        )

        PairWizardPhase.Idle                 -> IdleSection(
            onStartPairing = viewModel::startPairing
        )

        PairWizardPhase.InProgress           -> if (state.confirmCancel) {
            ConfirmCancelSection(
                onConfirm = viewModel::confirmCancelPairing,
                onDismiss = viewModel::dismissCancelConfirmation
            )
        } else {
            InProgressSection(
                state = state,
                onPinTextChange = viewModel::onPinTextChange,
                onSubmitPin = { viewModel.submitPin() },
                onRequestCancel = viewModel::requestCancelPairing
            )
        }

        PairWizardPhase.Finished             -> FinishedSection(
            onOk = viewModel::acknowledgeCompletion
        )

        PairWizardPhase.Aborted              -> AbortedSection(
            reason = state.abortReason,
            onOk = viewModel::acknowledgeCompletion
        )
    }
}

@Composable
private fun DriverNotInitializedSection(onGoBack: () -> Unit) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(R.string.combov2_go_back),
            onClick = onGoBack
        )
    ) {
        Text(
            text = stringResource(R.string.combov2_cannot_pair_driver_not_initialized_explanation),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun IdleSection(onStartPairing: () -> Unit) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(R.string.combov2_start_pairing),
            onClick = onStartPairing
        )
    ) {
        Text(
            text = stringResource(R.string.combov2_pairing_start_steps),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun InProgressSection(
    state: ComboV2PairWizardUiState,
    onPinTextChange: (String) -> Unit,
    onSubmitPin: () -> Boolean,
    onRequestCancel: () -> Unit
) {
    WizardStepLayout(
        secondaryButton = WizardButton(
            text = stringResource(R.string.combov2_cancel_pairing),
            onClick = onRequestCancel
        )
    ) {
        Text(
            text = stringResource(R.string.combov2_pairing_in_progress),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally)
        )

        Text(
            text = stringResource(R.string.combov2_steps_if_no_connection_during_pairing),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.stepDescription.isNotEmpty()) {
            Text(
                text = state.stepDescription,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        if (state.scanningIndeterminate) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { state.overallProgress },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (state.pinEntryVisible) {
            PinEntrySection(
                pinText = state.pinText,
                pinFailed = state.pinFailed,
                onPinTextChange = onPinTextChange,
                onSubmitPin = onSubmitPin
            )
        }
    }
}

@Composable
private fun PinEntrySection(
    pinText: String,
    pinFailed: Boolean,
    onPinTextChange: (String) -> Unit,
    onSubmitPin: () -> Boolean
) {
    val visualTransformation = remember { ComboV2PinVisualTransformation() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = pinText,
                onValueChange = onPinTextChange,
                label = { Text(stringResource(R.string.combov2_enter_pin)) },
                placeholder = { Text(stringResource(R.string.combov2_pin_hint)) },
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { onSubmitPin() },
                enabled = pinText.length == PAIRING_PIN_SIZE
            ) {
                Text(stringResource(app.aaps.core.ui.R.string.ok))
            }
        }
        if (pinFailed) {
            Text(
                text = stringResource(R.string.combov2_pairing_pin_failure),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ConfirmCancelSection(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.ok),
            onClick = onConfirm
        ),
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onDismiss
        )
    ) {
        Text(
            text = stringResource(R.string.combov2_confirm_cancel_pairing_message),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun FinishedSection(onOk: () -> Unit) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.ok),
            onClick = onOk
        )
    ) {
        Text(
            text = stringResource(R.string.combov2_pairing_finished_successfully),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AbortedSection(reason: String, onOk: () -> Unit) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.ok),
            onClick = onOk
        )
    ) {
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PermissionsDeniedSection(
    onRetry: () -> Unit,
    onGoBack: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.retry),
            onClick = onRetry
        ),
        secondaryButton = WizardButton(
            text = stringResource(R.string.combov2_go_back),
            onClick = onGoBack
        )
    ) {
        Text(
            text = stringResource(app.aaps.core.ui.R.string.ble_permissions_missing),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// ── Previews ──────────────────────────────────────────────────────────────
// Wrapped in plain MaterialTheme (not AapsTheme) because AapsTheme depends on
// LocalPreferences, which is not provided in preview contexts.

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun IdleSectionPreview() {
    MaterialTheme { IdleSection(onStartPairing = {}) }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun InProgressScanningPreview() {
    MaterialTheme {
        InProgressSection(
            state = ComboV2PairWizardUiState(
                phase = PairWizardPhase.InProgress,
                stepDescription = "Scanning for pump",
                overallProgress = 0.1f,
                scanningIndeterminate = true,
                pinEntryVisible = false
            ),
            onPinTextChange = {},
            onSubmitPin = { true },
            onRequestCancel = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun InProgressPinEntryPreview() {
    MaterialTheme {
        InProgressSection(
            state = ComboV2PairWizardUiState(
                phase = PairWizardPhase.InProgress,
                stepDescription = "Pump requests PIN",
                overallProgress = 0.6f,
                scanningIndeterminate = false,
                pinEntryVisible = true,
                pinText = "1234567",
                pinFailed = true
            ),
            onPinTextChange = {},
            onSubmitPin = { true },
            onRequestCancel = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun ConfirmCancelSectionPreview() {
    MaterialTheme { ConfirmCancelSection(onConfirm = {}, onDismiss = {}) }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun FinishedSectionPreview() {
    MaterialTheme { FinishedSection(onOk = {}) }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun AbortedSectionPreview() {
    MaterialTheme {
        AbortedSection(
            reason = "Pairing failed due to error: example error",
            onOk = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun DriverNotInitializedSectionPreview() {
    MaterialTheme { DriverNotInitializedSection(onGoBack = {}) }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun PermissionsDeniedSectionPreview() {
    MaterialTheme { PermissionsDeniedSection(onRetry = {}, onGoBack = {}) }
}
