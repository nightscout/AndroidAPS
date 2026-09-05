package app.aaps.core.data.format

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

actual object NumberFormatPlatform {

    actual val SEPARATOR_DOT: Char = '.'

    actual val localeSeparator: Char get() = DecimalFormatSymbols.getInstance().decimalSeparator

    // The locale is part of the key. A cached formatter keeps ALL the symbols of the locale it was
    // built with, not only the decimal separator - the minus sign and the digit shapes too. The app
    // can change the language while it runs (the activity is recreated, the process is not), and
    // two languages can share the decimal separator but differ elsewhere: Swedish, Norwegian and
    // Lithuanian all use a comma plus a real MINUS SIGN (U+2212), while German and Czech use a comma
    // plus an ordinary hyphen. Without the locale in the key, a negative number would keep the old
    // language's minus sign until the app is killed.
    private data class Key(val format: NumberFormat, val separator: Char, val locale: Locale)

    // DecimalFormat is not thread safe, so every thread gets its own instances.
    private val cache = object : ThreadLocal<MutableMap<Key, DecimalFormat>>() {
        override fun initialValue(): MutableMap<Key, DecimalFormat> = HashMap()
    }

    actual fun format(format: NumberFormat, value: Double): String = format(format, value, localeSeparator)

    actual fun format(format: NumberFormat, value: Double, separator: Char): String =
        formatterFor(Key(format, separator, Locale.getDefault())).format(value)

    private fun formatterFor(key: Key): DecimalFormat =
        cache.get()!!.getOrPut(key) {
            DecimalFormat().apply {
                minimumIntegerDigits = key.format.minIntegerDigits
                minimumFractionDigits = key.format.minFractionDigits
                maximumFractionDigits = key.format.maxFractionDigits
                // Old patterns like "0.00" never grouped. DecimalFormat() does by default.
                isGroupingUsed = false
                // HALF_EVEN is the DecimalFormat default, set here so it cannot drift.
                roundingMode = when (key.format.rounding) {
                    NumberRounding.HALF_EVEN -> RoundingMode.HALF_EVEN
                    NumberRounding.HALF_UP   -> RoundingMode.HALF_UP
                }
                decimalFormatSymbols = DecimalFormatSymbols.getInstance(key.locale).apply { decimalSeparator = key.separator }
            }
        }
}
