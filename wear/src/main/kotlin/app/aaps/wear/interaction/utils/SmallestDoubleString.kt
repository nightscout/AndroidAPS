package app.aaps.wear.interaction.utils

import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.regex.Pattern

/**
 * Helper to minimise various floating point values, with or without unit, to fit into specified
 * and limited size, scarifying precision (rounding up) and extra characters like leading zero,
 * following zero(s) in fractional part, extra plus sign etc.
 *
 * Created by dlvoy on 2019-11-12
 */
class SmallestDoubleString(inputString: String, withUnits: Units = Units.SKIP) {

    private var sign: String
    private var decimal: String
    private var separator: String
    private var fractional: String
    var extra = ""
    var units: String
    private val withUnits: Units

    enum class Units {
        SKIP, USE
    }

    fun minimise(maxSize: Int): String {
        val originalSeparator = separator
        if ("0$fractional".toInt() == 0) {
            separator = ""
            fractional = ""
        }
        if ("0$decimal".toInt() == 0 && fractional.isNotEmpty()) {
            decimal = ""
        }
        if (currentLen() <= maxSize) return toString()
        if (sign == "+") {
            sign = ""
        }
        if (currentLen() <= maxSize) {
            return toString()
        }
        while (fractional.length > 1 && fractional[fractional.length - 1] == '0') {
            fractional = fractional.substring(0, fractional.length - 1)
        }
        if (currentLen() <= maxSize) {
            return toString()
        }
        if (fractional.isNotEmpty()) {
            val remainingForFraction = maxSize - currentLen() + fractional.length
            var formatCandidate = "#"
            if (remainingForFraction >= 1) {
                formatCandidate = "#." + "#######".take(remainingForFraction)
            }
            val df = DecimalFormat(formatCandidate)
            df.roundingMode = RoundingMode.HALF_UP
            val decimalSup = decimal.ifEmpty { "0" }
            val result = sign + df.format("$decimalSup.$fractional".toDouble()).replace(",", originalSeparator).replace(".", originalSeparator) +
                if (withUnits == Units.USE) units else ""
            return if (decimal.isNotEmpty()) result else result.substring(1)
        }
        return toString()
    }

    private fun currentLen(): Int {
        return sign.length + decimal.length + separator.length + fractional.length +
            if (withUnits == Units.USE) units.length else 0
    }

    override fun toString(): String {
        return sign + decimal + separator + fractional +
            if (withUnits == Units.USE) units else ""
    }

    companion object {

        private val pattern = Pattern.compile("^([+-]?)([0-9]*)([,.]?)([0-9]*)(\\([^)]*\\))?(.*?)$", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
    }

    init {
        val matcher = pattern.matcher(inputString)
        matcher.matches()
        sign = matcher.group(1) ?: ""
        decimal = matcher.group(2) ?: ""
        separator = matcher.group(3) ?: ""
        fractional = matcher.group(4) ?: ""
        units = matcher.group(6) ?: ""
        if (fractional.isEmpty()) {
            separator = ""
            fractional = ""
        }
        val extraCandidate = matcher.group(5) ?: ""
        if (extraCandidate.length > 2) {
            extra = extraCandidate.substring(1, extraCandidate.length - 1)
        }
        units = units.trim()

        this.withUnits = withUnits
    }
}
