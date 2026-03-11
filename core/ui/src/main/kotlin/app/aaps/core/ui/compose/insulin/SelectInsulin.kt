package app.aaps.core.ui.compose.insulin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.insulin.ConcentrationType
import app.aaps.core.ui.R

@Composable
fun SelectInsulin(
    availableInsulins: List<ICfg>,
    selectedInsulin: ICfg?,
    activeInsulinLabel: String?,
    onInsulinSelect: (ICfg) -> Unit,
    initialExpanded: Boolean = false,
    concentrationDropDownEnabled: Boolean = false
) {
    var expanded by rememberSaveable { mutableStateOf(initialExpanded) }
    val allConcentrations = remember(availableInsulins) {
        availableInsulins
            .map { it.concentration }
            .distinct()
            .mapNotNull { ConcentrationType.fromDouble(it) }
            .sortedBy { it.value }
    }
    var selectedConcentration by rememberSaveable { mutableStateOf<ConcentrationType?>(ConcentrationType.U100) }

    LaunchedEffect(selectedInsulin) {
        if (selectedInsulin != null) {
            selectedConcentration = ConcentrationType.fromDouble(selectedInsulin.concentration)
        } else if (allConcentrations.isNotEmpty()) {
            selectedConcentration = allConcentrations.first()
        }
    }

    val filteredInsulins = remember(selectedConcentration, availableInsulins) {
        if (selectedConcentration != null) {
            availableInsulins.filter { it.concentration == selectedConcentration!!.value }
        } else {
            availableInsulins
        }
    }

    Column {
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
                val showConcentrationDropdown = concentrationDropDownEnabled && allConcentrations.size > 1 && selectedConcentration != null
                if (showConcentrationDropdown) {
                    ConcentrationDropdown(
                        selected = selectedConcentration!!,
                        concentrations = allConcentrations,
                        onSelect = { newConcentration ->
                            selectedConcentration = newConcentration
                            // Auto-select first insulin of new concentration
                            val firstMatch = availableInsulins.firstOrNull { it.concentration == newConcentration.value }
                            if (firstMatch != null) onInsulinSelect(firstMatch)
                        }
                    )
                }

                filteredInsulins.forEach { iCfg ->
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

private val previewInsulins = listOf(
    ICfg("Fiasp U100", peak = 55, dia = 5.0, concentration = 1.0),
    ICfg("Lyumjev U200", peak = 45, dia = 5.0, concentration = 2.0),
    ICfg("NovoRapid U100", peak = 75, dia = 5.0, concentration = 1.0)
)

@Preview(showBackground = true, name = "Select Insulin - Collapsed")
@Composable
private fun PreviewCollapsed() {
    MaterialTheme {
        SelectInsulin(
            availableInsulins = previewInsulins,
            selectedInsulin = previewInsulins[0],
            activeInsulinLabel = "Fiasp U100",
            onInsulinSelect = {}
        )
    }
}

@Preview(showBackground = true, name = "Select Insulin - Expanded")
@Composable
private fun PreviewExpanded() {
    MaterialTheme {
        SelectInsulin(
            availableInsulins = previewInsulins,
            selectedInsulin = previewInsulins[0],
            activeInsulinLabel = "Fiasp U100",
            onInsulinSelect = {},
            initialExpanded = true
        )
    }
}
