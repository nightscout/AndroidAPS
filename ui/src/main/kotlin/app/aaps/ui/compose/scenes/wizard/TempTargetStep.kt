package app.aaps.ui.compose.scenes.wizard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.SceneAction
import app.aaps.core.data.model.TTPreset
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.ui.compose.scenes.TempTargetEditor

@Composable
internal fun TempTargetStep(
    state: SceneWizardViewModel.WizardState,
    onToggle: (Boolean) -> Unit,
    onUpdate: (SceneAction) -> Unit,
    ttPresets: List<TTPreset>,
    formatBgWithUnits: (Double) -> String,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val canProceed = !state.ttEnabled || state.ttAction != null
    WizardStepLayout(
        secondaryButton = WizardButton(text = stringResource(R.string.back), onClick = onBack),
        primaryButton = WizardButton(text = stringResource(R.string.next), onClick = onNext, enabled = canProceed)
    ) {
        Text(
            text = stringResource(R.string.temporary_target),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.scene_wizard_tt_info),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ActionToggle(
            label = stringResource(R.string.scene_wizard_include_action, stringResource(R.string.temporary_target)),
            checked = state.ttEnabled,
            onCheckedChange = onToggle
        )
        AnimatedVisibility(
            visible = state.ttEnabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                TempTargetEditor(
                    action = state.ttAction,
                    onUpdate = onUpdate,
                    ttPresets = ttPresets,
                    formatBgWithUnits = formatBgWithUnits
                )
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun TempTargetStepPreview() {
    MaterialTheme {
        TempTargetStep(
            state = previewState,
            onToggle = {}, onUpdate = {},
            ttPresets = previewPresets,
            formatBgWithUnits = { "${it.toInt()} mg/dl" },
            onBack = {}, onNext = {}
        )
    }
}
