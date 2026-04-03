package app.aaps.pump.danars.compose

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.pump.BleScanStep
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardErrorBanner
import app.aaps.core.ui.compose.pump.WizardScreen
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.dana.R

@Composable
fun DanaRSPairWizardScreen(
    viewModel: DanaRSPairWizardViewModel,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val stepIndex = when (uiState.step) {
        WizardStep.BLE_SCAN         -> 0
        WizardStep.PAIRING_PROGRESS -> 1
        WizardStep.ENTER_PASSWORD   -> 1
        WizardStep.ENTER_PIN        -> 1
        WizardStep.COMPLETE         -> 2
        WizardStep.ERROR            -> 1
    }

    WizardScreen(
        currentStep = uiState.step,
        totalSteps = 3,
        currentStepIndex = stepIndex,
        canGoBack = uiState.step == WizardStep.BLE_SCAN,
        onBack = onCancel,
        cancelDialogTitle = stringResource(R.string.cancel_pairing_title),
        cancelDialogText = stringResource(R.string.cancel_pairing_message)
    ) { step, onCancelDialog ->
        when (step) {
            WizardStep.BLE_SCAN         -> BleScanStep(
                devices = uiState.devices,
                onSelectDevice = viewModel::selectDevice,
                onStartScan = viewModel::startScan,
                onCancel = onCancelDialog
            )

            WizardStep.PAIRING_PROGRESS -> PairingProgressStep(
                onCancel = onCancel
            )

            WizardStep.ENTER_PASSWORD   -> EnterPasswordStep(
                password = uiState.password,
                onPasswordChange = viewModel::updatePassword,
                onSubmit = viewModel::submitPassword,
                onCancel = onCancel
            )

            WizardStep.ENTER_PIN        -> EnterPinStep(
                pin1 = uiState.pin1,
                pin2 = uiState.pin2,
                onPin1Change = viewModel::updatePin1,
                onPin2Change = viewModel::updatePin2,
                onSubmit = viewModel::submitPin,
                onCancel = onCancel,
                errorMessage = uiState.errorMessage
            )

            WizardStep.COMPLETE         -> PairingCompleteStep(
                onDone = {
                    viewModel.finishWizard()
                    onFinish()
                }
            )

            WizardStep.ERROR            -> PairingErrorStep(
                errorMessage = uiState.errorMessage,
                onRetry = viewModel::retry,
                onCancel = onCancel
            )
        }
    }
}

@Composable
private fun PairingProgressStep(
    onCancel: () -> Unit
) {
    WizardStepLayout(
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        CircularProgressIndicator(
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.connecting_to_pump),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EnterPasswordStep(
    password: String,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.ok),
            onClick = onSubmit,
            enabled = password.length == 4
        ),
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        Text(
            text = stringResource(R.string.wrongpumppassword),
            style = MaterialTheme.typography.bodyLarge
        )

        OutlinedTextField(
            value = password,
            onValueChange = { input -> input.filter { it.isHexChar() }.take(4).uppercase().let(onPasswordChange) },
            label = { Text(stringResource(R.string.danars_password_title)) },
            supportingText = { Text(stringResource(app.aaps.core.validators.R.string.error_mustbe4hexadidits)) },
            keyboardOptions = hexKeyboardOptions,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EnterPinStep(
    pin1: String,
    pin2: String,
    onPin1Change: (String) -> Unit,
    onPin2Change: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    errorMessage: String?
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.ok),
            onClick = onSubmit,
            enabled = pin1.length == 12 && pin2.length == 8
        ),
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        Text(
            text = stringResource(R.string.press_ok_on_the_pump),
            style = MaterialTheme.typography.bodyLarge
        )

        errorMessage?.let { WizardErrorBanner(message = it) }

        OutlinedTextField(
            value = pin1,
            onValueChange = { input -> input.filter { it.isHexChar() }.take(12).uppercase().let(onPin1Change) },
            label = { Text(stringResource(R.string.num1pin)) },
            keyboardOptions = hexKeyboardOptions,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = pin2,
            onValueChange = { input -> input.filter { it.isHexChar() }.take(8).uppercase().let(onPin2Change) },
            label = { Text(stringResource(R.string.num2pin)) },
            keyboardOptions = hexKeyboardOptions,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PairingCompleteStep(
    onDone: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(R.string.done),
            onClick = onDone
        )
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.pairing_successful),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(R.string.pairing_successful_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PairingErrorStep(
    errorMessage: String?,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.pairing),
            onClick = onRetry
        ),
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        errorMessage?.let { WizardErrorBanner(message = it) }
            ?: WizardErrorBanner(message = stringResource(app.aaps.core.ui.R.string.error))
    }
}

private val hexKeyboardOptions = KeyboardOptions(
    keyboardType = KeyboardType.Ascii,
    capitalization = KeyboardCapitalization.Characters
)

private fun Char.isHexChar(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
