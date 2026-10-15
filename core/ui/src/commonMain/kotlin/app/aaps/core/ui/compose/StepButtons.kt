package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings

/**
 * The +/- buttons of [NumberInputRow] on their own, for a value that can be stepped but not typed.
 * Same step, range, hold-repeat and spoken names as the full row, so the two feel like one control.
 *
 * ```
 * (-) (+)
 * ```
 *
 * Emits the two buttons into the caller's row, so it takes no modifier of its own.
 *
 * @param label the field name, spoken with the step ("increment Correction by 0.05 U")
 * @param value current value
 * @param onValueChange called with the new value after a tap or a repeat
 * @param valueRange the value never leaves this range; the button at the edge is disabled
 * @param step amount of one tap
 * @param unitLabel unit of the step, spoken after it
 * @param decimalPlaces decimals of the spoken step
 * @param enabled false disables both buttons
 *
 * @see StepButtonsPreview
 */
@Composable
fun StepButtons(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    valueRange: ClosedFloatingPointRange<Double>,
    step: Double,
    unitLabel: TextRef? = null,
    decimalPlaces: Int = 0,
    enabled: Boolean = true,
) {
    val stepFormat = remember(decimalPlaces) { NumberFormat.withDecimals(decimalPlaces) }
    val stepNumber = stepFormat.format(step)
    val resolvedUnitLabel = unitLabel?.let { stringResource(it) } ?: ""
    val stepText =
        if (resolvedUnitLabel.isNotEmpty()) stringResource(CoreUiStrings.value_with_unit, stepNumber, resolvedUnitLabel)
        else stepNumber

    fun stepValue(direction: Int) {
        onValueChange(roundToStep(value + direction * step, step).coerceIn(valueRange))
    }

    RepeatingIconButton(
        onClick = { stepValue(-1) },
        enabled = enabled && value > valueRange.start
    ) {
        Icon(
            imageVector = Icons.Filled.Remove,
            contentDescription = stringResource(CoreUiStrings.a11y_min_button_description, label, stepText),
            modifier = Modifier.size(20.dp)
        )
    }
    RepeatingIconButton(
        onClick = { stepValue(1) },
        enabled = enabled && value < valueRange.endInclusive
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(CoreUiStrings.a11y_plus_button_description, label, stepText),
            modifier = Modifier.size(20.dp)
        )
    }
}
