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
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.SceneAction
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.ui.compose.scenes.ProfileSwitchEditor

@Composable
internal fun ProfileStep(
    state: SceneWizardViewModel.WizardState,
    onToggle: (Boolean) -> Unit,
    onUpdate: (SceneAction) -> Unit,
    profileNames: List<String>,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    WizardStepLayout(
        secondaryButton = WizardButton(text = stringResource(R.string.back), onClick = onBack),
        primaryButton = WizardButton(text = stringResource(R.string.next), onClick = onNext)
    ) {
        Text(
            text = stringResource(R.string.careportal_profileswitch),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.scene_wizard_profile_info),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ActionToggle(
            label = stringResource(R.string.scene_wizard_include_action, stringResource(R.string.careportal_profileswitch)),
            checked = state.profileEnabled,
            onCheckedChange = onToggle
        )
        AnimatedVisibility(
            visible = state.profileEnabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileSwitchEditor(
                    action = state.profileAction,
                    onUpdate = onUpdate,
                    profileNames = profileNames
                )
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ProfileStepPreview() {
    MaterialTheme {
        ProfileStep(
            state = previewState.copy(profileEnabled = true),
            onToggle = {}, onUpdate = {},
            profileNames = listOf("Default", "Sport", "Sick"),
            onBack = {}, onNext = {}
        )
    }
}
