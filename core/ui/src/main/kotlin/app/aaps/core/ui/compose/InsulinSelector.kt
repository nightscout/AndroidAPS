package app.aaps.core.ui.compose

import app.aaps.core.keys.interfaces.TextRef
import androidx.compose.ui.res.stringResource
import app.aaps.core.ui.UiStrings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.aaps.core.data.model.ICfg
import app.aaps.core.ui.R

/**
 * Drop-down for picking one insulin configuration out of the catalogue.
 *
 * Shared by the insulin dialog (recording a pen dose) and the profile activation screen (the first-ever
 * switch, where nothing is in force yet and the user has to say which insulin the switch records).
 *
 * [selected] is shown by label and may be null, which renders an empty field — callers that require a
 * choice should keep their confirm action disabled until it is non-null rather than substituting one
 * themselves: the catalogue is a list to choose from, not a source of "the current one".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsulinSelector(
    insulins: List<ICfg>,
    selected: ICfg?,
    onSelect: (ICfg) -> Unit,
    modifier: Modifier = Modifier,
    label: TextRef = UiStrings.select_insulin
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selected?.insulinLabel ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            insulins.forEach { iCfg ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = iCfg.insulinLabel,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    onClick = {
                        onSelect(iCfg)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
