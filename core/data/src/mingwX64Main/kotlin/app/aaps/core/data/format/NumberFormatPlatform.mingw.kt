package app.aaps.core.data.format

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.truncate

/**
 * Experiment only.
 *
 * mingwX64 exists here to prove that `commonMain` holds no JVM API. It is not a shipping target, so
 * this actual does the simple thing rather than reaching for the platform locale data. A real iOS
 * actual would use `NSNumberFormatter`, which is backed by the same CLDR data as the JVM one.
 */
actual object NumberFormatPlatform {

    actual val SEPARATOR_DOT: Char = '.'

    /** No locale data on this target, so always a dot. */
    actual val localeSeparator: Char = '.'

    actual fun format(format: NumberFormat, value: Double): String = format(format, value, localeSeparator)

    actual fun format(format: NumberFormat, value: Double, separator: Char): String {
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return if (value > 0) "∞" else "-∞"

        val negative = value < 0 || (value == 0.0 && 1.0 / value < 0)
        val scale = 10.0.pow(format.maxFractionDigits)
        val scaled = abs(value) * scale
        val floor = truncate(scaled)
        val rest = scaled - floor
        val rounded = when {
            rest > 0.5                                              -> floor + 1
            rest < 0.5                                              -> floor
            // Exactly halfway. Where the two modes differ - see NumberRounding.
            format.rounding == NumberRounding.HALF_UP               -> floor + 1
            floor.toLong() % 2L == 0L                               -> floor
            else                                                    -> floor + 1
        }

        var whole = truncate(rounded / scale).toLong().toString()
        var fraction = round(rounded - truncate(rounded / scale) * scale).toLong().toString()
        if (format.maxFractionDigits > 0) fraction = fraction.padStart(format.maxFractionDigits, '0')
        else fraction = ""
        // drop trailing zeros above the minimum
        while (fraction.length > format.minFractionDigits && fraction.endsWith('0'))
            fraction = fraction.dropLast(1)

        whole = whole.padStart(format.minIntegerDigits, '0')
        val sign = if (negative && (whole.any { it != '0' } || fraction.any { it != '0' })) "-" else ""
        return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole$separator$fraction"
    }
}
