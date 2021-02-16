package info.nightscout.androidaps.plugins.iob.iobCobCalculator

import android.os.SystemClock
import androidx.collection.LongSparseArray
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.data.MealData
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.db.TemporaryBasal
import info.nightscout.androidaps.events.*
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.data.AutosensData
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventNewHistoryBgData
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventNewHistoryData
import info.nightscout.androidaps.plugins.sensitivity.SensitivityAAPSPlugin
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin
import info.nightscout.androidaps.plugins.sensitivity.SensitivityWeightedAveragePlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToLong

@Singleton
open class IobCobCalculatorPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBusWrapper,
    private val sp: SP,
    resourceHelper: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePluginProvider,
    private val treatmentsPlugin: TreatmentsPlugin,
    private val sensitivityOref1Plugin: SensitivityOref1Plugin,
    private val sensitivityAAPSPlugin: SensitivityAAPSPlugin,
    private val sensitivityWeightedAveragePlugin: SensitivityWeightedAveragePlugin,
    private val fabricPrivacy: FabricPrivacy,
    private val dateUtil: DateUtil,
    private val repository: AppRepository
) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .pluginName(R.string.iobcobcalculator)
    .showInList(false)
    .neverVisible(true)
    .alwaysEnabled(true),
    aapsLogger, resourceHelper, injector
), IobCobCalculatorInterface {

    private val disposable = CompositeDisposable()
    private var iobTable = LongSparseArray<IobTotal?>() // oldest at index 0
    private var absIobTable = LongSparseArray<IobTotal?>() // oldest at index 0, absolute insulin in the body
    private var autosensDataTable = LongSparseArray<AutosensData>() // oldest at index 0
    private var basalDataTable = LongSparseArray<BasalData>() // oldest at index 0
    @Volatile var bgReadings: List<GlucoseValue> = listOf() // newest at index 0
    @Volatile var bucketedData: MutableList<InMemoryGlucoseValue>? = null

    // we need to make sure that bucketed_data will always have the same timestamp for correct use of cached values
    // once referenceTime != null all bucketed data should be (x * 5min) from referenceTime
    var referenceTime: Long = -1
    private var lastUsed5minCalculation: Boolean? = null // true if used 5min bucketed data
    val dataLock = Any()
    var stopCalculationTrigger = false
    private var thread: Thread? = null

    override fun onStart() {
        super.onStart()
        // EventConfigBuilderChange
        disposable.add(rxBus
            .toObservable(EventConfigBuilderChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event ->
                stopCalculation("onEventConfigBuilderChange")
                synchronized(dataLock) {
                    aapsLogger.debug(LTag.AUTOSENS, "Invalidating cached data because of configuration change.")
                    resetData()
                }
                runCalculation("onEventConfigBuilderChange", System.currentTimeMillis(), bgDataReload = false, limitDataToOldestAvailable = true, cause = event)
            }, fabricPrivacy::logException)
        )
        // EventNewBasalProfile
        disposable.add(rxBus
            .toObservable(EventNewBasalProfile::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event ->
                stopCalculation("onNewProfile")
                synchronized(dataLock) {
                    aapsLogger.debug(LTag.AUTOSENS, "Invalidating cached data because of new profile.")
                    resetData()
                }
                runCalculation("onNewProfile", System.currentTimeMillis(), bgDataReload = false, limitDataToOldestAvailable = true, cause = event)
            }, fabricPrivacy::logException)
        )
        // EventNewBG .... cannot be used for invalidating because only event with last BG is fired
        disposable.add(rxBus
            .toObservable(EventNewBG::class.java)
            .observeOn(aapsSchedulers.io)
            .debounce(1L, TimeUnit.SECONDS)
            .subscribe({ event ->
                stopCalculation("onEventNewBG")
                runCalculation("onEventNewBG", System.currentTimeMillis(), bgDataReload = true, limitDataToOldestAvailable = true, cause = event)
            }, fabricPrivacy::logException)
        )
        // EventPreferenceChange
        disposable.add(rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event ->
                if (event.isChanged(resourceHelper, R.string.key_openapsama_autosens_period) ||
                    event.isChanged(resourceHelper, R.string.key_age) ||
                    event.isChanged(resourceHelper, R.string.key_absorption_maxtime) ||
                    event.isChanged(resourceHelper, R.string.key_openapsama_min_5m_carbimpact) ||
                    event.isChanged(resourceHelper, R.string.key_absorption_cutoff) ||
                    event.isChanged(resourceHelper, R.string.key_openapsama_autosens_max) ||
                    event.isChanged(resourceHelper, R.string.key_openapsama_autosens_min) ||
                    event.isChanged(resourceHelper, R.string.key_insulin_oref_peak)) {
                    stopCalculation("onEventPreferenceChange")
                    synchronized(dataLock) {
                        aapsLogger.debug(LTag.AUTOSENS, "Invalidating cached data because of preference change.")
                        resetData()
                    }
                    runCalculation("onEventPreferenceChange", System.currentTimeMillis(), bgDataReload = false, limitDataToOldestAvailable = true, cause = event)
                }
            }, fabricPrivacy::logException)
        )
        // EventAppInitialized
        disposable.add(rxBus
            .toObservable(EventAppInitialized::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event -> runCalculation("onEventAppInitialized", System.currentTimeMillis(), bgDataReload = true, limitDataToOldestAvailable = true, cause = event) }, fabricPrivacy::logException)
        )
        // EventNewHistoryData
        disposable.add(rxBus
            .toObservable(EventNewHistoryData::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event -> newHistoryData(event, false) }, fabricPrivacy::logException)
        )
        // EventNewHistoryBgData
        disposable.add(rxBus
            .toObservable(EventNewHistoryBgData::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event -> newHistoryData(EventNewHistoryData(event.timestamp), true) }, fabricPrivacy::logException)
        )
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    override fun getAutosensDataTable(): LongSparseArray<AutosensData> {
        return autosensDataTable
    }

    fun adjustToReferenceTime(someTime: Long): Long {
        if (referenceTime == -1L) {
            referenceTime = someTime
            return someTime
        }
        var diff = abs(someTime - referenceTime)
        diff %= T.mins(5).msecs()
        if (diff > T.mins(2).plus(T.secs(30)).msecs()) diff -= T.mins(5).msecs()
        return someTime + diff
    }

    fun loadBgData(to: Long) {
        val profile = profileFunction.getProfile(to)
        var dia = Constants.defaultDIA
        if (profile != null) dia = profile.dia
        val start = to - T.hours((24 + dia).toLong()).msecs()
        if (DateUtil.isCloseToNow(to)) {
            // if close to now expect there can be some readings with time in close future (caused by wrong time setting)
            // so read all records
            bgReadings = repository.compatGetBgReadingsDataFromTime(start, false).blockingGet()
            aapsLogger.debug(LTag.AUTOSENS, "BG data loaded. Size: " + bgReadings.size + " Start date: " + dateUtil.dateAndTimeString(start))
        } else {
            bgReadings = repository.compatGetBgReadingsDataFromTime(start, to, false).blockingGet()
            aapsLogger.debug(LTag.AUTOSENS, "BG data loaded. Size: " + bgReadings.size + " Start date: " + dateUtil.dateAndTimeString(start) + " End date: " + dateUtil.dateAndTimeString(to))
        }
    }

    val isAbout5minData: Boolean
        get() {
            synchronized(dataLock) {
                if (bgReadings.size < 3) return true

                var totalDiff: Long = 0
                for (i in 1 until bgReadings.size) {
                    val bgTime = bgReadings[i].timestamp
                    val lastBgTime = bgReadings[i - 1].timestamp
                    var diff = lastBgTime - bgTime
                    diff %= T.mins(5).msecs()
                    if (diff > T.mins(2).plus(T.secs(30)).msecs()) diff -= T.mins(5).msecs()
                    totalDiff += diff
                    diff = abs(diff)
                    if (diff > T.secs(30).msecs()) {
                        aapsLogger.debug(LTag.AUTOSENS, "Interval detection: values: " + bgReadings.size + " diff: " + diff / 1000 + "[s] is5minData: " + false)
                        return false
                    }
                }
                val averageDiff = totalDiff / bgReadings.size / 1000
                val is5minData = averageDiff < 1
                aapsLogger.debug(LTag.AUTOSENS, "Interval detection: values: " + bgReadings.size + " averageDiff: " + averageDiff + "[s] is5minData: " + is5minData)
                return is5minData
            }
        }

    private fun resetData() {
        synchronized(dataLock) {
            iobTable = LongSparseArray()
            autosensDataTable = LongSparseArray()
            basalDataTable = LongSparseArray()
            absIobTable = LongSparseArray()
        }
    }

    fun createBucketedData() {
        val fiveMinData = isAbout5minData
        if (lastUsed5minCalculation != null && lastUsed5minCalculation != fiveMinData) {
            // changing mode => clear cache
            aapsLogger.debug("Invalidating cached data because of changed mode.")
            resetData()
        }
        lastUsed5minCalculation = fiveMinData
        if (isAbout5minData) createBucketedData5min() else createBucketedDataRecalculated()
    }

    fun findNewer(time: Long): GlucoseValue? {
        var lastFound = bgReadings[0]
        if (lastFound.timestamp < time) return null
        for (i in 1 until bgReadings.size) {
            if (bgReadings[i].timestamp == time) return bgReadings[i]
            if (bgReadings[i].timestamp > time) continue
            lastFound = bgReadings[i - 1]
            if (bgReadings[i].timestamp < time) break
        }
        return lastFound
    }

    fun findOlder(time: Long): GlucoseValue? {
        var lastFound = bgReadings[bgReadings.size - 1]
        if (lastFound.timestamp > time) return null
        for (i in bgReadings.size - 2 downTo 0) {
            if (bgReadings[i].timestamp == time) return bgReadings[i]
            if (bgReadings[i].timestamp < time) continue
            lastFound = bgReadings[i + 1]
            if (bgReadings[i].timestamp > time) break
        }
        return lastFound
    }

    private fun createBucketedDataRecalculated() {
        if (bgReadings.size < 3) {
            bucketedData = null
            return
        }
        bucketedData = ArrayList()
        var currentTime = bgReadings[0].timestamp - bgReadings[0].timestamp % T.mins(5).msecs()
        currentTime = adjustToReferenceTime(currentTime)
        aapsLogger.debug("Adjusted time " + dateUtil.dateAndTimeAndSecondsString(currentTime))
        //log.debug("First reading: " + new Date(currentTime).toLocaleString());
        while (true) {
            // test if current value is older than current time
            val newer = findNewer(currentTime)
            val older = findOlder(currentTime)
            if (newer == null || older == null) break
            if (older.timestamp == newer.timestamp) { // direct hit
                bucketedData?.add(InMemoryGlucoseValue(newer))
            } else {
                val bgDelta = newer.value - older.value
                val timeDiffToNew = newer.timestamp - currentTime
                val currentBg = newer.value - timeDiffToNew.toDouble() / (newer.timestamp - older.timestamp) * bgDelta
                val newBgReading = InMemoryGlucoseValue(currentTime, currentBg.roundToLong().toDouble(), true)
                bucketedData?.add(newBgReading)
                //log.debug("BG: " + newBgReading.value + " (" + new Date(newBgReading.date).toLocaleString() + ") Prev: " + older.value + " (" + new Date(older.date).toLocaleString() + ") Newer: " + newer.value + " (" + new Date(newer.date).toLocaleString() + ")");
            }
            currentTime -= T.mins(5).msecs()
        }
    }

    private fun createBucketedData5min() {
        if (bgReadings.size < 3) {
            bucketedData = null
            return
        }
        val bData: MutableList<InMemoryGlucoseValue> = ArrayList()
        bData.add(InMemoryGlucoseValue(bgReadings[0]))
        aapsLogger.debug(LTag.AUTOSENS, "Adding. bgTime: " + DateUtil.toISOString(bgReadings[0].timestamp) + " lastBgTime: " + "none-first-value" + " " + bgReadings[0].toString())
        var j = 0
        for (i in 1 until bgReadings.size) {
            val bgTime = bgReadings[i].timestamp
            var lastBgTime = bgReadings[i - 1].timestamp
            //log.error("Processing " + i + ": " + new Date(bgTime).toString() + " " + bgReadings.get(i).value + "   Previous: " + new Date(lastBgTime).toString() + " " + bgReadings.get(i - 1).value);
            check(!(bgReadings[i].value < 39 || bgReadings[i - 1].value < 39)) { "<39" }
            var elapsedMinutes = (bgTime - lastBgTime) / (60 * 1000)
            when {
                abs(elapsedMinutes) > 8 -> {
                    // interpolate missing data points
                    var lastBg = bgReadings[i - 1].value
                    elapsedMinutes = abs(elapsedMinutes)
                    //console.error(elapsed_minutes);
                    var nextBgTime: Long
                    while (elapsedMinutes > 5) {
                        nextBgTime = lastBgTime - 5 * 60 * 1000
                        j++
                        val gapDelta = bgReadings[i].value - lastBg
                        //console.error(gapDelta, lastBg, elapsed_minutes);
                        val nextBg = lastBg + 5.0 / elapsedMinutes * gapDelta
                        val newBgReading = InMemoryGlucoseValue(nextBgTime, nextBg.roundToLong().toDouble(), true)
                        //console.error("Interpolated", bData[j]);
                        bData.add(newBgReading)
                        aapsLogger.debug(LTag.AUTOSENS, "Adding. bgTime: " + DateUtil.toISOString(bgTime) + " lastBgTime: " + DateUtil.toISOString(lastBgTime) + " " + newBgReading.toString())
                        elapsedMinutes -= 5
                        lastBg = nextBg
                        lastBgTime = nextBgTime
                    }
                    j++
                    val newBgReading = InMemoryGlucoseValue(bgTime, bgReadings[i].value)
                    bData.add(newBgReading)
                    aapsLogger.debug(LTag.AUTOSENS, "Adding. bgTime: " + DateUtil.toISOString(bgTime) + " lastBgTime: " + DateUtil.toISOString(lastBgTime) + " " + newBgReading.toString())
                }

                abs(elapsedMinutes) > 2 -> {
                    j++
                    val newBgReading = InMemoryGlucoseValue(bgTime, bgReadings[i].value)
                    bData.add(newBgReading)
                    aapsLogger.debug(LTag.AUTOSENS, "Adding. bgTime: " + DateUtil.toISOString(bgTime) + " lastBgTime: " + DateUtil.toISOString(lastBgTime) + " " + newBgReading.toString())
                }

                else                    -> {
                    bData[j].value = (bData[j].value + bgReadings[i].value) / 2
                    //log.error("***** Average");
                }
            }
        }

        // Normalize bucketed data
        val oldest = bData[bData.size - 1]
        oldest.timestamp = adjustToReferenceTime(oldest.timestamp)
        aapsLogger.debug("Adjusted time " + dateUtil.dateAndTimeAndSecondsString(oldest.timestamp))
        for (i in bData.size - 2 downTo 0) {
            val current = bData[i]
            val previous = bData[i + 1]
            val mSecDiff = current.timestamp - previous.timestamp
            val adjusted = (mSecDiff - T.mins(5).msecs()) / 1000
            aapsLogger.debug(LTag.AUTOSENS, "Adjusting bucketed data time. Current: " + dateUtil.dateAndTimeAndSecondsString(current.timestamp) + " to: " + dateUtil.dateAndTimeAndSecondsString(previous.timestamp + T.mins(5).msecs()) + " by " + adjusted + " sec")
            if (abs(adjusted) > 90) {
                // too big adjustment, fallback to non 5 min data
                aapsLogger.debug(LTag.AUTOSENS, "Fallback to non 5 min data")
                createBucketedDataRecalculated()
                return
            }
            current.timestamp = previous.timestamp + T.mins(5).msecs()
        }
        aapsLogger.debug(LTag.AUTOSENS, "Bucketed data created. Size: " + bData.size)
        bucketedData = bData
    }

    fun calculateDetectionStart(from: Long, limitDataToOldestAvailable: Boolean): Long {
        val profile = profileFunction.getProfile(from)
        var dia = Constants.defaultDIA
        if (profile != null) dia = profile.dia
        val oldestDataAvailable = treatmentsPlugin.oldestDataAvailable()
        val getBGDataFrom: Long
        if (limitDataToOldestAvailable) {
            getBGDataFrom = max(oldestDataAvailable, (from - T.hours(1).msecs() * (24 + dia)).toLong())
            if (getBGDataFrom == oldestDataAvailable) aapsLogger.debug(LTag.AUTOSENS, "Limiting data to oldest available temps: " + dateUtil.dateAndTimeAndSecondsString(oldestDataAvailable))
        } else getBGDataFrom = (from - T.hours(1).msecs() * (24 + dia)).toLong()
        return getBGDataFrom
    }

    fun calculateFromTreatmentsAndTempsSynchronized(time: Long, profile: Profile?): IobTotal {
        synchronized(dataLock) { return calculateFromTreatmentsAndTemps(time, profile) }
    }

    private fun calculateFromTreatmentsAndTempsSynchronized(time: Long, lastAutosensResult: AutosensResult, exercise_mode: Boolean, half_basal_exercise_target: Int, isTempTarget: Boolean): IobTotal {
        synchronized(dataLock) { return calculateFromTreatmentsAndTemps(time, lastAutosensResult, exercise_mode, half_basal_exercise_target, isTempTarget) }
    }

    fun calculateFromTreatmentsAndTemps(fromTime: Long, profile: Profile?): IobTotal {
        val now = System.currentTimeMillis()
        val time = roundUpTime(fromTime)
        val cacheHit = iobTable[time]
        if (time < now && cacheHit != null) {
            //og.debug(">>> calculateFromTreatmentsAndTemps Cache hit " + new Date(time).toLocaleString());
            return cacheHit
        } // else log.debug(">>> calculateFromTreatmentsAndTemps Cache miss " + new Date(time).toLocaleString());
        val bolusIob = treatmentsPlugin.getCalculationToTimeTreatments(time).round()
        val basalIob = treatmentsPlugin.getCalculationToTimeTempBasals(time, true, now).round()
        // OpenAPSSMB only
        // Add expected zero temp basal for next 240 minutes
        val basalIobWithZeroTemp = basalIob.copy()
        val t = TemporaryBasal(injector)
            .date(now + 60 * 1000L)
            .duration(240)
            .absolute(0.0)
        if (t.date < time) {
            val calc = t.iobCalc(time, profile)
            basalIobWithZeroTemp.plus(calc)
        }
        basalIob.iobWithZeroTemp = IobTotal.combine(bolusIob, basalIobWithZeroTemp).round()
        val iobTotal = IobTotal.combine(bolusIob, basalIob).round()
        if (time < System.currentTimeMillis()) {
            iobTable.put(time, iobTotal)
        }
        return iobTotal
    }

    fun calculateAbsInsulinFromTreatmentsAndTempsSynchronized(fromTime: Long): IobTotal {
        synchronized(dataLock) {
            val now = System.currentTimeMillis()
            val time = roundUpTime(fromTime)
            val cacheHit = absIobTable[time]
            if (time < now && cacheHit != null) {
                //log.debug(">>> calculateFromTreatmentsAndTemps Cache hit " + new Date(time).toLocaleString());
                return cacheHit
            } // else log.debug(">>> calculateFromTreatmentsAndTemps Cache miss " + new Date(time).toLocaleString());
            val bolusIob = treatmentsPlugin.getCalculationToTimeTreatments(time).round()
            val basalIob = treatmentsPlugin.getAbsoluteIOBTempBasals(time).round()
            val iobTotal = IobTotal.combine(bolusIob, basalIob).round()
            if (time < System.currentTimeMillis()) {
                absIobTable.put(time, iobTotal)
            }
            return iobTotal
        }
    }

    private fun calculateFromTreatmentsAndTemps(time: Long, lastAutosensResult: AutosensResult, exercise_mode: Boolean, half_basal_exercise_target: Int, isTempTarget: Boolean): IobTotal {
        val now = DateUtil.now()
        val bolusIob = treatmentsPlugin.getCalculationToTimeTreatments(time).round()
        val basalIob = treatmentsPlugin.getCalculationToTimeTempBasals(time, now, lastAutosensResult, exercise_mode, half_basal_exercise_target, isTempTarget).round()
        // OpenAPSSMB only
        // Add expected zero temp basal for next 240 minutes
        val basalIobWithZeroTemp = basalIob.copy()
        val t = TemporaryBasal(injector)
            .date(now + 60 * 1000L)
            .duration(240)
            .absolute(0.0)
        if (t.date < time) {
            val profile = profileFunction.getProfile(t.date)
            if (profile != null) {
                val calc = t.iobCalc(time, profile, lastAutosensResult, exercise_mode, half_basal_exercise_target, isTempTarget)
                basalIobWithZeroTemp.plus(calc)
            }
        }
        basalIob.iobWithZeroTemp = IobTotal.combine(bolusIob, basalIobWithZeroTemp).round()
        return IobTotal.combine(bolusIob, basalIob).round()
    }

    fun findPreviousTimeFromBucketedData(time: Long): Long? {
        val bData = bucketedData ?: return null
        for (index in bData.indices) {
            if (bData[index].timestamp <= time) return bData[index].timestamp
        }
        return null
    }

    fun getBasalData(profile: Profile, fromTime: Long): BasalData {
        synchronized(dataLock) {
            val now = System.currentTimeMillis()
            val time = roundUpTime(fromTime)
            var retVal = basalDataTable[time]
            if (retVal == null) {
                //log.debug(">>> getBasalData Cache miss " + new Date(time).toLocaleString());
                retVal = BasalData()
                val tb = treatmentsPlugin.getTempBasalFromHistory(time)
                retVal.basal = profile.getBasal(time)
                if (tb != null) {
                    retVal.isTempBasalRunning = true
                    retVal.tempBasalAbsolute = tb.tempBasalConvertedToAbsolute(time, profile)
                } else {
                    retVal.isTempBasalRunning = false
                    retVal.tempBasalAbsolute = retVal.basal
                }
                if (time < now) {
                    basalDataTable.append(time, retVal)
                }
            } //else log.debug(">>> getBasalData Cache hit " +  new Date(time).toLocaleString());
            return retVal
        }
    }

    override fun getAutosensData(fromTime: Long): AutosensData? {
        var time = fromTime
        synchronized(dataLock) {
            val now = System.currentTimeMillis()
            if (time > now) {
                return null
            }
            val previous = findPreviousTimeFromBucketedData(time) ?: return null
            time = roundUpTime(previous)
            return autosensDataTable[time]
        }
    }

    fun getLastAutosensDataSynchronized(reason: String): AutosensData? {
        if (thread?.isAlive == true) {
            aapsLogger.debug(LTag.AUTOSENS, "AUTOSENSDATA is waiting for calculation thread: $reason")
            try {
                thread?.join(5000)
            } catch (ignored: InterruptedException) {
            }
            aapsLogger.debug(LTag.AUTOSENS, "AUTOSENSDATA finished waiting for calculation thread: $reason")
        }
        synchronized(dataLock) { return getLastAutosensData(reason) }
    }

    fun getCobInfo(_synchronized: Boolean, reason: String): CobInfo {
        val autosensData = if (_synchronized) getLastAutosensDataSynchronized(reason) else getLastAutosensData(reason)
        var displayCob: Double? = null
        var futureCarbs = 0.0
        val now = DateUtil.now()
        val treatments = treatmentsPlugin.treatmentsFromHistory
        if (autosensData != null) {
            displayCob = autosensData.cob
            for (treatment in treatments) {
                if (!treatment.isValid) continue
                if (roundUpTime(treatment.date) > roundUpTime(autosensData.time) && treatment.date <= now && treatment.carbs > 0) {
                    displayCob += treatment.carbs
                }
            }
        }
        for (treatment in treatments) {
            if (!treatment.isValid) continue
            if (treatment.date > now && treatment.carbs > 0) {
                futureCarbs += treatment.carbs
            }
        }
        return CobInfo(displayCob, futureCarbs)
    }

    fun slowAbsorptionPercentage(timeInMinutes: Int): Double {
        var sum = 0.0
        var count = 0
        val valuesToProcess = timeInMinutes / 5
        synchronized(dataLock) {
            var i = autosensDataTable.size() - 1
            while (i >= 0 && count < valuesToProcess) {
                if (autosensDataTable.valueAt(i).failoverToMinAbsorbtionRate) sum++
                count++
                i--
            }
        }
        return sum / count
    }

    fun getLastAutosensData(reason: String): AutosensData? {
        if (autosensDataTable.size() < 1) {
            aapsLogger.debug(LTag.AUTOSENS, "AUTOSENSDATA null: autosensDataTable empty ($reason)")
            return null
        }
        val data: AutosensData? = try {
            autosensDataTable.valueAt(autosensDataTable.size() - 1)
        } catch (e: Exception) {
            // data can be processed on the background
            // in this rare case better return null and do not block UI
            // APS plugin should use getLastAutosensDataSynchronized where the blocking is not an issue
            aapsLogger.error("AUTOSENSDATA null: Exception caught ($reason)")
            return null
        }
        if (data == null) {
            aapsLogger.error("AUTOSENSDATA null: data==null")
            return null
        }
        return if (data.time < System.currentTimeMillis() - 11 * 60 * 1000) {
            aapsLogger.debug(LTag.AUTOSENS, "AUTOSENSDATA null: data is old (" + reason + ") size()=" + autosensDataTable.size() + " lastData=" + dateUtil.dateAndTimeAndSecondsString(data.time))
            null
        } else {
            aapsLogger.debug(LTag.AUTOSENS, "AUTOSENSDATA ($reason) $data")
            data
        }
    }

    override fun lastDataTime(): String {
        return if (autosensDataTable.size() > 0) dateUtil.dateAndTimeAndSecondsString(autosensDataTable.valueAt(autosensDataTable.size() - 1).time) else "autosensDataTable empty"
    }

    val mealData: MealData
        get() {
            val result = MealData()
            val profile = profileFunction.getProfile() ?: return result
            val now = System.currentTimeMillis()
            val diaAgo = now - java.lang.Double.valueOf(profile.dia * T.hours(1).msecs()).toLong()
            val maxAbsorptionHours: Double = if (sensitivityAAPSPlugin.isEnabled() || sensitivityWeightedAveragePlugin.isEnabled()) {
                sp.getDouble(R.string.key_absorption_maxtime, Constants.DEFAULT_MAX_ABSORPTION_TIME)
            } else {
                sp.getDouble(R.string.key_absorption_cutoff, Constants.DEFAULT_MAX_ABSORPTION_TIME)
            }
            val absorptionTimeAgo = now - java.lang.Double.valueOf(maxAbsorptionHours * T.hours(1).msecs()).toLong()
            val treatments = treatmentsPlugin.treatmentsFromHistory
            for (treatment in treatments) {
                if (!treatment.isValid) continue
                val t = treatment.date
                if (t in (diaAgo + 1)..now) {
                    if (treatment.insulin > 0 && treatment.mealBolus) {
                        result.boluses += treatment.insulin
                    }
                }
                if (t in (absorptionTimeAgo + 1)..now) {
                    if (treatment.carbs >= 1) {
                        result.carbs += treatment.carbs
                        if (t > result.lastCarbTime) result.lastCarbTime = t
                    }
                }
            }
            val autosensData = getLastAutosensDataSynchronized("getMealData()")
            if (autosensData != null) {
                result.mealCOB = autosensData.cob
                result.slopeFromMinDeviation = autosensData.slopeFromMinDeviation
                result.slopeFromMaxDeviation = autosensData.slopeFromMaxDeviation
                result.usedMinCarbsImpact = autosensData.usedMinCarbsImpact
            }
            result.lastBolusTime = treatmentsPlugin.lastBolusTime
            return result
        }

    override fun calculateIobArrayInDia(profile: Profile): Array<IobTotal> {
        // predict IOB out to DIA plus 30m
        var time = System.currentTimeMillis()
        time = roundUpTime(time)
        val len = ((profile.dia * 60 + 30) / 5).toInt()
        val array = Array(len) { IobTotal(0) }
        for ((pos, i) in (0 until len).withIndex()) {
            val t = time + i * 5 * 60000
            val iob = calculateFromTreatmentsAndTempsSynchronized(t, profile)
            array[pos] = iob
        }
        return array
    }

    fun calculateIobArrayForSMB(lastAutosensResult: AutosensResult, exercise_mode: Boolean, half_basal_exercise_target: Int, isTempTarget: Boolean): Array<IobTotal> {
        // predict IOB out to DIA plus 30m
        val now = DateUtil.now()
        val len = 4 * 60 / 5
        val array = Array(len) { IobTotal(0) }
        for ((pos, i) in (0 until len).withIndex()) {
            val t = now + i * 5 * 60000
            val iob = calculateFromTreatmentsAndTempsSynchronized(t, lastAutosensResult, exercise_mode, half_basal_exercise_target, isTempTarget)
            array[pos] = iob
        }
        return array
    }

    fun iobArrayToString(array: Array<IobTotal>): String {
        val sb = StringBuilder()
        sb.append("[")
        for (i in array) {
            sb.append(DecimalFormatter.to2Decimal(i.iob))
            sb.append(", ")
        }
        sb.append("]")
        return sb.toString()
    }

    fun detectSensitivityWithLock(fromTime: Long, toTime: Long): AutosensResult {
        synchronized(dataLock) { return activePlugin.activeSensitivity.detectSensitivity(this, fromTime, toTime) }
    }

    fun stopCalculation(from: String) {
        if (thread?.state != Thread.State.TERMINATED) {
            stopCalculationTrigger = true
            aapsLogger.debug(LTag.AUTOSENS, "Stopping calculation thread: $from")
            while (thread?.state != Thread.State.TERMINATED) {
                SystemClock.sleep(100)
            }
            aapsLogger.debug(LTag.AUTOSENS, "Calculation thread stopped: $from")
        }
    }

    fun runCalculation(from: String, end: Long, bgDataReload: Boolean, limitDataToOldestAvailable: Boolean, cause: Event) {
        aapsLogger.debug(LTag.AUTOSENS, "Starting calculation thread: " + from + " to " + dateUtil.dateAndTimeAndSecondsString(end))
        if (thread == null || thread?.state == Thread.State.TERMINATED) {
            thread = if (sensitivityOref1Plugin.isEnabled()) IobCobOref1Thread(injector, this, treatmentsPlugin, from, end, bgDataReload, limitDataToOldestAvailable, cause) else IobCobThread(injector, this, treatmentsPlugin, from, end, bgDataReload, limitDataToOldestAvailable, cause)
            thread?.start()
        }
    }

    // When historical data is changed (coming from NS etc) finished calculations after this date must be invalidated
    private fun newHistoryData(ev: EventNewHistoryData, bgDataReload: Boolean) {
        //log.debug("Locking onNewHistoryData");
        stopCalculation("onEventNewHistoryData")
        synchronized(dataLock) {

            // clear up 5 min back for proper COB calculation
            val time = ev.time - 5 * 60 * 1000L
            aapsLogger.debug(LTag.AUTOSENS, "Invalidating cached data to: " + dateUtil.dateAndTimeAndSecondsString(time))
            for (index in iobTable.size() - 1 downTo 0) {
                if (iobTable.keyAt(index) > time) {
                    aapsLogger.debug(LTag.AUTOSENS, "Removing from iobTable: " + dateUtil.dateAndTimeAndSecondsString(iobTable.keyAt(index)))
                    iobTable.removeAt(index)
                } else {
                    break
                }
            }
            for (index in absIobTable.size() - 1 downTo 0) {
                if (absIobTable.keyAt(index) > time) {
                    aapsLogger.debug(LTag.AUTOSENS, "Removing from absIobTable: " + dateUtil.dateAndTimeAndSecondsString(absIobTable.keyAt(index)))
                    absIobTable.removeAt(index)
                } else {
                    break
                }
            }
            for (index in autosensDataTable.size() - 1 downTo 0) {
                if (autosensDataTable.keyAt(index) > time) {
                    aapsLogger.debug(LTag.AUTOSENS, "Removing from autosensDataTable: " + dateUtil.dateAndTimeAndSecondsString(autosensDataTable.keyAt(index)))
                    autosensDataTable.removeAt(index)
                } else {
                    break
                }
            }
            for (index in basalDataTable.size() - 1 downTo 0) {
                if (basalDataTable.keyAt(index) > time) {
                    aapsLogger.debug(LTag.AUTOSENS, "Removing from basalDataTable: " + dateUtil.dateAndTimeAndSecondsString(basalDataTable.keyAt(index)))
                    basalDataTable.removeAt(index)
                } else {
                    break
                }
            }
        }
        runCalculation("onEventNewHistoryData", System.currentTimeMillis(), bgDataReload, true, ev)
        //log.debug("Releasing onNewHistoryData");
    }

    fun clearCache() {
        synchronized(dataLock) {
            aapsLogger.debug(LTag.AUTOSENS, "Clearing cached data.")
            iobTable = LongSparseArray()
            autosensDataTable = LongSparseArray()
            basalDataTable = LongSparseArray()
        }
    }

    /*
     * Return last BgReading from database or null if db is empty
     */
    fun lastBg(): GlucoseValue? {
        val bgList = bgReadings
        for (i in bgList.indices) if (bgList[i].value >= 39) return bgList[i]
        return null
    }

    /*
     * Return bg reading if not old ( <9 min )
     * or null if older
     */
    fun actualBg(): GlucoseValue? {
        val lastBg = lastBg() ?: return null
        return if (lastBg.timestamp > System.currentTimeMillis() - 9 * 60 * 1000) lastBg else null
    }

    companion object {

        // roundup to whole minute
        fun roundUpTime(time: Long): Long {
            return if (time % 60000 == 0L) time else (time / 60000 + 1) * 60000
        }

        fun convertToJSONArray(iobArray: Array<IobTotal>): JSONArray {
            val array = JSONArray()
            for (i in iobArray.indices) {
                array.put(iobArray[i].determineBasalJson())
            }
            return array
        }

        // From https://gist.github.com/IceCreamYou/6ffa1b18c4c8f6aeaad2
        // Returns the value at a given percentile in a sorted numeric array.
        // "Linear interpolation between closest ranks" method
        fun percentile(arr: Array<Double>, p: Double): Double {
            if (arr.isEmpty()) return 0.0
            if (p <= 0) return arr[0]
            if (p >= 1) return arr[arr.size - 1]
            val index = arr.size * p
            val lower = floor(index)
            val upper = lower + 1
            val weight = index % 1
            return if (upper >= arr.size) arr[lower.toInt()] else arr[lower.toInt()] * (1 - weight) + arr[upper.toInt()] * weight
        }
    }
}