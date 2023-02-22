package info.nightscout.aaps.pump.common.data

import java.util.*

/**
 * BasalProfileDto contains info about BasalProfile.
 *    basalName = name of the pattern
 *    basalPatterns = array of 24 items, one for each hour
 */
class BasalProfileDto(var basalPatterns: DoubleArray?,
                      var basalName: String? = null) {

    fun basalProfileToString(): String {
        val sb = StringBuffer("Basal Profile [")
        for (i in basalPatterns!!.indices) {
            var time = if (i < 10) "0$i" else "" + i
            time += ":00"
            sb.append(String.format(Locale.ROOT, "%s=%.3f, ", time, basalPatterns!![i]))
        }
        sb.append("]")
        return sb.toString()
    }


    override fun toString(): String {
        return if (basalPatterns == null) {
            "Basal Profile [Not Set]"
        } else {
            basalProfileToString()
        }
    }

}