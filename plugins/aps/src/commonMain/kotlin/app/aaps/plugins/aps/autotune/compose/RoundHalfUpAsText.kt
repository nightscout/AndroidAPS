package app.aaps.plugins.aps.autotune.compose

import kotlin.math.abs

/**
 * [value] rounded half up to [decimals] places, the way `String.format("%.2f", value)` of the JVM
 * rounds it.
 *
 * That formatter rounds the shortest decimal text of the double, not the double itself. 1.005 is
 * stored a little below 1.005, so rounding the double gives 1.00, but its text is `1.005` and the
 * formatter shows 1.01. The Autotune results were shown that way, and this keeps them so when the
 * number is then written by `NumberFormat`, which takes the locale's separator and digits from the
 * platform. The answer is the double nearest to the rounded text, so writing it with [decimals]
 * places gives exactly that text.
 *
 * `RoundHalfUpAsTextTest` compares it with `String.format` over a range of values.
 */
internal fun roundHalfUpAsText(value: Double, decimals: Int): Double {
    require(decimals >= 0)
    if (!value.isFinite() || value == 0.0) return value
    val text = abs(value).toString()
    val e = text.indexOfFirst { it == 'E' || it == 'e' }
    val mantissa = if (e < 0) text else text.substring(0, e)
    val exponent = if (e < 0) 0 else text.substring(e + 1).toInt()
    val point = mantissa.indexOf('.')
    val wholeDigits = if (point < 0) mantissa else mantissa.substring(0, point)
    var digits = wholeDigits + (if (point < 0) "" else mantissa.substring(point + 1))
    // The decimal point sits after this many digits.
    var pointAt = wholeDigits.length + exponent
    if (pointAt < 0) {
        digits = "0".repeat(-pointAt) + digits
        pointAt = 0
    }
    val keep = pointAt + decimals
    if (digits.length <= keep) return value
    val kept = digits.substring(0, keep).toCharArray()
    var rounded = kept.concatToString()
    if (digits[keep] >= '5') {
        // Add one at the last kept place, carrying to the left. 9.99 becomes 10.0: one digit more.
        var i = kept.size - 1
        while (i >= 0 && kept[i] == '9') kept[i--] = '0'
        if (i >= 0) {
            kept[i] = kept[i] + 1
            rounded = kept.concatToString()
        } else {
            rounded = "1" + kept.concatToString()
            pointAt++
        }
    }
    val whole = rounded.substring(0, pointAt).ifEmpty { "0" }
    val result = (whole + "." + rounded.substring(pointAt).ifEmpty { "0" }).toDouble()
    return if (value < 0) -result else result
}
