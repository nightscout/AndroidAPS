package app.aaps.core.utils

import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val US_SYMBOLS = DecimalFormatSymbols(Locale.US)

private fun usPattern(decimals: Int): String =
    if (decimals <= 0) "#0" else "#0." + "0".repeat(decimals)

/**
 * Format the number with a dot as the decimal separator, whatever the device locale is.
 *
 * Use it for text that must not depend on the locale, like pump history and log lines.
 * The separator comes from [Locale.US] symbols, so nothing has to be repaired afterwards.
 * The old version formatted with the default locale and then replaced a comma with a dot, which
 * did nothing on locales that use another separator, for example Arabic.
 *
 * @param decimals how many digits after the dot, always shown. 0 means no decimals.
 */
fun Number.formatUS(decimals: Int): String =
// A new DecimalFormat for every call on purpose. DecimalFormat is not thread safe, and the
    // old shared array could give wrong text when two threads formatted at the same time.
    DecimalFormat(usPattern(decimals), US_SYMBOLS).format(this)

object StringUtil {

    fun fromBytes(ra: ByteArray?): String =
        if (ra == null) "null array"
        else String(ra, StandardCharsets.UTF_8)

    /**
     * Append To StringBuilder
     *
     * @param stringBuilder
     * @param stringToAdd
     * @param delimiter
     * @return
     */
    fun appendToStringBuilder(stringBuilder: StringBuilder, stringToAdd: String, delimiter: String): StringBuilder =
        if (stringBuilder.isNotEmpty()) stringBuilder.append(delimiter + stringToAdd)
        else stringBuilder.append(stringToAdd)

    fun getLeadingZero(number: Int, places: Int): String {
        var nn = "" + number
        while (nn.length < places) nn = "0$nn"
        return nn
    }

    fun getStringInLength(value: String, length: Int): String {
        val v = StringBuilder(value)
        if (v.length > length) return v.substring(0, length)
        for (i in v.length until length) v.append(" ")
        return v.toString()
    }

    fun splitString(str: String, characters: Int): List<String> {
        var s = str
        val outString: MutableList<String> = ArrayList()
        do {
            if (s.length > characters) {
                val token = s.substring(0, characters)
                outString.add(token)
                s = s.substring(characters)
            }
        } while (s.length > characters)
        outString.add(s)
        return outString
    }
}