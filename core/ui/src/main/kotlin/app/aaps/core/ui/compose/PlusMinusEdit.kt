package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat
import kotlin.math.roundToInt

/**
 * Compact `[ − ] [ editable value ] [ + ]` stepper for inline numeric editing.
 *
 * The TextField commits its value on focus loss or IME Done — any action button on the
 * same screen that depends on the value must call `focusManager.clearFocus()` first to
 * flush pending text. Long-pressing +/- auto-repeats; range boundaries disable the
 * corresponding button.
 *
 * @param value Current numeric value
 * @param onValueChange Called with the new value (already coerced into [valueRange])
 * @param valueRange Allowed value range; values outside are clamped on commit
 * @param step Step size for +/- buttons
 * @param valueFormat Format for the displayed text
 * @param unitLabel Resolved unit label (used when [unitLabelResId] is 0)
 * @param unitLabelResId Resource ID for the unit label, shown as the field's trailing icon
 * @param enabled Whether the stepper is interactive
 * @param modifier Modifier for the row container
 */
@Composable
fun PlusMinusEdit(
    value: Double,
    onValueChange: (Double) -> Unit,
    valueRange: ClosedFloatingPointRange<Double>,
    step: Double,
    valueFormat: DecimalFormat = DecimalFormat("0.0"),
    unitLabel: String = "",
    unitLabelResId: Int = 0,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val minValue = valueRange.start
    val maxValue = valueRange.endInclusive

    var isFocused by remember { mutableStateOf(false) }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(valueFormat.format(value)))
    }
    var isError by remember { mutableStateOf(false) }

    // Sync external value → text when not focused (e.g., +/- buttons or external update).
    LaunchedEffect(value) {
        if (!isFocused) {
            textFieldValue = TextFieldValue(valueFormat.format(value))
            isError = false
        }
    }

    val resolvedUnitLabel = when {
        unitLabelResId != 0    -> stringResource(unitLabelResId)
        unitLabel.isNotEmpty() -> unitLabel
        else                   -> ""
    }

    fun validateAndCommit(text: String) {
        val cleaned = text.trim().replace(",", ".")
        val parsed = cleaned.toDoubleOrNull()
        if (parsed == null) {
            isError = true
        } else {
            isError = false
            onValueChange(parsed.coerceIn(minValue, maxValue))
        }
    }

    // Flush pending text when leaving composition (back press without tapping Done).
    val latestTextProvider by rememberUpdatedState({ textFieldValue.text })
    val latestIsFocused by rememberUpdatedState(isFocused)
    DisposableEffect(Unit) {
        onDispose {
            if (latestIsFocused) validateAndCommit(latestTextProvider())
        }
    }

    fun stepValue(direction: Int) {
        val newValue = roundToStep(value + direction * step, step).coerceIn(minValue, maxValue)
        textFieldValue = TextFieldValue(valueFormat.format(newValue))
        isError = false
        onValueChange(newValue)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RepeatingIconButton(
            onClick = { stepValue(-1) },
            enabled = enabled && value > minValue,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "-",
                modifier = Modifier.size(16.dp)
            )
        }

        TextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                if (isError) isError = false
            },
            singleLine = true,
            enabled = enabled,
            isError = isError,
            trailingIcon = if (resolvedUnitLabel.isNotEmpty()) {
                { Text(resolvedUnitLabel) }
            } else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (step != step.roundToInt().toDouble())
                    KeyboardType.Decimal else KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    validateAndCommit(textFieldValue.text)
                    focusManager.clearFocus()
                }
            ),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    if (isFocused && !focusState.isFocused) {
                        validateAndCommit(textFieldValue.text)
                    }
                    if (!isFocused && focusState.isFocused) {
                        textFieldValue = textFieldValue.copy(
                            selection = TextRange(0, textFieldValue.text.length)
                        )
                    }
                    isFocused = focusState.isFocused
                }
        )

        RepeatingIconButton(
            onClick = { stepValue(1) },
            enabled = enabled && value < maxValue,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "+",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
