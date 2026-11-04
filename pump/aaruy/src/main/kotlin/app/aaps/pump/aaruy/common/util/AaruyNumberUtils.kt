package app.aaps.pump.aaruy.common.util

import java.math.BigDecimal
import java.math.RoundingMode

object AaruyNumberUtils {

    /** Rounds the decimal text of the number, so 1.25 is 1.3 even if the double is a little below 1.25. */
    private fun round(num: Double, scale: Int): Double =
        BigDecimal(num.toString()).setScale(scale, RoundingMode.HALF_UP).toDouble()

    /** The text has no trailing zeros, except one after the point: 1.5 and 2.0. NaN and infinity give [invalid]. */
    private fun roundToString(num: Double, scale: Int, invalid: String): String =
        try {
            round(round(num, scale), 3).toString()
        } catch (_: NumberFormatException) {
            invalid
        }

    fun doubleToInt(value: Double): Int =
        BigDecimal(getDotTwoString(value)).setScale(0, RoundingMode.HALF_UP).toInt()

    fun division(v1: Double, v2: Double, scale: Int): Double {
        require(scale >= 0) { "The scale must be a positive integer or zero" }
        require(v2 != 0.0) { "The v2 must not be zero" }
        return BigDecimal(v1.toString()).divide(BigDecimal(v2.toString()), scale, RoundingMode.HALF_UP).toDouble()
    }

    fun getDotOneDouble(num: Double): Double = round(num, 1)

    fun getDotOneString(num: Double): String = roundToString(num, 1, "0.0")

    fun getDotTwoString(num: Double): String = roundToString(num, 2, "0.00")

    fun getDotThreeString(num: Double): String = roundToString(num, 3, "0.000")
}
