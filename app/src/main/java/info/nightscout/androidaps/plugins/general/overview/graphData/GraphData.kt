package info.nightscout.androidaps.plugins.general.overview.graphData

import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.BarGraphSeries
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import com.jjoe64.graphview.series.Series
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.GlucoseValueDataPoint
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.LoopInterface
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.TreatmentsInterface
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.aps.openAPSSMB.SMBDefaults
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.*
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.Round
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.util.*
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class GraphData(
    private val injector: HasAndroidInjector,
    private val graph: GraphView,
    private val iobCobCalculatorPlugin: IobCobCalculatorPlugin,
    private val treatmentsPlugin: TreatmentsInterface

) {

    // IobCobCalculatorPlugin  Cannot be injected: HistoryBrowser
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var activePlugin: ActivePluginProvider

    var maxY = Double.MIN_VALUE
    private var minY = Double.MAX_VALUE
    private var bgReadingsArray: List<GlucoseValue>? = null
    private val units: String
    private val series: MutableList<Series<*>> = ArrayList()

    init {
        injector.androidInjector().inject(this)
        units = profileFunction.getUnits()
    }

    @Suppress("UNUSED_PARAMETER")
    fun addBgReadings(fromTime: Long, toTime: Long, lowLine: Double, highLine: Double, predictions: MutableList<GlucoseValueDataPoint>?) {
        var maxBgValue = Double.MIN_VALUE
        bgReadingsArray = iobCobCalculatorPlugin.bgReadings
        if (bgReadingsArray?.isEmpty() != false) {
            aapsLogger.debug("No BG data.")
            maxY = if (units == Constants.MGDL) 180.0 else 10.0
            minY = 0.0
            return
        }
        val bgListArray: MutableList<DataPointWithLabelInterface> = ArrayList()
        for (bg in bgReadingsArray!!) {
            if (bg.timestamp < fromTime || bg.timestamp > toTime) continue
            if (bg.value > maxBgValue) maxBgValue = bg.value
            bgListArray.add(GlucoseValueDataPoint(injector, bg))
        }
        if (predictions != null) {
            predictions.sortWith(Comparator { o1: GlucoseValueDataPoint, o2: GlucoseValueDataPoint -> o1.x.compareTo(o2.x) })
            for (prediction in predictions) if (prediction.data.value >= 40) bgListArray.add(prediction)
        }
        maxBgValue = Profile.fromMgdlToUnits(maxBgValue, units)
        maxBgValue = addUpperChartMargin(maxBgValue)
        if (highLine > maxBgValue) maxBgValue = highLine
        maxY = maxBgValue
        minY = 0.0
        addSeries(PointsWithLabelGraphSeries(Array(bgListArray.size) { i -> bgListArray[i] }))
    }

    internal fun setNumVerticalLabels() {
        graph.gridLabelRenderer.numVerticalLabels = if (units == Constants.MGDL) (maxY / 40 + 1).toInt() else (maxY / 2 + 1).toInt()
    }

    private fun addUpperChartMargin(maxBgValue: Double) =
        if (units == Constants.MGDL) Round.roundTo(maxBgValue, 40.0) + 80 else Round.roundTo(maxBgValue, 2.0) + 4

    fun addInRangeArea(fromTime: Long, toTime: Long, lowLine: Double, highLine: Double) {
        val inRangeAreaSeries: AreaGraphSeries<DoubleDataPoint>
        val inRangeAreaDataPoints = arrayOf(
            DoubleDataPoint(fromTime.toDouble(), lowLine, highLine),
            DoubleDataPoint(toTime.toDouble(), lowLine, highLine)
        )
        inRangeAreaSeries = AreaGraphSeries(inRangeAreaDataPoints)
        inRangeAreaSeries.color = 0
        inRangeAreaSeries.isDrawBackground = true
        inRangeAreaSeries.backgroundColor = resourceHelper.gc(R.color.inrangebackground)
        addSeries(inRangeAreaSeries)
    }

    // scale in % of vertical size (like 0.3)
    fun addBasals(fromTime: Long, toTime: Long, scale: Double) {
        var maxBasalValueFound = 0.0
        val basalScale = Scale()
        val baseBasalArray: MutableList<ScaledDataPoint> = ArrayList()
        val tempBasalArray: MutableList<ScaledDataPoint> = ArrayList()
        val basalLineArray: MutableList<ScaledDataPoint> = ArrayList()
        val absoluteBasalLineArray: MutableList<ScaledDataPoint> = ArrayList()
        var lastLineBasal = 0.0
        var lastAbsoluteLineBasal = -1.0
        var lastBaseBasal = 0.0
        var lastTempBasal = 0.0
        var time = fromTime
        while (time < toTime) {
            val profile = profileFunction.getProfile(time)
            if (profile == null) {
                time += 60 * 1000L
                continue
            }
            val basalData = iobCobCalculatorPlugin.getBasalData(profile, time)
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
            maxBasalValueFound = max(maxBasalValueFound, max(tempBasalValue, baseBasalValue))
            time += 60 * 1000L
        }

        // final points
        basalLineArray.add(ScaledDataPoint(toTime, lastLineBasal, basalScale))
        baseBasalArray.add(ScaledDataPoint(toTime, lastBaseBasal, basalScale))
        tempBasalArray.add(ScaledDataPoint(toTime, lastTempBasal, basalScale))
        absoluteBasalLineArray.add(ScaledDataPoint(toTime, lastAbsoluteLineBasal, basalScale))

        // create series
        addSeries(LineGraphSeries(Array(baseBasalArray.size) { i -> baseBasalArray[i] }).also {
            it.isDrawBackground = true
            it.backgroundColor = resourceHelper.gc(R.color.basebasal)
            it.thickness = 0
        })
        addSeries(LineGraphSeries(Array(tempBasalArray.size) { i -> tempBasalArray[i] }).also {
            it.isDrawBackground = true
            it.backgroundColor = resourceHelper.gc(R.color.tempbasal)
            it.thickness = 0
        })
        addSeries(LineGraphSeries(Array(basalLineArray.size) { i -> basalLineArray[i] }).also {
            it.setCustomPaint(Paint().also { paint ->
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = resourceHelper.getDisplayMetrics().scaledDensity * 2
                paint.pathEffect = DashPathEffect(floatArrayOf(2f, 4f), 0f)
                paint.color = resourceHelper.gc(R.color.basal)
            })
        })
        addSeries(LineGraphSeries(Array(absoluteBasalLineArray.size) { i -> absoluteBasalLineArray[i] }).also {
            it.setCustomPaint(Paint().also { absolutePaint ->
                absolutePaint.style = Paint.Style.STROKE
                absolutePaint.strokeWidth = resourceHelper.getDisplayMetrics().scaledDensity * 2
                absolutePaint.color = resourceHelper.gc(R.color.basal)
            })
        })
        basalScale.setMultiplier(maxY * scale / maxBasalValueFound)
    }

    fun addTargetLine(fromTime: Long, toTimeParam: Long, profile: Profile, lastRun: LoopInterface.LastRun?) {
        var toTime = toTimeParam
        val targetsSeriesArray: MutableList<DataPoint> = ArrayList()
        var lastTarget = -1.0
        lastRun?.constraintsProcessed?.let { toTime = max(it.latestPredictionsTime, toTime) }
        var time = fromTime
        while (time < toTime) {
            val tt = treatmentsPlugin.getTempTargetFromHistory(time)
            val value: Double = if (tt == null) {
                Profile.fromMgdlToUnits((profile.getTargetLowMgdl(time) + profile.getTargetHighMgdl(time)) / 2, units)
            } else {
                Profile.fromMgdlToUnits(tt.target(), units)
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
        addSeries(LineGraphSeries(Array(targetsSeriesArray.size) { i -> targetsSeriesArray[i] }).also {
            it.isDrawBackground = false
            it.color = resourceHelper.gc(R.color.tempTargetBackground)
            it.thickness = 2
        })
    }

    fun addTreatments(fromTime: Long, endTime: Long) {
        val filteredTreatments: MutableList<DataPointWithLabelInterface> = ArrayList()
        treatmentsPlugin.treatmentsFromHistory
            .filterTimeframe(fromTime, endTime)
            .filter { !it.isSMB || it.isValid }
            .forEach {
                it.y = getNearestBg(it.x.toLong())
                filteredTreatments.add(it)
            }

        // ProfileSwitch
        treatmentsPlugin.profileSwitchesFromHistory.list
            .filterTimeframe(fromTime, endTime)
            .forEach(filteredTreatments::add)

        // Extended bolus
        if (!activePlugin.activePump.isFakingTempsByExtendedBoluses) {
            treatmentsPlugin.extendedBolusesFromHistory.list
                .filterTimeframe(fromTime, endTime)
                .filter { it.duration != 0L }
                .forEach {
                    it.y = getNearestBg(it.x.toLong())
                    filteredTreatments.add(it)
                }
        }

        // Careportal
        MainApp.getDbHelper().getCareportalEventsFromTime(fromTime - 6 * 60 * 60 * 1000, true)
            .filterTimeframe(fromTime, endTime)
            .forEach {
                it.y = getNearestBg(it.x.toLong())
                filteredTreatments.add(it)
            }

        // increase maxY if a treatment forces it's own height that's higher than a BG value
        filteredTreatments.map { it.y }
            .maxOrNull()
            ?.let(::addUpperChartMargin)
            ?.let { maxY = maxOf(maxY, it) }

        addSeries(PointsWithLabelGraphSeries(filteredTreatments.toTypedArray()))
    }

    private fun getNearestBg(date: Long): Double {
        bgReadingsArray?.let { bgReadingsArray ->
            for (r in bgReadingsArray.indices) {
                val reading = bgReadingsArray[r]
                if (reading.timestamp > date) continue
                return Profile.fromMgdlToUnits(reading.value, units)
            }
            return if (bgReadingsArray.isNotEmpty()) Profile.fromMgdlToUnits(bgReadingsArray[0].value, units) else Profile.fromMgdlToUnits(100.0, units)
        } ?: return Profile.fromMgdlToUnits(100.0, units)
    }

    fun addActivity(fromTime: Long, toTime: Long, useForScale: Boolean, scale: Double) {
        val actArrayHist: MutableList<ScaledDataPoint> = ArrayList()
        val actArrayPrediction: MutableList<ScaledDataPoint> = ArrayList()
        val now = System.currentTimeMillis().toDouble()
        val actScale = Scale()
        var total: IobTotal
        var maxIAValue = 0.0
        var time = fromTime
        while (time <= toTime) {
            val profile = profileFunction.getProfile(time)
            if (profile == null) {
                time += 5 * 60 * 1000L
                continue
            }
            total = iobCobCalculatorPlugin.calculateFromTreatmentsAndTempsSynchronized(time, profile)
            val act: Double = total.activity
            if (time <= now) actArrayHist.add(ScaledDataPoint(time, act, actScale)) else actArrayPrediction.add(ScaledDataPoint(time, act, actScale))
            maxIAValue = max(maxIAValue, abs(act))
            time += 5 * 60 * 1000L
        }
        addSeries(FixedLineGraphSeries(Array(actArrayHist.size) { i -> actArrayHist[i] }).also {
            it.isDrawBackground = false
            it.color = resourceHelper.gc(R.color.activity)
            it.thickness = 3
        })
        addSeries(FixedLineGraphSeries(Array(actArrayPrediction.size) { i -> actArrayPrediction[i] }).also {
            it.setCustomPaint(Paint().also { paint ->
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
                paint.color = resourceHelper.gc(R.color.activity)
            })
        })
        if (useForScale) {
            maxY = maxIAValue
            minY = -maxIAValue
        }
        actScale.setMultiplier(maxY * scale / maxIAValue)
    }

    //Function below show -BGI to be able to compare curves with deviations
    fun addMinusBGI(fromTime: Long, toTime: Long, useForScale: Boolean, scale: Double, devBgiScale: Boolean) {
        val bgiArrayHist: MutableList<ScaledDataPoint> = ArrayList()
        val bgiArrayPrediction: MutableList<ScaledDataPoint> = ArrayList()
        val now = System.currentTimeMillis().toDouble()
        val bgiScale = Scale()
        var total: IobTotal
        var maxBGIValue = 0.0
        var time = fromTime
        while (time <= toTime) {
            val profile = profileFunction.getProfile(time)
            if (profile == null) {
                time += 5 * 60 * 1000L
                continue
            }
            val deviation = if (devBgiScale) iobCobCalculatorPlugin.getAutosensData(time)?.deviation ?:0.0 else 0.0

            total = iobCobCalculatorPlugin.calculateFromTreatmentsAndTempsSynchronized(time, profile)
            val bgi: Double = total.activity * profile.getIsfMgdl(time) * 5.0
            if (time <= now) bgiArrayHist.add(ScaledDataPoint(time, bgi, bgiScale)) else bgiArrayPrediction.add(ScaledDataPoint(time, bgi, bgiScale))
            maxBGIValue = max(maxBGIValue, max(abs(bgi), deviation))
            time += 5 * 60 * 1000L
        }
        addSeries(FixedLineGraphSeries(Array(bgiArrayHist.size) { i -> bgiArrayHist[i] }).also {
            it.isDrawBackground = false
            it.color = resourceHelper.gc(R.color.bgi)
            it.thickness = 3
        })
        addSeries(FixedLineGraphSeries(Array(bgiArrayPrediction.size) { i -> bgiArrayPrediction[i] }).also {
            it.setCustomPaint(Paint().also { paint ->
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
                paint.color = resourceHelper.gc(R.color.bgi)
            })
        })
        if (useForScale) {
            maxY = maxBGIValue
            minY = -maxBGIValue
        }
        bgiScale.setMultiplier(maxY * scale / maxBGIValue)
    }

    // scale in % of vertical size (like 0.3)
    fun addIob(fromTime: Long, toTime: Long, useForScale: Boolean, scale: Double, showPrediction: Boolean, absScale: Boolean) {
        val iobSeries: FixedLineGraphSeries<ScaledDataPoint?>
        val iobArray: MutableList<ScaledDataPoint> = ArrayList()
        var maxIobValueFound = Double.MIN_VALUE
        var lastIob = 0.0
        val iobScale = Scale()
        var time = fromTime
        while (time <= toTime) {
            val profile = profileFunction.getProfile(time)
            var iob = 0.0
            var absIob = 0.0
            if (profile != null) {
                iob = iobCobCalculatorPlugin.calculateFromTreatmentsAndTempsSynchronized(time, profile).iob
                if (absScale) absIob = iobCobCalculatorPlugin.calculateAbsInsulinFromTreatmentsAndTempsSynchronized(time).iob
            }
            if (abs(lastIob - iob) > 0.02) {
                if (abs(lastIob - iob) > 0.2) iobArray.add(ScaledDataPoint(time, lastIob, iobScale))
                iobArray.add(ScaledDataPoint(time, iob, iobScale))
                maxIobValueFound = if (absScale) max(maxIobValueFound, abs(absIob)) else max(maxIobValueFound, abs(iob))
                lastIob = iob
            }
            time += 5 * 60 * 1000L
        }
        iobSeries = FixedLineGraphSeries(Array(iobArray.size) { i -> iobArray[i] }).also {
            it.isDrawBackground = true
            it.backgroundColor = -0x7f000001 and resourceHelper.gc(R.color.iob) //50%
            it.color = resourceHelper.gc(R.color.iob)
            it.thickness = 3
        }
        if (showPrediction) {
            val autosensData = iobCobCalculatorPlugin.getLastAutosensDataSynchronized("GraphData")
            val lastAutosensResult = autosensData?.autosensResult ?: AutosensResult()
            val isTempTarget = treatmentsPlugin.getTempTargetFromHistory(System.currentTimeMillis()) != null
            val iobPrediction: MutableList<DataPointWithLabelInterface> = ArrayList()
            val iobPredictionArray = iobCobCalculatorPlugin.calculateIobArrayForSMB(lastAutosensResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, isTempTarget)
            for (i in iobPredictionArray) {
                iobPrediction.add(i.setColor(resourceHelper.gc(R.color.iobPredAS)))
                maxIobValueFound = max(maxIobValueFound, abs(i.iob))
            }
            addSeries(PointsWithLabelGraphSeries(Array(iobPrediction.size) { i -> iobPrediction[i] }))
            val iobPrediction2: MutableList<DataPointWithLabelInterface> = ArrayList()
            val iobPredictionArray2 = iobCobCalculatorPlugin.calculateIobArrayForSMB(AutosensResult(), SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, isTempTarget)
            for (i in iobPredictionArray2) {
                iobPrediction2.add(i.setColor(resourceHelper.gc(R.color.iobPred)))
                maxIobValueFound = max(maxIobValueFound, abs(i.iob))
            }
            addSeries(PointsWithLabelGraphSeries(Array(iobPrediction2.size) { i -> iobPrediction2[i] }))
            aapsLogger.debug(LTag.AUTOSENS, "IOB prediction for AS=" + DecimalFormatter.to2Decimal(lastAutosensResult.ratio) + ": " + iobCobCalculatorPlugin.iobArrayToString(iobPredictionArray))
            aapsLogger.debug(LTag.AUTOSENS, "IOB prediction for AS=" + DecimalFormatter.to2Decimal(1.0) + ": " + iobCobCalculatorPlugin.iobArrayToString(iobPredictionArray2))
        }
        if (useForScale) {
            maxY = maxIobValueFound
            minY = -maxIobValueFound
        }
        iobScale.setMultiplier(maxY * scale / maxIobValueFound)
        addSeries(iobSeries)
    }

    // scale in % of vertical size (like 0.3)
    fun addAbsIob(fromTime: Long, toTime: Long, useForScale: Boolean, scale: Double) {
        val iobSeries: FixedLineGraphSeries<ScaledDataPoint?>
        val iobArray: MutableList<ScaledDataPoint> = ArrayList()
        var maxIobValueFound = Double.MIN_VALUE
        var lastIob = 0.0
        val iobScale = Scale()
        var time = fromTime
        while (time <= toTime) {
            val profile = profileFunction.getProfile(time)
            var iob = 0.0
            if (profile != null) iob = iobCobCalculatorPlugin.calculateAbsInsulinFromTreatmentsAndTempsSynchronized(time).iob
            if (abs(lastIob - iob) > 0.02) {
                if (abs(lastIob - iob) > 0.2) iobArray.add(ScaledDataPoint(time, lastIob, iobScale))
                iobArray.add(ScaledDataPoint(time, iob, iobScale))
                maxIobValueFound = max(maxIobValueFound, abs(iob))
                lastIob = iob
            }
            time += 5 * 60 * 1000L
        }
        iobSeries = FixedLineGraphSeries(Array(iobArray.size) { i -> iobArray[i] }).also {
            it.isDrawBackground = true
            it.backgroundColor = -0x7f000001 and resourceHelper.gc(R.color.iob) //50%
            it.color = resourceHelper.gc(R.color.iob)
            it.thickness = 3
        }
        if (useForScale) {
            maxY = maxIobValueFound
            minY = -maxIobValueFound
        }
        iobScale.setMultiplier(maxY * scale / maxIobValueFound)
        addSeries(iobSeries)
    }

    // scale in % of vertical size (like 0.3)
    fun addCob(fromTime: Long, toTime: Long, useForScale: Boolean, scale: Double) {
        val minFailOverActiveList: MutableList<DataPointWithLabelInterface> = ArrayList()
        val cobArray: MutableList<ScaledDataPoint> = ArrayList()
        var maxCobValueFound = 0.0
        var lastCob = 0
        val cobScale = Scale()
        var time = fromTime
        while (time <= toTime) {
            iobCobCalculatorPlugin.getAutosensData(time)?.let { autosensData ->
                val cob = autosensData.cob.toInt()
                if (cob != lastCob) {
                    if (autosensData.carbsFromBolus > 0) cobArray.add(ScaledDataPoint(time, lastCob.toDouble(), cobScale))
                    cobArray.add(ScaledDataPoint(time, cob.toDouble(), cobScale))
                    maxCobValueFound = max(maxCobValueFound, cob.toDouble())
                    lastCob = cob
                }
                if (autosensData.failoverToMinAbsorbtionRate) {
                    autosensData.setScale(cobScale)
                    autosensData.setChartTime(time)
                    minFailOverActiveList.add(autosensData)
                }
            }
            time += 5 * 60 * 1000L
        }

        // COB
        addSeries(FixedLineGraphSeries(Array(cobArray.size) { i -> cobArray[i] }).also {
            it.isDrawBackground = true
            it.backgroundColor = -0x7f000001 and resourceHelper.gc(R.color.cob) //50%
            it.color = resourceHelper.gc(R.color.cob)
            it.thickness = 3
        })
        if (useForScale) {
            maxY = maxCobValueFound
            minY = 0.0
        }
        cobScale.setMultiplier(maxY * scale / maxCobValueFound)
        addSeries(PointsWithLabelGraphSeries(Array(minFailOverActiveList.size) { i -> minFailOverActiveList[i] }))
    }

    // scale in % of vertical size (like 0.3)
    fun addDeviations(fromTime: Long, toTime: Long, useForScale: Boolean, scale: Double, devBgiScale: Boolean) {
        class DeviationDataPoint(x: Double, y: Double, var color: Int, scale: Scale) : ScaledDataPoint(x, y, scale)

        val devArray: MutableList<DeviationDataPoint> = ArrayList()
        var maxDevValueFound = 0.0
        val devScale = Scale()
        var time = fromTime
        var total: IobTotal

        while (time <= toTime) {
            // if align Dev Scale with BGI scale, then calculate BGI value, else bgi = 0.0
            val bgi: Double = if (devBgiScale) {
                    val profile = profileFunction.getProfile(time)
                    total = iobCobCalculatorPlugin.calculateFromTreatmentsAndTempsSynchronized(time, profile)
                    total.activity * (profile?.getIsfMgdl(time) ?: 0.0) * 5.0
                } else 0.0

            iobCobCalculatorPlugin.getAutosensData(time)?.let { autosensData ->
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
                devArray.add(DeviationDataPoint(time.toDouble(), autosensData.deviation, color, devScale))
                maxDevValueFound = max(maxDevValueFound, max(abs(autosensData.deviation), abs(bgi)))
            }
            time += 5 * 60 * 1000L
        }

        // DEVIATIONS
        addSeries(BarGraphSeries(Array(devArray.size) { i -> devArray[i] }).also {
            it.setValueDependentColor { data: DeviationDataPoint -> data.color }
        })
        if (useForScale) {
            maxY = maxDevValueFound
            minY = -maxY
        }
        devScale.setMultiplier(maxY * scale / maxDevValueFound)
    }

    // scale in % of vertical size (like 0.3)
    fun addRatio(fromTime: Long, toTime: Long, useForScale: Boolean, scale: Double) {
        val ratioArray: MutableList<ScaledDataPoint> = ArrayList()
        var maxRatioValueFound = Double.MIN_VALUE
        var minRatioValueFound = Double.MAX_VALUE
        val ratioScale = Scale()
        var time = fromTime
        while (time <= toTime) {
            iobCobCalculatorPlugin.getAutosensData(time)?.let { autosensData ->
                ratioArray.add(ScaledDataPoint(time, autosensData.autosensResult.ratio - 1, ratioScale))
                maxRatioValueFound = max(maxRatioValueFound, autosensData.autosensResult.ratio - 1)
                minRatioValueFound = min(minRatioValueFound, autosensData.autosensResult.ratio - 1)
            }
            time += 5 * 60 * 1000L
        }

        // RATIOS
        addSeries(LineGraphSeries(Array(ratioArray.size) { i -> ratioArray[i] }).also {
            it.color = resourceHelper.gc(R.color.ratio)
            it.thickness = 3
        })
        if (useForScale) {
            maxY = max(maxRatioValueFound, abs(minRatioValueFound))
            minY = -maxY
        }
        ratioScale.setMultiplier(maxY * scale / max(maxRatioValueFound, abs(minRatioValueFound)))
    }

    // scale in % of vertical size (like 0.3)
    fun addDeviationSlope(fromTime: Long, toTime: Long, useForScale: Boolean, scale: Double) {
        val dsMaxArray: MutableList<ScaledDataPoint> = ArrayList()
        val dsMinArray: MutableList<ScaledDataPoint> = ArrayList()
        var maxFromMaxValueFound = 0.0
        var maxFromMinValueFound = 0.0
        val dsMaxScale = Scale()
        val dsMinScale = Scale()
        var time = fromTime
        while (time <= toTime) {
            iobCobCalculatorPlugin.getAutosensData(time)?.let { autosensData ->
                dsMaxArray.add(ScaledDataPoint(time, autosensData.slopeFromMaxDeviation, dsMaxScale))
                dsMinArray.add(ScaledDataPoint(time, autosensData.slopeFromMinDeviation, dsMinScale))
                maxFromMaxValueFound = max(maxFromMaxValueFound, abs(autosensData.slopeFromMaxDeviation))
                maxFromMinValueFound = max(maxFromMinValueFound, abs(autosensData.slopeFromMinDeviation))
            }
            time += 5 * 60 * 1000L
        }

        // Slopes
        addSeries(LineGraphSeries(Array(dsMaxArray.size) { i -> dsMaxArray[i] }).also {
            it.color = resourceHelper.gc(R.color.devslopepos)
            it.thickness = 3
        })
        addSeries(LineGraphSeries(Array(dsMinArray.size) { i -> dsMinArray[i] }).also {
            it.color = resourceHelper.gc(R.color.devslopeneg)
            it.thickness = 3
        })
        if (useForScale) {
            maxY = max(maxFromMaxValueFound, maxFromMinValueFound)
            minY = -maxY
        }
        dsMaxScale.setMultiplier(maxY * scale / maxFromMaxValueFound)
        dsMinScale.setMultiplier(maxY * scale / maxFromMinValueFound)
    }

    // scale in % of vertical size (like 0.3)
    fun addNowLine(now: Long) {
        val nowPoints = arrayOf(
            DataPoint(now.toDouble(), 0.0),
            DataPoint(now.toDouble(), maxY)
        )
        addSeries(LineGraphSeries(nowPoints).also {
            it.isDrawDataPoints = false
            // custom paint to make a dotted line
            it.setCustomPaint(Paint().also { paint ->
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                paint.pathEffect = DashPathEffect(floatArrayOf(10f, 20f), 0f)
                paint.color = Color.WHITE
            })
        })
    }

    fun formatAxis(fromTime: Long, endTime: Long) {
        graph.viewport.setMaxX(endTime.toDouble())
        graph.viewport.setMinX(fromTime.toDouble())
        graph.viewport.isXAxisBoundsManual = true
        graph.gridLabelRenderer.labelFormatter = TimeAsXAxisLabelFormatter("HH")
        graph.gridLabelRenderer.numHorizontalLabels = 7 // only 7 because of the space
    }

    private fun addSeries(s: Series<*>) = series.add(s)

    fun performUpdate() {
        // clear old data
        graph.series.clear()

        // add pre calculated series
        for (s in series) {
            if (!s.isEmpty) {
                s.onGraphViewAttached(graph)
                graph.series.add(s)
            }
        }
        var step = 1.0
        if (maxY < 1) step = 0.1
        graph.viewport.setMaxY(Round.ceilTo(maxY, step))
        graph.viewport.setMinY(Round.floorTo(minY, step))
        graph.viewport.isYAxisBoundsManual = true

        // draw it
        graph.onDataChanged(false, false)
    }
}

private fun <E : DataPointWithLabelInterface> List<E>.filterTimeframe(fromTime: Long, endTime: Long): List<E> =
    filter { it.x + it.duration >= fromTime && it.x <= endTime }
