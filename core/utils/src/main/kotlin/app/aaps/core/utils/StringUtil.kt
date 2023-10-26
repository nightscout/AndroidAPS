package app.aaps.core.utils

import java.nio.charset.StandardCharsets
import java.text.DecimalFormat

object StringUtil {

    private var DecimalFormatters = arrayOf(
        DecimalFormat("#0"), DecimalFormat("#0.0"), DecimalFormat("#0.00"), DecimalFormat("#0.000")
    )

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

    fun getFormattedValueUS(value: Number?, decimals: Int): String =
        DecimalFormatters[decimals].format(value).replace(",", ".")

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