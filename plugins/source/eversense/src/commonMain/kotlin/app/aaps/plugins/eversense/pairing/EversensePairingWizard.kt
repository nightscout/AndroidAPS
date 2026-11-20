package app.aaps.plugins.eversense.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.pump.BleScanStep
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardScreen
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.eversense.EversenseStrings
import app.aaps.plugins.eversense.pairing.EversensePairingViewModel.Step

/**
 * The pairing wizard: before you start, the account, the transmitter, the connection, done. The frame (progress, the
 * toolbar, "leave?" on back) is the pump wizards' [WizardScreen].
 *
 * @param onClose called once the wizard is over, finished or left
 */
@Composable
fun EversensePairingWizard(
    viewModel: EversensePairingViewModel,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onClose: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onClose() }

    WizardScreen(
        currentStep = state.step,
        totalSteps = Step.entries.size,
        currentStepIndex = state.step.ordinal,
        canGoBack = !state.busy,
        onBack = viewModel::finish,
        cancelDialogTitle = stringResource(EversenseStrings.eversense_pair_title),
        cancelDialogText = stringResource(EversenseStrings.eversense_pair_leave),
        // The first and the last screen lose nothing by leaving
        askBeforeLeaving = state.step != Step.BEFORE && state.step != Step.DONE,
        title = stringResource(EversenseStrings.eversense_pair_title),
        setToolbarConfig = setToolbarConfig
    ) { step, onCancel ->
        when (step) {
            Step.BEFORE      -> BeforeYouStart(onNext = viewModel::next, onCancel = onCancel)
            Step.ACCOUNT     -> AccountStep(state = state, account = account, viewModel = viewModel)
            Step.TRANSMITTER -> BleScanStep(
                devices = state.devices,
                onSelectDevice = viewModel::select,
                onStartScan = viewModel::startScan,
                onStopScan = viewModel::stopScan,
                onCancel = viewModel::back,
                title = stringResource(EversenseStrings.eversense_scan_title),
                subtitle = stringResource(EversenseStrings.eversense_scan_in_progress)
            )

            Step.CONNECT     -> ConnectStep(state = state, onRetry = viewModel::retry, onBack = viewModel::back)
            Step.DONE        -> DoneStep(transmitter = state.transmitter?.name.orEmpty(), onFinish = viewModel::finish)
        }
    }
}

@Composable
private fun BeforeYouStart(onNext: () -> Unit, onCancel: () -> Unit) {
    WizardStepLayout(
        primaryButton = WizardButton(stringResource(CoreUiStrings.next), onNext),
        secondaryButton = WizardButton(stringResource(CoreUiStrings.cancel), onCancel)
    ) {
        Text(stringResource(EversenseStrings.eversense_pair_before_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(EversenseStrings.eversense_pair_before_official_app), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(EversenseStrings.eversense_pair_before_force_stop), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(EversenseStrings.eversense_pair_before_nearby), style = MaterialTheme.typography.bodyLarge)
    }
}

/** A stored login: continue with it, or sign out. None: the model, the username and the password, then sign in. */
@Composable
private fun AccountStep(state: EversensePairingViewModel.UiState, account: EversenseAccount?, viewModel: EversensePairingViewModel) {
    val back = WizardButton(stringResource(CoreUiStrings.back), viewModel::back, enabled = !state.busy)
    if (account != null) {
        WizardStepLayout(
            primaryButton = WizardButton(stringResource(CoreUiStrings.next), viewModel::continueSignedIn),
            secondaryButton = WizardButton(stringResource(EversenseStrings.eversense_sign_out), viewModel::signOut)
        ) {
            Text(stringResource(EversenseStrings.eversense_pair_account_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(EversenseStrings.eversense_signed_in_as, account.username), style = MaterialTheme.typography.bodyLarge)
            account.model?.let { Text(stringResource(it.label()), style = MaterialTheme.typography.bodyMedium) }
        }
        return
    }
    val focusManager = LocalFocusManager.current
    WizardStepLayout(
        modifier = Modifier.clearFocusOnTap(focusManager),
        primaryButton = WizardButton(stringResource(EversenseStrings.eversense_sign_in), viewModel::signIn, enabled = state.canSignIn, loading = state.busy),
        secondaryButton = back
    ) {
        Text(stringResource(EversenseStrings.eversense_pair_account_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(EversenseStrings.eversense_pair_account_hint), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(EversenseStrings.eversense_pair_model), style = MaterialTheme.typography.bodyLarge)
        Column(Modifier.selectableGroup()) {
            EversenseModel.entries.forEach { model ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = state.model == model, onClick = { viewModel.setModel(model) }, role = Role.RadioButton),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The row is the button; the radio only shows the choice
                    RadioButton(selected = state.model == model, onClick = null)
                    Text(stringResource(model.label()), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::setUsername,
            label = { Text(stringResource(EversenseStrings.eversense_credentials_username)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::setPassword,
            label = { Text(stringResource(EversenseStrings.eversense_credentials_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        )
        state.signInError?.let { error ->
            Text(stringResource(error.message()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ConnectStep(state: EversensePairingViewModel.UiState, onRetry: () -> Unit, onBack: () -> Unit) {
    val name = state.transmitter?.name.orEmpty()
    val failed = state.pairResult
    WizardStepLayout(
        primaryButton = if (failed != null) WizardButton(stringResource(CoreUiStrings.retry), onRetry) else null,
        secondaryButton = WizardButton(stringResource(CoreUiStrings.back), onBack, enabled = !state.busy)
    ) {
        Text(stringResource(EversenseStrings.eversense_pair_connecting, name), style = MaterialTheme.typography.titleMedium)
        if (failed == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text(stringResource(CoreUiStrings.please_wait), style = MaterialTheme.typography.bodyLarge)
            }
            // A 365 registers online on its first connection, which takes longer than the Bluetooth part
            if (state.model != EversenseModel.E3) Text(stringResource(EversenseStrings.eversense_pair_registering), style = MaterialTheme.typography.bodyMedium)
        } else {
            val reason = if (failed == PairResult.NOT_STARTED) EversenseStrings.eversense_pair_not_started else EversenseStrings.eversense_pair_timed_out
            Text(stringResource(reason), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DoneStep(transmitter: String, onFinish: () -> Unit) {
    WizardStepLayout(primaryButton = WizardButton(stringResource(CoreUiStrings.finish), onFinish)) {
        Text(stringResource(EversenseStrings.eversense_pair_done, transmitter), style = MaterialTheme.typography.titleMedium)
    }
}

private fun EversenseModel.label() = when (this) {
    EversenseModel.E3         -> EversenseStrings.eversense_model_e3
    EversenseModel.E365_USA   -> EversenseStrings.eversense_model_365_usa
    EversenseModel.E365_OTHER -> EversenseStrings.eversense_model_365_other
}

private fun SignInResult.message() = when (this) {
    SignInResult.NO_CONNECTION -> EversenseStrings.eversense_sign_in_no_connection
    SignInResult.SERVER_ERROR  -> EversenseStrings.eversense_sign_in_server_error
    else                       -> EversenseStrings.eversense_sign_in_refused
}
