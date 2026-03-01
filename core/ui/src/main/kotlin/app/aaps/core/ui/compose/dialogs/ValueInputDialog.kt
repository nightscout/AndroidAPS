package app.aaps.core.ui.compose.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.R

import app.aaps.core.ui.compose.formatMinutesAsDuration
import java.text.DecimalFormat
import kotlin.math.roundToInt
import app.aaps.core.keys.R as KeysR

/**
 * Dialog for entering a numeric value directly.
 *
 * @param currentValue The current value to display
 * @param valueRange The allowed range for the value
 * @param step The step size for rounding
 * @param label Optional label for the input field
 * @param summary Optional summary/description text to show below the label
 * @param unitLabel Optional unit label to show after value
 * @param unitLabelResId Resource ID for unit label. When R.string.units_min, shows formatted preview
 * @param valueFormat Format for displaying/parsing the value
 * @param onValueConfirm Called when user confirms with a valid value
 * @param onDismiss Called when dialog is dismissed
 */
@Composable
fun ValueInputDialog(
    currentValue: Double,
    valueRange: ClosedFloatingPointRange<Double>,
    step: Double = 0.1,
    label: String? = null,
    summary: String? = null,
    unitLabel: String = "",
    unitLabelResId: Int = 0,
    valueFormat: DecimalFormat = DecimalFormat("0.0"),
    onValueConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val initialText = valueFormat.format(currentValue)
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(initialText, TextRange(0, initialText.length)))
    }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Check if this is minutes input for formatted preview
    val isMinutesUnit = unitLabelResId == KeysR.string.units_min

    fun validateAndParse(): Double? {
        val text = textFieldValue.text.replace(",", ".")
        return try {
            val parsed = text.toDouble()
            when {
                parsed < valueRange.start                                 -> {
                    isError = true
                    errorMessage = "Min: ${valueFormat.format(valueRange.start)}"
                    null
                }

                parsed > valueRange.endInclusive                          -> {
                    isError = true
                    errorMessage = "Max: ${valueFormat.format(valueRange.endInclusive)}"
                    null
                }

                isMinutesUnit && parsed != parsed.roundToInt().toDouble() -> {
                    isError = true
                    errorMessage = "Minutes must be whole numbers"
                    null
                }

                else                                                      -> {
                    isError = false
                    // Accept value as-is (no rounding to step)
                    parsed
                }
            }
        } catch (e: NumberFormatException) {
            isError = true
            errorMessage = "Invalid number"
            null
        }
    }

    fun confirm() {
        validateAndParse()?.let { value ->
            onValueConfirm(value)
            onDismiss()
        }
    }

    // Compute formatted preview for minutes
    val formattedPreview: String? = if (isMinutesUnit) {
        val minutes = textFieldValue.text.replace(",", ".").toDoubleOrNull()?.roundToInt()
        if (minutes != null && minutes >= 60) {
            "= ${formatMinutesAsDuration(minutes)}"
        } else null
    } else null

    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = label?.let { { Text(it) } },
        text = {
            Column {
                if (summary != null) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        // Clear error on input change
                        if (isError) {
                            isError = false
                        }
                    },
                    singleLine = true,
                    isError = isError,
                    supportingText = when {
                        isError                  -> {
                            { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
                        }

                        formattedPreview != null -> {
                            { Text(formattedPreview, color = MaterialTheme.colorScheme.primary) }
                        }

                        else                     -> null
                    },
                    suffix = if (unitLabel.isNotEmpty()) {
                        { Text(unitLabel) }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { confirm() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                Text(
                    text = "${valueFormat.format(valueRange.start)} - ${valueFormat.format(valueRange.endInclusive)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { confirm() }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun ValueInputDialogPreview() {
    MaterialTheme {
        ValueInputDialog(
            currentValue = 5.0,
            valueRange = 0.0..10.0,
            step = 0.5,
            label = "Insulin",
            unitLabel = "U",
            onValueConfirm = {},
            onDismiss = {}
        )
    }
}
