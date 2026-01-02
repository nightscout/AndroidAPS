package app.aaps.workflow.iob

import android.content.Context
import android.os.SystemClock
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.rx.events.EventIobCalculationProgress
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.utils.receivers.DataWorkerStorage
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

class IobCobOrefWorker @Inject internal constructor(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var context: Context
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var config: Config
    @Inject lateinit var profiler: Profiler
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var autosensDataProvider: Provider<AutosensData>
    @Inject lateinit var decimalFormatter: DecimalFormatter
    @Inject lateinit var processedDeviceStatusData: ProcessedDeviceStatusData

    class IobCobOrefWorkerData(
        val iobCobCalculator: IobCobCalculator, // cannot be injected : HistoryBrowser uses different instance
        val reason: String,
        val end: Long,
        val limitDataToOldestAvailable: Boolean,
        val cause: Event?
    )

    override suspend fun doWorkAndLog(): Result {
        val data = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as IobCobOrefWorkerData?
            ?: return Result.success(workDataOf("Error" to "missing input data"))

        val start = dateUtil.now()
        try {
            aapsLogger.debug(LTag.AUTOSENS) { "AUTOSENSDATA thread started: ${data.reason}" }
            if (!profileFunction.isProfileValid("IobCobThread")) {
                aapsLogger.debug(LTag.AUTOSENS) { "Aborting calculation thread (No profile): ${data.reason}" }
                return Result.success(workDataOf("Error" to "app still initializing"))
            }
            //log.debug("Locking calculateSensitivityData");
            val oldestTimeWithData = data.iobCobCalculator.calculateDetectionStart(data.end, data.limitDataToOldestAvailable)
            // work on local copy and set back when finished
            val ads = data.iobCobCalculator.ads.clone()
            val bucketedData = ads.bucketedData
            val autosensDataTable = ads.autosensDataTable
            if (bucketedData == null || bucketedData.size < 3) {
                aapsLogger.debug(LTag.AUTOSENS) { "Aborting calculation thread (No bucketed data available): ${data.reason}" }
                return Result.success(workDataOf("Error" to "Aborting calculation thread (No bucketed data available): ${data.reason}"))
            }
            val prevDataTime = ads.roundUpTime(bucketedData[bucketedData.size - 3].timestamp)
            aapsLogger.debug(LTag.AUTOSENS) { "Prev data time: " + dateUtil.dateAndTimeString(prevDataTime) }
            var previous = autosensDataTable[prevDataTime]
            // start from oldest to be able sub cob
            for (i in bucketedData.size - 4 downTo 0) {
                rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.IOB_COB_OREF, 100 - (100.0 * i / bucketedData.size).toInt(), data.cause))
                if (isStopped) {
                    aapsLogger.debug(LTag.AUTOSENS) { "Aborting calculation thread (trigger): ${data.reason}" }
                    return Result.failure(workDataOf("Error" to "Aborting calculation thread (trigger): ${data.reason}"))
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
                    continue  // profile not set yet
                }
                aapsLogger.debug(LTag.AUTOSENS) { "Processing calculation thread: ${data.reason} ($i/${bucketedData.size})" }
                val autosensData = autosensDataProvider.get()
                autosensData.time = bgTime
                if (previous != null) autosensData.activeCarbsList = previous.cloneCarbsList() else autosensData.activeCarbsList = ArrayList()

                //console.error(bgTime , bucketed_data[i].glucose);
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
                if (i < bucketedData.size - 16) { // we need 1h of data to calculate minDeviationSlope
                    var maxDeviation = 0.0
                    var minDeviation = 999.0
                    val hourAgo = bgTime + 10 * 1000 - 60 * 60 * 1000L
                    val hourAgoData = ads.getAutosensDataAtTime(hourAgo)
                    if (hourAgoData != null) {
                        val initialIndex = autosensDataTable.indexOfKey(hourAgoData.time)
                        aapsLogger.debug(LTag.AUTOSENS) { ">>>>> bucketed_data.size()=" + bucketedData.size + " i=" + i + " hourAgoData=" + hourAgoData.toString() }
                        var past = 1
                        // try {
                        while (past < 12) {
                            val ad = autosensDataTable.valueAt(initialIndex + past)
                            aapsLogger.debug(LTag.AUTOSENS) { ">>>>> past=$past ad=$ad" }
                            // if (ad == null) {
                            //     aapsLogger.debug(LTag.AUTOSENS, autosensDataTable.toString())
                            //     aapsLogger.debug(LTag.AUTOSENS, bucketedData.toString())
                            //     //aapsLogger.debug(LTag.AUTOSENS, data.iobCobCalculatorPlugin.getBgReadingsDataTable().toString())
                            //     val notification = Notification(Notification.SEND_LOGFILES, rh.gs(R.string.send_logfiles), Notification.LOW)
                            //     rxBus.send(EventNewNotification(notification))
                            //     sp.putBoolean("log_AUTOSENS", true)
                            //     break
                            // }
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
                        // } catch (e: Exception) {
                        //     aapsLogger.error("Unhandled exception", e)
                        //     fabricPrivacy.logException(e)
                        //     aapsLogger.debug(autosensDataTable.toString())
                        //     aapsLogger.debug(bucketedData.toString())
                        //     //aapsLogger.debug(data.iobCobCalculatorPlugin.getBgReadingsDataTable().toString())
                        //     val notification = Notification(Notification.SEND_LOGFILES, rh.gs(R.string.send_logfiles), Notification.LOW)
                        //     rxBus.send(EventNewNotification(notification))
                        //     sp.putBoolean("log_AUTOSENS", true)
                        //     break
                        // }
                    } else {
                        aapsLogger.debug(LTag.AUTOSENS) { ">>>>> bucketed_data.size()=${bucketedData.size} i=$i hourAgoData=null" }
                    }
                }
                val recentCarbTreatments = persistenceLayer.getCarbsFromTimeToTimeExpanded(bgTime - T.mins(5).msecs(), bgTime, true)
                for (recentCarbTreatment in recentCarbTreatments) {
                    autosensData.carbsFromBolus += recentCarbTreatment.amount
                    val isAAPSOrWeighted = activePlugin.activeSensitivity.isMinCarbsAbsorptionDynamic
                    if (recentCarbTreatment.amount > 0) {
                        val sens = profile.getIsfMgdlForCarbs(recentCarbTreatment.timestamp, "fromCarbs", config, processedDeviceStatusData)
                        val ic = profile.getIc(recentCarbTreatment.timestamp)
                        autosensData.activeCarbsList.add(fromCarbs(recentCarbTreatment, isOref1 = false, isAAPSOrWeighted, sens, ic, aapsLogger, dateUtil, preferences))
                    }
                    autosensData.pastSensitivity += "[" + decimalFormatter.to0Decimal(recentCarbTreatment.amount) + "g]"
                }

                // if we are absorbing carbs
                if (previous != null && previous.cob > 0) {
                    // calculate sum of min carb impact from all active treatments
                    var totalMinCarbsImpact = 0.0
                    if (activePlugin.activeSensitivity.isMinCarbsAbsorptionDynamic) {
                        //when the impact depends on a max time, sum them up as smaller carb sizes make them smaller
                        for (ii in autosensData.activeCarbsList.indices) {
                            val c = autosensData.activeCarbsList[ii]
                            totalMinCarbsImpact += c.min5minCarbImpact
                        }
                    } else {
                        //Oref sensitivity
                        totalMinCarbsImpact = preferences.get(DoubleKey.ApsAmaMin5MinCarbsImpact)
                    }

                    // figure out how many carbs that represents
                    // but always assume at least 3mg/dL/5m (default) absorption per active treatment
                    val ci = max(deviation, totalMinCarbsImpact)
                    if (ci != deviation) autosensData.failOverToMinAbsorptionRate = true
                    autosensData.this5MinAbsorption = ci * profile.getIc(bgTime) / sens
                    // and add that to the running total carbsAbsorbed
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
                rxBus.send(EventAutosensCalculationFinished(data.cause))
            }.start()
        } finally {
            rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.IOB_COB_OREF, 100, data.cause))
            aapsLogger.debug(LTag.AUTOSENS) { "AUTOSENSDATA thread ended: ${data.reason}" }
            profiler.log(LTag.AUTOSENS, "IobCobThread", start)
        }
        return Result.success()
    }
}