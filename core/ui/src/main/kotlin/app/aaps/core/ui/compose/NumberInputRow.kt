package app.aaps.core.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.dialogs.ValueInputDialog
import java.text.DecimalFormat
import kotlin.math.roundToInt
import app.aaps.core.keys.R as KeysR

/**
 * Composable that displays a numeric input with label, current value, and optional slider or direct text input.
 *
 * **Slider layout** (`useSlider = true`):
 * ```
 * Label                    2h 10m
 * ──────○────────────────
 * ```
 *
 * **Direct input layout** (`useSlider = false`, default):
 * ```
 * Label                    2h 10m       ← only when formatted differs from raw (e.g., duration)
 * ┌─────────────────────────────┐
 * │  (-)       130          (+) │
 * └─────────────────────────────┘
 * 0 — 300                               ← range, or error message
 * ```
 *
 * @param labelResId Resource ID for the display label
 * @param value Current numeric value
 * @param onValueChange Callback invoked when value changes, receives new value as Double
 * @param valueRange The range of values the input can represent
 * @param step Step increment for +/- buttons and slider
 * @param useSlider When true, uses the slider layout; when false (default), uses direct text input
 * @param controlPoints Pairs of (position [0-1], value) to create a non-linear slider (slider mode only)
 * @param unitLabelResId Resource ID for unit label (e.g., R.string.units_min, R.string.units_percent)
 * @param unitLabel Resolved unit label string (used when unitLabelResId is 0)
 * @param valueFormatResId Resource ID for formatting value with unit (e.g., "%1$.1f U")
 * @param formatAsInt If true, value is formatted as Int for stringResource (use with %d format strings)
 * @param valueFormat Custom DecimalFormat (overrides auto-created from decimalPlaces)
 * @param decimalPlaces Number of decimal places for value display (0 = integer, default). Ignored if valueFormat is set.
 * @param enabled Whether the input is interactive
 * @param modifier Modifier for the root Column container
 */
@Composable
fun NumberInputRow(
    labelResId: Int,
    value: Double,
    onValueChange: (Double) -> Unit,
    valueRange: ClosedFloatingPointRange<Double>,
    step: Double,
    modifier: Modifier = Modifier,
    useSlider: Boolean = false,
    controlPoints: List<Pair<Double, Double>>? = null,
    unitLabelResId: Int = 0,
    unitLabel: String = "",
    valueFormatResId: Int? = null,
    formatAsInt: Boolean = false,
    valueFormat: DecimalFormat? = null,
    decimalPlaces: Int = 0,
    enabled: Boolean = true,
) {
    val effectiveValueFormat = valueFormat ?: remember(decimalPlaces) {
        if (decimalPlaces == 0) DecimalFormat("0")
        else DecimalFormat("0.${"0".repeat(decimalPlaces)}")
    }

    if (useSlider) {
        SliderNumberInputRow(
            labelResId = labelResId,
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            step = step,
            controlPoints = controlPoints,
            unitLabelResId = unitLabelResId,
            unitLabel = unitLabel,
            valueFormatResId = valueFormatResId,
            formatAsInt = formatAsInt,
            valueFormat = effectiveValueFormat,
            enabled = enabled,
            modifier = modifier
        )
    } else {
        DirectNumberInputRow(
            labelResId = labelResId,
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            step = step,
            unitLabelResId = unitLabelResId,
            unitLabel = unitLabel,
            valueFormatResId = valueFormatResId,
            formatAsInt = formatAsInt,
            valueFormat = effectiveValueFormat,
            enabled = enabled,
            modifier = modifier
        )
    }
}

/**
 * Original slider-based layout: label + value on top, slider with +/- buttons below.
 */
@Composable
private fun SliderNumberInputRow(
    labelResId: Int,
    value: Double,
    onValueChange: (Double) -> Unit,
    valueRange: ClosedFloatingPointRange<Double>,
    step: Double,
    controlPoints: List<Pair<Double, Double>>?,
    unitLabelResId: Int,
    unitLabel: String,
    valueFormatResId: Int?,
    formatAsInt: Boolean,
    valueFormat: DecimalFormat,
    enabled: Boolean,
    modifier: Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val label = stringResource(labelResId)

    val resolvedUnitLabel = when {
        unitLabelResId != 0  -> stringResource(unitLabelResId)
        unitLabel.isNotEmpty() -> unitLabel
        else                   -> ""
    }

    val displayText = formatSliderDisplayValue(
        value = value,
        unitLabelResId = unitLabelResId,
        valueFormatResId = valueFormatResId,
        formatAsInt = formatAsInt,
        valueFormat = valueFormat,
        unitLabel = unitLabel
    )

    val contentAlpha = if (enabled) 1f else 0.38f

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            Text(
                text = displayText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                modifier = if (enabled) Modifier.clickable { showDialog = true } else Modifier
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        SliderWithButtons(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            step = step,
            controlPoints = controlPoints,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showDialog && enabled) {
        ValueInputDialog(
            currentValue = value,
            valueRange = valueRange,
            step = step,
            label = label,
            unitLabel = resolvedUnitLabel,
            unitLabelResId = unitLabelResId,
            valueFormat = valueFormat,
            onValueConfirm = onValueChange,
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * Direct text input layout: OutlinedTextField with +/- icon buttons inside,
 * optional formatted display above (when different from raw value),
 * and range or error as supporting text.
 */
@Composable
private fun DirectNumberInputRow(
    labelResId: Int,
    value: Double,
    onValueChange: (Double) -> Unit,
    valueRange: ClosedFloatingPointRange<Double>,
    step: Double,
    unitLabelResId: Int,
    unitLabel: String,
    valueFormatResId: Int?,
    formatAsInt: Boolean,
    valueFormat: DecimalFormat,
    enabled: Boolean,
    modifier: Modifier
) {
    val focusManager = LocalFocusManager.current
    val label = stringResource(labelResId)
    // Track whether the field is focused for editing
    var isFocused by remember { mutableStateOf(false) }
    var textFieldValue by remember(value) {
        mutableStateOf(TextFieldValue(if (value == 0.0) "" else valueFormat.format(value)))
    }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Sync text field when value changes externally (e.g., +/- buttons) and not focused
    LaunchedEffect(value) {
        if (!isFocused) {
            val text = if (value == 0.0) "" else valueFormat.format(value)
            textFieldValue = TextFieldValue(text)
            isError = false
        }
    }

    // Formatted display text for special cases (duration)
    val formattedDisplay = formatSliderDisplayValue(
        value = value,
        unitLabelResId = unitLabelResId,
        valueFormatResId = valueFormatResId,
        formatAsInt = formatAsInt,
        valueFormat = valueFormat,
        unitLabel = unitLabel
    )
    // Only show formatted display when it differs meaningfully from the raw number
    val rawDisplay = valueFormat.format(value)
    val showFormattedDisplay = formattedDisplay != rawDisplay &&
        formattedDisplay != "$rawDisplay $unitLabel".trim()

    // Range text
    val rangeText = "${valueFormat.format(valueRange.start)} — ${valueFormat.format(valueRange.endInclusive)}"

    // Pre-resolve error strings for use in non-composable validateAndCommit
    val errorInvalidNumber = stringResource(R.string.invalid_number)

    fun validateAndCommit(text: String) {
        val cleaned = text.trim().replace(",", ".")
        val parsed = if (cleaned.isEmpty()) 0.0 else cleaned.toDoubleOrNull()
        if (parsed == null) {
            isError = true
            errorMessage = errorInvalidNumber
        } else {
            isError = false
            onValueChange(parsed.coerceIn(valueRange))
        }
    }

    fun stepValue(direction: Int) {
        val newValue = roundToStep(value + direction * step, step)
            .coerceIn(valueRange)
        onValueChange(newValue)
    }

    val resolvedUnitLabel = when {
        unitLabelResId != 0    -> stringResource(unitLabelResId)
        unitLabel.isNotEmpty() -> unitLabel
        else                   -> ""
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        // TextField + range text below it
        Column(modifier = Modifier.weight(1f)) {
            TextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    textFieldValue = newValue
                    if (isError) isError = false
                },
                label = { Text(label) },
                singleLine = true,
                enabled = enabled,
                isError = isError,
                suffix = if (resolvedUnitLabel.isNotEmpty()) {
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
                    .fillMaxWidth()
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
            // Range and formatted display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isError) errorMessage else rangeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (showFormattedDisplay && !isError) {
                    Text(
                        text = formattedDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // +/- filled buttons with long-press repeat
        RepeatingIconButton(
            onClick = { stepValue(-1) },
            enabled = enabled && value > valueRange.start
        ) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = "Decrease",
                modifier = Modifier.size(20.dp)
            )
        }
        RepeatingIconButton(
            onClick = { stepValue(1) },
            enabled = enabled && value < valueRange.endInclusive
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Increase",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// --- Previews ---

@Preview(showBackground = true)
@Composable
private fun NumberInputRowBasicPreview() {
    MaterialTheme {
        NumberInputRow(
            labelResId = app.aaps.core.ui.R.string.carbs,
            value = 20.0,
            onValueChange = {},
            valueRange = 0.0..100.0,
            step = 1.0
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NumberInputRowWithUnitPreview() {
    MaterialTheme {
        NumberInputRow(
            labelResId = app.aaps.core.ui.R.string.insulin_label,
            value = 3.5,
            onValueChange = {},
            valueRange = 0.0..10.0,
            step = 0.1,
            decimalPlaces = 1,
            unitLabel = "U"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NumberInputRowMinutesPreview() {
    MaterialTheme {
        NumberInputRow(
            labelResId = app.aaps.core.ui.R.string.duration,
            value = 130.0,
            onValueChange = {},
            valueRange = 0.0..300.0,
            step = 10.0,
            unitLabelResId = KeysR.string.units_min
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NumberInputRowPercentPreview() {
    MaterialTheme {
        NumberInputRow(
            labelResId = app.aaps.core.ui.R.string.duration,
            value = 100.0,
            onValueChange = {},
            valueRange = 10.0..200.0,
            step = 5.0,
            unitLabelResId = KeysR.string.units_percent
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NumberInputRowSliderPreview() {
    MaterialTheme {
        NumberInputRow(
            labelResId = app.aaps.core.ui.R.string.carbs,
            value = 20.0,
            onValueChange = {},
            valueRange = 0.0..100.0,
            step = 1.0,
            useSlider = true
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NumberInputRowMinutesDirectPreview() {
    MaterialTheme {
        NumberInputRow(
            labelResId = app.aaps.core.ui.R.string.duration,
            value = 130.0,
            onValueChange = {},
            valueRange = 0.0..300.0,
            step = 10.0,
            unitLabelResId = KeysR.string.units_min
        )
    }
}
