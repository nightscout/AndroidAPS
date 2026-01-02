package app.aaps.implementation.iob

import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import java.util.Locale
import javax.inject.Inject
import kotlin.math.min

class AutosensDataObject @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val dateUtil: DateUtil
) : AutosensData {

    override var time = 0L
    override var bg = 0.0 // mgdl
    override var sens = 0.0
    override var pastSensitivity = ""
    override var deviation = 0.0
    override var validDeviation = false
    override var activeCarbsList: MutableList<AutosensData.CarbsInPast> = ArrayList()
    override var this5MinAbsorption = 0.0
    override var carbsFromBolus = 0.0
    override var cob = 0.0
    override var bgi = 0.0
    override var delta = 0.0
    override var avgDelta = 0.0
    override var avgDeviation = 0.0
    override var autosensResult = AutosensResult()
    override var slopeFromMaxDeviation = 0.0
    override var slopeFromMinDeviation = 999.0
    override var usedMinCarbsImpact = 0.0
    override var failOverToMinAbsorptionRate = false

    // Oref1
    override var absorbing = false
    override var mealCarbs = 0.0
    override var mealStartCounter = 999
    override var type = ""
    override var uam = false
    override var extraDeviation: MutableList<Double> = ArrayList()
    private fun fromCarbsInPast(other: AutosensData.CarbsInPast): AutosensData.CarbsInPast =
        AutosensData.CarbsInPast(
            time = other.time,
            carbs = other.carbs,
            min5minCarbImpact = other.min5minCarbImpact,
            remaining = other.remaining
        )

    override fun toString(): String {
        return String.format(
            Locale.ENGLISH,
            "AutosensData: %s pastSensitivity=%s  delta=%.02f  avgDelta=%.02f bgi=%.02f deviation=%.02f avgDeviation=%.02f absorbed=%.02f carbsFromBolus=%.02f cob=%.02f autosensRatio=%.02f slopeFromMaxDeviation=%.02f slopeFromMinDeviation=%.02f activeCarbsList=%s",
            dateUtil.dateAndTimeString(time),
            pastSensitivity,
            delta,
            avgDelta,
            bgi,
            deviation,
            avgDeviation,
            this5MinAbsorption,
            carbsFromBolus,
            cob,
            autosensResult.ratio,
            slopeFromMaxDeviation,
            slopeFromMinDeviation,
            activeCarbsList.toString()
        )
    }

    override fun cloneCarbsList(): MutableList<AutosensData.CarbsInPast> {
        val newActiveCarbsList: MutableList<AutosensData.CarbsInPast> = ArrayList()
        for (c in activeCarbsList) {
            newActiveCarbsList.add(fromCarbsInPast(c))
        }
        return newActiveCarbsList
    }

    // remove carbs older than timeframe
    override fun removeOldCarbs(toTime: Long, isAAPSOrWeighted: Boolean) {
        val maxAbsorptionHours: Double =
            if (isAAPSOrWeighted) preferences.get(DoubleKey.AbsorptionMaxTime)
            else preferences.get(DoubleKey.AbsorptionCutOff)
        var i = 0
        while (i < activeCarbsList.size) {
            val c = activeCarbsList[i]
            if (c.time + maxAbsorptionHours * 60 * 60 * 1000L < toTime) {
                activeCarbsList.removeAt(i--)
                if (c.remaining > 0) cob -= c.remaining
                aapsLogger.debug(LTag.AUTOSENS, "Removing carbs at " + dateUtil.dateAndTimeString(toTime) + " after " + maxAbsorptionHours + "h > " + c.toString())
            }
            i++
        }
    }

    override fun deductAbsorbedCarbs() {
        var ac = this5MinAbsorption
        var i = 0
        while (i < activeCarbsList.size && ac > 0) {
            val c = activeCarbsList[i]
            if (c.remaining > 0) {
                val sub = min(ac, c.remaining)
                c.remaining -= sub
                ac -= sub
            }
            i++
        }
    }
}