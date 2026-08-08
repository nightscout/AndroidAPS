package app.aaps.core.ui.compose

import app.aaps.core.keys.UnitType
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.R

/**
 * Maps a [UnitType] to the text that describes it.
 *
 * This lives in `:core:ui` rather than next to the enum on purpose. [UnitType] itself is a plain
 * enum with no Android in it, so it can move to a multiplatform module. The mapping to string
 * resources cannot, so it stays on the UI side and the enum travels alone.
 */

/**
 * The unit label on its own - "min", "U", "h".
 * Null when the unit has no label, which is the case for NONE and the generic doubles.
 */
fun UnitType.unitLabel(): TextRef? = when (this) {
    UnitType.NONE                                         -> null
    UnitType.GRAMS                                        -> TextRef.AndroidRes(R.string.units_grams)
    UnitType.MIN                                          -> TextRef.AndroidRes(R.string.units_min)
    UnitType.SEC                                          -> TextRef.AndroidRes(R.string.units_sec)
    UnitType.HOURS, UnitType.HOURS_DOUBLE                 -> TextRef.AndroidRes(R.string.units_hours)
    UnitType.DAYS                                         -> TextRef.AndroidRes(R.string.units_days)
    UnitType.PERCENT                                      -> TextRef.AndroidRes(R.string.units_percent)
    UnitType.INSULIN, UnitType.INSULIN_INT                -> TextRef.AndroidRes(R.string.units_insulin)
    UnitType.INSULIN_RATE                                 -> TextRef.AndroidRes(R.string.units_insulin_rate)
    UnitType.DOUBLE, UnitType.DOUBLE_2, UnitType.DOUBLE_3 -> null // generic doubles carry no unit
    UnitType.MGDL                                         -> TextRef.AndroidRes(R.string.units_mgdl)
}

/**
 * A value and its range, already filled in - "5 min (0 - 30)".
 *
 * The arguments are taken here rather than returned as a bare template id, because a [TextRef.AndroidRes]
 * with no arguments renders the raw `%1$d` template. Taking them makes that mistake impossible.
 */
fun UnitType.rangeText(value: Any, min: Any, max: Any): TextRef? =
    rangeFormatResId()?.let { TextRef.AndroidRes(it, listOf(value, min, max)) }

/**
 * Format template for a single value, e.g. `%1$d min`.
 *
 * Still a raw resource id, unlike the two above, because the slider applies it to whatever value
 * the user drags to - the arguments are not known here. It never leaves `:core:ui`.
 */
fun UnitType.valueFormatResId(): Int? = when (this) {
    UnitType.NONE         -> null
    UnitType.GRAMS        -> R.string.units_format_grams
    UnitType.MIN          -> R.string.units_format_min
    UnitType.SEC          -> R.string.units_format_sec
    UnitType.HOURS        -> R.string.units_format_hours
    UnitType.HOURS_DOUBLE -> R.string.units_format_hours_double
    UnitType.DAYS         -> R.string.units_format_days
    UnitType.PERCENT      -> R.string.units_format_percent
    UnitType.INSULIN      -> R.string.units_format_insulin
    UnitType.INSULIN_INT  -> R.string.units_format_insulin_int
    UnitType.INSULIN_RATE -> R.string.units_format_insulin_rate
    UnitType.DOUBLE       -> R.string.units_format_double
    UnitType.DOUBLE_2     -> R.string.units_format_double_2
    UnitType.DOUBLE_3     -> R.string.units_format_double_3
    UnitType.MGDL         -> R.string.units_format_mgdl
}

private fun UnitType.rangeFormatResId(): Int? = when (this) {
    UnitType.NONE         -> null
    UnitType.GRAMS        -> R.string.units_format_grams_range
    UnitType.MIN          -> R.string.units_format_min_range
    UnitType.SEC          -> R.string.units_format_sec_range
    UnitType.HOURS        -> R.string.units_format_hours_range
    UnitType.HOURS_DOUBLE -> R.string.units_format_hours_double_range
    UnitType.DAYS         -> R.string.units_format_days_range
    UnitType.PERCENT      -> R.string.units_format_percent_range
    UnitType.INSULIN      -> R.string.units_format_insulin_range
    UnitType.INSULIN_INT  -> R.string.units_format_insulin_int_range
    UnitType.INSULIN_RATE -> R.string.units_format_insulin_rate_range
    UnitType.DOUBLE       -> R.string.units_format_double_range
    UnitType.DOUBLE_2     -> R.string.units_format_double_2_range
    UnitType.DOUBLE_3     -> R.string.units_format_double_3_range
    UnitType.MGDL         -> R.string.units_format_mgdl_range
}

/** True when this unit should be rendered as a duration ("1 h 30 min") rather than a plain number. */
fun UnitType.isDuration(): Boolean = this == UnitType.MIN
