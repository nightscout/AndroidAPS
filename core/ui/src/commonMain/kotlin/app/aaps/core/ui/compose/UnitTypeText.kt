package app.aaps.core.ui.compose

import app.aaps.core.keys.interfaces.TextRef.Companion.withArgs
import app.aaps.core.ui.UiStrings
import app.aaps.core.keys.UnitType
import app.aaps.core.keys.interfaces.TextRef

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
    UnitType.GRAMS                                        -> UiStrings.units_grams
    UnitType.MIN                                          -> UiStrings.units_min
    UnitType.SEC                                          -> UiStrings.units_sec
    UnitType.HOURS, UnitType.HOURS_DOUBLE                 -> UiStrings.units_hours
    UnitType.DAYS                                         -> UiStrings.units_days
    UnitType.PERCENT                                      -> UiStrings.units_percent
    UnitType.INSULIN, UnitType.INSULIN_INT                -> UiStrings.units_insulin
    UnitType.INSULIN_RATE                                 -> UiStrings.units_insulin_rate
    UnitType.DOUBLE, UnitType.DOUBLE_2, UnitType.DOUBLE_3 -> null // generic doubles carry no unit
    UnitType.MGDL                                         -> UiStrings.units_mgdl
}

/**
 * A value and its range, already filled in - "5 min (0 - 30)".
 *
 * The arguments are taken here rather than returned as a bare template id, because a [TextRef.AndroidRes]
 * with no arguments renders the raw `%1$d` template. Taking them makes that mistake impossible.
 */
fun UnitType.rangeText(value: Any, min: Any, max: Any): TextRef? =
    rangeFormat()?.withArgs(value, min, max)

/**
 * Format template for a single value, e.g. `%1$d min`.
 *
 * Still a raw resource id, unlike the two above, because the slider applies it to whatever value
 * the user drags to - the arguments are not known here. It never leaves `:core:ui`.
 */
fun UnitType.valueFormat(): TextRef? = when (this) {
    UnitType.NONE         -> null
    UnitType.GRAMS        -> UiStrings.units_format_grams
    UnitType.MIN          -> UiStrings.units_format_min
    UnitType.SEC          -> UiStrings.units_format_sec
    UnitType.HOURS        -> UiStrings.units_format_hours
    UnitType.HOURS_DOUBLE -> UiStrings.units_format_hours_double
    UnitType.DAYS         -> UiStrings.units_format_days
    UnitType.PERCENT      -> UiStrings.units_format_percent
    UnitType.INSULIN      -> UiStrings.units_format_insulin
    UnitType.INSULIN_INT  -> UiStrings.units_format_insulin_int
    UnitType.INSULIN_RATE -> UiStrings.units_format_insulin_rate
    UnitType.DOUBLE       -> UiStrings.units_format_double
    UnitType.DOUBLE_2     -> UiStrings.units_format_double_2
    UnitType.DOUBLE_3     -> UiStrings.units_format_double_3
    UnitType.MGDL         -> UiStrings.units_format_mgdl
}

private fun UnitType.rangeFormat(): TextRef? = when (this) {
    UnitType.NONE         -> null
    UnitType.GRAMS        -> UiStrings.units_format_grams_range
    UnitType.MIN          -> UiStrings.units_format_min_range
    UnitType.SEC          -> UiStrings.units_format_sec_range
    UnitType.HOURS        -> UiStrings.units_format_hours_range
    UnitType.HOURS_DOUBLE -> UiStrings.units_format_hours_double_range
    UnitType.DAYS         -> UiStrings.units_format_days_range
    UnitType.PERCENT      -> UiStrings.units_format_percent_range
    UnitType.INSULIN      -> UiStrings.units_format_insulin_range
    UnitType.INSULIN_INT  -> UiStrings.units_format_insulin_int_range
    UnitType.INSULIN_RATE -> UiStrings.units_format_insulin_rate_range
    UnitType.DOUBLE       -> UiStrings.units_format_double_range
    UnitType.DOUBLE_2     -> UiStrings.units_format_double_2_range
    UnitType.DOUBLE_3     -> UiStrings.units_format_double_3_range
    UnitType.MGDL         -> UiStrings.units_format_mgdl_range
}

/** True when this unit should be rendered as a duration ("1 h 30 min") rather than a plain number. */
fun UnitType.isDuration(): Boolean = this == UnitType.MIN
