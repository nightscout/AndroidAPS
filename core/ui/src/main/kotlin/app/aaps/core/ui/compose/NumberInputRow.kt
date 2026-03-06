package app.aaps.core.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.dialogs.ValueInputDialog
import java.text.DecimalFormat
import app.aaps.core.keys.R as KeysR

/**
 * Composable that displays a numeric input with label, current value, and Material 3 slider.
 * Used for inputting numeric parameters with optional unit display and formatting.
 *
 * **Layout:**
 * ```
 * Label                    2h 10m
 * ──────○────────────────
 * ```
 *
 * The component displays:
 * - Top row: Label (left, labelLarge) and current value with unit (right, titleMedium bold in primary color, clickable)
 * - Bottom: Material 3 Slider with +/- buttons
 * - Clicking on value opens a dialog for direct input
 * - Special formatting for minutes: when unitLabelResId is units_min and value >= 60, displays as "Xh Ym"
 *
 * @param labelResId Resource ID for the display label
 * @param value Current numeric value
 * @param onValueChange Callback invoked when slider value changes, receives new value as Double
 * @param valueRange The range of values the slider can represent
 * @param step Step increment for slider (determines number of discrete positions)
 * @param controlPoints Pairs of (position [0-1], value) to create a non linear slider, if null slider is linear
 * @param unitLabelResId Resource ID for unit label (e.g., R.string.units_min, R.string.units_percent)
 * @param unitLabel Resolved unit label string (used when unitLabelResId is 0)
 * @param valueFormatResId Resource ID for formatting value with unit (e.g., "%1$.1f U")
 * @param formatAsInt If true, value is formatted as Int for stringResource (use with %d format strings)
 * @param valueFormat Custom DecimalFormat (overrides auto-created from decimalPlaces)
 * @param decimalPlaces Number of decimal places for value display (0 = integer, default). Ignored if valueFormat is set.
 * @param dialogLabelResId Resource ID for dialog title (defaults to labelResId when 0)
 * @param dialogSummary Summary/description for the input dialog
 * @param modifier Modifier for the root Column container
 */
@Composable
fun NumberInputRow(
    labelResId: Int,
    value: Double,
    onValueChange: (Double) -> Unit,
    valueRange: ClosedFloatingPointRange<Double>,
    step: Double,
    controlPoints: List<Pair<Double, Double>>? = null,
    unitLabelResId: Int = 0,
    unitLabel: String = "",
    valueFormatResId: Int? = null,
    formatAsInt: Boolean = false,
    valueFormat: DecimalFormat? = null,
    decimalPlaces: Int = 0,
    dialogLabelResId: Int = 0,
    dialogSummary: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val effectiveValueFormat = valueFormat ?: remember(decimalPlaces) {
        if (decimalPlaces == 0) DecimalFormat("0")
        else DecimalFormat("0.${"0".repeat(decimalPlaces)}")
    }

    // Resolve labels
    val label = stringResource(labelResId)
    val dialogLabel = if (dialogLabelResId != 0) stringResource(dialogLabelResId) else label

    // Resolve unit label string
    val resolvedUnitLabel = when {
        unitLabelResId != 0 -> stringResource(unitLabelResId)
        unitLabel.isNotEmpty() -> unitLabel
        else -> ""
    }

    // Format the displayed value using shared function
    val displayText = formatSliderDisplayValue(
        value = value,
        unitLabelResId = unitLabelResId,
        valueFormatResId = valueFormatResId,
        formatAsInt = formatAsInt,
        valueFormat = effectiveValueFormat,
        unitLabel = unitLabel
    )

    val contentAlpha = if (enabled) 1f else 0.38f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
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
            label = dialogLabel,
            summary = dialogSummary,
            unitLabel = resolvedUnitLabel,
            unitLabelResId = unitLabelResId,
            valueFormat = effectiveValueFormat,
            onValueConfirm = onValueChange,
            onDismiss = { showDialog = false }
        )
    }
}

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
