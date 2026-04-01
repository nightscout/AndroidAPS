package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.R
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.roundToInt
import app.aaps.core.keys.R as KeysR

/**
 * Formats minutes as duration string: "Xh Ym" when >= 60, "X mins" otherwise.
 * Composable version using stringResource.
 */
@Composable
fun formatMinutesAsDuration(minutes: Int): String {
    val abs = abs(minutes)
    val sign = if (minutes < 0) "-" else ""
    return if (abs >= 60) {
        val hours = abs / 60
        val mins = abs % 60
        sign + stringResource(R.string.format_hour_minute, hours, mins)
    } else {
        stringResource(R.string.format_mins, minutes)
    }
}

/**
 * Formats minutes as duration string: "Xh Ym" when >= 60, "X mins" otherwise.
 * Non-composable version using ResourceHelper.
 */
fun formatMinutesAsDuration(minutes: Int, rh: ResourceHelper): String {
    val abs = abs(minutes)
    val sign = if (minutes < 0) "-" else ""
    return if (abs >= 60) {
        val hours = abs / 60
        val mins = abs % 60
        sign + rh.gs(R.string.format_hour_minute, hours, mins)
    } else {
        rh.gs(R.string.format_mins, minutes)
    }
}

/**
 * Formats a slider/input value for display, handling minutes-as-duration, resource format strings,
 * unit labels, and plain value formatting.
 *
 * Priority order:
 * 1. Minutes unit (unitLabelResId == units_min) → "Xh Ym" or "X mins"
 * 2. valueFormatResId → stringResource with value (as Int if formatAsInt, else Double)
 * 3. unitLabel non-empty → "formatted_value unitLabel"
 * 4. Plain → valueFormat.format(value)
 */
@Composable
fun formatSliderDisplayValue(
    value: Double,
    unitLabelResId: Int = 0,
    valueFormatResId: Int? = null,
    formatAsInt: Boolean = false,
    valueFormat: DecimalFormat,
    unitLabel: String = ""
): String {
    val isMinutesUnit = unitLabelResId == KeysR.string.units_min
    val resolvedUnitLabel = when {
        unitLabelResId != 0    -> stringResource(unitLabelResId)
        unitLabel.isNotEmpty() -> unitLabel
        else                   -> ""
    }
    return when {
        isMinutesUnit                  -> formatMinutesAsDuration(value.roundToInt())

        valueFormatResId != null       -> {
            if (formatAsInt) stringResource(valueFormatResId, value.roundToInt())
            else stringResource(valueFormatResId, value)
        }

        resolvedUnitLabel.isNotEmpty() -> "${valueFormat.format(value)} $resolvedUnitLabel"
        else                           -> valueFormat.format(value)
    }
}
