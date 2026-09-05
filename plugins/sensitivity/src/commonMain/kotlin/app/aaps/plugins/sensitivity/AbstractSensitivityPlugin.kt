package app.aaps.plugins.sensitivity

import androidx.collection.LongSparseArray
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.Sensitivity
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import kotlin.math.max
import kotlin.math.min

abstract class AbstractSensitivityPlugin(
    pluginDescription: PluginDescription,
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    protected val preferences: Preferences
) : PluginBase(pluginDescription, aapsLogger, rh), Sensitivity {

    abstract override fun detectSensitivity(
        ads: AutosensDataStore,
        fromTime: Long,
        toTime: Long,
        profile: EffectiveProfile?,
        siteChanges: List<TE>,
        profileSwitches: List<PS>
    ): AutosensResult

    fun fillResult(
        ratio: Double, carbsAbsorbed: Double, pastSensitivity: String,
        ratioLimit: String, sensResult: String, deviationsArraySize: Int
    ): AutosensResult {
        return fillResult(
            ratio, carbsAbsorbed, pastSensitivity, ratioLimit, sensResult,
            deviationsArraySize,
            preferences.get(DoubleKey.AutosensMin),
            preferences.get(DoubleKey.AutosensMax)
        )
    }

    fun fillResult(
        ratioParam: Double, carbsAbsorbed: Double, pastSensitivity: String,
        ratioLimitParam: String, sensResult: String, deviationsArraySize: Int,
        ratioMin: Double, ratioMax: Double
    ): AutosensResult {
        var ratio = ratioParam
        var ratioLimit = ratioLimitParam
        val rawRatio = ratio
        ratio = max(ratio, ratioMin)
        ratio = min(ratio, ratioMax)

        //If not-excluded data <= MIN_HOURS -> don't do Autosens
        //If not-excluded data >= MIN_HOURS_FULL_AUTOSENS -> full Autosens
        //Between MIN_HOURS and MIN_HOURS_FULL_AUTOSENS: gradually increase autosens
        val autosensContrib = (min(
            max(Sensitivity.MIN_HOURS, deviationsArraySize / 12.0),
            Sensitivity.MIN_HOURS_FULL_AUTOSENS
        ) - Sensitivity.MIN_HOURS) / (Sensitivity.MIN_HOURS_FULL_AUTOSENS - Sensitivity.MIN_HOURS)
        ratio = autosensContrib * (ratio - 1) + 1
        if (autosensContrib != 1.0) {
            ratioLimit += "(" + deviationsArraySize + " of " + Sensitivity.MIN_HOURS_FULL_AUTOSENS * 12 + " values) "
        }
        if (ratio != rawRatio) {
            ratioLimit += "Ratio limited from $rawRatio to $ratio"
            aapsLogger.debug(LTag.AUTOSENS, ratioLimit)
        }
        val output = AutosensResult()
        output.ratio = Round.roundTo(ratio, 0.01)
        output.carbsAbsorbed = Round.roundTo(carbsAbsorbed, 0.01)
        output.pastSensitivity = pastSensitivity
        output.ratioLimit = ratioLimit
        output.sensResult = sensResult
        return output
    }

    /**
     * First index in the time-sorted [table] whose time is at or after [time], using the
     * LongSparseArray binary search. Lets a scan start at the detection window instead of walking
     * the whole table on every call.
     */
    protected fun firstIndexAtOrAfter(table: LongSparseArray<AutosensData>, time: Long): Int {
        val i = table.indexOfKey(time)
        return if (i >= 0) i else i.inv()
    }
}
