package info.nightscout.androidaps.plugins.iob.iobCobCalculator

import android.os.SystemClock
import androidx.collection.LongSparseArray
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.data.MealData
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.entities.ExtendedBolus
import info.nightscout.androidaps.database.entities.TemporaryBasal
import info.nightscout.androidaps.database.interfaces.end
import info.nightscout.androidaps.events.*
import info.nightscout.androidaps.extensions.convertedToAbsolute
import info.nightscout.androidaps.extensions.iobCalc
import info.nightscout.androidaps.extensions.toTemporaryBasal
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.data.AutosensData
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventNewHistoryData
import info.nightscout.androidaps.plugins.sensitivity.SensitivityAAPSPlugin
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin
import info.nightscout.androidaps.plugins.sensitivity.SensitivityWeightedAveragePlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

@Singleton
open class IobCobCalculatorPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBusWrapper,
    private val sp: SP,
    resourceHelper: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
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
), IobCobCalculator {

    private val disposable = CompositeDisposable()

    private var iobTable = LongSparseArray<IobTotal>() // oldest at index 0
    private var basalDataTable = LongSparseArray<BasalData>() // oldest at index 0

    override var ads: AutosensDataStore = AutosensDataStore()

    private val dataLock = Any()
    var stopCalculationTrigger = false
    private var thread: Thread? = null

    override fun onStart() {
        super.onStart()
        // EventConfigBuilderChange
        disposable += rxBus
            .toObservable(EventConfigBuilderChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event -> resetDataAndRunCalculation("onEventConfigBuilderChange", event) }, fabricPrivacy::logException)
        // EventNewBasalProfile
        disposable += rxBus
            .toObservable(EventNewBasalProfile::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event -> resetDataAndRunCalculation("onNewProfile", event) }, fabricPrivacy::logException)
        // EventPreferenceChange
        disposable += rxBus
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
                    resetDataAndRunCalculation("onEventPreferenceChange", event)
                }
            }, fabricPrivacy::logException)
        // EventAppInitialized
        disposable += rxBus
            .toObservable(EventAppInitialized::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event -> runCalculation("onEventAppInitialized", System.currentTimeMillis(), bgDataReload = true, limitDataToOldestAvailable = true, cause = event) }, fabricPrivacy::logException)
        // EventNewHistoryData
        disposable += rxBus
            .toObservable(EventNewHistoryData::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event -> newHistoryData(event.oldDataTimestamp, event.reloadBgData, if (event.newestGlucoseValue != null) EventNewBG(event.newestGlucoseValue) else event) }, fabricPrivacy::logException)
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    private fun resetDataAndRunCalculation(reason: String, event: Event?) {
        stopCalculation(reason)
        clearCache()
        ads.reset()
        runCalculation(reason, System.currentTimeMillis(), bgDataReload = false, limitDataToOldestAvailable = true, cause = event)
    }

    override fun clearCache() {
        synchronized(dataLock) {
            aapsLogger.debug(LTag.AUTOSENS, "Clearing cached data.")
            iobTable = LongSparseArray()
            basalDataTable = LongSparseArray()
        }
    }

    private fun oldestDataAvailable(): Long {
        var oldestTime = System.currentTimeMillis()
        val oldestTempBasal = repository.getOldestTemporaryBasalRecord()
        if (oldestTempBasal != null) oldestTime = min(oldestTime, oldestTempBasal.timestamp)
        val oldestExtendedBolus = repository.getOldestExtendedBolusRecord()
        if (oldestExtendedBolus != null) oldestTime = min(oldestTime, oldestExtendedBolus.timestamp)
        val oldestBolus = repository.getOldestBolusRecord()
        if (oldestBolus != null) oldestTime = min(oldestTime, oldestBolus.timestamp)
        val oldestCarbs = repository.getOldestCarbsRecord()
        if (oldestCarbs != null) oldestTime = min(oldestTime, oldestCarbs.timestamp)
        val oldestPs = repository.getOldestEffectiveProfileSwitchRecord()
        if (oldestPs != null) oldestTime = min(oldestTime, oldestPs.timestamp)
        oldestTime -= 15 * 60 * 1000L // allow 15 min before
        return oldestTime
    }

    fun calculateDetectionStart(from: Long, limitDataToOldestAvailable: Boolean): Long {
        val profile = profileFunction.getProfile(from)
        var dia = Constants.defaultDIA
        if (profile != null) dia = profile.dia
        val oldestDataAvailable = oldestDataAvailable()
        val getBGDataFrom: Long
        if (limitDataToOldestAvailable) {
            getBGDataFrom = max(oldestDataAvailable, (from - T.hours(1).msecs() * (24 + dia)).toLong())
            if (getBGDataFrom == oldestDataAvailable) aapsLogger.debug(LTag.AUTOSENS, "Limiting data to oldest available temps: " + dateUtil.dateAndTimeAndSecondsString(oldestDataAvailable))
        } else getBGDataFrom = (from - T.hours(1).msecs() * (24 + dia)).toLong()
        return getBGDataFrom
    }

    override fun calculateFromTreatmentsAndTemps(toTime: Long, profile: Profile): IobTotal {
        val now = System.currentTimeMillis()
        val time = ads.roundUpTime(toTime)
        val cacheHit = iobTable[time]
        if (time < now && cacheHit != null) {
            //og.debug(">>> calculateFromTreatmentsAndTemps Cache hit " + new Date(time).toLocaleString());
            return cacheHit
        } // else log.debug(">>> calculateFromTreatmentsAndTemps Cache miss " + new Date(time).toLocaleString());
        val bolusIob = calculateIobFromBolusToTime(time).round()
        val basalIob = calculateIobToTimeFromTempBasalsIncludingConvertedExtended(time).round()
        // OpenAPSSMB only
        // Add expected zero temp basal for next 240 minutes
        val basalIobWithZeroTemp = basalIob.copy()
        val t = TemporaryBasal(
            timestamp = now + 60 * 1000L,
            duration = 240,
            rate = 0.0,
            isAbsolute = true,
            type = TemporaryBasal.Type.NORMAL)
        if (t.timestamp < time) {
            val calc = t.iobCalc(time, profile, activePlugin.activeInsulin)
            basalIobWithZeroTemp.plus(calc)
        }
        basalIob.iobWithZeroTemp = IobTotal.combine(bolusIob, basalIobWithZeroTemp).round()
        val iobTotal = IobTotal.combine(bolusIob, basalIob).round()
        if (time < System.currentTimeMillis()) {
            synchronized(dataLock) {
                iobTable.put(time, iobTotal)
            }
        }
        return iobTotal
    }

    private fun calculateFromTreatmentsAndTemps(time: Long, lastAutosensResult: AutosensResult, exercise_mode: Boolean, half_basal_exercise_target: Int, isTempTarget: Boolean): IobTotal {
        val now = dateUtil.now()
        val bolusIob = calculateIobFromBolusToTime(time).round()
        val basalIob = getCalculationToTimeTempBasals(time, lastAutosensResult, exercise_mode, half_basal_exercise_target, isTempTarget).round()
        // OpenAPSSMB only
        // Add expected zero temp basal for next 240 minutes
        val basalIobWithZeroTemp = basalIob.copy()
        val t = TemporaryBasal(
            timestamp = now + 60 * 1000L,
            duration = 240,
            rate = 0.0,
            isAbsolute = true,
            type = TemporaryBasal.Type.NORMAL)
        if (t.timestamp < time) {
            val profile = profileFunction.getProfile(t.timestamp)
            if (profile != null) {
                val calc = t.iobCalc(time, profile, lastAutosensResult, exercise_mode, half_basal_exercise_target, isTempTarget, activePlugin.activeInsulin)
                basalIobWithZeroTemp.plus(calc)
            }
        }
        basalIob.iobWithZeroTemp = IobTotal.combine(bolusIob, basalIobWithZeroTemp).round()
        return IobTotal.combine(bolusIob, basalIob).round()
    }

    override fun getBasalData(profile: Profile, fromTime: Long): BasalData {
        val now = System.currentTimeMillis()
        val time = ads.roundUpTime(fromTime)
        var retVal = basalDataTable[time]
        if (retVal == null) {
            //log.debug(">>> getBasalData Cache miss " + new Date(time).toLocaleString());
            retVal = BasalData()
            val tb = getTempBasalIncludingConvertedExtended(time)
            retVal.basal = profile.getBasal(time)
            if (tb != null) {
                retVal.isTempBasalRunning = true
                retVal.tempBasalAbsolute = tb.convertedToAbsolute(time, profile)
            } else {
                retVal.isTempBasalRunning = false
                retVal.tempBasalAbsolute = retVal.basal
            }
            if (time < now) {
                synchronized(dataLock) {
                    basalDataTable.append(time, retVal)
                }
            }
        } //else log.debug(">>> getBasalData Cache hit " +  new Date(time).toLocaleString());
        return retVal
    }

    override fun getLastAutosensDataWithWaitForCalculationFinish(reason: String): AutosensData? {
        if (thread?.isAlive == true) {
            aapsLogger.debug(LTag.AUTOSENS, "AUTOSENSDATA is waiting for calculation thread: $reason")
            try {
                thread?.join(5000)
            } catch (ignored: InterruptedException) {
            }
            aapsLogger.debug(LTag.AUTOSENS, "AUTOSENSDATA finished waiting for calculation thread: $reason")
        }
        return ads.getLastAutosensData(reason, aapsLogger, dateUtil)
    }

    override fun getCobInfo(waitForCalculationFinish: Boolean, reason: String): CobInfo {
        val autosensData =
            if (waitForCalculationFinish) getLastAutosensDataWithWaitForCalculationFinish(reason)
            else ads.getLastAutosensData(reason, aapsLogger, dateUtil)
        var displayCob: Double? = null
        var futureCarbs = 0.0
        val now = dateUtil.now()
        val carbs = repository.getCarbsDataFromTimeExpanded(now, true).blockingGet()
        if (autosensData != null) {
            displayCob = autosensData.cob
            carbs.forEach { carb ->
                if (ads.roundUpTime(carb.timestamp) > ads.roundUpTime(autosensData.time) && carb.timestamp <= now) {
                    displayCob += carb.amount
                }
            }
        }
        // Future carbs
        carbs.forEach { carb -> if (carb.timestamp > now) futureCarbs += carb.amount }
        return CobInfo(displayCob, futureCarbs)
    }

    override fun getMealDataWithWaitingForCalculationFinish(): MealData {
        val result = MealData()
        val now = System.currentTimeMillis()
        val maxAbsorptionHours: Double = if (sensitivityAAPSPlugin.isEnabled() || sensitivityWeightedAveragePlugin.isEnabled()) {
            sp.getDouble(R.string.key_absorption_maxtime, Constants.DEFAULT_MAX_ABSORPTION_TIME)
        } else {
            sp.getDouble(R.string.key_absorption_cutoff, Constants.DEFAULT_MAX_ABSORPTION_TIME)
        }
        val absorptionTimeAgo = now - (maxAbsorptionHours * T.hours(1).msecs()).toLong()
        repository.getCarbsDataFromTimeToTimeExpanded(absorptionTimeAgo + 1, now, true)
            .blockingGet()
            .forEach {
                if (it.amount > 0) {
                    result.carbs += it.amount
                    if (it.timestamp > result.lastCarbTime) result.lastCarbTime = it.timestamp
                }
            }
        val autosensData = getLastAutosensDataWithWaitForCalculationFinish("getMealData()")
        if (autosensData != null) {
            result.mealCOB = autosensData.cob
            result.slopeFromMinDeviation = autosensData.slopeFromMinDeviation
            result.slopeFromMaxDeviation = autosensData.slopeFromMaxDeviation
            result.usedMinCarbsImpact = autosensData.usedMinCarbsImpact
        }
        val lastBolus = repository.getLastBolusRecordWrapped().blockingGet()
        result.lastBolusTime = if (lastBolus is ValueWrapper.Existing) lastBolus.value.timestamp else 0L
        return result
    }

    override fun calculateIobArrayInDia(profile: Profile): Array<IobTotal> {
        // predict IOB out to DIA plus 30m
        var time = System.currentTimeMillis()
        time = ads.roundUpTime(time)
        val len = ((profile.dia * 60 + 30) / 5).toInt()
        val array = Array(len) { IobTotal(0) }
        for ((pos, i) in (0 until len).withIndex()) {
            val t = time + i * 5 * 60000
            val iob = calculateFromTreatmentsAndTemps(t, profile)
            array[pos] = iob
        }
        return array
    }

    override fun calculateIobArrayForSMB(lastAutosensResult: AutosensResult, exercise_mode: Boolean, half_basal_exercise_target: Int, isTempTarget: Boolean): Array<IobTotal> {
        // predict IOB out to DIA plus 30m
        val now = dateUtil.now()
        val len = 4 * 60 / 5
        val array = Array(len) { IobTotal(0) }
        for ((pos, i) in (0 until len).withIndex()) {
            val t = now + i * 5 * 60000
            val iob = calculateFromTreatmentsAndTemps(t, lastAutosensResult, exercise_mode, half_basal_exercise_target, isTempTarget)
            array[pos] = iob
        }
        return array
    }

    override fun iobArrayToString(array: Array<IobTotal>): String {
        val sb = StringBuilder()
        sb.append("[")
        for (i in array) {
            sb.append(DecimalFormatter.to2Decimal(i.iob))
            sb.append(", ")
        }
        sb.append("]")
        return sb.toString()
    }

    fun stopCalculation(from: String) {
        if (thread?.state != Thread.State.TERMINATED) {
            stopCalculationTrigger = true
            aapsLogger.debug(LTag.AUTOSENS, "Stopping calculation thread: $from")
            while (thread != null && thread?.state != Thread.State.TERMINATED) {
                SystemClock.sleep(100)
            }
            aapsLogger.debug(LTag.AUTOSENS, "Calculation thread stopped: $from")
        }
    }

    fun runCalculation(from: String, end: Long, bgDataReload: Boolean, limitDataToOldestAvailable: Boolean, cause: Event?) {
        aapsLogger.debug(LTag.AUTOSENS, "Starting calculation thread: " + from + " to " + dateUtil.dateAndTimeAndSecondsString(end))
        if (thread == null || thread?.state == Thread.State.TERMINATED) {
            thread =
                if (sensitivityOref1Plugin.isEnabled()) IobCobOref1Thread(injector, this, from, end, bgDataReload, limitDataToOldestAvailable, cause)
                else IobCobThread(injector, this, from, end, bgDataReload, limitDataToOldestAvailable, cause)
            thread?.start()
        }
    }

    // When historical data is changed (coming from NS etc) finished calculations after this date must be invalidated
    private fun newHistoryData(oldDataTimestamp: Long, bgDataReload: Boolean, event: Event) {
        //log.debug("Locking onNewHistoryData");
        stopCalculation("onEventNewHistoryData")
        synchronized(dataLock) {

            // clear up 5 min back for proper COB calculation
            val time = oldDataTimestamp - 5 * 60 * 1000L
            aapsLogger.debug(LTag.AUTOSENS, "Invalidating cached data to: " + dateUtil.dateAndTimeAndSecondsString(time))
            for (index in iobTable.size() - 1 downTo 0) {
                if (iobTable.keyAt(index) > time) {
                    aapsLogger.debug(LTag.AUTOSENS, "Removing from iobTable: " + dateUtil.dateAndTimeAndSecondsString(iobTable.keyAt(index)))
                    iobTable.removeAt(index)
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
            ads.newHistoryData(time, aapsLogger, dateUtil)
        }
        runCalculation("onEventNewHistoryData", System.currentTimeMillis(), bgDataReload, true, event)
        //log.debug("Releasing onNewHistoryData");
    }

    override fun convertToJSONArray(iobArray: Array<IobTotal>): JSONArray {
        val array = JSONArray()
        for (i in iobArray.indices) {
            array.put(iobArray[i].determineBasalJson(dateUtil))
        }
        return array
    }

    companion object {

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

    /**
     *  Time range to the past for IOB calculation
     *  @return milliseconds
     */
    fun range(): Long = ((/*overviewData.rangeToDisplay + */(profileFunction.getProfile()?.dia
        ?: Constants.defaultDIA)) * 60 * 60 * 1000).toLong()

    override fun calculateIobFromBolus(): IobTotal = calculateIobFromBolusToTime(dateUtil.now())

    /**
     * Calculate IobTotal from boluses and extended to provided timestamp.
     * NOTE: Only isValid == true boluses are included
     * NOTE: if faking by TBR by extended boluses is enabled, extended boluses are not included
     *  and are calculated towards temporary basals
     *
     * @param toTime timestamp in milliseconds
     * @return calculated iob
     */
    private fun calculateIobFromBolusToTime(toTime: Long): IobTotal {
        val total = IobTotal(toTime)
        val profile = profileFunction.getProfile() ?: return total
        val dia = profile.dia
        val divisor = sp.getDouble(R.string.key_openapsama_bolussnooze_dia_divisor, 2.0)

        val boluses = repository.getBolusesDataFromTime(toTime - range(), true).blockingGet()

        boluses.forEach { t ->
            if (t.isValid && t.timestamp < toTime) {
                val tIOB = t.iobCalc(activePlugin, toTime, dia)
                total.iob += tIOB.iobContrib
                total.activity += tIOB.activityContrib
                if (t.amount > 0 && t.timestamp > total.lastBolusTime) total.lastBolusTime = t.timestamp
                if (t.type != Bolus.Type.SMB) {
                    // instead of dividing the DIA that only worked on the bilinear curves,
                    // multiply the time the treatment is seen active.
                    val timeSinceTreatment = toTime - t.timestamp
                    val snoozeTime = t.timestamp + (timeSinceTreatment * divisor).toLong()
                    val bIOB = t.iobCalc(activePlugin, snoozeTime, dia)
                    total.bolussnooze += bIOB.iobContrib
                }
            }
        }

        total.plus(calculateIobToTimeFromExtendedBoluses(toTime))
        return total
    }

    private fun calculateIobToTimeFromExtendedBoluses(toTime: Long): IobTotal {
        val total = IobTotal(toTime)
        val now = dateUtil.now()
        val pumpInterface = activePlugin.activePump
        if (!pumpInterface.isFakingTempsByExtendedBoluses) {
            val extendedBoluses = repository.getExtendedBolusDataFromTimeToTime(toTime - range(), toTime, true).blockingGet()
            for (pos in extendedBoluses.indices) {
                val e = extendedBoluses[pos]
                if (e.timestamp > toTime) continue
                if (e.end > now) e.duration = now - e.timestamp
                val profile = profileFunction.getProfile(e.timestamp) ?: return total
                val calc = e.iobCalc(toTime, profile, activePlugin.activeInsulin)
                total.plus(calc)
            }
        }
        return total
    }

    override fun getTempBasal(timestamp: Long): TemporaryBasal? {
        val tb = repository.getTemporaryBasalActiveAt(timestamp).blockingGet()
        if (tb is ValueWrapper.Existing) return tb.value
        return null
    }

    override fun getExtendedBolus(timestamp: Long): ExtendedBolus? {
        val tb = repository.getExtendedBolusActiveAt(timestamp).blockingGet()
        if (tb is ValueWrapper.Existing) return tb.value
        return null
    }

    override fun getTempBasalIncludingConvertedExtended(timestamp: Long): TemporaryBasal? {

        val tb = repository.getTemporaryBasalActiveAt(timestamp).blockingGet()
        if (tb is ValueWrapper.Existing) return tb.value
        val eb = repository.getExtendedBolusActiveAt(timestamp).blockingGet()
        val profile = profileFunction.getProfile(timestamp) ?: return null
        if (eb is ValueWrapper.Existing && activePlugin.activePump.isFakingTempsByExtendedBoluses)
            return eb.value.toTemporaryBasal(profile)
        return null
    }

    override fun calculateAbsoluteIobFromBaseBasals(toTime: Long): IobTotal {
        val total = IobTotal(toTime)
        var i = toTime - range()
        while (i < toTime) {
            val profile = profileFunction.getProfile(i)
            if (profile == null) {
                i += T.mins(5).msecs()
                continue
            }
            val running = profile.getBasal(i)
            val bolus = Bolus(
                timestamp = i,
                amount = running * 5.0 / 60.0,
                type = Bolus.Type.NORMAL,
                isBasalInsulin = true
            )
            val iob = bolus.iobCalc(activePlugin, toTime, profile.dia)
            total.basaliob += iob.iobContrib
            total.activity += iob.activityContrib
            i += T.mins(5).msecs()
        }
        return total
    }

    override fun calculateIobFromTempBasalsIncludingConvertedExtended(): IobTotal =
        calculateIobToTimeFromTempBasalsIncludingConvertedExtended(dateUtil.now())

    override fun calculateIobToTimeFromTempBasalsIncludingConvertedExtended(toTime: Long): IobTotal {
        val total = IobTotal(toTime)
        val now = dateUtil.now()
        val pumpInterface = activePlugin.activePump

        val temporaryBasals = repository.getTemporaryBasalsDataFromTimeToTime(toTime - range(), toTime, true).blockingGet()
        for (pos in temporaryBasals.indices) {
            val t = temporaryBasals[pos]
            if (t.timestamp > toTime) continue
            val profile = profileFunction.getProfile(t.timestamp) ?: continue
            if (t.end > now) t.duration = now - t.timestamp
            val calc = t.iobCalc(toTime, profile, activePlugin.activeInsulin)
            //log.debug("BasalIOB " + new Date(time) + " >>> " + calc.basalIob);
            total.plus(calc)
        }
        if (pumpInterface.isFakingTempsByExtendedBoluses) {
            val totalExt = IobTotal(toTime)
            val extendedBoluses = repository.getExtendedBolusDataFromTimeToTime(toTime - range(), toTime, true).blockingGet()
            for (pos in extendedBoluses.indices) {
                val e = extendedBoluses[pos]
                if (e.timestamp > toTime) continue
                val profile = profileFunction.getProfile(e.timestamp) ?: continue
                if (e.end > now) e.duration = now - e.timestamp
                val calc = e.iobCalc(toTime, profile, activePlugin.activeInsulin)
                totalExt.plus(calc)
            }
            // Convert to basal iob
            totalExt.basaliob = totalExt.iob
            totalExt.iob = 0.0
            totalExt.netbasalinsulin = totalExt.extendedBolusInsulin
            totalExt.hightempinsulin = totalExt.extendedBolusInsulin
            total.plus(totalExt)
        }
        return total
    }

    open fun getCalculationToTimeTempBasals(toTime: Long, lastAutosensResult: AutosensResult, exercise_mode: Boolean, half_basal_exercise_target: Int, isTempTarget: Boolean): IobTotal {
        val total = IobTotal(toTime)
        val pumpInterface = activePlugin.activePump
        val now = dateUtil.now()
        val temporaryBasals = repository.getTemporaryBasalsDataFromTimeToTime(toTime - range(), toTime, true).blockingGet()
        for (pos in temporaryBasals.indices) {
            val t = temporaryBasals[pos]
            if (t.timestamp > toTime) continue
            val profile = profileFunction.getProfile(t.timestamp) ?: continue
            if (t.end > now) t.duration = now - t.timestamp
            val calc = t.iobCalc(toTime, profile, lastAutosensResult, exercise_mode, half_basal_exercise_target, isTempTarget, activePlugin.activeInsulin)
            //log.debug("BasalIOB " + new Date(time) + " >>> " + calc.basalIob);
            total.plus(calc)
        }
        if (pumpInterface.isFakingTempsByExtendedBoluses) {
            val totalExt = IobTotal(toTime)
            val extendedBoluses = repository.getExtendedBolusDataFromTimeToTime(toTime - range(), toTime, true).blockingGet()
            for (pos in extendedBoluses.indices) {
                val e = extendedBoluses[pos]
                if (e.timestamp > toTime) continue
                val profile = profileFunction.getProfile(e.timestamp) ?: continue
                if (e.end > now) e.duration = now - e.timestamp
                val calc = e.iobCalc(toTime, profile, lastAutosensResult, exercise_mode, half_basal_exercise_target, isTempTarget, activePlugin.activeInsulin)
                totalExt.plus(calc)
            }
            // Convert to basal iob
            totalExt.basaliob = totalExt.iob
            totalExt.iob = 0.0
            totalExt.netbasalinsulin = totalExt.extendedBolusInsulin
            totalExt.hightempinsulin = totalExt.extendedBolusInsulin
            total.plus(totalExt)
        }
        return total
    }
}