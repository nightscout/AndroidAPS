package app.aaps.core.data.format

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterRoundHalfEven
import platform.Foundation.NSNumberFormatterRoundHalfUp
import platform.Foundation.currentLocale

/**
 * `NSNumberFormatter` is backed by the same CLDR data as the JVM `DecimalFormat`, so this is meant to
 * produce the same text rather than an approximation of it. Every setting here mirrors one line of
 * the JVM actual: no grouping, half-even rounding, and an explicit decimal separator.
 *
 * A formatter is built per call instead of being cached. `NSNumberFormatter` is not thread safe, and
 * the JVM side solves that with a `ThreadLocal` cache; there is no need to carry that complexity
 * until something measures it, because this formats screen values rather than a hot loop.
 *
 * **This has not been checked against the JVM output.** The two implementations agreeing is the
 * whole point of using `NSNumberFormatter`, but comparing them needs a machine that can run
 * Kotlin/Native tests for an Apple target, which Windows cannot do.
 */
actual object NumberFormatPlatform {

    actual val SEPARATOR_DOT: Char = '.'

    actual val localeSeparator: Char
        get() = formatter().decimalSeparator?.firstOrNull() ?: SEPARATOR_DOT

    actual fun format(format: NumberFormat, value: Double): String = format(format, value, localeSeparator)

    actual fun format(format: NumberFormat, value: Double, separator: Char): String =
        formatter().apply {
            setMinimumIntegerDigits(format.minIntegerDigits.toULong())
            setMinimumFractionDigits(format.minFractionDigits.toULong())
            setMaximumFractionDigits(format.maxFractionDigits.toULong())
            // Old patterns like "0.00" never grouped, and NSNumberFormatter groups by default.
            setUsesGroupingSeparator(false)
            setRoundingMode(
                when (format.rounding) {
                    NumberRounding.HALF_EVEN -> NSNumberFormatterRoundHalfEven
                    NumberRounding.HALF_UP   -> NSNumberFormatterRoundHalfUp
                }
            )
            setDecimalSeparator(separator.toString())
        }.stringFromNumber(NSNumber(double = value)) ?: ""

    private fun formatter(): NSNumberFormatter =
        NSNumberFormatter().apply {
            setNumberStyle(NSNumberFormatterDecimalStyle)
            setLocale(NSLocale.currentLocale)
        }
}
