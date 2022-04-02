package info.nightscout.androidaps.plugins.iob.iobCobCalculator.data

import android.content.Context
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.Carbs
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.aps.openAPSSMB.SMBDefaults
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DataPointWithLabelInterface
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.PointsWithLabelGraphSeries
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.Scale
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import kotlin.math.min

class AutosensData(injector: HasAndroidInjector) : DataPointWithLabelInterface {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var sp: SP
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil

    inner class CarbsInPast {

        var time: Long
        var carbs: Double
        var min5minCarbImpact = 0.0
        var remaining: Double

        constructor(t: Carbs, isAAPSOrWeighted: Boolean) {
            time = t.timestamp
            carbs = t.amount
            remaining = t.amount
            val profile = profileFunction.getProfile(t.timestamp)
            if (isAAPSOrWeighted && profile != null) {
                val maxAbsorptionHours = sp.getDouble(R.string.key_absorption_maxtime, Constants.DEFAULT_MAX_ABSORPTION_TIME)
                val sens = profile.getIsfMgdl(t.timestamp)
                val ic = profile.getIc(t.timestamp)
                min5minCarbImpact = t.amount / (maxAbsorptionHours * 60 / 5) * sens / ic
                aapsLogger.debug(
                    LTag.AUTOSENS,
                    """Min 5m carbs impact for ${carbs}g @${dateUtil.dateAndTimeString(t.timestamp)} for ${maxAbsorptionHours}h calculated to $min5minCarbImpact ISF: $sens IC: $ic"""
                )
            } else {
                min5minCarbImpact = sp.getDouble(R.string.key_openapsama_min_5m_carbimpact, SMBDefaults.min_5m_carbimpact)
            }
        }

        internal constructor(other: CarbsInPast) {
            time = other.time
            carbs = other.carbs
            min5minCarbImpact = other.min5minCarbImpact
            remaining = other.remaining
        }

        override fun toString(): String =
            String.format(Locale.ENGLISH, "CarbsInPast: time: %s carbs: %.02f min5minCI: %.02f remaining: %.2f", dateUtil.dateAndTimeString(time), carbs, min5minCarbImpact, remaining)
    }

    var time = 0L
    var bg = 0.0 // mgdl
    var chartTime: Long = 0
    var pastSensitivity = ""
    var deviation = 0.0
    var validDeviation = false
    var activeCarbsList: MutableList<CarbsInPast> = ArrayList()
    var absorbed = 0.0
    var carbsFromBolus = 0.0
    var cob = 0.0
    var bgi = 0.0
    var delta = 0.0
    var avgDelta = 0.0
    var avgDeviation = 0.0
    var autosensResult = AutosensResult()
    var slopeFromMaxDeviation = 0.0
    var slopeFromMinDeviation = 999.0
    var usedMinCarbsImpact = 0.0
    var failOverToMinAbsorptionRate = false

    // Oref1
    var absorbing = false
    var mealCarbs = 0.0
    var mealStartCounter = 999
    var type = ""
    var uam = false
    var extraDeviation: MutableList<Double> = ArrayList()
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
            absorbed,
            carbsFromBolus,
            cob,
            autosensResult.ratio,
            slopeFromMaxDeviation,
            slopeFromMinDeviation,
            activeCarbsList.toString()
        )
    }

    fun cloneCarbsList(): MutableList<CarbsInPast> {
        val newActiveCarbsList: MutableList<CarbsInPast> = ArrayList()
        for (c in activeCarbsList) {
            newActiveCarbsList.add(CarbsInPast(c))
        }
        return newActiveCarbsList
    }

    // remove carbs older than timeframe
    fun removeOldCarbs(toTime: Long, isAAPSOrWeighted: Boolean) {
        val maxAbsorptionHours: Double =
            if (isAAPSOrWeighted) sp.getDouble(R.string.key_absorption_maxtime, Constants.DEFAULT_MAX_ABSORPTION_TIME)
            else sp.getDouble(R.string.key_absorption_cutoff, Constants.DEFAULT_MAX_ABSORPTION_TIME)
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

    fun deductAbsorbedCarbs() {
        var ac = absorbed
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

    // ------- DataPointWithLabelInterface ------
    var scale: Scale? = null

    override fun getX(): Double = chartTime.toDouble()
    override fun getY(): Double = scale!!.transform(cob)

    override fun setY(y: Double) {}
    override val label: String? = null
    override val duration = 0L
    override val shape = PointsWithLabelGraphSeries.Shape.COBFAILOVER
    override val size = 0.5f
    override fun color(context: Context?): Int {
        return rh.gac(context,R.attr.cobColor)
    }

    init {
        injector.androidInjector().inject(this)
    }
}