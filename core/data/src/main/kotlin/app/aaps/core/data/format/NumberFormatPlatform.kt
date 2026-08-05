package app.aaps.core.data.format

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/**
 * Renders a [NumberFormat] as text.
 *
 * This is the only place in the app that still uses a platform number formatter. Everything else
 * works with [NumberFormat], which is pure Kotlin. When this module is built for other platforms,
 * this object becomes the platform specific part and nothing else has to change.
 *
 * Output is the same as the old `java.text.DecimalFormat` patterns:
 * grouping separators are off, and rounding is half-even.
 */
object NumberFormatPlatform {

    /** Decimal separator for text that must not depend on the locale, for example server data. */
    const val SEPARATOR_DOT = '.'

    /** Decimal separator of the current locale. */
    val localeSeparator: Char get() = DecimalFormatSymbols.getInstance().decimalSeparator

    private data class Key(val format: NumberFormat, val separator: Char)

    // DecimalFormat is not thread safe, so every thread gets its own instances.
    private val cache = object : ThreadLocal<MutableMap<Key, DecimalFormat>>() {
        override fun initialValue(): MutableMap<Key, DecimalFormat> = HashMap()
    }

    fun format(format: NumberFormat, value: Double): String = format(format, value, localeSeparator)

    fun format(format: NumberFormat, value: Double, separator: Char): String =
        formatterFor(Key(format, separator)).format(value)

    private fun formatterFor(key: Key): DecimalFormat =
        cache.get()!!.getOrPut(key) {
            DecimalFormat().apply {
                minimumIntegerDigits = key.format.minIntegerDigits
                minimumFractionDigits = key.format.minFractionDigits
                maximumFractionDigits = key.format.maxFractionDigits
                // Old patterns like "0.00" never grouped. DecimalFormat() does by default.
                isGroupingUsed = false
                // Same as the default of DecimalFormat, set here so it cannot drift.
                roundingMode = RoundingMode.HALF_EVEN
                decimalFormatSymbols = DecimalFormatSymbols.getInstance().apply { decimalSeparator = key.separator }
            }
        }
}
