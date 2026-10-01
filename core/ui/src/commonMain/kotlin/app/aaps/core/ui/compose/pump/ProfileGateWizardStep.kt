package app.aaps.core.ui.compose.pump

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.stringResource
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for ViewModels that host the profile gate wizard step.
 *
 * The step is shown as the first wizard page when no `ProfileSwitch` exists
 * at activation time. It has two modes:
 *  - `availableProfiles` is empty → user has no `LocalProfile` defined.
 *    The step shows an explanatory message and a Cancel button.
 *  - `availableProfiles` is non-empty → user picks one and taps Activate;
 *    the host calls `profileFunction.createProfileSwitch(...)`.
 */
interface ProfileGateStepHost {

    val availableProfiles: StateFlow<List<String>>
    val selectedProfile: StateFlow<String?>
    fun selectProfile(name: String)
    fun activateSelectedProfile()
    fun cancelGate()
}

/**
 * @see ProfileGateHasStorePreview
 * @see ProfileGateNoStorePreview
 */
@Composable
fun ProfileGateWizardStep(host: ProfileGateStepHost) {
    val profiles by host.availableProfiles.collectAsStateWithLifecycle()
    val selected by host.selectedProfile.collectAsStateWithLifecycle()

    val hasStore = profiles.isNotEmpty()

    WizardStepLayout(
        primaryButton = if (hasStore) WizardButton(
            text = stringResource(CoreUiStrings.activate_profile),
            onClick = { host.activateSelectedProfile() },
            enabled = selected != null
        ) else null,
        secondaryButton = WizardButton(
            text = stringResource(CoreUiStrings.cancel),
            onClick = { host.cancelGate() }
        )
    ) {
        if (hasStore) {
            Text(
                text = stringResource(CoreUiStrings.pump_wizard_profile_gate_pick),
                style = MaterialTheme.typography.bodyLarge
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
            ) {
                profiles.forEach { name ->
                    val isSelected = selected == name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                onClick = { host.selectProfile(name) }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { host.selectProfile(name) }
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            Text(
                text = stringResource(CoreUiStrings.pump_wizard_profile_gate_no_store),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
