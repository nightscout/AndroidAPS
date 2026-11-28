package app.aaps.ui.compose.foodManagement

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * A text field that proposes the values already used, so the same group is not typed twice in two
 * ways. The list shows the values that contain what is typed. A new text makes a new value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SuggestionField(value: String, onValueChange: (String) -> Unit, label: String, suggestions: List<String>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    // True once the user types in the field. A value set from outside (when an existing food is
    // opened) is not a search: it must not hide the other choices.
    var typedByUser by remember { mutableStateOf(false) }
    val typed = value.trim()
    // A value already chosen or filled in shows every choice, so another one can be picked without
    // clearing it. While a new text is typed, only the values that contain it are shown.
    val filtering = typedByUser && typed.isNotEmpty() && suggestions.none { it.equals(typed, ignoreCase = true) }
    val shown = if (filtering) suggestions.filter { it.contains(typed, ignoreCase = true) } else suggestions
    val open = expanded && shown.isNotEmpty()
    // True while the list was opened with the arrow. ExposedDropdownMenuBox gives the focus to the
    // text field each time the list opens, and the focus brings the keyboard, which hides the list.
    // So the field cannot take the focus until the list is closed. A tap on the text still gives the
    // focus, to type a new value.
    var openedByArrow by remember { mutableStateOf(false) }
    LaunchedEffect(open) { if (!open) openedByArrow = false }
    val focusManager = LocalFocusManager.current
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { expanded = it }, modifier = modifier) {
        CompactTextField(
            value = value,
            onValueChange = { onValueChange(it); typedByUser = true; expanded = true },
            label = label,
            // No arrow when there is nothing to propose: the label keeps the whole width.
            trailingIcon = if (suggestions.isEmpty()) null else {
                {
                    // The whole touch area opens the list, not only the small arrow: a tap next to
                    // the arrow must not go to the text and bring the keyboard.
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    openedByArrow = true
                                    focusManager.clearFocus()
                                }
                            }
                            .menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable)
                    ) {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = open)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusProperties { canFocus = !openedByArrow }
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { expanded = false }) {
            shown.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = { onValueChange(item); typedByUser = false; expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
