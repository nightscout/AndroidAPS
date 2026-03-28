package app.aaps.core.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.dialogs.ValueInputDialog
import kotlinx.coroutines.delay
import java.text.DecimalFormat
import kotlin.math.roundToInt
import app.aaps.core.keys.R as KeysR

/**
 * A Slider with +/- buttons on each side for fine-grained value control.
 * Optionally displays a clickable value label that opens a dialog for direct input.
 *
 * @param value Current value
 * @param onValueChange Called when value changes
 * @param valueRange The range of values the slider can represent
 * @param step The step size for +/- buttons (default 0.1)
 * @param controlPoints Pairs of (position [0-1], value) to create a non linear slider, if null slider is linear
 * @param showValue Whether to show a clickable value label (default false)
 * @param valueFormatResId Resource ID for formatting value with unit (e.g., "%1$.1f U" or "%1$d min")
 * @param formatAsInt If true, value is formatted as Int for stringResource (use with %d format strings)
 * @param valueFormat Format for the value (used for dialog and fallback)
 * @param unitLabel Unit label for dialog input suffix (deprecated, use unitLabelResId)
 * @param unitLabelResId Resource ID for unit label. When R.string.units_min, auto-formats as "Xh Ym"
 * @param dialogLabel Label for the input dialog
 * @param dialogSummary Summary/description for the input dialog
 * @param modifier Modifier for the Row container
 */
@Composable
fun SliderWithButtons(
    value: Double,
    onValueChange: (Double) -> Unit,
    valueRange: ClosedFloatingPointRange<Double>,
    step: Double = 0.1,
    controlPoints: List<Pair<Double, Double>>? = null,
    showValue: Boolean = false,
    valueFormatResId: Int? = null,
    formatAsInt: Boolean = false,
    valueFormat: DecimalFormat = DecimalFormat("0.0"),
    unitLabel: String = "",
    unitLabelResId: Int = 0,
    dialogLabel: String? = null,
    dialogSummary: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val minValue = valueRange.start
    val maxValue = valueRange.endInclusive
    var showDialog by remember { mutableStateOf(false) }

    // Normalise ControlPoints to ensure % and values are consistent with min & maxValue
    val normalizedControlPoints by remember(controlPoints, minValue, maxValue) {
        mutableStateOf(
            if (controlPoints != null) {
                // check controlPoints are valid
                require(controlPoints.size >= 2) { "At least 2 control points required" }
                require(controlPoints.first().first == 0.0) { "First point must have 0.0 position" }
                require(controlPoints.last().first == 1.0) { "Last point must have 1.0 position" }
                require(controlPoints.map { it.first }.zipWithNext { a, b -> a < b }.all { it }) {
                    "Control points must be in increasing order"
                }
                controlPoints
            } else {
                // linear ControlPoints by default
                listOf(0.0 to minValue, 1.0 to maxValue)
            }
        )
    }

    // Calculate slider position [0.1] from targetValue
    fun valueToPosition(targetValue: Double): Float {
        val clampedValue = targetValue.coerceIn(minValue, maxValue)

        // find the right segment
        for (i in 0 until normalizedControlPoints.size - 1) {
            val (startPos, startValue) = normalizedControlPoints[i]
            val (endPos, endValue) = normalizedControlPoints[i + 1]

            if (clampedValue in startValue..endValue || clampedValue in endValue..startValue) {
                // linear interpolation between the two points
                if (endValue == startValue) return startPos.toFloat()

                val segmentRatio = (clampedValue - startValue) / (endValue - startValue)
                val position = startPos + segmentRatio * (endPos - startPos)
                return position.toFloat()
            }
        }

        // Fallback: linear conversion (should never occur with valid points)
        return ((clampedValue - minValue) / (maxValue - minValue)).toFloat()
    }

    // Convert slider position [0-1] to targetValue
    fun positionToValue(position: Float): Double {
        val clampedPos = position.coerceIn(0f, 1f)
        // find slider segment
        for (i in 0 until normalizedControlPoints.size - 1) {
            val (startPos, startValue) = normalizedControlPoints[i]
            val (endPos, endValue) = normalizedControlPoints[i + 1]

            if (clampedPos in startPos.toFloat()..endPos.toFloat()) {
                // Linear interpolation within segment
                if (endPos == startPos) return startValue

                val segmentRatio = (clampedPos - startPos) / (endPos - startPos)
                return startValue + segmentRatio * (endValue - startValue)
            }
        }
        // Fallback: linear conversion (should never occur with valid points)
        return minValue + clampedPos * (maxValue - minValue)
    }

    // Calculate current position [0-1]
    val currentPosition = valueToPosition(value)
    val currentValue = value.coerceIn(minValue, maxValue)
    val posForCurrent = valueToPosition(currentValue)
    val posForCurrentPlusStep = valueToPosition((currentValue + step).coerceAtMost(maxValue))
    val posForCurrentMinusStep = valueToPosition((currentValue - step).coerceAtLeast(minValue))
    val dynamicStepPosUp = (posForCurrentPlusStep - posForCurrent).coerceAtLeast(0.001f)
    val dynamicStepPosDown = (posForCurrent - posForCurrentMinusStep).coerceAtLeast(0.001f)

    // Check if this is minutes input for special formatting
    val isMinutesUnit = unitLabelResId == KeysR.string.units_min
    val resolvedUnitLabel = when {
        unitLabelResId != 0 -> stringResource(unitLabelResId)
        unitLabel.isNotEmpty() -> unitLabel
        else -> ""
    }

    // Use shared formatting function for display text
    val displayText = if (showValue) formatSliderDisplayValue(
        value = value,
        unitLabelResId = unitLabelResId,
        valueFormatResId = valueFormatResId,
        formatAsInt = formatAsInt,
        valueFormat = valueFormat,
        unitLabel = unitLabel
    ) else ""

    BoxWithConstraints(modifier = modifier) {
        val showSlider = maxWidth >= 180.dp

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Minus button
            RepeatingIconButton(
                onClick = {
                    val newPos = (currentPosition - dynamicStepPosDown).coerceAtLeast(0f)
                    val newValue = positionToValue(newPos)
                    val roundedValue = roundToStep(newValue, step).coerceIn(minValue, maxValue)
                    onValueChange(roundedValue)
                },
                enabled = enabled && value > minValue,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "-",
                    modifier = Modifier.size(16.dp)
                )
            }

            if (showSlider) {
                // Non-Linear Slider
                Slider(
                    value = currentPosition,
                    onValueChange = { newPos ->
                        val newValue = positionToValue(newPos)
                        val rounded = roundToStep(newValue, step)
                        onValueChange(rounded.coerceIn(minValue, maxValue))
                    },
                    enabled = enabled,
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // Plus button
            RepeatingIconButton(
                onClick = {
                    val newPos = (currentPosition + dynamicStepPosUp).coerceAtMost(1f)
                    val newValue = positionToValue(newPos)
                    val roundedValue = roundToStep(newValue, step).coerceIn(minValue, maxValue)
                    onValueChange(roundedValue)
                },
                enabled = enabled && value < maxValue,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "+",
                    modifier = Modifier.size(16.dp)
                )
            }

            // Optional clickable value label
            if (showValue) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .widthIn(min = if (isMinutesUnit || valueFormatResId != null || resolvedUnitLabel.isNotEmpty()) 70.dp else 40.dp)
                        .then(if (enabled) Modifier.clickable { showDialog = true } else Modifier)
                        .padding(start = 4.dp)
                )
            }
        }
    }

    // Value input dialog
    if (showDialog && enabled) {
        ValueInputDialog(
            currentValue = value,
            valueRange = valueRange,
            step = step,
            label = dialogLabel,
            summary = dialogSummary,
            unitLabel = resolvedUnitLabel,
            unitLabelResId = unitLabelResId,
            valueFormat = valueFormat,
            onValueConfirm = onValueChange,
            onDismiss = { showDialog = false }
        )
    }
}

internal fun roundToStep(value: Double, step: Double): Double {
    val scaled = (value / step).roundToInt() * step
    // Fix floating point precision errors (e.g., 6.1000000000005 -> 6.1)
    val decimals = step.toString().substringAfter('.', "").length
    val factor = Math.pow(10.0, decimals.toDouble())
    return Math.round(scaled * factor) / factor
}

/**
 * An icon button that repeats its onClick action while being held down.
 * Speed increases progressively the longer the button is held.
 */
@Composable
internal fun RepeatingIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    initialDelayMs: Long = 500L,
    maxDelayMs: Long = 200L,
    minDelayMs: Long = 50L,
    accelerationFactor: Float = 0.8f,
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isPressed, enabled) {
        if (isPressed && enabled) {
            delay(initialDelayMs)
            var currentDelay = maxDelayMs.toFloat()
            while (isPressed && enabled) {
                currentOnClick()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                delay(currentDelay.toLong())
                currentDelay = (currentDelay * accelerationFactor).coerceAtLeast(minDelayMs.toFloat())
            }
        }
    }

    FilledTonalIconButton(
        onClick = {
            onClick()
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        },
        enabled = enabled,
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                isPressed = true
                waitForUpOrCancellation()
                isPressed = false
            }
        }
    ) {
        content()
    }
}

@Preview(showBackground = true)
@Composable
private fun SliderWithButtonsPreview() {
    MaterialTheme {
        SliderWithButtons(
            value = 5.0,
            onValueChange = {},
            valueRange = 0.0..10.0,
            step = 0.5
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SliderWithButtonsValuePreview() {
    MaterialTheme {
        SliderWithButtons(
            value = 3.5,
            onValueChange = {},
            valueRange = 0.0..10.0,
            step = 0.1,
            showValue = true,
            valueFormat = DecimalFormat("0.0"),
            unitLabel = "U"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SliderWithButtonsIntPreview() {
    MaterialTheme {
        SliderWithButtons(
            value = 45.0,
            onValueChange = {},
            valueRange = 0.0..120.0,
            step = 5.0,
            showValue = true,
            valueFormat = DecimalFormat("0"),
            unitLabelResId = KeysR.string.units_min
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SliderWithButtonsNonLinearPreview() {
    MaterialTheme {
        Column {
            SliderWithButtons(
                value = 50.0,
                onValueChange = {},
                valueRange = 0.0..500.0,
                step = 1.0,
                controlPoints = listOf(
                    0.0 to 0.0,
                    0.5 to 50.0,
                    1.0 to 500.0
                ),
                showValue = true,
                valueFormat = DecimalFormat("0")
            )
            SliderWithButtons(
                value = 250.0,
                onValueChange = {},
                valueRange = 0.0..500.0,
                step = 1.0,
                controlPoints = listOf(
                    0.0 to 0.0,
                    0.5 to 50.0,
                    1.0 to 500.0
                ),
                showValue = true,
                valueFormat = DecimalFormat("0")
            )
        }
    }
}
