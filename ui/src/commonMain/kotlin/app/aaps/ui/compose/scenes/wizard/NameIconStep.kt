package app.aaps.ui.compose.scenes.wizard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.scenes.SceneIconPicker

/**
 * @see NameIconStepPreview
 */
@Composable
internal fun NameIconStep(
    state: SceneWizardViewModel.WizardState,
    onSetName: (String) -> Unit,
    onSetIcon: (String) -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    WizardStepLayout(
        secondaryButton = WizardButton(text = stringResource(CoreUiStrings.back), onClick = onBack),
        primaryButton = WizardButton(
            text = stringResource(CoreUiStrings.scene_wizard_finish),
            onClick = onFinish,
            enabled = state.name.isNotBlank()
        )
    ) {
        Text(
            text = stringResource(CoreUiStrings.scene),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(CoreUiStrings.scene_wizard_name_info),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = state.name,
            onValueChange = onSetName,
            label = { Text(stringResource(CoreUiStrings.scene)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        SceneIconPicker(
            selectedKey = state.icon,
            onIconSelected = onSetIcon
        )
    }
}
