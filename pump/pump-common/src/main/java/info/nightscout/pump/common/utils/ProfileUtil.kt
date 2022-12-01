package info.nightscout.pump.common.utils

import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.Profile.ProfileValue
import info.nightscout.interfaces.pump.defs.PumpType
import java.util.Locale

object ProfileUtil {

    fun getProfileDisplayable(profile: Profile, pumpType: PumpType): String {
        val stringBuilder = StringBuilder()
        for (basalValue in profile.getBasalValues()) {
            val basalValueValue = pumpType.determineCorrectBasalSize(basalValue.value)
            val hour = basalValue.timeAsSeconds / (60 * 60)
            stringBuilder.append((if (hour < 10) "0" else "") + hour + ":00")
            stringBuilder.append(String.format(Locale.ENGLISH, "%.3f", basalValueValue))
            stringBuilder.append(", ")
        }
        return if (stringBuilder.length > 3) stringBuilder.substring(0, stringBuilder.length - 2) else stringBuilder.toString()
    }

    fun getBasalProfilesDisplayable(profiles: Array<ProfileValue>, pumpType: PumpType): String {
        val stringBuilder = StringBuilder()
        for (basalValue in profiles) {
            val basalValueValue = pumpType.determineCorrectBasalSize(basalValue.value)
            val hour = basalValue.timeAsSeconds / (60 * 60)
            stringBuilder.append((if (hour < 10) "0" else "") + hour + ":00")
            stringBuilder.append(" ")
            stringBuilder.append(String.format(Locale.ENGLISH, "%.3f", basalValueValue))
            stringBuilder.append(",\n")
        }
        return if (stringBuilder.length > 3) stringBuilder.substring(0, stringBuilder.length - 2) else stringBuilder.toString()
    }

    fun getBasalProfilesDisplayableAsStringOfArray(profile: Profile, pumpType: PumpType): String {
        val stringBuilder = java.lang.StringBuilder()
        // for (basalValue in profiles) {
        //     val basalValueValue = pumpType.determineCorrectBasalSize(basalValue.value)
        //     val hour = basalValue.timeAsSeconds / (60 * 60)
        //     stringBuilder.append((if (hour < 10) "0" else "") + hour + ":00")
        //     stringBuilder.append(String.format(Locale.ENGLISH, "%.3f", basalValueValue))
        //     stringBuilder.append(", ")
        // }
        // return if (stringBuilder.length > 3) stringBuilder.substring(0, stringBuilder.length - 2) else stringBuilder.toString()

        var entriesCopy = profile.getBasalValues()

        for (i in entriesCopy.indices) {
            val current = entriesCopy[i]
            // var currentTime = if (current.startTime_raw % 2 == 0) current.startTime_raw.toInt() else current.startTime_raw - 1
            // currentTime = currentTime * 30 / 60
            val currentTime = current.timeAsSeconds / (60 * 60)

            var lastHour: Int
            lastHour = if (i + 1 == entriesCopy.size) {
                24
            } else {
                val basalProfileEntry = entriesCopy[i + 1]
                //val rawTime = if (basalProfileEntry.startTime_raw % 2 == 0) basalProfileEntry.startTime_raw.toInt() else basalProfileEntry.startTime_raw - 1
                //rawTime * 30 / 60
                basalProfileEntry.timeAsSeconds / (60 * 60)
            }

            // System.out.println("Current time: " + currentTime + " Next Time: " + lastHour);
            for (j in currentTime until lastHour) {
                // if (pumpType == null)
                //     basalByHour[j] = current.rate
                // else
                //basalByHour[j] = pumpType.determineCorrectBasalSize(current.value)

                stringBuilder.append(String.format(Locale.ENGLISH, "%.3f", pumpType.determineCorrectBasalSize(current.value)))
                stringBuilder.append(" ")
            }
        }

        return stringBuilder.toString().trim()

    }

}