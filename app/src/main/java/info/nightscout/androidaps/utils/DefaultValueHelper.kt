package info.nightscout.androidaps.utils

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions

object DefaultValueHelper {
    /**
     * returns the corresponding EatingSoon TempTarget based on the given units (MMOL / MGDL)
     *
     * @param units
     * @return
     */
    fun getDefaultEatingSoonTT(units: String): Double {
        return if (Constants.MMOL == units) Constants.defaultEatingSoonTTmmol else Constants.defaultEatingSoonTTmgdl
    }

    /**
     * returns the corresponding Activity TempTarget based on the given units (MMOL / MGDL)
     *
     * @param units
     * @return
     */
    fun getDefaultActivityTT(units: String): Double {
        return if (Constants.MMOL == units) Constants.defaultActivityTTmmol else Constants.defaultActivityTTmgdl
    }

    /**
     * returns the corresponding Hypo TempTarget based on the given units (MMOL / MGDL)
     *
     * @param units
     * @return
     */
    fun getDefaultHypoTT(units: String): Double {
        return if (Constants.MMOL == units) Constants.defaultHypoTTmmol else Constants.defaultHypoTTmgdl
    }

    /**
     * returns the configured EatingSoon TempTarget, if this is set to 0, the Default-Value is returned.
     *
     * @return
     */
    @JvmStatic
    fun determineEatingSoonTT(): Double {
        val units = ProfileFunctions.getSystemUnits()
        var value = SP.getDouble(R.string.key_eatingsoon_target, getDefaultEatingSoonTT(units))
        value = Profile.toCurrentUnits(value)
        return if (value > 0) value else getDefaultEatingSoonTT(units)
    }

    @JvmStatic
    fun determineEatingSoonTTDuration(): Int {
        val value = SP.getInt(R.string.key_eatingsoon_duration, Constants.defaultEatingSoonTTDuration)
        return if (value > 0) value else Constants.defaultEatingSoonTTDuration
    }

    /**
     * returns the configured Activity TempTarget, if this is set to 0, the Default-Value is returned.
     *
     * @return
     */
    @JvmStatic
    fun determineActivityTT(): Double {
        val units = ProfileFunctions.getSystemUnits()
        var value = SP.getDouble(R.string.key_activity_target, getDefaultActivityTT(units))
        value = Profile.toCurrentUnits(value)
        return if (value > 0) value else getDefaultActivityTT(units)
    }

    @JvmStatic
    fun determineActivityTTDuration(): Int {
        val value = SP.getInt(R.string.key_activity_duration, Constants.defaultActivityTTDuration)
        return if (value > 0) value else Constants.defaultActivityTTDuration
    }

    /**
     * returns the configured Hypo TempTarget, if this is set to 0, the Default-Value is returned.
     *
     * @return
     */
    @JvmStatic
    fun determineHypoTT(): Double {
        val units = ProfileFunctions.getSystemUnits()
        var value = SP.getDouble(R.string.key_hypo_target, getDefaultHypoTT(units))
        value = Profile.toCurrentUnits(value)
        return if (value > 0) value else getDefaultHypoTT(units)
    }

    @JvmStatic
    fun determineHypoTTDuration(): Int {
        val value = SP.getInt(R.string.key_hypo_duration, Constants.defaultHypoTTDuration)
        return if (value > 0) value else Constants.defaultHypoTTDuration
    }
}