package app.aaps.core.ui.compose.preference

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.StringValidator
import app.aaps.core.ui.compose.AapsSpacing

/**
 * Inline text field for string preferences — for use in wizards and forms
 * where the input should be directly visible (not behind a dialog).
 *
 * Applies the key's [StringValidator] automatically with error display.
 */
@Composable
fun InlineStringPreferenceItem(
    stringKey: StringPreferenceKey,
    titleResId: Int = 0
) {
    val effectiveTitleResId = if (titleResId != 0) titleResId else stringKey.titleResId
    val state = rememberPreferenceStringState(stringKey)
    val validator = stringKey.validator
    val text = state.value

    val validationResult = remember(text) {
        if (text.isEmpty()) StringValidator.ValidationResult.VALID
        else validator.validate(text)
    }

    OutlinedTextField(
        value = text,
        onValueChange = { newValue ->
            state.value = newValue
        },
        label = if (effectiveTitleResId != 0) {
            { Text(stringResource(effectiveTitleResId)) }
        } else null,
        isError = !validationResult.isValid,
        supportingText = if (!validationResult.isValid) {
            { Text(validationResult.errorMessage ?: "") }
        } else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Inline radio button group for string list preferences — for use in wizards and forms
 * where all options should be directly visible (not behind a dialog).
 *
 * Uses the same preference state management as [AdaptiveStringListPreferenceItem].
 */
@Composable
fun InlineStringListPreferenceItem(
    stringKey: StringPreferenceKey,
    titleResId: Int = 0,
    entries: Map<String, String>
) {
    val effectiveTitleResId = if (titleResId != 0) titleResId else stringKey.titleResId
    val state = rememberPreferenceStringState(stringKey)
    val selectedValue = state.value

    if (effectiveTitleResId != 0) {
        Text(
            text = stringResource(effectiveTitleResId),
            style = MaterialTheme.typography.titleMedium
        )
    }

    Column(modifier = Modifier.selectableGroup()) {
        entries.forEach { (value, label) ->
            val isSelected = selectedValue == value
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = isSelected,
                        onClick = { state.value = value },
                        role = Role.RadioButton
                    )
                    .padding(vertical = AapsSpacing.small)
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = null
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = AapsSpacing.medium)
                )
            }
        }
    }
}
