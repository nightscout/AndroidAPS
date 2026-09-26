package app.aaps.cgm.dexcomg7.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.cgm.dexcomg7.R
import app.aaps.cgm.dexcomg7.session.G7PairingState
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.banner.ErrorBanner
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardScreen
import app.aaps.core.ui.compose.pump.WizardStepLayout
import kotlinx.coroutines.delay

@Composable
fun G7PairingScreen(
    viewModel: G7PairingViewModel,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val stepIndex = when (uiState.step) {
        G7PairingStep.INTRO                                       -> 0
        G7PairingStep.CODE, G7PairingStep.SCANNER                 -> 1
        G7PairingStep.PAIRING, G7PairingStep.FAILED               -> 2
        G7PairingStep.DONE                                        -> 3
    }

    WizardScreen(
        currentStep = uiState.step,
        totalSteps = 4,
        currentStepIndex = stepIndex,
        canGoBack = uiState.step == G7PairingStep.INTRO || uiState.step == G7PairingStep.CODE,
        onBack = onCancel,
        cancelDialogTitle = stringResource(R.string.dexcom_g7_cancel_pairing_title),
        cancelDialogText = stringResource(R.string.dexcom_g7_cancel_pairing_text)
    ) { step, onCancelDialog ->
        when (step) {
            G7PairingStep.INTRO   -> IntroStep(uiState.conflictingApps, viewModel.isReplacingSensor, viewModel::continueFromIntro, onCancelDialog)
            G7PairingStep.CODE    -> CodeStep(uiState, viewModel::updateCode, viewModel::openScanner, viewModel::startPairing, onCancelDialog)
            G7PairingStep.SCANNER -> ScannerStep(viewModel::onBarcode, viewModel::closeScanner)
            G7PairingStep.PAIRING -> ProgressStep(uiState.pairing) {
                viewModel.cancel()
                onCancel()
            }

            G7PairingStep.DONE    -> DoneStep((uiState.pairing as? G7PairingState.Succeeded)?.sensorName, onFinish)
            G7PairingStep.FAILED  -> FailedStep(uiState.failureText, viewModel::retry, onCancel)
        }
    }
}

@Composable
private fun IntroStep(conflictingApps: List<String>, replacing: Boolean, onNext: () -> Unit, onCancel: () -> Unit) {
    WizardStepLayout(
        primaryButton = WizardButton(stringResource(app.aaps.core.ui.R.string.next), onNext),
        secondaryButton = WizardButton(stringResource(app.aaps.core.ui.R.string.cancel), onCancel)
    ) {
        if (conflictingApps.isNotEmpty()) {
            ErrorBanner(message = stringResource(R.string.dexcom_g7_conflicting_apps, conflictingApps.joinToString(", ")))
            Spacer(Modifier.height(AapsSpacing.large))
        }
        Text(stringResource(R.string.dexcom_g7_intro_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(AapsSpacing.medium))
        if (replacing) {
            Text(stringResource(R.string.dexcom_g7_intro_replacing), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(AapsSpacing.medium))
        }
        Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
            listOf(
                R.string.dexcom_g7_intro_step_1,
                R.string.dexcom_g7_intro_step_2,
                R.string.dexcom_g7_intro_step_3,
                R.string.dexcom_g7_intro_step_4
            ).forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun CodeStep(
    uiState: G7PairingUiState,
    onCodeChange: (String) -> Unit,
    onScan: () -> Unit,
    onPair: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(stringResource(R.string.dexcom_g7_pair), onPair, enabled = uiState.codeIsValid),
        secondaryButton = WizardButton(stringResource(app.aaps.core.ui.R.string.cancel), onCancel)
    ) {
        Text(stringResource(R.string.dexcom_g7_code_explanation), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(AapsSpacing.large))
        OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.size(AapsSpacing.medium))
            Text(stringResource(R.string.dexcom_g7_scan_barcode))
        }
        Spacer(Modifier.height(AapsSpacing.large))
        OutlinedTextField(
            value = uiState.code,
            onValueChange = onCodeChange,
            label = { Text(stringResource(R.string.dexcom_g7_pairing_code)) },
            supportingText = { Text(stringResource(R.string.dexcom_g7_pairing_code_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        uiState.scanMessage?.let {
            Spacer(Modifier.height(AapsSpacing.medium))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ScannerStep(onBarcode: (String) -> Boolean, onClose: () -> Unit) {
    WizardStepLayout(
        secondaryButton = WizardButton(stringResource(R.string.dexcom_g7_type_code_instead), onClose),
        scrollable = false
    ) {
        Text(stringResource(R.string.dexcom_g7_scan_explanation), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(AapsSpacing.large))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            G7BarcodeScanner(onBarcode = onBarcode, onPermissionDenied = onClose)
        }
    }
}

@Composable
private fun ProgressStep(state: G7PairingState, onCancel: () -> Unit) {
    // Elapsed time, so a long wait for a sensor that is still on another phone's lease does not look stuck.
    val startedAt = when (state) {
        is G7PairingState.Scanning       -> state.startedAt
        is G7PairingState.Authenticating -> state.startedAt
        else                             -> null
    }
    val elapsed by produceState(0L, startedAt) {
        while (startedAt != null) {
            value = (System.currentTimeMillis() - startedAt) / 1000
            delay(1000)
        }
    }
    WizardStepLayout(
        secondaryButton = WizardButton(stringResource(app.aaps.core.ui.R.string.cancel), onCancel)
    ) {
        Spacer(Modifier.height(AapsSpacing.xxLarge))
        CircularProgressIndicator(modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(AapsSpacing.extraLarge))
        val text = when (state) {
            is G7PairingState.Scanning       ->
                if (state.candidates.isEmpty()) stringResource(R.string.dexcom_g7_pairing_scanning)
                else stringResource(R.string.dexcom_g7_pairing_scanning_found, state.candidates.joinToString(", "))

            is G7PairingState.Authenticating -> stringResource(R.string.dexcom_g7_pairing_authenticating, state.candidate, state.attempt)
            else                             -> stringResource(R.string.dexcom_g7_pairing_starting)
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(AapsSpacing.medium))
        Text(
            stringResource(R.string.dexcom_g7_pairing_elapsed, elapsed / 60, elapsed % 60),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(AapsSpacing.large))
        Text(
            stringResource(R.string.dexcom_g7_pairing_hint),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DoneStep(sensorName: String?, onDone: () -> Unit) {
    WizardStepLayout(
        primaryButton = WizardButton(stringResource(app.aaps.core.ui.R.string.ok), onDone)
    ) {
        Spacer(Modifier.height(AapsSpacing.xxLarge))
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(AapsSpacing.extraLarge))
        Text(
            stringResource(R.string.dexcom_g7_pairing_done, sensorName ?: ""),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(AapsSpacing.medium))
        Text(
            stringResource(R.string.dexcom_g7_pairing_done_hint),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FailedStep(text: String?, onRetry: () -> Unit, onCancel: () -> Unit) {
    WizardStepLayout(
        primaryButton = WizardButton(stringResource(R.string.dexcom_g7_try_again), onRetry),
        secondaryButton = WizardButton(stringResource(app.aaps.core.ui.R.string.cancel), onCancel)
    ) {
        ErrorBanner(message = text ?: stringResource(R.string.dexcom_g7_pairing_failed_all))
    }
}
