package info.nightscout.androidaps.utils

import java.util.regex.Pattern

object PercentageSplitter {

    // Matches "Profile name (200%,-2h)", "Profile name (50%)
    private val splitPattern = Pattern.compile("(.+)\\(\\d+%(,-?\\d+h)?\\)")

    /**
     * Removes the suffix for percentage and timeshift from a profile name. This is the inverse of what
     * [ProfileSwitch.getCustomizedName()] does.
     * Since the customized name is used for the PS upload to NS, this is needed get the original profile name
     * when retrieving the PS from NS again.
     */
    fun pureName(name: String): String {
        val percentageMatch = splitPattern.matcher(name)
        return if (percentageMatch.find()) percentageMatch.group(1).trim { it <= ' ' } else name
    }
}