package app.aaps.core.keys

/**
 * Enum defining unit types for preference values.
 * Used to format values with appropriate units in UI.
 */
enum class UnitType {

    NONE,
    GRAMS,
    MIN,
    SEC,
    HOURS,
    HOURS_DOUBLE,
    DAYS,
    PERCENT,
    INSULIN,
    INSULIN_INT,
    INSULIN_RATE,
    DOUBLE,
    DOUBLE_2,
    MGDL
}

/**
 * Returns the resource ID for formatting a single value with this unit type.
 * Use with stringResource(resId, value) in Compose.
 */
fun UnitType.valueResId(): Int? = when (this) {
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
    UnitType.MGDL         -> R.string.units_format_mgdl
}

/**
 * Returns the resource ID for formatting a value with range (value, min, max).
 * Use with stringResource(resId, value, min, max) in Compose.
 */
fun UnitType.rangeResId(): Int? = when (this) {
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
    UnitType.MGDL         -> R.string.units_format_mgdl_range
}

/**
 * Returns the number of decimal places for this unit type.
 */
fun UnitType.decimalPlaces(): Int = when (this) {
    UnitType.DOUBLE_2                                                               -> 2
    UnitType.INSULIN, UnitType.INSULIN_RATE, UnitType.DOUBLE, UnitType.HOURS_DOUBLE -> 1
    else                                                                            -> 0
}

/**
 * Returns the step size for slider/increment controls.
 */
fun UnitType.step(): Double = when (this) {
    UnitType.DOUBLE_2                                                               -> 0.01
    UnitType.INSULIN, UnitType.INSULIN_RATE, UnitType.DOUBLE, UnitType.HOURS_DOUBLE -> 0.1
    else                                                                            -> 1.0
}

/**
 * Returns the resource ID for the unit label string (e.g., "min", "U", "h").
 * Use with stringResource(resId) in Compose for slider value display.
 */
fun UnitType.unitLabelResId(): Int? = when (this) {
    UnitType.NONE                          -> null
    UnitType.GRAMS                         -> R.string.units_grams
    UnitType.MIN                           -> R.string.units_min
    UnitType.SEC                           -> R.string.units_sec
    UnitType.HOURS, UnitType.HOURS_DOUBLE  -> R.string.units_hours
    UnitType.DAYS                          -> R.string.units_days
    UnitType.PERCENT                       -> R.string.units_percent
    UnitType.INSULIN, UnitType.INSULIN_INT -> R.string.units_insulin
    UnitType.INSULIN_RATE                  -> R.string.units_insulin_rate
    UnitType.DOUBLE, UnitType.DOUBLE_2     -> null  // No unit label for generic doubles
    UnitType.MGDL                          -> R.string.units_mgdl
}
