package info.nightscout.androidaps.utils

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class DefaultValueHelper @Inject constructor(
    private val sp: SP,
    private val profileFunction: ProfileFunction
) {

    /**
     * returns the corresponding EatingSoon TempTarget based on the given units (MMOL / MGDL)
     *
     * @param units
     * @return
     */
    private fun getDefaultEatingSoonTT(units: String): Double {
        return if (Constants.MMOL == units) Constants.defaultEatingSoonTTmmol else Constants.defaultEatingSoonTTmgdl
    }

    /**
     * returns the corresponding Activity TempTarget based on the given units (MMOL / MGDL)
     *
     * @param units
     * @return
     */
    private fun getDefaultActivityTT(units: String): Double {
        return if (Constants.MMOL == units) Constants.defaultActivityTTmmol else Constants.defaultActivityTTmgdl
    }

    /**
     * returns the corresponding Hypo TempTarget based on the given units (MMOL / MGDL)
     *
     * @param units
     * @return
     */
    private fun getDefaultHypoTT(units: String): Double {
        return if (Constants.MMOL == units) Constants.defaultHypoTTmmol else Constants.defaultHypoTTmgdl
    }

    /**
     * returns the configured EatingSoon TempTarget, if this is set to 0, the Default-Value is returned.
     *
     * @return
     */
    fun determineEatingSoonTT(): Double {
        val units = profileFunction.getUnits()
        var value = sp.getDouble(R.string.key_eatingsoon_target, getDefaultEatingSoonTT(units))
        value = Profile.toCurrentUnits(profileFunction, value)
        return if (value > 0) value else getDefaultEatingSoonTT(units)
    }

    fun determineEatingSoonTTDuration(): Int {
        val value = sp.getInt(R.string.key_eatingsoon_duration, Constants.defaultEatingSoonTTDuration)
        return if (value > 0) value else Constants.defaultEatingSoonTTDuration
    }

    /**
     * returns the configured Activity TempTarget, if this is set to 0, the Default-Value is returned.
     *
     * @return
     */
    fun determineActivityTT(): Double {
        val units = profileFunction.getUnits()
        var value = sp.getDouble(R.string.key_activity_target, getDefaultActivityTT(units))
        value = Profile.toCurrentUnits(profileFunction, value)
        return if (value > 0) value else getDefaultActivityTT(units)
    }

    fun determineActivityTTDuration(): Int {
        val value = sp.getInt(R.string.key_activity_duration, Constants.defaultActivityTTDuration)
        return if (value > 0) value else Constants.defaultActivityTTDuration
    }

    /**
     * returns the configured Hypo TempTarget, if this is set to 0, the Default-Value is returned.
     *
     * @return
     */
    fun determineHypoTT(): Double {
        val units = profileFunction.getUnits()
        var value = sp.getDouble(R.string.key_hypo_target, getDefaultHypoTT(units))
        value = Profile.toCurrentUnits(profileFunction, value)
        return if (value > 0) value else getDefaultHypoTT(units)
    }

    fun determineHypoTTDuration(): Int {
        val value = sp.getInt(R.string.key_hypo_duration, Constants.defaultHypoTTDuration)
        return if (value > 0) value else Constants.defaultHypoTTDuration
    }

    var bgTargetLow = 80.0
    var bgTargetHigh = 180.0

    fun determineHighLine(): Double {
        var highLineSetting = sp.getDouble(R.string.key_high_mark, bgTargetHigh)
        if (highLineSetting < 1) highLineSetting = Constants.HIGHMARK
        highLineSetting = Profile.toCurrentUnits(profileFunction, highLineSetting)
        return highLineSetting
    }

    fun determineLowLine(): Double {
        var lowLineSetting = sp.getDouble(R.string.key_low_mark, bgTargetLow)
        if (lowLineSetting < 1) lowLineSetting = Constants.LOWMARK
        lowLineSetting = Profile.toCurrentUnits(profileFunction, lowLineSetting)
        return lowLineSetting
    }
}