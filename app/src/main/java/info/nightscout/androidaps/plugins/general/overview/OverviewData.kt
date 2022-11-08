package info.nightscout.androidaps.plugins.general.overview

import android.content.Context
import androidx.annotation.ColorInt
import com.jjoe64.graphview.series.BarGraphSeries
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.extensions.convertedToPercent
import info.nightscout.androidaps.extensions.isInProgress
import info.nightscout.androidaps.extensions.toStringFull
import info.nightscout.androidaps.extensions.toStringShort
import info.nightscout.androidaps.extensions.valueToUnits
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DataPointWithLabelInterface
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.FixedLineGraphSeries
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.PointsWithLabelGraphSeries
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.Scale
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.ScaledDataPoint
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.CobInfo
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverviewData @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val sp: SP,
    private val activePlugin: ActivePlugin,
    private val defaultValueHelper: DefaultValueHelper,
    private val profileFunction: ProfileFunction,
    private val repository: AppRepository,
    private val fabricPrivacy: FabricPrivacy
) {

    var rangeToDisplay = 6 // for graph
    var toTime: Long = 0
    var fromTime: Long = 0
    var endTime: Long = 0

    fun reset() {
        pumpStatus = ""
        calcProgressPct = 100
        bgReadingsArray = ArrayList()
        bucketedGraphSeries = PointsWithLabelGraphSeries()
        bgReadingGraphSeries = PointsWithLabelGraphSeries()
        predictionsGraphSeries = PointsWithLabelGraphSeries()
        baseBasalGraphSeries = LineGraphSeries()
        tempBasalGraphSeries = LineGraphSeries()
        basalLineGraphSeries = LineGraphSeries()
        absoluteBasalGraphSeries = LineGraphSeries()
        activitySeries = FixedLineGraphSeries()
        activityPredictionSeries = FixedLineGraphSeries()
        iobSeries = FixedLineGraphSeries()
        absIobSeries = FixedLineGraphSeries()
        iobPredictions1Series = PointsWithLabelGraphSeries()
        //iobPredictions2Series = PointsWithLabelGraphSeries()
        minusBgiSeries = FixedLineGraphSeries()
        minusBgiHistSeries = FixedLineGraphSeries()
        cobSeries = FixedLineGraphSeries()
        cobMinFailOverSeries = PointsWithLabelGraphSeries()
        deviationsSeries = BarGraphSeries()
        ratioSeries = LineGraphSeries()
        dsMaxSeries = LineGraphSeries()
        dsMinSeries = LineGraphSeries()
        treatmentsSeries = PointsWithLabelGraphSeries()
        epsSeries = PointsWithLabelGraphSeries()
    }

    fun initRange() {
        rangeToDisplay = sp.getInt(R.string.key_rangetodisplay, 6)

        val calendar = Calendar.getInstance().also {
            it.timeInMillis = System.currentTimeMillis()
            it[Calendar.MILLISECOND] = 0
            it[Calendar.SECOND] = 0
            it[Calendar.MINUTE] = 0
            it.add(Calendar.HOUR, 1)
        }

        toTime = calendar.timeInMillis + 100000 // little bit more to avoid wrong rounding - GraphView specific
        fromTime = toTime - T.hours(rangeToDisplay.toLong()).msecs()
        endTime = toTime
    }

    /*
     * PUMP STATUS
     */

    var pumpStatus: String = ""

    /*
     * CALC PROGRESS
     */

    var calcProgressPct: Int = 100

    /*
     * BG
     */

    val lastBg: GlucoseValue?
        get() =
            repository.getLastGlucoseValueWrapped().blockingGet().let { gvWrapped ->
                if (gvWrapped is ValueWrapper.Existing) gvWrapped.value
                else null
            }

    val isLow: Boolean
        get() = lastBg?.let { lastBg ->
            lastBg.valueToUnits(profileFunction.getUnits(), sp) < defaultValueHelper.determineLowLine()
        } ?: false

    val isHigh: Boolean
        get() = lastBg?.let { lastBg ->
            lastBg.valueToUnits(profileFunction.getUnits(), sp) > defaultValueHelper.determineHighLine()
        } ?: false

    @ColorInt
    fun lastBgColor(context: Context?): Int =
        when {
            isLow  -> rh.gac(context, R.attr.bgLow)
            isHigh -> rh.gac(context, R.attr.highColor)
            else   -> rh.gac(context, R.attr.bgInRange)
        }

    val lastBgDescription: String
        get() = when {
            isLow  -> rh.gs(R.string.a11y_low)
            isHigh -> rh.gs(R.string.a11y_high)
            else   -> rh.gs(R.string.a11y_inrange)
        }

    val isActualBg: Boolean
        get() =
            lastBg?.let { lastBg ->
                lastBg.timestamp > dateUtil.now() - T.mins(9).msecs()
            } ?: false

    /*
     * TEMPORARY BASAL
     */

    fun temporaryBasalText(iobCobCalculator: IobCobCalculator): String =
        profileFunction.getProfile()?.let { profile ->
            var temporaryBasal = iobCobCalculator.getTempBasalIncludingConvertedExtended(dateUtil.now())
            if (temporaryBasal?.isInProgress == false) temporaryBasal = null
            temporaryBasal?.let { "T:" + it.toStringShort() }
                ?: rh.gs(R.string.pump_basebasalrate, profile.getBasal())
        } ?: rh.gs(R.string.notavailable)

    fun temporaryBasalDialogText(iobCobCalculator: IobCobCalculator): String =
        profileFunction.getProfile()?.let { profile ->
            iobCobCalculator.getTempBasalIncludingConvertedExtended(dateUtil.now())?.let { temporaryBasal ->
                "${rh.gs(R.string.basebasalrate_label)}: ${rh.gs(R.string.pump_basebasalrate, profile.getBasal())}" +
                    "\n" + rh.gs(R.string.tempbasal_label) + ": " + temporaryBasal.toStringFull(profile, dateUtil)
            }
                ?: "${rh.gs(R.string.basebasalrate_label)}: ${rh.gs(R.string.pump_basebasalrate, profile.getBasal())}"
        } ?: rh.gs(R.string.notavailable)

    fun temporaryBasalIcon(iobCobCalculator: IobCobCalculator): Int =
        profileFunction.getProfile()?.let { profile ->
            iobCobCalculator.getTempBasalIncludingConvertedExtended(dateUtil.now())?.let { temporaryBasal ->
                val percentRate = temporaryBasal.convertedToPercent(dateUtil.now(), profile)
                when {
                    percentRate > 100 -> R.drawable.ic_cp_basal_tbr_high
                    percentRate < 100 -> R.drawable.ic_cp_basal_tbr_low
                    else              -> R.drawable.ic_cp_basal_no_tbr
                }
            }
        } ?: R.drawable.ic_cp_basal_no_tbr

    fun temporaryBasalColor(context: Context?, iobCobCalculator: IobCobCalculator): Int = iobCobCalculator.getTempBasalIncludingConvertedExtended(dateUtil.now())?.let { rh.gac(context , R.attr.basal) }
            ?: rh.gac(context, R.attr.defaultTextColor)

    /*
     * EXTENDED BOLUS
    */

    fun extendedBolusText(iobCobCalculator: IobCobCalculator): String =
        iobCobCalculator.getExtendedBolus(dateUtil.now())?.let { extendedBolus ->
            if (!extendedBolus.isInProgress(dateUtil)) ""
            else if (!activePlugin.activePump.isFakingTempsByExtendedBoluses) rh.gs(R.string.pump_basebasalrate, extendedBolus.rate)
            else ""
        } ?: ""

    fun extendedBolusDialogText(iobCobCalculator: IobCobCalculator): String =
        iobCobCalculator.getExtendedBolus(dateUtil.now())?.toStringFull(dateUtil) ?: ""

    /*
     * IOB, COB
     */

    fun bolusIob(iobCobCalculator: IobCobCalculator): IobTotal = iobCobCalculator.calculateIobFromBolus().round()
    fun basalIob(iobCobCalculator: IobCobCalculator): IobTotal = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
    fun cobInfo(iobCobCalculator: IobCobCalculator): CobInfo = iobCobCalculator.getCobInfo(true, "Overview COB")

    val lastCarbsTime: Long
        get() = repository.getLastCarbsRecordWrapped().blockingGet().let { lastCarbs ->
            if (lastCarbs is ValueWrapper.Existing) lastCarbs.value.timestamp else 0L
        }

    fun iobText(iobCobCalculator: IobCobCalculator): String =
        rh.gs(R.string.formatinsulinunits, bolusIob(iobCobCalculator).iob + basalIob(iobCobCalculator).basaliob)

    fun iobDialogText(iobCobCalculator: IobCobCalculator): String =
        rh.gs(R.string.formatinsulinunits, bolusIob(iobCobCalculator).iob + basalIob(iobCobCalculator).basaliob) + "\n" +
            rh.gs(R.string.bolus) + ": " + rh.gs(R.string.formatinsulinunits, bolusIob(iobCobCalculator).iob) + "\n" +
            rh.gs(R.string.basal) + ": " + rh.gs(R.string.formatinsulinunits, basalIob(iobCobCalculator).basaliob)

    /*
     * TEMP TARGET
     */

    val temporaryTarget: TemporaryTarget?
        get() =
            repository.getTemporaryTargetActiveAt(dateUtil.now()).blockingGet().let { tempTarget ->
                if (tempTarget is ValueWrapper.Existing) tempTarget.value
                else null
            }

    /*
     * SENSITIVITY
     */

    fun lastAutosensData(iobCobCalculator: IobCobCalculator) = iobCobCalculator.ads.getLastAutosensData("Overview", aapsLogger, dateUtil)

    /*
     * Graphs
     */

    var bgReadingsArray: List<GlucoseValue> = ArrayList()
    var maxBgValue = Double.MIN_VALUE
    var bucketedGraphSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()
    var bgReadingGraphSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()
    var predictionsGraphSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()

    val basalScale = Scale()
    var baseBasalGraphSeries: LineGraphSeries<ScaledDataPoint> = LineGraphSeries()
    var tempBasalGraphSeries: LineGraphSeries<ScaledDataPoint> = LineGraphSeries()
    var basalLineGraphSeries: LineGraphSeries<ScaledDataPoint> = LineGraphSeries()
    var absoluteBasalGraphSeries: LineGraphSeries<ScaledDataPoint> = LineGraphSeries()

    var temporaryTargetSeries: LineGraphSeries<DataPoint> = LineGraphSeries()

    var maxIAValue = 0.0
    val actScale = Scale()
    var activitySeries: FixedLineGraphSeries<ScaledDataPoint> = FixedLineGraphSeries()
    var activityPredictionSeries: FixedLineGraphSeries<ScaledDataPoint> = FixedLineGraphSeries()

    var maxEpsValue = 0.0
    val epsScale = Scale()
    var epsSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()
    var maxTreatmentsValue = 0.0
    var treatmentsSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()
    var maxTherapyEventValue = 0.0
    var therapyEventSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()

    var maxIobValueFound = Double.MIN_VALUE
    val iobScale = Scale()
    var iobSeries: FixedLineGraphSeries<ScaledDataPoint> = FixedLineGraphSeries()
    var absIobSeries: FixedLineGraphSeries<ScaledDataPoint> = FixedLineGraphSeries()
    var iobPredictions1Series: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()
    //var iobPredictions2Series: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()

    var maxBGIValue = Double.MIN_VALUE
    val bgiScale = Scale()
    var minusBgiSeries: FixedLineGraphSeries<ScaledDataPoint> = FixedLineGraphSeries()
    var minusBgiHistSeries: FixedLineGraphSeries<ScaledDataPoint> = FixedLineGraphSeries()

    var maxCobValueFound = Double.MIN_VALUE
    val cobScale = Scale()
    var cobSeries: FixedLineGraphSeries<ScaledDataPoint> = FixedLineGraphSeries()
    var cobMinFailOverSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()

    var maxDevValueFound = Double.MIN_VALUE
    val devScale = Scale()
    var deviationsSeries: BarGraphSeries<OverviewPlugin.DeviationDataPoint> = BarGraphSeries()

    var maxRatioValueFound = 5.0                    //even if sens data equals 0 for all the period, minimum scale is between 95% and 105%
    var minRatioValueFound = -maxRatioValueFound
    val ratioScale = Scale()
    var ratioSeries: LineGraphSeries<ScaledDataPoint> = LineGraphSeries()

    var maxFromMaxValueFound = Double.MIN_VALUE
    var maxFromMinValueFound = Double.MIN_VALUE
    val dsMaxScale = Scale()
    val dsMinScale = Scale()
    var dsMaxSeries: LineGraphSeries<ScaledDataPoint> = LineGraphSeries()
    var dsMinSeries: LineGraphSeries<ScaledDataPoint> = LineGraphSeries()
}
