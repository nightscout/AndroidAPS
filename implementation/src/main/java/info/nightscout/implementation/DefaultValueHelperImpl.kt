package info.nightscout.implementation

import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.profile.DefaultValueHelper
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class DefaultValueHelperImpl @Inject constructor(
    private val sp: SP,
    private val profileFunction: ProfileFunction
) : DefaultValueHelper {

    /**
     * returns the corresponding EatingSoon TempTarget based on the given units (MMOL / MGDL)
     *
     * @param units
     * @return
     */
    private fun getDefaultEatingSoonTT(units: GlucoseUnit): Double {
        return if (GlucoseUnit.MMOL == units) Constants.defaultEatingSoonTTmmol else Constants.defaultEatingSoonTTmgdl
    }

    /**
     * returns the corresponding Activity TempTarget based on the given units (MMOL / MGDL)
     *
     * @param units
     * @return
     */
    private fun getDefaultActivityTT(units: GlucoseUnit): Double {
        return if (GlucoseUnit.MMOL == units) Constants.defaultActivityTTmmol else Constants.defaultActivityTTmgdl
    }

    /**
     * returns the corresponding Hypo TempTarget based on the given units (MMOL / MGDL)
     *
     * @param units
     * @return
     */
    private fun getDefaultHypoTT(units: GlucoseUnit): Double {
        return if (GlucoseUnit.MMOL == units) Constants.defaultHypoTTmmol else Constants.defaultHypoTTmgdl
    }

    /**
     * returns the configured EatingSoon TempTarget, if this is set to 0, the Default-Value is returned.
     *
     * @return
     */
    override fun determineEatingSoonTT(): Double {
        val units = profileFunction.getUnits()
        var value = sp.getDouble(info.nightscout.core.utils.R.string.key_eatingsoon_target, getDefaultEatingSoonTT(units))
        value = Profile.toCurrentUnits(profileFunction, value)
        return if (value > 0) value else getDefaultEatingSoonTT(units)
    }

    override fun determineEatingSoonTTDuration(): Int {
        val value = sp.getInt(info.nightscout.core.utils.R.string.key_eatingsoon_duration, Constants.defaultEatingSoonTTDuration)
        return if (value > 0) value else Constants.defaultEatingSoonTTDuration
    }

    /**
     * returns the configured Activity TempTarget, if this is set to 0, the Default-Value is returned.
     *
     * @return
     */
    override fun determineActivityTT(): Double {
        val units = profileFunction.getUnits()
        var value = sp.getDouble(info.nightscout.core.utils.R.string.key_activity_target, getDefaultActivityTT(units))
        value = Profile.toCurrentUnits(profileFunction, value)
        return if (value > 0) value else getDefaultActivityTT(units)
    }

    override fun determineActivityTTDuration(): Int {
        val value = sp.getInt(info.nightscout.core.utils.R.string.key_activity_duration, Constants.defaultActivityTTDuration)
        return if (value > 0) value else Constants.defaultActivityTTDuration
    }

    /**
     * returns the configured Hypo TempTarget, if this is set to 0, the Default-Value is returned.
     *
     * @return
     */
    override fun determineHypoTT(): Double {
        val units = profileFunction.getUnits()
        var value = sp.getDouble(info.nightscout.core.utils.R.string.key_hypo_target, getDefaultHypoTT(units))
        value = Profile.toCurrentUnits(profileFunction, value)
        return if (value > 0) value else getDefaultHypoTT(units)
    }

    override fun determineHypoTTDuration(): Int {
        val value = sp.getInt(info.nightscout.core.utils.R.string.key_hypo_duration, Constants.defaultHypoTTDuration)
        return if (value > 0) value else Constants.defaultHypoTTDuration
    }

    override var bgTargetLow = 80.0
    override var bgTargetHigh = 180.0

    override fun determineHighLine(): Double {
        var highLineSetting = sp.getDouble(info.nightscout.core.utils.R.string.key_high_mark, bgTargetHigh)
        if (highLineSetting < 1) highLineSetting = Constants.HIGH_MARK
        highLineSetting = Profile.toCurrentUnits(profileFunction, highLineSetting)
        return highLineSetting
    }

    override fun determineLowLine(): Double {
        var lowLineSetting = sp.getDouble(info.nightscout.core.utils.R.string.key_low_mark, bgTargetLow)
        if (lowLineSetting < 1) lowLineSetting = Constants.LOW_MARK
        lowLineSetting = Profile.toCurrentUnits(profileFunction, lowLineSetting)
        return lowLineSetting
    }
}