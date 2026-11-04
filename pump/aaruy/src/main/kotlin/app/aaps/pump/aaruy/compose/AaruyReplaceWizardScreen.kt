package app.aaps.pump.aaruy.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardScreen
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.aaruy.R

@Composable
fun AaruyReplaceWizardScreen(
    viewModel: AaruyReplaceWizardViewModel,
    title: String,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onFinish: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()

    if (state.confirmDroplets) {
        OkCancelDialog(
            title = title,
            message = stringResource(R.string.actions_reservoir_prime_confirm),
            onConfirm = viewModel::dropletsSeen,
            onDismiss = viewModel::dropletsNotSeen
        )
    }

    WizardScreen(
        currentStep = state.step,
        totalSteps = AaruyReplaceStep.entries.size,
        currentStepIndex = state.step.ordinal,
        canGoBack = !state.busy && state.step != AaruyReplaceStep.DONE,
        onBack = onFinish,
        cancelDialogTitle = title,
        cancelDialogText = stringResource(R.string.if_exit_replace_view),
        title = title,
        setToolbarConfig = setToolbarConfig
    ) { step, _ ->
        val canSend = connected && !state.busy
        when (step) {
            AaruyReplaceStep.REWIND       -> Step(
                text = stringResource(R.string.actions_reservoir_rewind_prompt),
                state = state,
                connected = connected,
                primary = WizardButton(stringResource(R.string.aaruy_rewind), viewModel::rewind, enabled = canSend, loading = state.busy)
            )

            AaruyReplaceStep.ASSEMBLE     -> Step(
                text = stringResource(R.string.actions_reservoir_prime_before),
                state = state,
                connected = connected,
                primary = WizardButton(stringResource(R.string.setting_next), viewModel::assembled, enabled = canSend)
            )

            AaruyReplaceStep.PRIME        -> Step(
                text = stringResource(if (state.dropletsSeenByPump) R.string.locate_check_liquid else R.string.actions_reservoir_prime_comment),
                note = stringResource(R.string.actions_reservoir_prime_comment_note),
                state = state,
                connected = connected,
                primary = WizardButton(stringResource(R.string.setting_next), viewModel::askDroplets, enabled = canSend && state.primed),
                secondary = WizardButton(stringResource(R.string.actions_reservoir_prime), viewModel::prime, enabled = canSend, loading = state.busy)
            )

            AaruyReplaceStep.CONNECT      -> Step(
                text = stringResource(R.string.actions_reservoir_prime_cannula_before),
                state = state,
                connected = connected,
                primary = WizardButton(stringResource(R.string.setting_next), viewModel::connectedToBody, enabled = canSend)
            )

            AaruyReplaceStep.FILL_CANNULA -> Step(
                text = stringResource(R.string.actions_reservoir_prime_cannula),
                note = stringResource(R.string.actions_reservoir_prime_cannula_note),
                state = state,
                connected = connected,
                primary = WizardButton(stringResource(R.string.actions_reservoir_prime_cannula_action), viewModel::fillCannula, enabled = canSend, loading = state.busy),
                secondary = WizardButton(stringResource(R.string.actions_reservoir_cannula_skip), viewModel::skipFillingCannula, enabled = canSend)
            ) {
                NeedleChoice(stringResource(R.string.soft_needle_6), state.needleType == 0, enabled = canSend) { viewModel.selectNeedle(0) }
                NeedleChoice(stringResource(R.string.soft_needle_9), state.needleType == 1, enabled = canSend) { viewModel.selectNeedle(1) }
            }

            AaruyReplaceStep.DONE         -> Step(
                text = stringResource(R.string.aaruy_replace_done),
                state = state,
                connected = true,
                primary = WizardButton(stringResource(app.aaps.core.ui.R.string.ok), onFinish)
            )
        }
    }
}

@Composable
private fun Step(
    text: String,
    state: AaruyReplaceUiState,
    connected: Boolean,
    primary: WizardButton,
    note: String? = null,
    secondary: WizardButton? = null,
    extra: @Composable () -> Unit = {}
) {
    WizardStepLayout(primaryButton = primary, secondaryButton = secondary) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
        extra()
        note?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        state.error?.let { Text(text = stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        if (!connected) Text(text = stringResource(R.string.bluetooth_disconnect), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun NeedleChoice(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(text)
    }
}
