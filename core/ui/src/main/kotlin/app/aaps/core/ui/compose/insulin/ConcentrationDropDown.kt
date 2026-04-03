package app.aaps.core.ui.compose.insulin

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.aaps.core.interfaces.insulin.ConcentrationType
import app.aaps.core.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcentrationDropdown(
    selected: ConcentrationType,
    concentrations: List<ConcentrationType>,
    onSelect: (ConcentrationType) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = stringResource(selected.label),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.concentration_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            concentrations.forEach { concentration ->
                DropdownMenuItem(
                    text = { Text(stringResource(concentration.label)) },
                    onClick = {
                        onSelect(concentration)
                        expanded = false
                    }
                )
            }
        }
    }
}
