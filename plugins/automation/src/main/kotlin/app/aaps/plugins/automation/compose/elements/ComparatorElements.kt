package app.aaps.plugins.automation.compose.elements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.ComparatorConnect
import app.aaps.plugins.automation.elements.ComparatorExists

private fun Comparator.Compare.glyph(): String = when (this) {
    Comparator.Compare.IS_LESSER           -> "<"
    Comparator.Compare.IS_EQUAL_OR_LESSER  -> "≤"
    Comparator.Compare.IS_EQUAL            -> "="
    Comparator.Compare.IS_EQUAL_OR_GREATER -> "≥"
    Comparator.Compare.IS_GREATER          -> ">"
    Comparator.Compare.IS_NOT_AVAILABLE    -> "N/A"
}

/**
 * Compact chip-with-dropdown comparator picker. Fits on one row next to a value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparatorEditor(
    value: Comparator.Compare,
    onValueChange: (Comparator.Compare) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(value.glyph()) }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Comparator.Compare.entries.forEach { cmp ->
                DropdownMenuItem(
                    text = { Text("${cmp.glyph()}   ${stringResource(cmp.stringRes)}") },
                    onClick = { onValueChange(cmp); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparatorConnectEditor(
    value: ComparatorConnect.Compare,
    onValueChange: (ComparatorConnect.Compare) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(stringResource(value.stringRes)) }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ComparatorConnect.Compare.entries.forEach { cmp ->
                DropdownMenuItem(
                    text = { Text(stringResource(cmp.stringRes)) },
                    onClick = { onValueChange(cmp); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparatorExistsEditor(
    value: ComparatorExists.Compare,
    onValueChange: (ComparatorExists.Compare) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(stringResource(value.stringRes)) }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ComparatorExists.Compare.entries.forEach { cmp ->
                DropdownMenuItem(
                    text = { Text(stringResource(cmp.stringRes)) },
                    onClick = { onValueChange(cmp); expanded = false }
                )
            }
        }
    }
}

/**
 * Helper row: compact comparator chip + label + value (weighted to the right).
 * Used by most leaf trigger editors to fit on one line.
 */
@Composable
fun CompareRow(
    comparator: Comparator.Compare,
    onComparatorChange: (Comparator.Compare) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ComparatorEditor(comparator, onComparatorChange)
        if (label.isNotEmpty()) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Box(modifier = Modifier.weight(1f)) { content() }
        if (suffix != null) {
            Text(suffix, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun PreviewComparator() {
    MaterialTheme {
        var v by remember { mutableStateOf(Comparator.Compare.IS_EQUAL) }
        ComparatorEditor(value = v, onValueChange = { v = it })
    }
}
