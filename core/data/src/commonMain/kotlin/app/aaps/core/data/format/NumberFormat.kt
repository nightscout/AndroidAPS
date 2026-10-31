package app.aaps.core.data.format

/**
 * How to render a number as text.
 *
 * This type is pure Kotlin on purpose. It replaces `java.text.DecimalFormat` in API signatures
 * and in Compose parameters, so that the code holding it can be shared with other platforms.
 * The rendering itself is done by [NumberFormatPlatform], which is the only place that still
 * uses a platform number formatter.
 *
 * Digit counts follow the same rules as the old `DecimalFormat` patterns:
 * - [minIntegerDigits] pads the part before the decimal separator with leading zeros.
 *   Pattern `"00"` means `minIntegerDigits = 2`.
 * - [minFractionDigits] is how many digits after the separator are always shown.
 *   Pattern `"0.0"` means `minFractionDigits = 1`.
 * - [maxFractionDigits] is how many digits after the separator are shown at most.
 *   Trailing zeros above [minFractionDigits] are dropped.
 *   Pattern `"0.0#"` means `minFractionDigits = 1` and `maxFractionDigits = 2`.
 *
 * Rounding is half-even, same as before.
 *
 * The decimal separator is not part of this type. It comes from the platform, so a device set
 * to Czech or German keeps showing a comma exactly as it does today. Pass
 * [NumberFormatPlatform.SEPARATOR_DOT] where the output must always use a dot, for example when
 * the text is sent to a server or written to a file.
 */
class NumberFormat(
    val minIntegerDigits: Int = 1,
    val minFractionDigits: Int = 0,
    val maxFractionDigits: Int = minFractionDigits,
    val rounding: NumberRounding = NumberRounding.HALF_EVEN
) {

    init {
        require(minIntegerDigits >= 0) { "minIntegerDigits must not be negative" }
        require(minFractionDigits >= 0) { "minFractionDigits must not be negative" }
        require(maxFractionDigits >= minFractionDigits) { "maxFractionDigits must not be smaller than minFractionDigits" }
    }

    /** Format using the decimal separator of the current locale. */
    fun format(value: Double): String = NumberFormatPlatform.format(this, value)

    /** Format using the decimal separator of the current locale. */
    fun format(value: Long): String = NumberFormatPlatform.format(this, value.toDouble())

    /** Format using the decimal separator of the current locale. */
    fun format(value: Int): String = NumberFormatPlatform.format(this, value.toDouble())

    /**
     * Format using an explicit decimal separator.
     * Use [NumberFormatPlatform.SEPARATOR_DOT] for text that must not depend on the locale.
     */
    fun format(value: Double, separator: Char): String = NumberFormatPlatform.format(this, value, separator)

    override fun equals(other: Any?): Boolean =
        other is NumberFormat &&
            minIntegerDigits == other.minIntegerDigits &&
            minFractionDigits == other.minFractionDigits &&
            maxFractionDigits == other.maxFractionDigits &&
            rounding == other.rounding

    override fun hashCode(): Int = ((minIntegerDigits * 31 + minFractionDigits) * 31 + maxFractionDigits) * 31 + rounding.ordinal

    override fun toString(): String = "NumberFormat($minIntegerDigits, $minFractionDigits, $maxFractionDigits, $rounding)"

    companion object {

        /** No decimals. Old pattern `"0"` / `"#0"` / `"#"`. */
        val INTEGER = NumberFormat(minFractionDigits = 0)

        /** Two integer digits, no decimals. Old pattern `"00"`. */
        val INTEGER_2_DIGITS = NumberFormat(minIntegerDigits = 2, minFractionDigits = 0)

        /** Always one decimal. Old pattern `"0.0"` / `"#0.0"`. */
        val DECIMAL_1 = NumberFormat(minFractionDigits = 1)

        /** Always two decimals. Old pattern `"0.00"` / `"#0.00"` / `"##0.00"`. */
        val DECIMAL_2 = NumberFormat(minFractionDigits = 2)

        /** Always three decimals. Old pattern `"0.000"` / `"#0.000"`. */
        val DECIMAL_3 = NumberFormat(minFractionDigits = 3)

        /** Always six decimals. Old pattern `"0.000000"`. */
        val DECIMAL_6 = NumberFormat(minFractionDigits = 6)

        /** At most one decimal, dropped when it is zero. Old pattern `"#.#"`. */
        val UP_TO_1_DECIMAL = NumberFormat(minFractionDigits = 0, maxFractionDigits = 1)

        /** At most two decimals, dropped when they are zero. Old pattern `"0.##"`. */
        val UP_TO_2_DECIMALS = NumberFormat(minFractionDigits = 0, maxFractionDigits = 2)

        /** One decimal, plus a second one when it is not zero. Old pattern `"0.0#"`. */
        val DECIMAL_1_UP_TO_2 = NumberFormat(minFractionDigits = 1, maxFractionDigits = 2)

        /** Two decimals, plus a third one when it is not zero. Old pattern `"0.00#"`. */
        val DECIMAL_2_UP_TO_3 = NumberFormat(minFractionDigits = 2, maxFractionDigits = 3)

        /** Three decimals, plus a fourth one when it is not zero. Old pattern `"0.000#"`. */
        val DECIMAL_3_UP_TO_4 = NumberFormat(minFractionDigits = 3, maxFractionDigits = 4)

        /**
         * Fixed decimals, ties away from zero.
         *
         * For a single value on screen, where the reader expects .5 to go up rather than to the
         * even neighbour. See [NumberRounding].
         */
        fun withDecimalsHalfUp(decimals: Int): NumberFormat =
            NumberFormat(minFractionDigits = decimals, rounding = NumberRounding.HALF_UP)

        /** Format with the given number of fixed decimals. */
        fun withDecimals(decimals: Int): NumberFormat = when (decimals) {
            0    -> INTEGER
            1    -> DECIMAL_1
            2    -> DECIMAL_2
            3    -> DECIMAL_3
            else -> NumberFormat(minFractionDigits = decimals)
        }
    }
}
