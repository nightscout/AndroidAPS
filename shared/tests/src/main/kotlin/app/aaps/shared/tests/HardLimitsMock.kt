package app.aaps.shared.tests

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

@Suppress("unused")
class HardLimitsMock @Inject constructor(
    private val preferences: Preferences,
    private val rh: ResourceHelper
) : HardLimits {

    // The limits themselves come from HardLimits, so the mock can never drift from the real values.
    // Only verifyHardLimits differs: it just clamps, without the notification and database side effects.

    private fun loadAge(): HardLimits.AgeType {
        val stored = preferences.get(StringKey.SafetyAge)
        val values = ageEntryValues()
        return HardLimits.AgeType.entries.firstOrNull { values[it.ordinal] == stored } ?: HardLimits.AgeType.ADULT
    }

    override fun maxBolus(): Double = HardLimits.MAX_BOLUS.getValue(loadAge())
    override fun maxIobAMA(): Double = HardLimits.MAX_IOB_AMA.getValue(loadAge())
    override fun maxIobSMB(): Double = HardLimits.MAX_IOB_SMB.getValue(loadAge())
    override fun maxBasal(): Double = HardLimits.MAX_BASAL.getValue(loadAge())
    override fun diaRange(): ClosedFloatingPointRange<Double> = HardLimits.LIMIT_DIA.getValue(loadAge())
    override fun peakRange(): IntRange = HardLimits.LIMIT_PEAK
    override fun icRange(): ClosedFloatingPointRange<Double> = HardLimits.LIMIT_IC.getValue(loadAge())

    // safety checks
    override fun checkHardLimits(value: Double, valueName: Int, lowLimit: Double, highLimit: Double): Boolean =
        value == verifyHardLimits(value, valueName, lowLimit, highLimit)

    override fun checkHardLimits(value: Double, valueName: TextRef, lowLimit: Double, highLimit: Double): Boolean =
        value == verifyHardLimits(value, valueName, lowLimit, highLimit)

    // Both forms clamp identically here; the name is only used for the message the real one posts.
    override fun verifyHardLimits(value: Double, valueName: TextRef, lowLimit: Double, highLimit: Double): Double =
        verifyHardLimits(value, 0, lowLimit, highLimit)

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