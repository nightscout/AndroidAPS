package app.aaps.workflow

import android.content.Context
import android.os.SystemClock
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.aps.SMBDefaults
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.AbsIobGraphData
import app.aaps.core.interfaces.overview.graph.ActivityGraphData
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.overview.graph.BgiGraphData
import app.aaps.core.interfaces.overview.graph.CobFailOverPoint
import app.aaps.core.interfaces.overview.graph.CobGraphData
import app.aaps.core.interfaces.overview.graph.DevSlopeGraphData
import app.aaps.core.interfaces.overview.graph.DeviationDataPoint
import app.aaps.core.interfaces.overview.graph.DeviationType
import app.aaps.core.interfaces.overview.graph.DeviationsGraphData
import app.aaps.core.interfaces.overview.graph.GraphDataPoint
import app.aaps.core.interfaces.overview.graph.IobGraphData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.overview.graph.RatioGraphData
import app.aaps.core.interfaces.overview.graph.TimeRange
import app.aaps.core.interfaces.overview.graph.VarSensGraphData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.rx.events.EventBucketedDataCreated
import app.aaps.core.interfaces.smoothing.SmoothingContext
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.combine
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.workflow.iob.fromCarbs
import kotlinx.coroutines.Dispatchers
import java.util.Calendar
import java.util.GregorianCalendar
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Merged worker covering: BG load+smooth, bucketed data prep, BG readings prep,
 * IOB/COB autosens (oref1 or oref), and the IOB/autosens graph data pass.
 *
 * [bgDataReload] controls the optional initial DB load+smooth pass.
 * [emitFinalProgress] is set when this worker is the last in the chain (HISTORY);
 * otherwise [PostCalculationWorker] emits the final signal.
 */
class PrepareGraphDataWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var workflowChainData: WorkflowChainData
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var config: Config
    @Inject lateinit var profiler: Profiler
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var decimalFormatter: DecimalFormatter
    @Inject lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Inject lateinit var autosensDataProvider: Provider<AutosensData>

    class PrepareGraphData(
        val iobCobCalculator: IobCobCalculator, // cannot be injected : HistoryBrowser uses different instance
        val overviewData: OverviewData,
        val cache: OverviewDataCache,
        val signals: CalculationSignalsEmitter,
        val reason: String,
        val end: Long,
        val bgDataReload: Boolean,
        val limitDataToOldestAvailable: Boolean,
        val triggeredByNewBG: Boolean,
        val emitFinalProgress: Boolean
    )

    override suspend fun doWorkAndLog(): Result {
        val data = workflowChainData.prepareFor(
            inputData.getString(WorkflowChainData.JOB_KEY),
            inputData.getLong(WorkflowChainData.GEN_KEY, -1L)
        ) ?: return Result.failure(workDataOf("Error" to "missing or stale input data"))

        // ===== Phase 1: Load BG into ads + smooth (was LoadBgDataWorker) =====
        if (data.bgDataReload) {
            data.iobCobCalculator.ads.loadBgData(data.end)
            data.iobCobCalculator.ads.smoothData(data.iobCobCalculator)
            rxBus.send(EventBucketedDataCreated())
            data.iobCobCalculator.clearCache()
        }
        if (isStopped) return Result.failure(workDataOf("Error" to "stopped"))

        // ===== Phase 2: Bucketed data → cache (was PrepareBucketedDataWorker) =====
        prepareBucketedData(data)
        if (isStopped) return Result.failure(workDataOf("Error" to "stopped"))

        // ===== Phase 3: BG readings → cache (was PrepareBgDataWorker) =====
        prepareBgData(data)

        data.signals.emitProgress(CalculationWorkflow.ProgressData.DRAW_BG, 100)
        if (isStopped) return Result.failure(workDataOf("Error" to "stopped"))

        // ===== Phases 4 & 5: IOB/COB autosens + graph data prep (was IobCobOref* + PrepareIobAutosens) =====
        if (activePlugin.activeSensitivity.isOref1) runIobCobOref1(data) else runIobCobOref(data)
        if (isStopped) return Result.failure(workDataOf("Error" to "stopped"))
        prepareIobAutosensGraphData(data)
        if (isStopped) return Result.failure(workDataOf("Error" to "stopped"))
        data.signals.emitProgress(CalculationWorkflow.ProgressData.DRAW_IOB, 100)

        // ===== Phase 6: Final progress emit (terminal worker only) =====
        if (data.emitFinalProgress) {
            data.signals.emitProgress(CalculationWorkflow.ProgressData.DRAW_FINAL, 100)
        }

        return Result.success()
    }

    // ---------- Phase 1 helpers (LoadBgDataWorker logic) ----------

    private suspend fun AutosensDataStore.loadBgData(to: Long) {
        val start = to - T.hours((24 + 10 /* max dia */).toLong()).msecs()
        // there can be some readings with time in close future (caused by wrong time setting on sensor)
        // so add 2 minutes
        val readings = persistenceLayer.getBgReadingsDataFromTimeToTime(start, to + T.mins(2).msecs(), false)
        synchronized(dataLock) {
            bgReadings = readings
            aapsLogger.debug(LTag.AUTOSENS) { "BG data loaded. Size: ${bgReadings.size} Start date: ${dateUtil.dateAndTimeString(start)} End date: ${dateUtil.dateAndTimeString(to)}" }
            createBucketedData(aapsLogger, dateUtil)
        }
    }

    private suspend fun AutosensDataStore.smoothData(iobCobCalculator: IobCobCalculator) {
        val bolusIob = iobCobCalculator.calculateIobFromBolus().iob
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().iob
        val smoothingContext = SmoothingContext(cachedTotalIobUnits = bolusIob + basalIob)
        val workingCopy: MutableList<InMemoryGlucoseValue> = synchronized(dataLock) {
            bucketedData?.map { it.copy(smoothed = null) }?.toMutableList()
        } ?: return
        val smoothed = activePlugin.activeSmoothing.smooth(workingCopy, smoothingContext)
        synchronized(dataLock) {
            bucketedData = smoothed
        }
    }

    // ---------- Phase 2 (PrepareBucketedDataWorker logic) ----------

    private fun prepareBucketedData(data: PrepareGraphData) {
        val bucketedData = data.iobCobCalculator.ads.getBucketedDataTableCopy() ?: return
        if (bucketedData.isEmpty()) {
            aapsLogger.debug("No bucketed data.")
            return
        }
        // Refresh the 24h window so history navigation doesn't inherit a stale range.
        val newToTime = data.overviewData.toTime
        val newFromTime = newToTime - T.hours(Constants.GRAPH_TIME_RANGE_HOURS.toLong()).msecs()
        data.cache.updateTimeRange(TimeRange(fromTime = newFromTime, toTime = newToTime, endTime = newToTime))

        val highMark = preferences.get(UnitDoubleKey.OverviewHighMark)
        val lowMark = preferences.get(UnitDoubleKey.OverviewLowMark)

        val bucketedDataPoints = bucketedData
            .filter { it.timestamp in newFromTime..newToTime }
            .map { value ->
                val valueInUnits = profileUtil.fromMgdlToUnits(value.recalculated)
                val range = when {
                    valueInUnits > highMark -> BgRange.HIGH
                    valueInUnits < lowMark  -> BgRange.LOW
                    else                    -> BgRange.IN_RANGE
                }
                BgDataPoint(
                    timestamp = value.timestamp,
                    value = valueInUnits,
                    range = range,
                    type = BgType.BUCKETED,
                    filledGap = value.filledGap
                )
            }
        data.cache.updateBucketedData(bucketedDataPoints)
    }

    // ---------- Phase 3 (PrepareBgDataWorker logic) ----------

    private suspend fun prepareBgData(data: PrepareGraphData) {
        val toTime = data.overviewData.toTime
        val fromTime = toTime - T.hours(Constants.GRAPH_TIME_RANGE_HOURS.toLong()).msecs()
        val bgReadingsArray = persistenceLayer.getBgReadingsDataFromTimeToTime(fromTime, toTime, false)

        val highMarkInUnits = preferences.get(UnitDoubleKey.OverviewHighMark)
        val lowMarkInUnits = preferences.get(UnitDoubleKey.OverviewLowMark)

        val bgDataPoints = bgReadingsArray
            .filter { it.timestamp in fromTime..toTime }
            .map { bg ->
                val valueInUnits = profileUtil.fromMgdlToUnits(bg.value)
                BgDataPoint(
                    timestamp = bg.timestamp,
                    value = valueInUnits,
                    range = when {
                        valueInUnits > highMarkInUnits -> BgRange.HIGH
                        valueInUnits < lowMarkInUnits  -> BgRange.LOW
                        else                           -> BgRange.IN_RANGE
                    },
                    type = BgType.REGULAR
                )
            }

        data.cache.updateTimeRange(TimeRange(fromTime = fromTime, toTime = toTime, endTime = toTime))
        data.cache.updateBgReadings(bgDataPoints)
    }

    // ---------- Phase 4: IOB/COB oref1 (was IobCobOref1Worker) ----------

    private suspend fun runIobCobOref1(data: PrepareGraphData) {
        val start = dateUtil.now()
        try {
            aapsLogger.debug(LTag.AUTOSENS, "AUTOSENSDATA thread started: ${data.reason}")
            if (!profileFunction.isProfileValid("IobCobThread")) {
                aapsLogger.debug(LTag.AUTOSENS, "Aborting calculation thread (No profile): ${data.reason}")
                return
            }
            val oldestTimeWithData = data.iobCobCalculator.calculateDetectionStart(data.end, data.limitDataToOldestAvailable)
            // work on local copy and set back when finished
            val ads = data.iobCobCalculator.ads.clone()
            val bucketedData = ads.bucketedData
            val autosensDataTable = ads.autosensDataTable
            if (bucketedData == null || bucketedData.size < 3) {
                aapsLogger.debug(LTag.AUTOSENS) { "Aborting calculation thread (No bucketed data available): ${data.reason}" }
                return
            }
            val prevDataTime = ads.roundUpTime(bucketedData[bucketedData.size - 3].timestamp)
            aapsLogger.debug(LTag.AUTOSENS) { "Prev data time: " + dateUtil.dateAndTimeString(prevDataTime) }
            var previous = autosensDataTable[prevDataTime]
            // start from oldest to be able sub cob
            for (i in bucketedData.size - 4 downTo 0) {
                data.signals.emitProgress(CalculationWorkflow.ProgressData.IOB_COB_OREF, 100 - (100.0 * i / bucketedData.size).toInt())
                if (isStopped) {
                    aapsLogger.debug(LTag.AUTOSENS, "Aborting calculation thread (trigger): ${data.reason}")
                    return
                }
                // check if data already exists
                var bgTime = bucketedData[i].timestamp
                bgTime = ads.roundUpTime(bgTime)
                if (bgTime > ads.roundUpTime(dateUtil.now())) continue
                var existing: AutosensData?
                if (autosensDataTable[bgTime].also { existing = it } != null) {
                    previous = existing
                    continue
                }
                val profile = profileFunction.getProfile(bgTime)
                if (profile == null) {
                    aapsLogger.debug(LTag.AUTOSENS, "Aborting calculation thread (no profile): ${data.reason}")
                    continue
                }
                aapsLogger.debug(LTag.AUTOSENS, "Processing calculation thread: ${data.reason} ($i/${bucketedData.size})")
                val autosensData = autosensDataProvider.get()
                autosensData.time = bgTime
                if (previous != null) autosensData.activeCarbsList = previous.cloneCarbsList() else autosensData.activeCarbsList = ArrayList()

                var avgDelta: Double
                var delta: Double
                val bg: Double = bucketedData[i].recalculated
                if (bg < 39 || bucketedData[i + 3].recalculated < 39) {
                    aapsLogger.error("! value < 39")
                    continue
                }
                autosensData.bg = bg
                delta = bg - bucketedData[i + 1].recalculated
                avgDelta = (bg - bucketedData[i + 3].recalculated) / 3
                val sens = profile.getIsfMgdlForCarbs(bgTime, "iobCobOref1Worker", config, processedDeviceStatusData)
                val iob = data.iobCobCalculator.calculateFromTreatmentsAndTemps(bgTime, profile)
                val bgi = -iob.activity * sens * 5
                val deviation = delta - bgi
                val avgDeviation = ((avgDelta - bgi) * 1000).roundToLong() / 1000.0
                var slopeFromMaxDeviation = 0.0
                var slopeFromMinDeviation = 999.0

                // https://github.com/openaps/oref0/blob/master/lib/determine-basal/cob-autosens.js#L169
                if (i < bucketedData.size - 16) { // we need 1h of data to calculate minDeviationSlope
                    var maxDeviation = 0.0
                    var minDeviation = 999.0
                    val hourAgo = bgTime + 10 * 1000 - 60 * 60 * 1000L
                    val hourAgoData = ads.getAutosensDataAtTime(hourAgo)
                    if (hourAgoData != null) {
                        val initialIndex = autosensDataTable.indexOfKey(hourAgoData.time)
                        aapsLogger.debug(LTag.AUTOSENS) { ">>>>> bucketed_data.size()=" + bucketedData.size + " i=" + i + " hourAgoData=" + hourAgoData.toString() }
                        var past = 1
                        while (past < 12) {
                            val ad = autosensDataTable.valueAt(initialIndex + past)
                            aapsLogger.debug(LTag.AUTOSENS) { ">>>>> past=$past ad=$ad" }
                            // let it here crash on NPE to get more data as i cannot reproduce this bug
                            val deviationSlope = (ad.avgDeviation - avgDeviation) / (ad.time - bgTime) * 1000 * 60 * 5
                            if (ad.avgDeviation > maxDeviation) {
                                slopeFromMaxDeviation = min(0.0, deviationSlope)
                                maxDeviation = ad.avgDeviation
                            }
                            if (ad.avgDeviation < minDeviation) {
                                slopeFromMinDeviation = max(0.0, deviationSlope)
                                minDeviation = ad.avgDeviation
                            }
                            past++
                        }
                    } else {
                        aapsLogger.debug(LTag.AUTOSENS) { ">>>>> bucketed_data.size()=${bucketedData.size} i=$i hourAgoData=null" }
                    }
                }
                // Use exclusive start (+1ms) to avoid double-counting carbs at window boundaries
                // when consecutive 5-min windows share a boundary timestamp (issue #4596)
                val recentCarbTreatments = persistenceLayer.getCarbsFromTimeToTimeExpanded(bgTime - T.mins(5).msecs() + 1, bgTime, true)
                for (recentCarbTreatment in recentCarbTreatments) {
                    autosensData.carbsFromBolus += recentCarbTreatment.amount
                    val isAAPSOrWeighted = activePlugin.activeSensitivity.isMinCarbsAbsorptionDynamic
                    if (recentCarbTreatment.amount > 0) {
                        val sensForCarbs = profile.getIsfMgdlForCarbs(recentCarbTreatment.timestamp, "fromCarbs", config, processedDeviceStatusData)
                        val ic = profile.getIc(recentCarbTreatment.timestamp)
                        autosensData.activeCarbsList.add(fromCarbs(recentCarbTreatment, isOref1 = true, isAAPSOrWeighted, sensForCarbs, ic, aapsLogger, dateUtil, preferences))
                    }
                    autosensData.pastSensitivity += "[" + decimalFormatter.to0Decimal(recentCarbTreatment.amount) + "g]"
                }

                // if we are absorbing carbs
                if (previous != null && previous.cob > 0) {
                    val totalMinCarbsImpact = preferences.get(DoubleKey.ApsSmbMin5MinCarbsImpact)
                    val ci = max(deviation, totalMinCarbsImpact)
                    if (ci != deviation) autosensData.failOverToMinAbsorptionRate = true
                    autosensData.this5MinAbsorption = ci * profile.getIc(bgTime) / sens
                    autosensData.cob = max(previous.cob - autosensData.this5MinAbsorption, 0.0)
                    autosensData.mealCarbs = previous.mealCarbs
                    autosensData.deductAbsorbedCarbs()
                    autosensData.usedMinCarbsImpact = totalMinCarbsImpact
                    autosensData.absorbing = previous.absorbing
                    autosensData.mealStartCounter = previous.mealStartCounter
                    autosensData.type = previous.type
                    autosensData.uam = previous.uam
                }
                val isAAPSOrWeighted = activePlugin.activeSensitivity.isMinCarbsAbsorptionDynamic
                autosensData.removeOldCarbs(bgTime, isAAPSOrWeighted)
                autosensData.cob = max(autosensData.cob + autosensData.carbsFromBolus, 0.0)
                autosensData.mealCarbs += autosensData.carbsFromBolus
                autosensData.deviation = deviation
                autosensData.bgi = bgi
                autosensData.sens = sens
                autosensData.delta = delta
                autosensData.avgDelta = avgDelta
                autosensData.avgDeviation = avgDeviation
                autosensData.slopeFromMaxDeviation = slopeFromMaxDeviation
                autosensData.slopeFromMinDeviation = slopeFromMinDeviation

                // If mealCOB is zero but all deviations since hitting COB=0 are positive, exclude from autosens
                if (autosensData.cob > 0 || autosensData.absorbing || autosensData.mealCarbs > 0) {
                    autosensData.absorbing = deviation > 0
                    if (autosensData.mealStartCounter > 60 && autosensData.cob < 0.5) {
                        autosensData.absorbing = false
                    }
                    if (!autosensData.absorbing && autosensData.cob < 0.5) {
                        autosensData.mealCarbs = 0.0
                    }
                    if (autosensData.type != "csf") {
                        autosensData.mealStartCounter = 0
                    }
                    autosensData.mealStartCounter++
                    autosensData.type = "csf"
                } else {
                    val currentBasal = profile.getBasal(bgTime)
                    if (iob.iob > 2 * currentBasal || autosensData.uam || autosensData.mealStartCounter < 9) {
                        autosensData.mealStartCounter++
                        autosensData.uam = deviation > 0
                        autosensData.type = "uam"
                    } else {
                        autosensData.type = "non-meal"
                    }
                }

                when (autosensData.type) {
                    "non-meal" -> {
                        when {
                            abs(deviation) < Constants.DEVIATION_TO_BE_EQUAL -> {
                                autosensData.pastSensitivity += "="
                                autosensData.validDeviation = true
                            }

                            deviation > 0                                    -> {
                                autosensData.pastSensitivity += "+"
                                autosensData.validDeviation = true
                            }

                            else                                             -> {
                                autosensData.pastSensitivity += "-"
                                autosensData.validDeviation = true
                            }
                        }
                    }

                    "uam"      -> {
                        autosensData.pastSensitivity += "u"
                    }

                    else       -> {
                        autosensData.pastSensitivity += "x"
                    }
                }

                // add one neutral deviation every 2 hours to help decay over long exclusion periods
                val calendar = GregorianCalendar()
                calendar.timeInMillis = bgTime
                val minute = calendar[Calendar.MINUTE]
                val hours = calendar[Calendar.HOUR_OF_DAY]
                if (minute in 0..4 && hours % 2 == 0) autosensData.extraDeviation.add(0.0)
                previous = autosensData
                if (bgTime < dateUtil.now()) autosensDataTable.put(bgTime, autosensData)
                aapsLogger.debug(LTag.AUTOSENS) {
                    "Running detectSensitivity from: " + dateUtil.dateAndTimeString(oldestTimeWithData) + " to: " + dateUtil.dateAndTimeString(bgTime) + " lastDataTime:" + ads.lastDataTime(dateUtil)
                }
                val sensitivity = activePlugin.activeSensitivity.detectSensitivity(ads, oldestTimeWithData, bgTime)
                aapsLogger.debug(LTag.AUTOSENS, "Sensitivity result: $sensitivity")
                autosensData.autosensResult = sensitivity
                aapsLogger.debug(LTag.AUTOSENS) { autosensData.toString() }
            }
            data.iobCobCalculator.ads = ads
            Thread {
                SystemClock.sleep(1000)
                rxBus.send(EventAutosensCalculationFinished(data.triggeredByNewBG))
            }.start()
        } finally {
            data.signals.emitProgress(CalculationWorkflow.ProgressData.IOB_COB_OREF, 100)
            aapsLogger.debug(LTag.AUTOSENS) { "AUTOSENSDATA thread ended: ${data.reason}" }
            profiler.log(LTag.AUTOSENS, "IobCobOref1Thread", start)
        }
    }

    // ---------- Phase 4: IOB/COB oref (was IobCobOrefWorker) ----------

    private suspend fun runIobCobOref(data: PrepareGraphData) {
        val start = dateUtil.now()
        try {
            aapsLogger.debug(LTag.AUTOSENS) { "AUTOSENSDATA thread started: ${data.reason}" }
            if (!profileFunction.isProfileValid("IobCobThread")) {
                aapsLogger.debug(LTag.AUTOSENS) { "Aborting calculation thread (No profile): ${data.reason}" }
                return
            }
            val oldestTimeWithData = data.iobCobCalculator.calculateDetectionStart(data.end, data.limitDataToOldestAvailable)
            // work on local copy and set back when finished
            val ads = data.iobCobCalculator.ads.clone()
            val bucketedData = ads.bucketedData
            val autosensDataTable = ads.autosensDataTable
            if (bucketedData == null || bucketedData.size < 3) {
                aapsLogger.debug(LTag.AUTOSENS) { "Aborting calculation thread (No bucketed data available): ${data.reason}" }
                return
            }
            val prevDataTime = ads.roundUpTime(bucketedData[bucketedData.size - 3].timestamp)
            aapsLogger.debug(LTag.AUTOSENS) { "Prev data time: " + dateUtil.dateAndTimeString(prevDataTime) }
            var previous = autosensDataTable[prevDataTime]
            // start from oldest to be able to sub cob
            for (i in bucketedData.size - 4 downTo 0) {
                data.signals.emitProgress(CalculationWorkflow.ProgressData.IOB_COB_OREF, 100 - (100.0 * i / bucketedData.size).toInt())
                if (isStopped) {
                    aapsLogger.debug(LTag.AUTOSENS) { "Aborting calculation thread (trigger): ${data.reason}" }
                    return
                }
                // check if data already exists
                var bgTime = bucketedData[i].timestamp
                bgTime = ads.roundUpTime(bgTime)
                if (bgTime > ads.roundUpTime(dateUtil.now())) continue
                var existing: AutosensData?
                if (autosensDataTable[bgTime].also { existing = it } != null) {
                    previous = existing
                    continue
                }
                val profile = profileFunction.getProfile(bgTime)
                if (profile == null) {
                    aapsLogger.debug(LTag.AUTOSENS) { "Aborting calculation thread (no profile): ${data.reason}" }
                    continue
                }
                aapsLogger.debug(LTag.AUTOSENS) { "Processing calculation thread: ${data.reason} ($i/${bucketedData.size})" }
                val autosensData = autosensDataProvider.get()
                autosensData.time = bgTime
                if (previous != null) autosensData.activeCarbsList = previous.cloneCarbsList() else autosensData.activeCarbsList = ArrayList()

                var avgDelta: Double
                var delta: Double
                val bg: Double = bucketedData[i].recalculated
                if (bg < 39 || bucketedData[i + 3].recalculated < 39) {
                    aapsLogger.error("! value < 39")
                    continue
                }
                autosensData.bg = bg
                delta = bg - bucketedData[i + 1].recalculated
                avgDelta = (bg - bucketedData[i + 3].recalculated) / 3
                val sens = profile.getIsfMgdlForCarbs(bgTime, "IobCobOrefWorker", config, processedDeviceStatusData)
                val iob = data.iobCobCalculator.calculateFromTreatmentsAndTemps(bgTime, profile)
                val bgi = -iob.activity * sens * 5
                val deviation = delta - bgi
                val avgDeviation = ((avgDelta - bgi) * 1000).roundToLong() / 1000.0
                var slopeFromMaxDeviation = 0.0
                var slopeFromMinDeviation = 999.0

                // https://github.com/openaps/oref0/blob/master/lib/determine-basal/cob-autosens.js#L169
                if (i < bucketedData.size - 16) {
                    var maxDeviation = 0.0
                    var minDeviation = 999.0
                    val hourAgo = bgTime + 10 * 1000 - 60 * 60 * 1000L
                    val hourAgoData = ads.getAutosensDataAtTime(hourAgo)
                    if (hourAgoData != null) {
                        val initialIndex = autosensDataTable.indexOfKey(hourAgoData.time)
                        aapsLogger.debug(LTag.AUTOSENS) { ">>>>> bucketed_data.size()=" + bucketedData.size + " i=" + i + " hourAgoData=" + hourAgoData.toString() }
                        var past = 1
                        while (past < 12) {
                            val ad = autosensDataTable.valueAt(initialIndex + past)
                            aapsLogger.debug(LTag.AUTOSENS) { ">>>>> past=$past ad=$ad" }
                            val deviationSlope = (ad.avgDeviation - avgDeviation) / (ad.time - bgTime) * 1000 * 60 * 5
                            if (ad.avgDeviation > maxDeviation) {
                                slopeFromMaxDeviation = min(0.0, deviationSlope)
                                maxDeviation = ad.avgDeviation
                            }
                            if (ad.avgDeviation < minDeviation) {
                                slopeFromMinDeviation = max(0.0, deviationSlope)
                                minDeviation = ad.avgDeviation
                            }
                            past++
                        }
                    } else {
                        aapsLogger.debug(LTag.AUTOSENS) { ">>>>> bucketed_data.size()=${bucketedData.size} i=$i hourAgoData=null" }
                    }
                }
                // Use exclusive start (+1ms) to avoid double-counting carbs at window boundaries (issue #4596)
                val recentCarbTreatments = persistenceLayer.getCarbsFromTimeToTimeExpanded(bgTime - T.mins(5).msecs() + 1, bgTime, true)
                for (recentCarbTreatment in recentCarbTreatments) {
                    autosensData.carbsFromBolus += recentCarbTreatment.amount
                    val isAAPSOrWeighted = activePlugin.activeSensitivity.isMinCarbsAbsorptionDynamic
                    if (recentCarbTreatment.amount > 0) {
                        val sensForCarbs = profile.getIsfMgdlForCarbs(recentCarbTreatment.timestamp, "fromCarbs", config, processedDeviceStatusData)
                        val ic = profile.getIc(recentCarbTreatment.timestamp)
                        autosensData.activeCarbsList.add(fromCarbs(recentCarbTreatment, isOref1 = false, isAAPSOrWeighted, sensForCarbs, ic, aapsLogger, dateUtil, preferences))
                    }
                    autosensData.pastSensitivity += "[" + decimalFormatter.to0Decimal(recentCarbTreatment.amount) + "g]"
                }

                if (previous != null && previous.cob > 0) {
                    var totalMinCarbsImpact = 0.0
                    if (activePlugin.activeSensitivity.isMinCarbsAbsorptionDynamic) {
                        for (ii in autosensData.activeCarbsList.indices) {
                            val c = autosensData.activeCarbsList[ii]
                            totalMinCarbsImpact += c.min5minCarbImpact
                        }
                    } else {
                        totalMinCarbsImpact = preferences.get(DoubleKey.ApsAmaMin5MinCarbsImpact)
                    }
                    val ci = max(deviation, totalMinCarbsImpact)
                    if (ci != deviation) autosensData.failOverToMinAbsorptionRate = true
                    autosensData.this5MinAbsorption = ci * profile.getIc(bgTime) / sens
                    autosensData.cob = max(previous.cob - autosensData.this5MinAbsorption, 0.0)
                    autosensData.deductAbsorbedCarbs()
                    autosensData.usedMinCarbsImpact = totalMinCarbsImpact
                }
                val isAAPSOrWeighted = activePlugin.activeSensitivity.isMinCarbsAbsorptionDynamic
                autosensData.removeOldCarbs(bgTime, isAAPSOrWeighted)
                autosensData.cob = max(autosensData.cob + autosensData.carbsFromBolus, 0.0)
                autosensData.deviation = deviation
                autosensData.bgi = bgi
                autosensData.sens = sens
                autosensData.delta = delta
                autosensData.avgDelta = avgDelta
                autosensData.avgDeviation = avgDeviation
                autosensData.slopeFromMaxDeviation = slopeFromMaxDeviation
                autosensData.slopeFromMinDeviation = slopeFromMinDeviation

                // calculate autosens only without COB
                if (autosensData.cob <= 0) {
                    when {
                        abs(deviation) < Constants.DEVIATION_TO_BE_EQUAL -> {
                            autosensData.pastSensitivity += "="
                            autosensData.validDeviation = true
                        }

                        deviation > 0                                    -> {
                            autosensData.pastSensitivity += "+"
                            autosensData.validDeviation = true
                        }

                        else                                             -> {
                            autosensData.pastSensitivity += "-"
                            autosensData.validDeviation = true
                        }
                    }
                } else {
                    autosensData.pastSensitivity += "C"
                }
                previous = autosensData
                if (bgTime < dateUtil.now()) autosensDataTable.put(bgTime, autosensData)
                aapsLogger.debug(LTag.AUTOSENS) {
                    "Running detectSensitivity from: ${dateUtil.dateAndTimeString(oldestTimeWithData)} to: ${dateUtil.dateAndTimeString(bgTime)} lastDataTime:${ads.lastDataTime(dateUtil)}"
                }
                val sensitivity = activePlugin.activeSensitivity.detectSensitivity(ads, oldestTimeWithData, bgTime)
                aapsLogger.debug(LTag.AUTOSENS) { "Sensitivity result: $sensitivity" }
                autosensData.autosensResult = sensitivity
                aapsLogger.debug(LTag.AUTOSENS, autosensData.toString())
            }
            data.iobCobCalculator.ads = ads
            Thread {
                SystemClock.sleep(1000)
                rxBus.send(EventAutosensCalculationFinished(data.triggeredByNewBG))
            }.start()
        } finally {
            data.signals.emitProgress(CalculationWorkflow.ProgressData.IOB_COB_OREF, 100)
            aapsLogger.debug(LTag.AUTOSENS) { "AUTOSENSDATA thread ended: ${data.reason}" }
            profiler.log(LTag.AUTOSENS, "IobCobThread", start)
        }
    }

    // ---------- Phase 5: IOB/autosens graph data (was PrepareIobAutosensGraphDataWorker) ----------

    private suspend fun prepareIobAutosensGraphData(data: PrepareGraphData) {
        val cacheTimeRange = data.cache.timeRangeFlow.value
        val fromTime = cacheTimeRange?.fromTime ?: data.overviewData.fromTime
        val endTime = cacheTimeRange?.endTime ?: data.overviewData.endTime

        data.signals.emitProgress(CalculationWorkflow.ProgressData.PREPARE_IOB_AUTOSENS_DATA, 0)

        val now = dateUtil.now().toDouble()
        var time = fromTime
        var maxActivity = 0.0

        val iobListCompose: MutableList<GraphDataPoint> = ArrayList()
        val absIobListCompose: MutableList<GraphDataPoint> = ArrayList()
        val cobListCompose: MutableList<GraphDataPoint> = ArrayList()
        val cobFailOverListCompose: MutableList<CobFailOverPoint> = ArrayList()
        val activityListCompose: MutableList<GraphDataPoint> = ArrayList()
        val activityPredictionListCompose: MutableList<GraphDataPoint> = ArrayList()
        val bgiListCompose: MutableList<GraphDataPoint> = ArrayList()
        val bgiPredictionListCompose: MutableList<GraphDataPoint> = ArrayList()
        val deviationsListCompose: MutableList<DeviationDataPoint> = ArrayList()
        val ratioListCompose: MutableList<GraphDataPoint> = ArrayList()
        val dsMaxListCompose: MutableList<GraphDataPoint> = ArrayList()
        val dsMinListCompose: MutableList<GraphDataPoint> = ArrayList()

        val adsData = data.iobCobCalculator.ads.clone()

        while (time <= endTime) {
            if (isStopped) return
            val progress = (time - fromTime).toDouble() / (endTime - fromTime) * 100.0
            data.signals.emitProgress(CalculationWorkflow.ProgressData.PREPARE_IOB_AUTOSENS_DATA, progress.toInt())
            val profile = profileFunction.getProfile(time)
            if (profile == null) {
                time += 5 * 60 * 1000L
                continue
            }
            val iob = data.iobCobCalculator.calculateFromTreatmentsAndTemps(time, profile)
            val baseBasalIob = data.iobCobCalculator.calculateAbsoluteIobFromBaseBasals(time)
            val absIob = IobTotal.combine(iob, baseBasalIob)
            val autosensData = adsData.getAutosensDataAtTime(time)

            iobListCompose.add(GraphDataPoint(time, iob.iob))
            absIobListCompose.add(GraphDataPoint(time, absIob.iob))

            if (autosensData != null) {
                cobListCompose.add(GraphDataPoint(time, autosensData.cob))
                if (autosensData.failOverToMinAbsorptionRate) {
                    cobFailOverListCompose.add(CobFailOverPoint(time, autosensData.cob))
                }

                val bgiCompose: Double = iob.activity * autosensData.sens * 5.0
                if (time <= now) bgiListCompose.add(GraphDataPoint(time, bgiCompose))
                else bgiPredictionListCompose.add(GraphDataPoint(time, bgiCompose))

                val deviationType = when {
                    autosensData.type == "uam"          -> DeviationType.UAM
                    autosensData.type == "csf"          -> DeviationType.CSF
                    autosensData.pastSensitivity == "C" -> DeviationType.CSF
                    autosensData.pastSensitivity == "+" -> DeviationType.POSITIVE
                    autosensData.pastSensitivity == "-" -> DeviationType.NEGATIVE
                    else                                -> DeviationType.EQUAL
                }
                deviationsListCompose.add(DeviationDataPoint(time, autosensData.deviation, deviationType))

                ratioListCompose.add(GraphDataPoint(time, 100.0 * (autosensData.autosensResult.ratio - 1)))

                dsMaxListCompose.add(GraphDataPoint(time, autosensData.slopeFromMaxDeviation))
                dsMinListCompose.add(GraphDataPoint(time, autosensData.slopeFromMinDeviation))
            }

            if (time <= now) activityListCompose.add(GraphDataPoint(time, iob.activity))
            else activityPredictionListCompose.add(GraphDataPoint(time, iob.activity))
            if (iob.activity > maxActivity) maxActivity = iob.activity
            else if (-iob.activity > maxActivity) maxActivity = -iob.activity

            time += 5 * 60 * 1000L
        }

        val iobPredictionsListCompose: MutableList<GraphDataPoint> = ArrayList()
        val lastAutosensData = adsData.getLastAutosensData("GraphData", aapsLogger, dateUtil)
        val lastAutosensResult = lastAutosensData?.autosensResult ?: AutosensResult()
        val isTempTarget = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now()) != null
        val iobPredictionArray = data.iobCobCalculator.calculateIobArrayForSMB(lastAutosensResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, isTempTarget)
        for (i in iobPredictionArray) {
            iobPredictionsListCompose.add(GraphDataPoint(i.time, i.iob))
        }
        aapsLogger.debug(LTag.AUTOSENS, "IOB prediction for AS=" + decimalFormatter.to2Decimal(lastAutosensResult.ratio) + ": " + data.iobCobCalculator.iobArrayToString(iobPredictionArray))

        val varSensListCompose: MutableList<GraphDataPoint> = ArrayList()
        val apsResults = persistenceLayer.getApsResults(fromTime, endTime)
        apsResults.forEach {
            it.variableSens?.let { variableSens ->
                val varSens = profileUtil.fromMgdlToUnits(variableSens)
                varSensListCompose.add(GraphDataPoint(it.date, varSens))
            }
        }

        data.cache.updateIobGraph(IobGraphData(iob = iobListCompose, predictions = iobPredictionsListCompose))
        data.cache.updateAbsIobGraph(AbsIobGraphData(absIob = absIobListCompose))
        data.cache.updateCobGraph(CobGraphData(cob = cobListCompose, failOverPoints = cobFailOverListCompose))
        data.cache.updateActivityGraph(
            ActivityGraphData(
                activity = activityListCompose,
                activityPrediction = activityPredictionListCompose,
                maxActivity = maxActivity
            )
        )
        data.cache.updateBgiGraph(BgiGraphData(bgi = bgiListCompose, bgiPrediction = bgiPredictionListCompose))
        data.cache.updateDeviationsGraph(DeviationsGraphData(deviations = deviationsListCompose))
        data.cache.updateRatioGraph(RatioGraphData(ratio = ratioListCompose))
        data.cache.updateDevSlopeGraph(DevSlopeGraphData(dsMax = dsMaxListCompose, dsMin = dsMinListCompose))
        data.cache.updateVarSensGraph(VarSensGraphData(varSens = varSensListCompose))

        data.signals.emitProgress(CalculationWorkflow.ProgressData.PREPARE_IOB_AUTOSENS_DATA, 100)
    }
}
