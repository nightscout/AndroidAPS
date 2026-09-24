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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.formatMinutesAsDuration
import app.aaps.core.ui.compose.stringResource
import kotlin.math.roundToInt

/**
 * Dialog for entering a numeric value directly.
 *
 * @param currentValue The current value to display
 * @param valueRange The allowed range for the value
 * @param step The step size for rounding
 * @param label Optional label for the input field
 * @param summary Optional summary/description text to show below the label
 * @param unitLabel Optional unit label to show after value
 * @param asDuration Show a "= Xh Ym" preview under the field
 * @param valueFormat Format for displaying/parsing the value
 * @param onValueConfirm Called when user confirms with a valid value
 * @param onDismiss Called when dialog is dismissed
 *
 * @see ValueInputDialogPreview
 */
@Composable
fun ValueInputDialog(
    currentValue: Double,
    valueRange: ClosedFloatingPointRange<Double>,
    step: Double = 0.1,
    label: String? = null,
    summary: String? = null,
    unitLabel: TextRef? = null,
    asDuration: Boolean = false,
    valueFormat: NumberFormat = NumberFormat.DECIMAL_1,
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
    val resolvedUnitLabel = unitLabel?.let { stringResource(it) } ?: ""

    // The allowed range, shown under the field while the entry is good. The same line carries the
    // error message when the entry is bad - see the comment on that Text below.
    val rangeText = "${valueFormat.format(valueRange.start)} - ${valueFormat.format(valueRange.endInclusive)}"

    // Resolved here because validateAndParse() is not a composable and cannot call stringResource.
    val errorInvalidNumber = stringResource(CoreUiStrings.invalid_number)
    val errorOutOfRange = stringResource(InterfacesStrings.confirmation_line, stringResource(CoreUiStrings.error), rangeText)

    // The three messages below are still hardcoded English: no matching string resource exists yet.
    fun validateAndParse(): Double? {
        val text = textFieldValue.text.replace(",", ".")
        return try {
            val parsed = text.toDouble()
            when {
                parsed < valueRange.start                              -> {
                    isError = true
                    // Carries the whole range, so the hint the user needs is not replaced by the
                    // error - the supporting line below shows one or the other, not both. Also
                    // English-only before this, on a field that takes insulin and carb amounts.
                    errorMessage = errorOutOfRange
                    null
                }

                parsed > valueRange.endInclusive                       -> {
                    isError = true
                    errorMessage = errorOutOfRange
                    null
                }

                asDuration && parsed != parsed.roundToInt().toDouble() -> {
                    isError = true
                    // "Minutes must be whole numbers" would say it better, but no such string
                    // exists and an untranslated English sentence helps fewer people than a
                    // translated general one. Worth a dedicated string later.
                    errorMessage = errorInvalidNumber
                    null
                }

                else                                                   -> {
                    isError = false
                    // Accept value as-is (no rounding to step)
                    parsed
                }
            }
        } catch (e: NumberFormatException) {
            isError = true
            errorMessage = errorInvalidNumber
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
    val formattedPreview: String? = if (asDuration) {
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
                    // Only the duration preview lives in this slot now. The error used to be here,
                    // but this slot has no node at all while the entry is good, so the error line
                    // was a node that APPEARED - and Compose never announces a node that appeared
                    // (sendSemanticsPropertyChangeEvents skips any node with no previous entry).
                    // The error moved to the range line below, which is always in the tree.
                    supportingText = formattedPreview?.let { preview ->
                        { Text(preview, color = MaterialTheme.colorScheme.primary) }
                    },
                    suffix = if (resolvedUnitLabel.isNotEmpty()) {
                        { Text(resolvedUnitLabel) }
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

                // This Text is composed whether or not there is an error, so only its content
                // CHANGES - that is the case a live region can announce. A rejected value is
                // something the user must not miss, so the region is Assertive and interrupts.
                //
                // The live region is set only while the error is shown. Adding it together with the
                // new text still announces (the property change is what sends the event, and the
                // node already carries the region by the time the event is read), while dropping it
                // keeps the switch back to the plain range quiet - otherwise the range would be read
                // out over the user's own typing as soon as the first key cleared the error.
                Text(
                    text = if (isError) errorMessage else rangeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .semantics { if (isError) liveRegion = LiveRegionMode.Assertive }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { confirm() }) {
                Text(stringResource(CoreUiStrings.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CoreUiStrings.cancel))
            }
        }
    )
}
