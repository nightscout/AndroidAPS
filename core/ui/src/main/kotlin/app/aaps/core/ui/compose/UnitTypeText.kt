package app.aaps.core.ui.compose

import app.aaps.core.keys.UnitType
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.R as KeysR

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
    UnitType.GRAMS                                        -> TextRef.Res(KeysR.string.units_grams)
    UnitType.MIN                                          -> TextRef.Res(KeysR.string.units_min)
    UnitType.SEC                                          -> TextRef.Res(KeysR.string.units_sec)
    UnitType.HOURS, UnitType.HOURS_DOUBLE                 -> TextRef.Res(KeysR.string.units_hours)
    UnitType.DAYS                                         -> TextRef.Res(KeysR.string.units_days)
    UnitType.PERCENT                                      -> TextRef.Res(KeysR.string.units_percent)
    UnitType.INSULIN, UnitType.INSULIN_INT                -> TextRef.Res(KeysR.string.units_insulin)
    UnitType.INSULIN_RATE                                 -> TextRef.Res(KeysR.string.units_insulin_rate)
    UnitType.DOUBLE, UnitType.DOUBLE_2, UnitType.DOUBLE_3 -> null // generic doubles carry no unit
    UnitType.MGDL                                         -> TextRef.Res(KeysR.string.units_mgdl)
}

/**
 * A value and its range, already filled in - "5 min (0 - 30)".
 *
 * The arguments are taken here rather than returned as a bare template id, because a [TextRef.Res]
 * with no arguments renders the raw `%1$d` template. Taking them makes that mistake impossible.
 */
fun UnitType.rangeText(value: Any, min: Any, max: Any): TextRef? =
    rangeFormatResId()?.let { TextRef.Res(it, listOf(value, min, max)) }

/**
 * Format template for a single value, e.g. `%1$d min`.
 *
 * Still a raw resource id, unlike the two above, because the slider applies it to whatever value
 * the user drags to - the arguments are not known here. It never leaves `:core:ui`.
 */
fun UnitType.valueFormatResId(): Int? = when (this) {
    UnitType.NONE         -> null
    UnitType.GRAMS        -> KeysR.string.units_format_grams
    UnitType.MIN          -> KeysR.string.units_format_min
    UnitType.SEC          -> KeysR.string.units_format_sec
    UnitType.HOURS        -> KeysR.string.units_format_hours
    UnitType.HOURS_DOUBLE -> KeysR.string.units_format_hours_double
    UnitType.DAYS         -> KeysR.string.units_format_days
    UnitType.PERCENT      -> KeysR.string.units_format_percent
    UnitType.INSULIN      -> KeysR.string.units_format_insulin
    UnitType.INSULIN_INT  -> KeysR.string.units_format_insulin_int
    UnitType.INSULIN_RATE -> KeysR.string.units_format_insulin_rate
    UnitType.DOUBLE       -> KeysR.string.units_format_double
    UnitType.DOUBLE_2     -> KeysR.string.units_format_double_2
    UnitType.DOUBLE_3     -> KeysR.string.units_format_double_3
    UnitType.MGDL         -> KeysR.string.units_format_mgdl
}

private fun UnitType.rangeFormatResId(): Int? = when (this) {
    UnitType.NONE         -> null
    UnitType.GRAMS        -> KeysR.string.units_format_grams_range
    UnitType.MIN          -> KeysR.string.units_format_min_range
    UnitType.SEC          -> KeysR.string.units_format_sec_range
    UnitType.HOURS        -> KeysR.string.units_format_hours_range
    UnitType.HOURS_DOUBLE -> KeysR.string.units_format_hours_double_range
    UnitType.DAYS         -> KeysR.string.units_format_days_range
    UnitType.PERCENT      -> KeysR.string.units_format_percent_range
    UnitType.INSULIN      -> KeysR.string.units_format_insulin_range
    UnitType.INSULIN_INT  -> KeysR.string.units_format_insulin_int_range
    UnitType.INSULIN_RATE -> KeysR.string.units_format_insulin_rate_range
    UnitType.DOUBLE       -> KeysR.string.units_format_double_range
    UnitType.DOUBLE_2     -> KeysR.string.units_format_double_2_range
    UnitType.DOUBLE_3     -> KeysR.string.units_format_double_3_range
    UnitType.MGDL         -> KeysR.string.units_format_mgdl_range
}

/** True when this unit should be rendered as a duration ("1 h 30 min") rather than a plain number. */
fun UnitType.isDuration(): Boolean = this == UnitType.MIN
