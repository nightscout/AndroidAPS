package app.aaps.pump.aaruy.compose

import app.aaps.core.ui.CoreUiStrings
import app.aaps.pump.aaruy.AaruyStrings
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
import app.aaps.core.ui.compose.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.insulin.SelectInsulin
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardScreen
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.core.ui.compose.siteRotation.SiteLocationWizardStep

@Composable
fun AaruyReplaceWizardScreen(
    viewModel: AaruyReplaceWizardViewModel,
    title: String,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onFinish: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.confirmDroplets) {
        OkCancelDialog(
            title = title,
            message = stringResource(AaruyStrings.actions_reservoir_prime_confirm),
            onConfirm = viewModel::dropletsSeen,
            onDismiss = viewModel::dropletsNotSeen
        )
    }

    WizardScreen(
        currentStep = state.step,
        totalSteps = state.steps.size,
        currentStepIndex = state.steps.indexOf(state.step).coerceAtLeast(0),
        canGoBack = !state.busy && state.step != AaruyReplaceStep.DONE,
        onBack = onFinish,
        cancelDialogTitle = title,
        cancelDialogText = stringResource(AaruyStrings.if_exit_replace_view),
        title = title,
        setToolbarConfig = setToolbarConfig
    ) { step, _ ->
        // The queue opens the link for each step, so no link has to be up for a button.
        val canSend = !state.busy
        when (step) {
            AaruyReplaceStep.REWIND       -> Step(
                text = stringResource(AaruyStrings.actions_reservoir_rewind_prompt),
                state = state,
                primary = WizardButton(stringResource(AaruyStrings.aaruy_rewind), viewModel::rewind, enabled = canSend, loading = state.busy)
            )

            AaruyReplaceStep.ASSEMBLE     -> Step(
                text = stringResource(AaruyStrings.actions_reservoir_prime_before),
                hint = stringResource(AaruyStrings.aaruy_motor_still_running),
                state = state,
                primary = WizardButton(stringResource(CoreUiStrings.next), viewModel::assembled, enabled = canSend)
            )

            AaruyReplaceStep.SELECT_INSULIN -> {
                val availableInsulins by viewModel.availableInsulins.collectAsStateWithLifecycle()
                val selectedInsulin by viewModel.selectedInsulin.collectAsStateWithLifecycle()
                val activeInsulinLabel by viewModel.activeInsulinLabel.collectAsStateWithLifecycle()
                WizardStepLayout(
                    primaryButton = WizardButton(stringResource(CoreUiStrings.next), viewModel::insulinSelected, enabled = selectedInsulin != null)
                ) {
                    SelectInsulin(
                        availableInsulins = availableInsulins,
                        selectedInsulin = selectedInsulin,
                        activeInsulinLabel = activeInsulinLabel,
                        onInsulinSelect = viewModel::selectInsulin,
                        initialExpanded = true,
                        concentrationDropDownEnabled = viewModel.concentrationEnabled
                    )
                }
            }

            AaruyReplaceStep.PRIME        -> Step(
                text = stringResource(if (state.dropletsSeenByPump) AaruyStrings.locate_check_liquid else AaruyStrings.actions_reservoir_prime_comment),
                note = stringResource(AaruyStrings.actions_reservoir_prime_comment_note),
                hint = if (state.primed) stringResource(AaruyStrings.aaruy_motor_still_running) else null,
                state = state,
                primary = WizardButton(stringResource(CoreUiStrings.next), viewModel::askDroplets, enabled = canSend && state.primed),
                secondary = WizardButton(stringResource(AaruyStrings.actions_reservoir_prime), viewModel::prime, enabled = canSend, loading = state.busy)
            )

            AaruyReplaceStep.CONNECT      -> Step(
                text = stringResource(AaruyStrings.actions_reservoir_prime_cannula_before),
                state = state,
                primary = WizardButton(stringResource(CoreUiStrings.next), viewModel::connectedToBody, enabled = canSend)
            )

            AaruyReplaceStep.SITE_LOCATION  -> SiteLocationWizardStep(host = viewModel)

            AaruyReplaceStep.FILL_CANNULA -> Step(
                text = stringResource(AaruyStrings.actions_reservoir_prime_cannula),
                note = stringResource(AaruyStrings.actions_reservoir_prime_cannula_note),
                // The pump log cannot say if the cannula was filled before the change stopped.
                hint = if (state.resumed) stringResource(AaruyStrings.aaruy_replace_resumed) else null,
                state = state,
                primary = WizardButton(
                    stringResource(if (state.cannulaFilled) AaruyStrings.aaruy_start_basal else AaruyStrings.actions_reservoir_prime_cannula_action),
                    viewModel::fillCannula, enabled = canSend, loading = state.busy
                ),
                // After the fill, Skip would record no new cannula.
                secondary = if (state.cannulaFilled) null else WizardButton(stringResource(AaruyStrings.actions_reservoir_cannula_skip), viewModel::skipFillingCannula, enabled = canSend)
            ) {
                NeedleChoice(stringResource(AaruyStrings.soft_needle_6), state.needleType == 0, enabled = canSend) { viewModel.selectNeedle(0) }
                NeedleChoice(stringResource(AaruyStrings.soft_needle_9), state.needleType == 1, enabled = canSend) { viewModel.selectNeedle(1) }
            }

            AaruyReplaceStep.DONE         -> Step(
                text = stringResource(AaruyStrings.aaruy_replace_done),
                state = state,
                primary = WizardButton(stringResource(CoreUiStrings.ok), onFinish)
            )
        }
    }
}

@Composable
private fun Step(
    text: String,
    state: AaruyReplaceUiState,
    primary: WizardButton,
    note: String? = null,
    hint: String? = null,
    secondary: WizardButton? = null,
    extra: @Composable () -> Unit = {}
) {
    WizardStepLayout(primaryButton = primary, secondaryButton = secondary) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
        // The pump reports the end of a motor move before the motor stops, and nothing else tells when it does.
        hint?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
        extra()
        note?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        state.error?.let { Text(text = stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
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
