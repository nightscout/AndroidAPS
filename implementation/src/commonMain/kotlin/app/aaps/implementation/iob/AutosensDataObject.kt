package app.aaps.implementation.iob

import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.format.NumberFormatPlatform
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlin.math.min

// Deliberately NOT @SingleIn: the @Binds this replaces had no scope. AutosensData is a value object
// computed per run, so each caller is meant to get its own.
@ContributesBinding(AppScope::class)
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

    /**
     * A debug line, so the separator must not follow the phone's locale.
     *
     * `String.format(Locale.ENGLISH, "%.02f", …)` is JVM only. [NumberFormat.withDecimalsHalfUp] is
     * the multiplatform equivalent: same two fixed decimals, and the same ties-away-from-zero rounding
     * that `%.02f` does - `DECIMAL_2` would round half to even instead. [SEPARATOR_DOT] stands in for
     * `Locale.ENGLISH`, so a Czech phone keeps logging `1.50` rather than `1,50`.
     *
     * `AutosensDataObjectTest` pins the whole line, spacing included.
     */
    override fun toString(): String {
        fun d(value: Double) = twoDecimals.format(value, NumberFormatPlatform.SEPARATOR_DOT)
        return "AutosensData: ${dateUtil.dateAndTimeString(time)} pastSensitivity=$pastSensitivity " +
            " delta=${d(delta)}  avgDelta=${d(avgDelta)} bgi=${d(bgi)} deviation=${d(deviation)}" +
            " avgDeviation=${d(avgDeviation)} absorbed=${d(this5MinAbsorption)}" +
            " carbsFromBolus=${d(carbsFromBolus)} cob=${d(cob)} autosensRatio=${d(autosensResult.ratio)}" +
            " slopeFromMaxDeviation=${d(slopeFromMaxDeviation)} slopeFromMinDeviation=${d(slopeFromMinDeviation)}" +
            " activeCarbsList=$activeCarbsList"
    }

    private companion object {

        val twoDecimals = NumberFormat.withDecimalsHalfUp(2)
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