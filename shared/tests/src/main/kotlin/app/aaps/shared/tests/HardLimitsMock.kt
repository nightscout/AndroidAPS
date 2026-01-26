package app.aaps.shared.tests

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

@Suppress("unused")
class HardLimitsMock @Inject constructor(
    private val preferences: Preferences,
    private val rh: ResourceHelper
) : HardLimits {

    companion object {

        private const val CHILD = 0
        private const val TEENAGE = 1
        private const val ADULT = 2
        private const val RESISTANT_ADULT = 3
        private const val PREGNANT = 4
        private val MAX_BOLUS = doubleArrayOf(5.0, 10.0, 17.0, 25.0, 60.0)

        // Very Hard Limits Ranges
        // First value is the Lowest and second value is the Highest a Limit can define
        val VERY_HARD_LIMIT_MIN_BG = doubleArrayOf(80.0, 180.0)
        val VERY_HARD_LIMIT_MAX_BG = doubleArrayOf(90.0, 200.0)
        val VERY_HARD_LIMIT_TARGET_BG = doubleArrayOf(80.0, 200.0)

        // Very Hard Limits Ranges for Temp Targets
        val VERY_HARD_LIMIT_TEMP_MIN_BG = intArrayOf(72, 180)
        val VERY_HARD_LIMIT_TEMP_MAX_BG = intArrayOf(72, 270)
        val VERY_HARD_LIMIT_TEMP_TARGET_BG = intArrayOf(72, 200)
        val MIN_DIA = doubleArrayOf(5.0, 5.0, 5.0, 5.0, 5.0)
        val MAX_DIA = doubleArrayOf(9.0, 9.0, 9.0, 9.0, 10.0)
        const val MIN_PEAK = 35 // mgdl
        const val MAX_PEAK = 120 // mgdl
        val MIN_IC = doubleArrayOf(2.0, 2.0, 2.0, 2.0, 0.3)
        val MAX_IC = doubleArrayOf(100.0, 100.0, 100.0, 100.0, 100.0)
        const val MIN_ISF = 2.0 // mgdl
        const val MAX_ISF = 1000.0 // mgdl
        val MAX_IOB_AMA = doubleArrayOf(3.0, 5.0, 7.0, 12.0, 25.0)
        val MAX_IOB_SMB = doubleArrayOf(7.0, 13.0, 22.0, 30.0, 70.0)
        val MAX_BASAL = doubleArrayOf(2.0, 5.0, 10.0, 12.0, 25.0)

        //LGS Hard limits
        //No IOB at all
        const val MAX_IOB_LGS = 0.0

    }

    private fun loadAge(): Int = when (preferences.get(StringKey.SafetyAge)) {
        ageEntryValues()[HardLimits.AgeType.CHILD.ordinal]           -> HardLimits.AgeType.CHILD.ordinal
        ageEntryValues()[HardLimits.AgeType.TEENAGE.ordinal]         -> HardLimits.AgeType.TEENAGE.ordinal
        ageEntryValues()[HardLimits.AgeType.ADULT.ordinal]           -> HardLimits.AgeType.ADULT.ordinal
        ageEntryValues()[HardLimits.AgeType.RESISTANT_ADULT.ordinal] -> HardLimits.AgeType.RESISTANT_ADULT.ordinal
        ageEntryValues()[HardLimits.AgeType.PREGNANT.ordinal]        -> HardLimits.AgeType.PREGNANT.ordinal
        else                                                         -> HardLimits.AgeType.ADULT.ordinal
    }

    override fun maxBolus(): Double = MAX_BOLUS[loadAge()]
    override fun maxIobAMA(): Double = MAX_IOB_AMA[loadAge()]
    override fun maxIobSMB(): Double = MAX_IOB_SMB[loadAge()]
    override fun maxBasal(): Double = MAX_BASAL[loadAge()]
    override fun minDia(): Double = MIN_DIA[loadAge()]
    override fun maxDia(): Double = MAX_DIA[loadAge()]
    override fun minPeak(): Int = MIN_PEAK
    override fun maxPeak(): Int = MAX_PEAK
    override fun minIC(): Double = MIN_IC[loadAge()]
    override fun maxIC(): Double = MAX_IC[loadAge()]

    // safety checks
    override fun checkHardLimits(value: Double, valueName: Int, lowLimit: Double, highLimit: Double): Boolean =
        value == verifyHardLimits(value, valueName, lowLimit, highLimit)

    override fun isInRange(value: Double, lowLimit: Double, highLimit: Double): Boolean =
        value in lowLimit..highLimit

    override fun verifyHardLimits(value: Double, valueName: Int, lowLimit: Double, highLimit: Double): Double {
        var newValue = value
        if (newValue < lowLimit || newValue > highLimit) {
            newValue = max(newValue, lowLimit)
            newValue = min(newValue, highLimit)
        }
        return newValue
    }

    override fun ageEntries() = arrayOf<CharSequence>(
        rh.gs(app.aaps.core.ui.R.string.child),
        rh.gs(app.aaps.core.ui.R.string.teenage),
        rh.gs(app.aaps.core.ui.R.string.adult),
        rh.gs(app.aaps.core.ui.R.string.resistant_adult),
        rh.gs(app.aaps.core.ui.R.string.pregnant),
    )

    @Suppress("SpellCheckingInspection")
    override fun ageEntryValues() = arrayOf<CharSequence>(
        "child",
        "teenage",
        "adult",
        "resistantadult",
        "pregnant"
    )
}