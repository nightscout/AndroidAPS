package info.nightscout.androidaps.plugins.general.overview

import android.graphics.DashPathEffect
import android.graphics.Paint
import com.jjoe64.graphview.series.BarGraphSeries
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.*
import info.nightscout.androidaps.extensions.*
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.aps.openAPSSMB.SMBDefaults
import info.nightscout.androidaps.plugins.general.nsclient.data.NSDeviceStatus
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.*
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.CobInfo
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.data.AutosensData
import info.nightscout.androidaps.utils.*
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@Singleton
class OverviewData @Inject constructor(
    private val injector: HasAndroidInjector,
    private val aapsLogger: AAPSLogger,
    private val resourceHelper: ResourceHelper,
    private val dateUtil: DateUtil,
    private val sp: SP,
    private val activePlugin: ActivePlugin,
    private val defaultValueHelper: DefaultValueHelper,
    private val profileFunction: ProfileFunction,
    private val config: Config,
    private val loopPlugin: LoopPlugin,
    private val nsDeviceStatus: NSDeviceStatus,
    private val repository: AppRepository,
    private val overviewMenus: OverviewMenus,
    private val iobCobCalculator: IobCobCalculator,
    private val translator: Translator
) {

    enum class Property {
        TIME,
        CALC_PROGRESS,
        PROFILE,
        TEMPORARY_BASAL,
        EXTENDED_BOLUS,
        TEMPORARY_TARGET,
        BG,
        IOB_COB,
        SENSITIVITY,
        GRAPH
    }

    val minRangeToDisplay = 6
    var rangeToDisplay = minRangeToDisplay // for graph
    val rangeMaxToDisplay = 24
    var toTime: Long = 0
    var fromTimeArray = longArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
    var fromTime: Long
        get() = fromTimeArray.get(indexRange)
        set(from) { fromTimeArray = longArrayOf(from, from, from, from, from, from, from, from) }
    var endTime: Long = 0
    var fromTimeData: Long = 0
    var endTimeData: Long = 0
    var predictionHours = 0

    fun reset() {
        profile = null
        profileName = null
        profileNameWithRemainingTime = null
        calcProgress = ""
        lastBg = null
        temporaryBasal = null
        extendedBolus = null
        bolusIob = null
        basalIob = null
        cobInfo = null
        lastCarbsTime = 0L
        temporaryTarget = null
        lastAutosensData = null
        bgReadingsArray = ArrayList()
        bucketedListArray = java.util.ArrayList()
        bgReadingGraphArray = java.util.ArrayList()
        filteredTreatments = java.util.ArrayList()
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
        iobPredictions2Series = PointsWithLabelGraphSeries()
        minusBgiSeries = FixedLineGraphSeries()
        minusBgiHistSeries = FixedLineGraphSeries()
        cobSeries = FixedLineGraphSeries()
        cobMinFailOverSeries = PointsWithLabelGraphSeries()
        deviationsSeries = BarGraphSeries()
        ratioSeries = LineGraphSeries()
        dsMaxSeries = LineGraphSeries()
        dsMinSeries = LineGraphSeries()
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
        fromTimeData = toTime - T.hours(rangeMaxToDisplay.toLong()).msecs()
        endTimeData = toTime + T.hours(predictionHours.toLong()).msecs()
        fromTimeArray = longArrayOf(
            endTimeData - T.hours(minRangeToDisplay.toLong()).msecs(),
            toTime - T.hours(minRangeToDisplay.toLong()).msecs(),
            endTimeData - T.hours(minRangeToDisplay.toLong()).msecs() * 2,
            toTime - T.hours(minRangeToDisplay.toLong()).msecs() * 2,
            endTimeData - T.hours(minRangeToDisplay.toLong()).msecs() * 3,
            toTime - T.hours(minRangeToDisplay.toLong()).msecs() * 3,
            endTimeData - T.hours(minRangeToDisplay.toLong()).msecs() * 4,
            toTime - T.hours(minRangeToDisplay.toLong()).msecs() * 4,
        )
        endTime = toTime + if (predictAvailable) T.hours(predictionHours.toLong()).msecs() else 0
    }

    /*
     * PROFILE
     */
    var profile: Profile? = null
    var profileName: String? = null
    var profileNameWithRemainingTime: String? = null

    val profileBackgroundColor: Int
        get() =
            profile?.let { profile ->
                if (profile.percentage != 100 || profile.timeshift != 0) resourceHelper.gc(R.color.ribbonWarning)
                else resourceHelper.gc(R.color.ribbonDefault)
            } ?: resourceHelper.gc(R.color.ribbonTextDefault)

    val profileTextColor: Int
        get() =
            profile?.let { profile ->
                if (profile.percentage != 100 || profile.timeshift != 0) resourceHelper.gc(R.color.ribbonTextWarning)
                else resourceHelper.gc(R.color.ribbonTextDefault)
            } ?: resourceHelper.gc(R.color.ribbonTextDefault)

    /*
     * CALC PROGRESS
     */

    var calcProgress: String = ""

    /*
     * BG
     */

    var lastBg: GlucoseValue? = null

    val lastBgColor: Int
        get() = lastBg?.let { lastBg ->
            when {
                lastBg.valueToUnits(profileFunction.getUnits()) < defaultValueHelper.determineLowLine()  -> resourceHelper.gc(R.color.low)
                lastBg.valueToUnits(profileFunction.getUnits()) > defaultValueHelper.determineHighLine() -> resourceHelper.gc(R.color.high)
                else                                                                                     -> resourceHelper.gc(R.color.inrange)
            }
        } ?: resourceHelper.gc(R.color.inrange)

    val isActualBg: Boolean
        get() =
            lastBg?.let { lastBg ->
                lastBg.timestamp > dateUtil.now() - T.mins(9).msecs()
            } ?: false

    /*
     * TEMPORARY BASAL
     */

    var temporaryBasal: TemporaryBasal? = null

    val temporaryBasalText: String
        get() =
            profile?.let { profile ->
                if (temporaryBasal?.isInProgress == false) temporaryBasal = null
                temporaryBasal?.let { "T:" + it.toStringShort() }
                    ?: resourceHelper.gs(R.string.pump_basebasalrate, profile.getBasal())
            } ?: resourceHelper.gs(R.string.notavailable)

    val temporaryBasalDialogText: String
        get() = profile?.let { profile ->
            temporaryBasal?.let { temporaryBasal ->
                "${resourceHelper.gs(R.string.basebasalrate_label)}: ${resourceHelper.gs(R.string.pump_basebasalrate, profile.getBasal())}" +
                    "\n" + resourceHelper.gs(R.string.tempbasal_label) + ": " + temporaryBasal.toStringFull(profile, dateUtil)
            }
                ?: "${resourceHelper.gs(R.string.basebasalrate_label)}: ${resourceHelper.gs(R.string.pump_basebasalrate, profile.getBasal())}"
        } ?: resourceHelper.gs(R.string.notavailable)

    val temporaryBasalIcon: Int
        get() =
            profile?.let { profile ->
                temporaryBasal?.let { temporaryBasal ->
                    val percentRate = temporaryBasal.convertedToPercent(dateUtil.now(), profile)
                    when {
                        percentRate > 100 -> R.drawable.ic_cp_basal_tbr_high
                        percentRate < 100 -> R.drawable.ic_cp_basal_tbr_low
                        else              -> R.drawable.ic_cp_basal_no_tbr
                    }
                }
            } ?: R.drawable.ic_cp_basal_no_tbr

    val temporaryBasalColor: Int
        get() = temporaryBasal?.let { resourceHelper.gc(R.color.basal) }
            ?: resourceHelper.gc(R.color.defaulttextcolor)

    /*
     * EXTENDED BOLUS
    */

    var extendedBolus: ExtendedBolus? = null

    val extendedBolusText: String
        get() =
            extendedBolus?.let { extendedBolus ->
                if (!extendedBolus.isInProgress(dateUtil)) {
                    this@OverviewData.extendedBolus = null
                    ""
                } else if (!activePlugin.activePump.isFakingTempsByExtendedBoluses) resourceHelper.gs(R.string.pump_basebasalrate, extendedBolus.rate)
                else ""
            } ?: ""

    val extendedBolusDialogText: String
        get() = extendedBolus?.toStringFull(dateUtil) ?: ""

    /*
     * IOB, COB
     */

    var bolusIob: IobTotal? = null
    var basalIob: IobTotal? = null
    var cobInfo: CobInfo? = null
    var lastCarbsTime: Long = 0L

    val iobText: String
        get() =
            bolusIob?.let { bolusIob ->
                basalIob?.let { basalIob ->
                    resourceHelper.gs(R.string.formatinsulinunits, bolusIob.iob + basalIob.basaliob)
                } ?: resourceHelper.gs(R.string.value_unavailable_short)
            } ?: resourceHelper.gs(R.string.value_unavailable_short)

    val iobDialogText: String
        get() =
            bolusIob?.let { bolusIob ->
                basalIob?.let { basalIob ->
                    resourceHelper.gs(R.string.formatinsulinunits, bolusIob.iob + basalIob.basaliob) + "\n" +
                        resourceHelper.gs(R.string.bolus) + ": " + resourceHelper.gs(R.string.formatinsulinunits, bolusIob.iob) + "\n" +
                        resourceHelper.gs(R.string.basal) + ": " + resourceHelper.gs(R.string.formatinsulinunits, basalIob.basaliob)
                } ?: resourceHelper.gs(R.string.value_unavailable_short)
            } ?: resourceHelper.gs(R.string.value_unavailable_short)

    /*
     * TEMP TARGET
     */

    var temporaryTarget: TemporaryTarget? = null

    /*
     * SENSITIVITY
     */

    var lastAutosensData: AutosensData? = null
    /*
     * Graphs
     */
    var predictAvailable: Boolean = false
    var indexRange: Int = 0
        get() = 2 * (rangeToDisplay / minRangeToDisplay - 1) + if (predictAvailable) 0 else 1
    var bgReadingsArray: List<GlucoseValue> = ArrayList()
    var maxBgArray = doubleArrayOf(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE)
    var maxBgValue = Double.MIN_VALUE
        get() = maxBgArray.get(indexRange)
    var bucketedListArray: MutableList<DataPointWithLabelInterface> = java.util.ArrayList()
    var bucketedGraphSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()
        get() {
            val BdArray = bucketedListArray.filter { it.x >= fromTime.toDouble() && it.x <= endTime.toDouble()}
            return PointsWithLabelGraphSeries(Array(BdArray.size) { i -> BdArray[i] })
        }
    var bgReadingGraphArray: MutableList<DataPointWithLabelInterface> = java.util.ArrayList()
    var bgReadingGraphSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()
        get() {
            val BgArray = bgReadingGraphArray.filter { it.x >= fromTime.toDouble() && it.x <= endTime.toDouble()}
            return PointsWithLabelGraphSeries(Array(BgArray.size) { i -> BgArray[i] })
        }
    var predictionsGraphSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()

    var maxBasalArray = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    var maxBasalValueFound = 0.0
        get() = maxBasalArray.get(indexRange)
    val basalScale = Scale()
    var baseBasalGraphSeries: LineGraphSeries<ScaledDataPoint> = LineGraphSeries()
    var tempBasalGraphSeries: LineGraphSeries<ScaledDataPoint> = LineGraphSeries()
    var basalLineGraphSeries: LineGraphSeries<ScaledDataPoint> = LineGraphSeries()
    var absoluteBasalGraphSeries: LineGraphSeries<ScaledDataPoint> = LineGraphSeries()

    var temporaryTargetSeries: LineGraphSeries<DataPoint> = LineGraphSeries()

    var maxIAArray = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    var maxIAValue = 0.0
        get() = maxIAArray.get(indexRange)
    val actScale = Scale()
    var activitySeries: FixedLineGraphSeries<ScaledDataPoint> = FixedLineGraphSeries()
    var activityPredictionSeries: FixedLineGraphSeries<ScaledDataPoint> = FixedLineGraphSeries()

    var maxTreatmentsArray = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    var maxTreatmentsValue = 0.0
        get() = maxTreatmentsArray.get(indexRange)
    var filteredTreatments: MutableList<DataPointWithLabelInterface> = java.util.ArrayList()
    var treatmentsSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()
        get() {
            val treatmentsArray = filteredTreatments.filter { it.x >= fromTime.toDouble() && it.x <= endTime.toDouble()}
            return PointsWithLabelGraphSeries(Array(treatmentsArray.size) { i -> treatmentsArray[i] })
        }
    var maxIobArray = doubleArrayOf(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE)
    var maxIobValueFound = Double.MIN_VALUE
        get() = maxIobArray.get(indexRange)
    val iobScale = Scale()
    var iobSeries: FixedLineGraphSeries<ScaledDataPoint> = FixedLineGraphSeries()
    var absIobSeries: FixedLineGraphSeries<ScaledDataPoint> = FixedLineGraphSeries()
    var iobPredictions1Series: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()
    var iobPredictions2Series: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()

    var maxBGIArray = doubleArrayOf(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE)
    var maxBGIValue = Double.MIN_VALUE
        get() = maxBGIArray.get(indexRange)
    val bgiScale = Scale()
    var minusBgiSeries: FixedLineGraphSeries<ScaledDataPoint> = FixedLineGraphSeries()
    var minusBgiHistSeries: FixedLineGraphSeries<ScaledDataPoint> = FixedLineGraphSeries()

    var maxCobArray = doubleArrayOf(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE)
    var maxCobValueFound = Double.MIN_VALUE
        get() = maxCobArray.get(indexRange)
    val cobScale = Scale()
    var cobSeries: FixedLineGraphSeries<ScaledDataPoint> = FixedLineGraphSeries()
    var cobMinFailOverSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()

    var maxDevArray = doubleArrayOf(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE)
    var maxDevValueFound = Double.MIN_VALUE
        get() = maxDevArray.get(indexRange)
    val devScale = Scale()
    var deviationsSeries: BarGraphSeries<OverviewPlugin.DeviationDataPoint> = BarGraphSeries()

    var maxRatioArray = doubleArrayOf(5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0)
    var maxRatioValueFound = 5.0                    //even if sens data equals 0 for all the period, minimum scale is between 95% and 105%
        get() = maxRatioArray.get(indexRange)
    var minRatioValueFound = -maxRatioValueFound
    val ratioScale = Scale()
    var ratioSeries: LineGraphSeries<ScaledDataPoint> = LineGraphSeries()

    var maxFromMaxArray = doubleArrayOf(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE)
    var maxFromMaxValueFound = Double.MIN_VALUE
        get() = maxFromMaxArray.get(indexRange)
    var maxFromMinArray = doubleArrayOf(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE)
    var maxFromMinValueFound = Double.MIN_VALUE
        get() = maxFromMinArray.get(indexRange)
    val dsMaxScale = Scale()
    val dsMinScale = Scale()
    var dsMaxSeries: LineGraphSeries<ScaledDataPoint> = LineGraphSeries()
    var dsMinSeries: LineGraphSeries<ScaledDataPoint> = LineGraphSeries()

    @Synchronized
    @Suppress("SameParameterValue", "UNUSED_PARAMETER")
    fun prepareBgData(from: String) {
//        val start = dateUtil.now()
        maxBgArray = doubleArrayOf(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE)
        bgReadingsArray = repository.compatGetBgReadingsDataFromTime(fromTimeData, toTime, false).blockingGet()
        bgReadingGraphArray = java.util.ArrayList()
        for (bg in bgReadingsArray) {
            if (bg.timestamp < fromTimeData || bg.timestamp > toTime) continue
            //if (bg.value > maxBgValue) maxBgValue = bg.value
            maxTime(maxBgArray, bg.timestamp, bg.value)
            bgReadingGraphArray.add(GlucoseValueDataPoint(bg, defaultValueHelper, profileFunction, resourceHelper))
        }
        //bgReadingGraphSeries = PointsWithLabelGraphSeries(Array(bgListArray.size) { i -> bgListArray[i] })
        maxBgArray = maxBgArray.map { addUpperChartMargin(max(Profile.fromMgdlToUnits(it, profileFunction.getUnits()), defaultValueHelper.determineHighLine())) }.toDoubleArray()
//        profiler.log(LTag.UI, "prepareBgData() $from", start)
    }

    @Suppress("UNUSED_PARAMETER")
    @Synchronized
    fun preparePredictions(from: String) {
//        val start = dateUtil.now()
        val apsResult = if (config.APS) loopPlugin.lastRun?.constraintsProcessed else nsDeviceStatus.getAPSResult(injector)
        val predictionsAvailable = if (config.APS) loopPlugin.lastRun?.request?.hasPredictions == true else config.NSCLIENT
        predictAvailable = predictionsAvailable
        val menuChartSettings = overviewMenus.setting
        // align to hours
        val calendar = Calendar.getInstance().also {
            it.timeInMillis = System.currentTimeMillis()
            it[Calendar.MILLISECOND] = 0
            it[Calendar.SECOND] = 0
            it[Calendar.MINUTE] = 0
            it.add(Calendar.HOUR, 1)
        }
        predictionHours = apsResult?.let { (ceil(it.latestPredictionsTime - System.currentTimeMillis().toDouble()) / (60 * 60 * 1000)).toInt() }
            ?: 0
        predictionHours = min(2, predictionHours)
        predictionHours = max(0, predictionHours)
        toTime = calendar.timeInMillis + 100000 // little bit more to avoid wrong rounding - GraphView specific
        endTimeData = toTime + T.hours(predictionHours.toLong()).msecs()
        fromTimeArray = longArrayOf(
            endTimeData - T.hours(minRangeToDisplay.toLong()).msecs(),
            toTime - T.hours(minRangeToDisplay.toLong()).msecs(),
            endTimeData - T.hours(minRangeToDisplay.toLong()).msecs() * 2,
            toTime - T.hours(minRangeToDisplay.toLong()).msecs() * 2,
            endTimeData - T.hours(minRangeToDisplay.toLong()).msecs() * 3,
            toTime - T.hours(minRangeToDisplay.toLong()).msecs() * 3,
            endTimeData - T.hours(minRangeToDisplay.toLong()).msecs() * 4,
            toTime - T.hours(minRangeToDisplay.toLong()).msecs() * 4,
        )
        if (predictionsAvailable && apsResult != null && menuChartSettings[0][OverviewMenus.CharType.PRE.ordinal]) {
            //fromTime = toTime - T.hours(hoursToFetch.toLong()).msecs()
            endTime = toTime + T.hours(predictionHours.toLong()).msecs()
        } else {
            //fromTime = toTime - T.hours(rangeToDisplay.toLong()).msecs()
            endTime = toTime
        }

        val bgListArray: MutableList<DataPointWithLabelInterface> = java.util.ArrayList()
        val predictions: MutableList<GlucoseValueDataPoint>? = apsResult?.predictions
            ?.map { bg -> GlucoseValueDataPoint(bg, defaultValueHelper, profileFunction, resourceHelper) }
            ?.toMutableList()
        if (predictions != null) {
            predictions.sortWith { o1: GlucoseValueDataPoint, o2: GlucoseValueDataPoint -> o1.x.compareTo(o2.x) }
            for (prediction in predictions) if (prediction.data.value >= 40) bgListArray.add(prediction)
        }
        predictionsGraphSeries = PointsWithLabelGraphSeries(Array(bgListArray.size) { i -> bgListArray[i] })
//        profiler.log(LTag.UI, "preparePredictions() $from", start)
    }

    @Synchronized
    @Suppress("SameParameterValue", "UNUSED_PARAMETER")
    fun prepareBucketedData(from: String) {
//        val start = dateUtil.now()
        val bucketedData = iobCobCalculator.ads.getBucketedDataTableCopy() ?: return
        if (bucketedData.isEmpty()) {
            aapsLogger.debug("No bucketed data.")
            return
        }
        bucketedListArray = java.util.ArrayList()
        for (inMemoryGlucoseValue in bucketedData) {
            if (inMemoryGlucoseValue.timestamp < fromTimeData || inMemoryGlucoseValue.timestamp > toTime) continue
            bucketedListArray.add(InMemoryGlucoseValueDataPoint(inMemoryGlucoseValue, profileFunction, resourceHelper))
        }
//        bucketedGraphSeries = PointsWithLabelGraphSeries(Array(bucketedListArray.size) { i -> bucketedListArray[i] })
//        profiler.log(LTag.UI, "prepareBucketedData() $from", start)
    }

    @Suppress("UNUSED_PARAMETER")
    @Synchronized
    fun prepareBasalData(from: String) {
//        val start = dateUtil.now()
        maxBasalArray = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val baseBasalArray: MutableList<ScaledDataPoint> = java.util.ArrayList()
        val tempBasalArray: MutableList<ScaledDataPoint> = java.util.ArrayList()
        val basalLineArray: MutableList<ScaledDataPoint> = java.util.ArrayList()
        val absoluteBasalLineArray: MutableList<ScaledDataPoint> = java.util.ArrayList()
        var lastLineBasal = 0.0
        var lastAbsoluteLineBasal = -1.0
        var lastBaseBasal = 0.0
        var lastTempBasal = 0.0
        var time = fromTimeData
        while (time < toTime) {
            val profile = profileFunction.getProfile(time)
            if (profile == null) {
                time += 60 * 1000L
                continue
            }
            val basalData = iobCobCalculator.getBasalData(profile, time)
            val baseBasalValue = basalData.basal
            var absoluteLineValue = baseBasalValue
            var tempBasalValue = 0.0
            var basal = 0.0
            if (basalData.isTempBasalRunning) {
                tempBasalValue = basalData.tempBasalAbsolute
                absoluteLineValue = tempBasalValue
                if (tempBasalValue != lastTempBasal) {
                    tempBasalArray.add(ScaledDataPoint(time, lastTempBasal, basalScale))
                    tempBasalArray.add(ScaledDataPoint(time, tempBasalValue.also { basal = it }, basalScale))
                }
                if (lastBaseBasal != 0.0) {
                    baseBasalArray.add(ScaledDataPoint(time, lastBaseBasal, basalScale))
                    baseBasalArray.add(ScaledDataPoint(time, 0.0, basalScale))
                    lastBaseBasal = 0.0
                }
            } else {
                if (baseBasalValue != lastBaseBasal) {
                    baseBasalArray.add(ScaledDataPoint(time, lastBaseBasal, basalScale))
                    baseBasalArray.add(ScaledDataPoint(time, baseBasalValue.also { basal = it }, basalScale))
                    lastBaseBasal = baseBasalValue
                }
                if (lastTempBasal != 0.0) {
                    tempBasalArray.add(ScaledDataPoint(time, lastTempBasal, basalScale))
                    tempBasalArray.add(ScaledDataPoint(time, 0.0, basalScale))
                }
            }
            if (baseBasalValue != lastLineBasal) {
                basalLineArray.add(ScaledDataPoint(time, lastLineBasal, basalScale))
                basalLineArray.add(ScaledDataPoint(time, baseBasalValue, basalScale))
            }
            if (absoluteLineValue != lastAbsoluteLineBasal) {
                absoluteBasalLineArray.add(ScaledDataPoint(time, lastAbsoluteLineBasal, basalScale))
                absoluteBasalLineArray.add(ScaledDataPoint(time, basal, basalScale))
            }
            lastAbsoluteLineBasal = absoluteLineValue
            lastLineBasal = baseBasalValue
            lastTempBasal = tempBasalValue
            maxTime(maxBasalArray, time, max(tempBasalValue, baseBasalValue))
            //maxBasalValueFound = max(maxBasalValueFound, max(tempBasalValue, baseBasalValue))
            time += 60 * 1000L
        }

        // final points
        basalLineArray.add(ScaledDataPoint(toTime, lastLineBasal, basalScale))
        baseBasalArray.add(ScaledDataPoint(toTime, lastBaseBasal, basalScale))
        tempBasalArray.add(ScaledDataPoint(toTime, lastTempBasal, basalScale))
        absoluteBasalLineArray.add(ScaledDataPoint(toTime, lastAbsoluteLineBasal, basalScale))

        // create series
        baseBasalGraphSeries = LineGraphSeries(Array(baseBasalArray.size) { i -> baseBasalArray[i] }).also {
            it.isDrawBackground = true
            it.backgroundColor = resourceHelper.gc(R.color.basebasal)
            it.thickness = 0
        }
        tempBasalGraphSeries = LineGraphSeries(Array(tempBasalArray.size) { i -> tempBasalArray[i] }).also {
            it.isDrawBackground = true
            it.backgroundColor = resourceHelper.gc(R.color.tempbasal)
            it.thickness = 0
        }
        basalLineGraphSeries = LineGraphSeries(Array(basalLineArray.size) { i -> basalLineArray[i] }).also {
            it.setCustomPaint(Paint().also { paint ->
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = resourceHelper.getDisplayMetrics().scaledDensity * 2
                paint.pathEffect = DashPathEffect(floatArrayOf(2f, 4f), 0f)
                paint.color = resourceHelper.gc(R.color.basal)
            })
        }
        absoluteBasalGraphSeries = LineGraphSeries(Array(absoluteBasalLineArray.size) { i -> absoluteBasalLineArray[i] }).also {
            it.setCustomPaint(Paint().also { absolutePaint ->
                absolutePaint.style = Paint.Style.STROKE
                absolutePaint.strokeWidth = resourceHelper.getDisplayMetrics().scaledDensity * 2
                absolutePaint.color = resourceHelper.gc(R.color.basal)
            })
        }
//        profiler.log(LTag.UI, "prepareBasalData() $from", start)
    }

    @Suppress("UNUSED_PARAMETER")
    @Synchronized
    fun prepareTemporaryTargetData(from: String) {
//        val start = dateUtil.now()
        val profile = profile ?: return
        val units = profileFunction.getUnits()
        var toTime = toTime
        val targetsSeriesArray: MutableList<DataPoint> = java.util.ArrayList()
        var lastTarget = -1.0
        loopPlugin.lastRun?.constraintsProcessed?.let { toTime = max(it.latestPredictionsTime, toTime) }
        var time = fromTimeData
        while (time < toTime) {
            val tt = repository.getTemporaryTargetActiveAt(time).blockingGet()
            val value: Double = if (tt is ValueWrapper.Existing) {
                Profile.fromMgdlToUnits(tt.value.target(), units)
            } else {
                Profile.fromMgdlToUnits((profile.getTargetLowMgdl(time) + profile.getTargetHighMgdl(time)) / 2, units)
            }
            if (lastTarget != value) {
                if (lastTarget != -1.0) targetsSeriesArray.add(DataPoint(time.toDouble(), lastTarget))
                targetsSeriesArray.add(DataPoint(time.toDouble(), value))
            }
            lastTarget = value
            time += 5 * 60 * 1000L
        }
        // final point
        targetsSeriesArray.add(DataPoint(toTime.toDouble(), lastTarget))
        // create series
        temporaryTargetSeries = LineGraphSeries(Array(targetsSeriesArray.size) { i -> targetsSeriesArray[i] }).also {
            it.isDrawBackground = false
            it.color = resourceHelper.gc(R.color.tempTargetBackground)
            it.thickness = 2
        }
//        profiler.log(LTag.UI, "prepareTemporaryTargetData() $from", start)
    }

    @Suppress("UNUSED_PARAMETER")
    @Synchronized
    fun prepareTreatmentsData(from: String) {
//        val start = dateUtil.now()
        maxTreatmentsArray = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        filteredTreatments = java.util.ArrayList()
        repository.getBolusesIncludingInvalidFromTimeToTime(fromTimeData, endTimeData, true).blockingGet()
            .map { BolusDataPoint(it, resourceHelper, activePlugin, defaultValueHelper) }
            .filter { it.data.type != Bolus.Type.SMB || it.data.isValid }
            .forEach {
                it.y = getNearestBg(it.x.toLong())
                filteredTreatments.add(it)
            }
        repository.getCarbsIncludingInvalidFromTimeToTimeExpanded(fromTimeData, endTimeData, true).blockingGet()
            .map { CarbsDataPoint(it, resourceHelper) }
            .forEach {
                it.y = getNearestBg(it.x.toLong())
                filteredTreatments.add(it)
            }

        // ProfileSwitch
        repository.getEffectiveProfileSwitchDataFromTimeToTime(fromTimeData, endTimeData, true).blockingGet()
            .map { EffectiveProfileSwitchDataPoint(it) }
            .forEach(filteredTreatments::add)

        // OfflineEvent
        repository.getOfflineEventDataFromTimeToTime(fromTimeData, endTimeData, true).blockingGet()
            .map { TherapyEventDataPoint(TherapyEvent(timestamp = it.timestamp, duration = it.duration, type = TherapyEvent.Type.APS_OFFLINE, glucoseUnit = TherapyEvent.GlucoseUnit.MMOL), resourceHelper, profileFunction, translator) }
            .forEach(filteredTreatments::add)

        // Extended bolus
        if (!activePlugin.activePump.isFakingTempsByExtendedBoluses) {
            repository.getExtendedBolusDataFromTimeToTime(fromTimeData, endTimeData, true).blockingGet()
                .map { ExtendedBolusDataPoint(it) }
                .filter { it.duration != 0L }
                .forEach {
                    it.y = getNearestBg(it.x.toLong())
                    filteredTreatments.add(it)
                }
        }

        // Careportal
        repository.compatGetTherapyEventDataFromToTime(fromTimeData - T.hours(6).msecs(), endTimeData).blockingGet()
            .map { TherapyEventDataPoint(it, resourceHelper, profileFunction, translator) }
            .filterTimeframe(fromTimeData, endTimeData)
            .forEach {
                if (it.y == 0.0)
                    it.y = getNearestBg(it.x.toLong())
                else
                    maxTime(maxTreatmentsArray, it.data.timestamp, addUpperChartMargin(it.y))
                filteredTreatments.add(it)
            }

        //treatmentsSeries = PointsWithLabelGraphSeries(filteredTreatments.toTypedArray())
//        profiler.log(LTag.UI, "prepareTreatmentsData() $from", start)
    }

    @Suppress("UNUSED_PARAMETER")
    @Synchronized
    fun prepareIobAutosensData(from: String) {
//        val start = dateUtil.now()
        val iobArray: MutableList<ScaledDataPoint> = java.util.ArrayList()
        val absIobArray: MutableList<ScaledDataPoint> = java.util.ArrayList()
        maxIobArray = doubleArrayOf(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE)
        var lastIob = 0.0
        var absLastIob = 0.0
        var time = fromTimeData

        val minFailOverActiveList: MutableList<DataPointWithLabelInterface> = java.util.ArrayList()
        val cobArray: MutableList<ScaledDataPoint> = java.util.ArrayList()
        maxCobArray = doubleArrayOf(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE)
        var lastCob = 0

        val actArrayHist: MutableList<ScaledDataPoint> = java.util.ArrayList()
        val actArrayPrediction: MutableList<ScaledDataPoint> = java.util.ArrayList()
        val now = dateUtil.now().toDouble()
        maxIAArray = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

        val bgiArrayHist: MutableList<ScaledDataPoint> = java.util.ArrayList()
        val bgiArrayPrediction: MutableList<ScaledDataPoint> = java.util.ArrayList()
        maxBGIArray = doubleArrayOf(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE)

        val devArray: MutableList<OverviewPlugin.DeviationDataPoint> = java.util.ArrayList()
        maxDevArray = doubleArrayOf(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE)

        val ratioArray: MutableList<ScaledDataPoint> = java.util.ArrayList()
        maxRatioArray = doubleArrayOf(5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0)

        val dsMaxArray: MutableList<ScaledDataPoint> = java.util.ArrayList()
        val dsMinArray: MutableList<ScaledDataPoint> = java.util.ArrayList()
        maxFromMaxArray = doubleArrayOf(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE)
        maxFromMinArray = doubleArrayOf(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE)

        val adsData = iobCobCalculator.ads.clone()

        while (time <= toTime) {
            val profile = profileFunction.getProfile(time)
            if (profile == null) {
                time += 5 * 60 * 1000L
                continue
            }
            // IOB
            val iob = iobCobCalculator.calculateFromTreatmentsAndTemps(time, profile)
            val baseBasalIob = iobCobCalculator.calculateAbsoluteIobFromBaseBasals(time)
            val absIob = IobTotal.combine(iob, baseBasalIob)
            val autosensData = adsData.getAutosensDataAtTime(time)
            if (abs(lastIob - iob.iob) > 0.02) {
                if (abs(lastIob - iob.iob) > 0.2) iobArray.add(ScaledDataPoint(time, lastIob, iobScale))
                iobArray.add(ScaledDataPoint(time, iob.iob, iobScale))
                maxTime(maxIobArray, time, abs(iob.iob))
                lastIob = iob.iob
            }
            if (abs(absLastIob - absIob.iob) > 0.02) {
                if (abs(absLastIob - absIob.iob) > 0.2) absIobArray.add(ScaledDataPoint(time, absLastIob, iobScale))
                absIobArray.add(ScaledDataPoint(time, absIob.iob, iobScale))
                maxTime(maxIobArray, time, abs(absIob.iob))
                absLastIob = absIob.iob
            }

            // COB
            if (autosensData != null) {
                val cob = autosensData.cob.toInt()
                if (cob != lastCob) {
                    if (autosensData.carbsFromBolus > 0) cobArray.add(ScaledDataPoint(time, lastCob.toDouble(), cobScale))
                    cobArray.add(ScaledDataPoint(time, cob.toDouble(), cobScale))
                    maxTime(maxCobArray, time, cob.toDouble())
                    lastCob = cob
                }
                if (autosensData.failoverToMinAbsorbtionRate) {
                    autosensData.setScale(cobScale)
                    autosensData.setChartTime(time)
                    minFailOverActiveList.add(autosensData)
                }
            }

            // ACTIVITY
            if (time <= now) actArrayHist.add(ScaledDataPoint(time, iob.activity, actScale))
            else actArrayPrediction.add(ScaledDataPoint(time, iob.activity, actScale))
            maxTime(maxIAArray, time, abs(iob.activity))

            // BGI
            val devBgiScale = overviewMenus.isEnabledIn(OverviewMenus.CharType.DEV) == overviewMenus.isEnabledIn(OverviewMenus.CharType.BGI)
            val deviation = if (devBgiScale) autosensData?.deviation ?: 0.0 else 0.0
            val bgi: Double = iob.activity * profile.getIsfMgdl(time) * 5.0
            if (time <= now) bgiArrayHist.add(ScaledDataPoint(time, bgi, bgiScale))
            else bgiArrayPrediction.add(ScaledDataPoint(time, bgi, bgiScale))
            maxTime(maxBGIArray, time, max(abs(bgi), deviation))

            // DEVIATIONS
            if (autosensData != null) {
                var color = resourceHelper.gc(R.color.deviationblack) // "="
                if (autosensData.type == "" || autosensData.type == "non-meal") {
                    if (autosensData.pastSensitivity == "C") color = resourceHelper.gc(R.color.deviationgrey)
                    if (autosensData.pastSensitivity == "+") color = resourceHelper.gc(R.color.deviationgreen)
                    if (autosensData.pastSensitivity == "-") color = resourceHelper.gc(R.color.deviationred)
                } else if (autosensData.type == "uam") {
                    color = resourceHelper.gc(R.color.uam)
                } else if (autosensData.type == "csf") {
                    color = resourceHelper.gc(R.color.deviationgrey)
                }
                devArray.add(OverviewPlugin.DeviationDataPoint(time.toDouble(), autosensData.deviation, color, devScale))
                maxTime(maxDevArray, time, abs(autosensData.deviation))
            }

            // RATIO
            if (autosensData != null) {
                ratioArray.add(ScaledDataPoint(time, 100.0 * (autosensData.autosensResult.ratio - 1), ratioScale))
                maxTime(maxRatioArray, time, 100.0 * (autosensData.autosensResult.ratio - 1))
                maxTime(maxRatioArray, time, 100.0 * (1 - autosensData.autosensResult.ratio))
            }

            // DEV SLOPE
            if (autosensData != null) {
                dsMaxArray.add(ScaledDataPoint(time, autosensData.slopeFromMaxDeviation, dsMaxScale))
                dsMinArray.add(ScaledDataPoint(time, autosensData.slopeFromMinDeviation, dsMinScale))
                maxTime(maxFromMaxArray, time, abs(autosensData.slopeFromMaxDeviation))
                maxTime(maxFromMinArray, time, abs(autosensData.slopeFromMinDeviation))
            }

            time += 5 * 60 * 1000L
        }
        // IOB
        iobSeries = FixedLineGraphSeries(Array(iobArray.size) { i -> iobArray[i] }).also {
            it.isDrawBackground = true
            it.backgroundColor = -0x7f000001 and resourceHelper.gc(R.color.iob) //50%
            it.color = resourceHelper.gc(R.color.iob)
            it.thickness = 3
        }
        absIobSeries = FixedLineGraphSeries(Array(absIobArray.size) { i -> absIobArray[i] }).also {
            it.isDrawBackground = true
            it.backgroundColor = -0x7f000001 and resourceHelper.gc(R.color.iob) //50%
            it.color = resourceHelper.gc(R.color.iob)
            it.thickness = 3
        }

        if (overviewMenus.setting[0][OverviewMenus.CharType.PRE.ordinal]) {
            val autosensData = adsData.getLastAutosensData("GraphData", aapsLogger, dateUtil)
            val lastAutosensResult = autosensData?.autosensResult ?: AutosensResult()
            val isTempTarget = repository.getTemporaryTargetActiveAt(dateUtil.now()).blockingGet() is ValueWrapper.Existing
            val iobPrediction: MutableList<DataPointWithLabelInterface> = java.util.ArrayList()
            val iobPredictionArray = iobCobCalculator.calculateIobArrayForSMB(lastAutosensResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, isTempTarget)
            for (i in iobPredictionArray) {
                iobPrediction.add(i.setColor(resourceHelper.gc(R.color.iobPredAS)))
                maxTime(maxIobArray, i.time, abs(i.iob))
            }
            iobPredictions1Series = PointsWithLabelGraphSeries(Array(iobPrediction.size) { i -> iobPrediction[i] })
            val iobPrediction2: MutableList<DataPointWithLabelInterface> = java.util.ArrayList()
            val iobPredictionArray2 = iobCobCalculator.calculateIobArrayForSMB(AutosensResult(), SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, isTempTarget)
            for (i in iobPredictionArray2) {
                iobPrediction2.add(i.setColor(resourceHelper.gc(R.color.iobPred)))
                maxTime(maxIobArray, i.time, abs(i.iob))
            }
            iobPredictions2Series = PointsWithLabelGraphSeries(Array(iobPrediction2.size) { i -> iobPrediction2[i] })
            aapsLogger.debug(LTag.AUTOSENS, "IOB prediction for AS=" + DecimalFormatter.to2Decimal(lastAutosensResult.ratio) + ": " + iobCobCalculator.iobArrayToString(iobPredictionArray))
            aapsLogger.debug(LTag.AUTOSENS, "IOB prediction for AS=" + DecimalFormatter.to2Decimal(1.0) + ": " + iobCobCalculator.iobArrayToString(iobPredictionArray2))
        } else {
            iobPredictions1Series = PointsWithLabelGraphSeries()
            iobPredictions2Series = PointsWithLabelGraphSeries()
        }

        // COB
        cobSeries = FixedLineGraphSeries(Array(cobArray.size) { i -> cobArray[i] }).also {
            it.isDrawBackground = true
            it.backgroundColor = -0x7f000001 and resourceHelper.gc(R.color.cob) //50%
            it.color = resourceHelper.gc(R.color.cob)
            it.thickness = 3
        }
        cobMinFailOverSeries = PointsWithLabelGraphSeries(Array(minFailOverActiveList.size) { i -> minFailOverActiveList[i] })

        // ACTIVITY
        activitySeries = FixedLineGraphSeries(Array(actArrayHist.size) { i -> actArrayHist[i] }).also {
            it.isDrawBackground = false
            it.color = resourceHelper.gc(R.color.activity)
            it.thickness = 3
        }
        activityPredictionSeries = FixedLineGraphSeries(Array(actArrayPrediction.size) { i -> actArrayPrediction[i] }).also {
            it.setCustomPaint(Paint().also { paint ->
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
                paint.color = resourceHelper.gc(R.color.activity)
            })
        }

        // BGI
        minusBgiSeries = FixedLineGraphSeries(Array(bgiArrayHist.size) { i -> bgiArrayHist[i] }).also {
            it.isDrawBackground = false
            it.color = resourceHelper.gc(R.color.bgi)
            it.thickness = 3
        }
        minusBgiHistSeries = FixedLineGraphSeries(Array(bgiArrayPrediction.size) { i -> bgiArrayPrediction[i] }).also {
            it.setCustomPaint(Paint().also { paint ->
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
                paint.color = resourceHelper.gc(R.color.bgi)
            })
        }

        // DEVIATIONS
        deviationsSeries = BarGraphSeries(Array(devArray.size) { i -> devArray[i] }).also {
            it.setValueDependentColor { data: OverviewPlugin.DeviationDataPoint -> data.color }
        }

        // RATIO
        ratioSeries = LineGraphSeries(Array(ratioArray.size) { i -> ratioArray[i] }).also {
            it.color = resourceHelper.gc(R.color.ratio)
            it.thickness = 3
        }

        // DEV SLOPE
        dsMaxSeries = LineGraphSeries(Array(dsMaxArray.size) { i -> dsMaxArray[i] }).also {
            it.color = resourceHelper.gc(R.color.devslopepos)
            it.thickness = 3
        }
        dsMinSeries = LineGraphSeries(Array(dsMinArray.size) { i -> dsMinArray[i] }).also {
            it.color = resourceHelper.gc(R.color.devslopeneg)
            it.thickness = 3
        }

//        profiler.log(LTag.UI, "prepareIobAutosensData() $from", start)
    }

    private fun addUpperChartMargin(maxBgValue: Double) =
        if (profileFunction.getUnits() == GlucoseUnit.MGDL) Round.roundTo(maxBgValue, 40.0) + 80 else Round.roundTo(maxBgValue, 2.0) + 4

    private fun getNearestBg(date: Long): Double {
        bgReadingsArray.let { bgReadingsArray ->
            for (reading in bgReadingsArray) {
                if (reading.timestamp > date) continue
                return Profile.fromMgdlToUnits(reading.value, profileFunction.getUnits())
            }
            return if (bgReadingsArray.isNotEmpty()) Profile.fromMgdlToUnits(bgReadingsArray[0].value, profileFunction.getUnits())
            else Profile.fromMgdlToUnits(100.0, profileFunction.getUnits())
        }
    }

    private fun <E : DataPointWithLabelInterface> List<E>.filterTimeframe(fromTime: Long, endTime: Long): List<E> =
        filter { it.x + it.duration >= fromTime && it.x <= endTime }

    private fun maxTime(arrayMax: DoubleArray, time: Long, value: Double) {
        for(range in 1..4) {
            if (time < endTimeData && time > endTimeData - T.hours(minRangeToDisplay.toLong()).msecs() * range)
                arrayMax.set(2 * (range - 1), max(value, arrayMax.get(2 * (range - 1))))
            if (time < toTime && time > toTime - T.hours(minRangeToDisplay.toLong()).msecs() * range)
                arrayMax.set(2 * (range - 1) + 1, max(value, arrayMax.get(2 * (range - 1) + 1)))
        }
    }
}
