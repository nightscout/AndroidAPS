package app.aaps.ui.compose.scenes.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.Scene
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout

@Composable
internal fun ChainStep(
    state: SceneWizardViewModel.WizardState,
    availableTargets: List<Scene>,
    onSetChainTarget: (String?) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    WizardStepLayout(
        secondaryButton = WizardButton(text = stringResource(R.string.back), onClick = onBack),
        primaryButton = WizardButton(text = stringResource(R.string.next), onClick = onNext)
    ) {
        Text(
            text = stringResource(R.string.scene_wizard_chain_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.scene_wizard_chain_info),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ChainTargetRow(
            label = stringResource(R.string.scene_chain_none),
            selected = state.chainTargetId == null,
            onSelect = { onSetChainTarget(null) }
        )
        availableTargets.forEach { scene ->
            ChainTargetRow(
                label = scene.name,
                selected = state.chainTargetId == scene.id,
                onSelect = { onSetChainTarget(scene.id) }
            )
        }
    }
}

@Composable
private fun ChainTargetRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ChainStepPreview() {
    MaterialTheme {
        ChainStep(
            state = previewState,
            availableTargets = listOf(
                Scene(id = "b", name = "Post-Meal"),
                Scene(id = "c", name = "Recovery")
            ),
            onSetChainTarget = {},
            onBack = {}, onNext = {}
        )
    }
}
