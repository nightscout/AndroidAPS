package app.aaps.pump.medtrum.compose.steps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.ICfg
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.code.PatchStep
import app.aaps.pump.medtrum.compose.MedtrumPatchViewModel

@Composable
fun SelectInsulinStep(
    viewModel: MedtrumPatchViewModel,
    onCancel: () -> Unit
) {
    val availableInsulins by viewModel.availableInsulins.collectAsStateWithLifecycle()
    val selectedInsulin by viewModel.selectedInsulin.collectAsStateWithLifecycle()
    val activeInsulinLabel by viewModel.activeInsulinLabel.collectAsStateWithLifecycle()

    SelectInsulinStepContent(
        availableInsulins = availableInsulins,
        selectedInsulin = selectedInsulin,
        activeInsulinLabel = activeInsulinLabel,
        onInsulinSelect = viewModel::selectInsulin,
        onNext = { viewModel.moveStep(PatchStep.PRIME) },
        onCancel = onCancel
    )
}

@Composable
internal fun SelectInsulinStepContent(
    availableInsulins: List<ICfg>,
    selectedInsulin: ICfg?,
    activeInsulinLabel: String?,
    onInsulinSelect: (ICfg) -> Unit,
    onNext: () -> Unit,
    onCancel: () -> Unit,
    initialExpanded: Boolean = false
) {
    var expanded by rememberSaveable { mutableStateOf(initialExpanded) }

    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(R.string.next),
            onClick = onNext
        ),
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        Text(
            text = stringResource(R.string.select_insulin_description),
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(Modifier.height(16.dp))

        // Show current insulin with a Change button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.current_insulin),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = selectedInsulin?.insulinLabel ?: activeInsulinLabel ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            FilledTonalButton(onClick = { expanded = !expanded }) {
                Text(stringResource(R.string.change_insulin))
            }
        }

        // Expandable insulin selection list
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                availableInsulins.forEach { iCfg ->
                    val isSelected = iCfg.insulinLabel == selectedInsulin?.insulinLabel
                    val isActive = iCfg.insulinLabel == activeInsulinLabel

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onInsulinSelect(iCfg)
                                expanded = false
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onInsulinSelect(iCfg)
                                expanded = false
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = iCfg.insulinLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isActive) {
                                Text(
                                    text = stringResource(R.string.current_insulin),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// region Previews

private val previewInsulins = listOf(
    ICfg("Fiasp U100", peak = 55, dia = 5.0, concentration = 1.0),
    ICfg("Lyumjev U200", peak = 45, dia = 5.0, concentration = 2.0),
    ICfg("NovoRapid U100", peak = 75, dia = 5.0, concentration = 1.0)
)

@Preview(showBackground = true, name = "Select Insulin - Collapsed")
@Composable
private fun PreviewCollapsed() {
    MaterialTheme {
        SelectInsulinStepContent(
            availableInsulins = previewInsulins,
            selectedInsulin = previewInsulins[0],
            activeInsulinLabel = "Fiasp U100",
            onInsulinSelect = {},
            onNext = {},
            onCancel = {}
        )
    }
}

@Preview(showBackground = true, name = "Select Insulin - Expanded")
@Composable
private fun PreviewExpanded() {
    MaterialTheme {
        SelectInsulinStepContent(
            availableInsulins = previewInsulins,
            selectedInsulin = previewInsulins[0],
            activeInsulinLabel = "Fiasp U100",
            onInsulinSelect = {},
            onNext = {},
            onCancel = {},
            initialExpanded = true
        )
    }
}

// endregion
