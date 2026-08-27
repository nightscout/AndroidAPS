package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Formats minutes as duration string: "X h Y min" when >= 60 (omits minutes if zero), "X min" otherwise.
 * Composable version using stringResource.
 */
@Composable
fun formatMinutesAsDuration(minutes: Int): String {
    val abs = abs(minutes)
    val sign = if (minutes < 0) "-" else ""
    return if (abs >= 60) {
        val hours = abs / 60
        val mins = abs % 60
        sign + if (mins == 0) stringResource(CoreUiStrings.format_hours_only, hours)
        else stringResource(CoreUiStrings.format_hour_minute, hours, mins)
    } else {
        stringResource(CoreUiStrings.format_mins, minutes)
    }
}

/**
 * Formats minutes as duration string: "X h Y min" when >= 60 (omits minutes if zero), "X min" otherwise.
 * Non-composable version using TextResolver.
 */
fun formatMinutesAsDuration(minutes: Int, rh: TextResolver): String {
    val abs = abs(minutes)
    val sign = if (minutes < 0) "-" else ""
    return if (abs >= 60) {
        val hours = abs / 60
        val mins = abs % 60
        sign + if (mins == 0) rh.gs(CoreUiStrings.format_hours_only, hours)
        else rh.gs(CoreUiStrings.format_hour_minute, hours, mins)
    } else {
        rh.gs(CoreUiStrings.format_mins, minutes)
    }
}

/**
 * Formats a slider/input value for display, handling durations, resource format strings,
 * unit labels, and plain value formatting.
 *
 * Priority order:
 * 1. [asDuration] → "X h Y min" or "X min"
 * 2. valueFormat → stringResource with value (as Int if formatAsInt, else Double)
 * 3. unitLabel set → "formatted_value unitLabel"
 * 4. Plain → valueFormat.format(value)
 *
 * @param asDuration render the value as a duration. This used to be inferred by comparing the label
 *   against `units_min`, which tied the behaviour to one particular string resource. Callers say what
 *   they mean instead.
 */
@Composable
fun formatSliderDisplayValue(
    value: Double,
    unitLabel: TextRef? = null,
    valueFormatRef: TextRef? = null,
    formatAsInt: Boolean = false,
    valueFormat: NumberFormat,
    asDuration: Boolean = false
): String {
    val resolvedUnitLabel = unitLabel?.let { stringResource(it) } ?: ""
    return when {
        asDuration                     -> formatMinutesAsDuration(value.roundToInt())

        valueFormatRef != null         -> {
            if (formatAsInt) stringResource(valueFormatRef, value.roundToInt())
            else stringResource(valueFormatRef, value)
        }

        resolvedUnitLabel.isNotEmpty() -> "${valueFormat.format(value)} $resolvedUnitLabel"
        else                           -> valueFormat.format(value)
    }
}
