package app.aaps.plugins.automation.compose.elements

import app.aaps.core.ui.compose.stringResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.aaps.plugins.automation.elements.InputLocationMode

/**
 * @see PreviewDropdown
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationDropdown(
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = label?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onValueChange(opt); expanded = false }
                )
            }
        }
    }
}

/**
 * @see PreviewOnOff
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputDropdownOnOffEditor(
    on: Boolean,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(true to "On", false to "Off")
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (v, label) ->
            SegmentedButton(
                selected = on == v,
                onClick = { onValueChange(v) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size)
            ) { Text(label) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputLocationModeEditor(
    value: InputLocationMode.Mode,
    onValueChange: (InputLocationMode.Mode) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = InputLocationMode.Mode.entries
    val labels = options.map { stringResource(it.stringRes) }
    val currentLabel = labels[options.indexOf(value)]
    AutomationDropdown(
        value = currentLabel,
        options = labels,
        onValueChange = { picked ->
            val idx = labels.indexOf(picked)
            if (idx in options.indices) onValueChange(options[idx])
        },
        modifier = modifier
    )
}
