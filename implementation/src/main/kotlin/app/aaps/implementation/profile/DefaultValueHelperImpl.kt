package app.aaps.implementation.profile

import app.aaps.annotations.OpenForTesting
import app.aaps.core.interfaces.configuration.Constants
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.profile.DefaultValueHelper
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class DefaultValueHelperImpl @Inject constructor(
    private val sp: SP,
    private val profileUtil: ProfileUtil
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
        val units = profileUtil.units
        var value = sp.getDouble(app.aaps.core.utils.R.string.key_eatingsoon_target, getDefaultEatingSoonTT(units))
        value = profileUtil.valueInCurrentUnitsDetect(value)
        return if (value > 0) value else getDefaultEatingSoonTT(units)
    }

    override fun determineEatingSoonTTDuration(): Int {
        val value = sp.getInt(app.aaps.core.utils.R.string.key_eatingsoon_duration, Constants.defaultEatingSoonTTDuration)
        return if (value > 0) value else Constants.defaultEatingSoonTTDuration
    }

    /**
     * returns the configured Activity TempTarget, if this is set to 0, the Default-Value is returned.
     *
     * @return
     */
    override fun determineActivityTT(): Double {
        val units = profileUtil.units
        var value = sp.getDouble(app.aaps.core.utils.R.string.key_activity_target, getDefaultActivityTT(units))
        value = profileUtil.valueInCurrentUnitsDetect(value)
        return if (value > 0) value else getDefaultActivityTT(units)
    }

    override fun determineActivityTTDuration(): Int {
        val value = sp.getInt(app.aaps.core.utils.R.string.key_activity_duration, Constants.defaultActivityTTDuration)
        return if (value > 0) value else Constants.defaultActivityTTDuration
    }

    /**
     * returns the configured Hypo TempTarget, if this is set to 0, the Default-Value is returned.
     *
     * @return
     */
    override fun determineHypoTT(): Double {
        val units = profileUtil.units
        var value = sp.getDouble(app.aaps.core.utils.R.string.key_hypo_target, getDefaultHypoTT(units))
        value = profileUtil.valueInCurrentUnitsDetect(value)
        return if (value > 0) value else getDefaultHypoTT(units)
    }

    override fun determineHypoTTDuration(): Int {
        val value = sp.getInt(app.aaps.core.utils.R.string.key_hypo_duration, Constants.defaultHypoTTDuration)
        return if (value > 0) value else Constants.defaultHypoTTDuration
    }

    override var bgTargetLow = 80.0
    override var bgTargetHigh = 180.0

    override fun determineHighLine(): Double {
        var highLineSetting = sp.getDouble(app.aaps.core.utils.R.string.key_high_mark, bgTargetHigh)
        if (highLineSetting < 1) highLineSetting = Constants.HIGH_MARK
        highLineSetting = profileUtil.valueInCurrentUnitsDetect(highLineSetting)
        return highLineSetting
    }

    override fun determineLowLine(): Double {
        var lowLineSetting = sp.getDouble(app.aaps.core.utils.R.string.key_low_mark, bgTargetLow)
        if (lowLineSetting < 1) lowLineSetting = Constants.LOW_MARK
        lowLineSetting = profileUtil.valueInCurrentUnitsDetect(lowLineSetting)
        return lowLineSetting
    }
}