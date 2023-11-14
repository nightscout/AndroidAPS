package app.aaps.implementation.profile

import app.aaps.core.data.configuration.Constants
import app.aaps.core.interfaces.profile.DefaultValueHelper
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.Preferences
import dagger.Reusable
import javax.inject.Inject

@Reusable
class DefaultValueHelperImpl @Inject constructor(
    private val sp: SP,
    private val preferences: Preferences,
    private val profileUtil: ProfileUtil
) : DefaultValueHelper {

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