package app.aaps.core.data.format

/**
 * Renders a [NumberFormat] as text.
 *
 * This is the only place in the app that still uses a platform number formatter. Everything else
 * works with [NumberFormat], which is pure Kotlin.
 *
 * The locale data (separators, minus sign, digit shapes) belongs to the platform - it is the CLDR
 * database that ships with the JVM and with iOS. Writing it again in common Kotlin would mean
 * carrying megabytes of it and then disagreeing with the operating system, so this stays a seam.
 *
 * Output must match the old `java.text.DecimalFormat` patterns: no grouping separator, rounding
 * half-even. `NumberFormatTest` in `jvmTest` checks that against the real `DecimalFormat`.
 */
expect object NumberFormatPlatform {

    /** Decimal separator for text that must not depend on the locale, for example server data. */
    val SEPARATOR_DOT: Char

    /** Decimal separator of the current locale. */
    val localeSeparator: Char

    fun format(format: NumberFormat, value: Double): String

    fun format(format: NumberFormat, value: Double, separator: Char): String
}
