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
import app.aaps.core.data.model.SceneAction
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.scenes.ProfileSwitchEditor

/**
 * @see ProfileStepPreview
 */
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
        secondaryButton = WizardButton(text = stringResource(CoreUiStrings.back), onClick = onBack),
        primaryButton = WizardButton(text = stringResource(CoreUiStrings.next), onClick = onNext)
    ) {
        Text(
            text = stringResource(CoreUiStrings.careportal_profileswitch),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(CoreUiStrings.scene_wizard_profile_info),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ActionToggle(
            label = stringResource(CoreUiStrings.scene_wizard_include_action, stringResource(CoreUiStrings.careportal_profileswitch)),
            checked = state.profileEnabled,
            onCheckedChange = onToggle
        )
        AnimatedVisibility(
            visible = state.profileEnabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                ProfileSwitchEditor(
                    action = state.profileAction,
                    onUpdate = onUpdate,
                    profileNames = profileNames
                )
            }
        }
    }
}
