package app.aaps.plugins.main.iob.iobCobCalculator

import androidx.collection.LongSparseArray
import app.aaps.core.data.aps.BasalData
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.iob.CobInfo
import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.devAssert
import app.aaps.core.data.model.iobCalc
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.EnforcedState
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import app.aaps.core.interfaces.rx.events.EventCalibrationChanged
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.rx.events.EventTimeZoneChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.MidnightTime
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.combine
import app.aaps.core.objects.extensions.convertedToAbsolute
import app.aaps.core.objects.extensions.iobCalc
import app.aaps.core.objects.extensions.plus
import app.aaps.core.objects.extensions.round
import app.aaps.plugins.main.MainStrings
import app.aaps.plugins.main.iob.iobCobCalculator.data.AutosensDataStoreObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class IobCobCalculatorPlugin(
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val preferences: Preferences,
    rh: TextResolver,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val dateUtil: DateUtil,
    private val persistenceLayer: PersistenceLayer,
    private val overviewData: OverviewData,
    private val calculationWorkflow: CalculationWorkflow,
    private val decimalFormatter: DecimalFormatter,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val signals: CalculationSignalsEmitter,
    notificationManager: NotificationManager,
    // Last on purpose: the call site in HistoryWindowGraph passes it as a trailing lambda.
    private val cache: () -> OverviewDataCache
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .pluginName(MainStrings.iob_cob_calculator)
        .showInList { false }
        .enforce(EnforcedState.Enabled),
    aapsLogger, rh, notificationManager
), IobCobCalculator {

    private var scope: CoroutineScope? = null

    /** How long a burst of time zone changes is collected before one full reset. A var only for tests. */
    internal var timeZoneResetDebounce: Duration = 10.seconds

    private var iobTable = LongSparseArray<IobTotal>() // oldest at index 0

    // Written by the calculation when it publishes its result (PrepareGraphDataRunner.publishAds) and
    // read by the UI, the loop and the watch on other threads. Every store guards its own state, so a
    // stale read cannot corrupt anything - but without @Volatile a reader can go on using the previous
    // store after a newer one has been published, so a fresh result becomes visible later than it
    // should. kotlin.concurrent.Volatile, which works in common code.
    @Volatile override var ads: AutosensDataStore = AutosensDataStoreObject()

    // Guards iobTable, every read and every write. Internal only so a test can hold it.
    internal val dataLock = AapsLock()

    @OptIn(FlowPreview::class)
    override suspend fun onStart() {
        super.onStart()
        val newScope = CoroutineScope(aapsIoDispatcher + SupervisorJob())
        scope = newScope
        // EventConfigBuilderChange
        rxBus.toFlow(EventConfigBuilderChange::class)
            .collectResilient(newScope, aapsLogger, LTag.AUTOSENS, start = CoroutineStart.UNDISPATCHED) { resetDataAndRunCalculation("onEventConfigBuilderChange") }
        // EventTimeZoneChanged: the basal profile is read in the current time zone, so every cached IOB,
        // basal and autosens value of the past would now be calculated differently. Until the past is
        // calculated with the offset of its own time (_docs/IOB_TIME_ZONE.md), start again from scratch.
        // Debounced: automatic zone detection near a border can send several changes in a row, and each
        // full recalculation stopped by the next one would be thrown away.
        rxBus.toFlow(EventTimeZoneChanged::class)
            .debounce(timeZoneResetDebounce)
            .collectResilient(newScope, aapsLogger, LTag.AUTOSENS, start = CoroutineStart.UNDISPATCHED) { resetDataAndRunCalculation("onEventTimeZoneChanged") }
        // EventCalibrationChanged → the fit changed, so bucketed data needs to be re-smoothed
        // with the new calibration applied. scheduleHistoryDataChange has its own 5s debounce
        // so bursts (delete-many, bulk-add) collapse into one workflow run.
        rxBus.toFlow(EventCalibrationChanged::class)
            .collectResilient(newScope, aapsLogger, LTag.AUTOSENS, start = CoroutineStart.UNDISPATCHED) {
                val invalidateFrom = dateUtil.now() - T.hours(24).msecs()
                scheduleHistoryDataChange(invalidateFrom, reloadBgData = true, triggeredByNewBG = false)
            }
        // The collectors below all use collectResilient. A plain onEach cancels its collection for good
        // when the body throws, and with no CoroutineExceptionHandler in place the same throw also takes
        // the process down. Either way the plugin stops reacting to database changes, and for the
        // GlucoseValue collector that means no more BG triggered loop runs (issue #5066).
        // EffectiveProfileSwitch changes
        persistenceLayer.observeChanges(EPS::class)
            .collectResilient(newScope, aapsLogger, LTag.AUTOSENS) { epsList ->
                epsList.minOfOrNull { it.timestamp }?.let { timestamp ->
                    newHistoryData(timestamp, bgDataReload = false, triggeredByNewBG = false)
                }
            }
        // Preference changes
        merge(
            preferences.observe(IntKey.AutosensPeriod).drop(1).map {},
            preferences.observe(StringKey.SafetyAge).drop(1).map {},
            preferences.observe(DoubleKey.AbsorptionMaxTime).drop(1).map {},
            preferences.observe(DoubleKey.ApsAmaMin5MinCarbsImpact).drop(1).map {},
            preferences.observe(DoubleKey.ApsSmbMin5MinCarbsImpact).drop(1).map {},
            preferences.observe(DoubleKey.AbsorptionCutOff).drop(1).map {},
            preferences.observe(DoubleKey.AutosensMax).drop(1).map {},
            preferences.observe(DoubleKey.AutosensMin).drop(1).map {},
        ).collectResilient(newScope, aapsLogger, LTag.AUTOSENS) { resetDataAndRunCalculation("onPreferenceChange") }
        // GlucoseValue changes → reload BG data + trigger loop
        persistenceLayer.observeChanges(GV::class)
            .collectResilient(newScope, aapsLogger, LTag.AUTOSENS) { gvList ->
                // Filter row by row, and before the reduction below. One change list can carry a new
                // reading together with an id only update of an older row (CgmSourceTransaction does
                // exactly that), and reducing first would hide the new reading behind the unchanged one.
                val changed = gvList.filterNot { ads.holdsSameData(it) }
                if (changed.size < gvList.size)
                    aapsLogger.debug(LTag.AUTOSENS) { "Ignoring ${gvList.size - changed.size} of ${gvList.size} glucose changes, nothing the calculation reads is different" }
                changed.minOfOrNull { it.timestamp }?.let { timestamp ->
                    scheduleHistoryDataChange(timestamp, reloadBgData = true, triggeredByNewBG = true)
                }
            }
        // Treatment changes → invalidate caches
        persistenceLayer.observeChanges(CA::class)
            .collectResilient(newScope, aapsLogger, LTag.AUTOSENS) { list ->
                list.minOfOrNull { it.timestamp }?.let { scheduleHistoryDataChange(it, reloadBgData = false) }
            }
        persistenceLayer.observeChanges(BS::class)
            .collectResilient(newScope, aapsLogger, LTag.AUTOSENS) { list ->
                list.minOfOrNull { it.timestamp }?.let { scheduleHistoryDataChange(it, reloadBgData = false) }
            }
        persistenceLayer.observeChanges(BCR::class)
            .collectResilient(newScope, aapsLogger, LTag.AUTOSENS) { list ->
                list.minOfOrNull { it.timestamp }?.let { scheduleHistoryDataChange(it, reloadBgData = false) }
            }
        persistenceLayer.observeChanges(TB::class)
            .collectResilient(newScope, aapsLogger, LTag.AUTOSENS) { list ->
                list.minOfOrNull { it.timestamp }?.let { scheduleHistoryDataChange(it, reloadBgData = false) }
            }
        persistenceLayer.observeChanges(EB::class)
            .collectResilient(newScope, aapsLogger, LTag.AUTOSENS) { list ->
                list.minOfOrNull { it.timestamp }?.let { scheduleHistoryDataChange(it, reloadBgData = false) }
            }
        // Units change
        preferences.observe(StringKey.GeneralUnits).drop(1)
            .collectResilient(newScope, aapsLogger, LTag.AUTOSENS) {
                scheduleHistoryDataChange(0, reloadBgData = true)
            }
        // EventAppInitialized fires once, early. UNDISPATCHED matters most here of the three: a
        // scheduled collector could miss it outright and the main calculation would never be kicked off.
        rxBus.toFlow(EventAppInitialized::class)
            .collectResilient(newScope, aapsLogger, LTag.AUTOSENS, start = CoroutineStart.UNDISPATCHED) {
                calculationWorkflow.runCalculation(
                    CalculationWorkflow.MAIN_CALCULATION,
                    this,
                    overviewData,
                    cache(),
                    signals,
                    "onEventAppInitialized",
                    dateUtil.now(),
                    bgDataReload = true,
                    triggeredByNewBG = false
                )
            }
    }

    override suspend fun onStop() {
        // Cancels the pending debounce too - it is launched in this scope, which is why the old
        // single thread executor and its shutdown are gone.
        scope?.cancel()
        scope = null
        // The waiting change has to go too. A cancel during the debounce leaves it set, and the scheduler
        // would then merge every later change into it and never schedule anything again.
        historyScheduler.drop()
        super.onStop()
    }

    private suspend fun resetDataAndRunCalculation(reason: String) {
        // The full recalculation below covers every change that is still waiting, so it is dropped.
        // Otherwise it would wait for this run and then recalculate part of it again.
        historyScheduler.drop()
        calculationWorkflow.stopCalculation(CalculationWorkflow.MAIN_CALCULATION, reason)
        clearCache()
        ads.reset()
        calculationWorkflow.runCalculation(
            job = CalculationWorkflow.MAIN_CALCULATION,
            iobCobCalculator = this,
            overviewData = overviewData,
            cache = cache(),
            signals = signals,
            reason = reason,
            end = dateUtil.now(),
            bgDataReload = true,
            triggeredByNewBG = false
        )
    }

    override fun clearCache() {
        dataLock.withLock {
            aapsLogger.debug(LTag.AUTOSENS, "Clearing cached data.")
            iobTable = LongSparseArray()
        }
    }

    override fun bgDataReloaded() {
        // The calculation reaches 24 h + DIA back (calculateDetectionStart); older entries are never asked for
        val oldest = dateUtil.now() - T.hours(CACHED_IOB_HOURS).msecs()
        dataLock.withLock {
            // Oldest at index 0. A few per BG: one value every 5 minutes moves past the limit.
            var count = 0
            while (iobTable.size() > 0 && iobTable.keyAt(0) < oldest) {
                iobTable.removeAt(0)
                count++
            }
            aapsLogger.debug(LTag.AUTOSENS, "BG data reloaded: kept ${iobTable.size()} cached IOB values, dropped $count older than $CACHED_IOB_HOURS h")
        }
    }

    private suspend fun oldestDataAvailable(): Long {
        var oldestTime = dateUtil.now()
        val oldestTempBasal = persistenceLayer.getOldestTemporaryBasalRecord()
        if (oldestTempBasal != null) oldestTime = min(oldestTime, oldestTempBasal.timestamp)
        val oldestExtendedBolus = persistenceLayer.getOldestExtendedBolusRecord()
        if (oldestExtendedBolus != null) oldestTime = min(oldestTime, oldestExtendedBolus.timestamp)
        val oldestBolus = persistenceLayer.getOldestBolus()
        if (oldestBolus != null) oldestTime = min(oldestTime, oldestBolus.timestamp)
        val oldestCarbs = persistenceLayer.getOldestCarbs()
        if (oldestCarbs != null) oldestTime = min(oldestTime, oldestCarbs.timestamp)
        val oldestPs = persistenceLayer.getOldestEffectiveProfileSwitch()
        if (oldestPs != null) oldestTime = min(oldestTime, oldestPs.timestamp)
        oldestTime -= 15 * 60 * 1000L // allow 15 min before
        return oldestTime
    }

    override suspend fun calculateDetectionStart(from: Long, limitDataToOldestAvailable: Boolean): Long {
        val profile = profileFunction.getProfile(from)
        val dia = profile?.iCfg?.dia ?: Constants.DEFAULT_DIA
        val oldestDataAvailable = oldestDataAvailable()
        val getBGDataFrom: Long
        if (limitDataToOldestAvailable) {
            getBGDataFrom = max(oldestDataAvailable, (from - T.hours(1).msecs() * (24 + dia)).toLong())
            if (getBGDataFrom == oldestDataAvailable) aapsLogger.debug(LTag.AUTOSENS, "Limiting data to oldest available temps: " + dateUtil.dateAndTimeAndSecondsString(oldestDataAvailable))
        } else getBGDataFrom = (from - T.hours(1).msecs() * (24 + dia)).toLong()
        return getBGDataFrom
    }

    override suspend fun calculateFromTreatmentsAndTemps(toTime: Long, profile: EffectiveProfile): IobTotal {
        val now = dateUtil.now()
        val time = ads.roundUpTime(toTime)
        // Under the same lock as every write. iobTable is a plain LongSparseArray, and the writers
        // (new BG, history change, clearCache) run on other threads: a read without the lock could
        // see the arrays half compacted and return the IOB of another time, or throw (#5211).
        val cacheHit = dataLock.withLock { iobTable[time] }
        if (time < now && cacheHit != null) {
            //og.debug(">>> calculateFromTreatmentsAndTemps Cache hit " + new Date(time).toLocaleString());
            return cacheHit
        } // else log.debug(">>> calculateFromTreatmentsAndTemps Cache miss " + new Date(time).toLocaleString());
        val bolusIob = calculateIobFromBolusToTime(time).round()
        val basalIob = calculateIobToTimeFromTempBasalsIncludingConvertedExtended(time).round()
        // OpenAPSSMB only
        // Add expected zero temp basal for next 240 minutes
        val basalIobWithZeroTemp = basalIob.copy()
        val t = TB(
            timestamp = now + 60 * 1000L,
            duration = 240 * 60 * 1000L,
            rate = 0.0,
            isAbsolute = true,
            type = TB.Type.NORMAL,
        )
        if (t.timestamp < time) {
            val calc = t.iobCalc(time, profile)
            basalIobWithZeroTemp.plus(calc)
        }
        basalIob.iobWithZeroTemp = IobTotal.combine(bolusIob, basalIobWithZeroTemp).round()
        val iobTotal = IobTotal.combine(bolusIob, basalIob).round()
        if (time < dateUtil.now()) {
            dataLock.withLock {
                iobTable.put(time, iobTotal)
            }
        }
        return iobTotal
    }

    private suspend fun calculateFromTreatmentsAndTemps(time: Long, lastAutosensResult: AutosensResult, exerciseMode: Boolean, halfBasalExerciseTarget: Int, isTempTarget: Boolean): IobTotal {
        val now = dateUtil.now()
        val bolusIob = calculateIobFromBolusToTime(time).round()
        val basalIob = getCalculationToTimeTempBasals(time, lastAutosensResult, exerciseMode, halfBasalExerciseTarget, isTempTarget).round()
        // OpenAPSSMB only
        // Add expected zero temp basal for next 240 minutes
        val basalIobWithZeroTemp = basalIob.copy()
        val t = TB(
            timestamp = now + 60 * 1000L,
            duration = 240 * 60 * 1000L,
            rate = 0.0,
            isAbsolute = true,
            type = TB.Type.NORMAL
        )
        if (t.timestamp < time) {
            val profile = profileFunction.getProfile(t.timestamp)
            if (profile != null) {
                val calc = t.iobCalc(time, profile, lastAutosensResult, exerciseMode, halfBasalExerciseTarget, isTempTarget)
                basalIobWithZeroTemp.plus(calc)
            }
        }
        basalIob.iobWithZeroTemp = IobTotal.combine(bolusIob, basalIobWithZeroTemp).round()
        return IobTotal.combine(bolusIob, basalIob).round()
    }

    // Not cached. A cache keyed by time held a value made with the profile of whichever caller came
    // first, and the callers that asked for the past (overview basal graph, TDD) read their range at
    // once now. The callers left ask for now, which was never stored anyway.
    override suspend fun getBasalData(profile: Profile, fromTime: Long): BasalData {
        val time = ads.roundUpTime(fromTime)
        val retVal = BasalData()
        val tb = processedTbrEbData.getTempBasalIncludingConvertedExtended(time)
        retVal.basal = profile.getBasal(time)
        if (tb != null) {
            retVal.isTempBasalRunning = true
            retVal.tempBasalAbsolute = tb.convertedToAbsolute(time, profile)
        } else {
            retVal.isTempBasalRunning = false
            retVal.tempBasalAbsolute = retVal.basal
        }
        return retVal
    }

    override suspend fun getLastAutosensDataWithWaitForCalculationFinish(reason: String): AutosensData? {
        calculationWorkflow.waitForCalculationFinish(CalculationWorkflow.MAIN_CALCULATION, reason)
        return ads.getLastAutosensData(reason, aapsLogger, dateUtil)
    }

    override suspend fun getCobInfo(reason: String): CobInfo {
        val autosensData = ads.getLastAutosensData(reason, aapsLogger, dateUtil)
        var displayCob: Double? = null
        var futureCarbs = 0.0
        val now = dateUtil.now()
        var timestamp = now
        val carbs = persistenceLayer.getCarbsFromTimeExpanded(autosensData?.time ?: now, true)
        if (autosensData != null) {
            displayCob = autosensData.cob
            carbs.forEach { carb ->
                if (carb.timestamp > autosensData.time && carb.timestamp <= now) {
                    displayCob = displayCob!! + carb.amount
                    displayCob = max(displayCob, 0.0)
                }
            }
            timestamp = autosensData.time
        }
        // Future carbs
        carbs.forEach { carb -> if (carb.timestamp > now) futureCarbs += carb.amount }
        return CobInfo(timestamp, displayCob, futureCarbs)
    }

    override suspend fun getMealDataWithWaitingForCalculationFinish(): MealData {
        val result = MealData()
        val now = dateUtil.now()
        val maxAbsorptionHours: Double = activePlugin.activeSensitivity.maxAbsorptionHours()
        val absorptionTimeAgo = now - (maxAbsorptionHours * T.hours(1).msecs()).toLong()
        persistenceLayer.getCarbsFromTimeToTimeExpanded(absorptionTimeAgo + 1, now, true)
            .forEach {
                if (it.amount != 0.0) {
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
        val lastBolus = persistenceLayer.getNewestBolus()
        result.lastBolusTime = lastBolus?.timestamp ?: 0L
        return result
    }

    override suspend fun calculateIobArrayInDia(profile: EffectiveProfile): Array<IobTotal> {
        // predict IOB out to DIA plus 30m
        var time = dateUtil.now()
        time = ads.roundUpTime(time)
        val len = ((profile.iCfg.dia * 60 + 30) / 5).toInt()
        val array = Array(len) { IobTotal(0) }
        for ((pos, i) in (0 until len).withIndex()) {
            val t = time + i * 5 * 60000
            val iob = calculateFromTreatmentsAndTemps(t, profile)
            array[pos] = iob
        }
        return array
    }

    override suspend fun calculateIobArrayForSMB(lastAutosensResult: AutosensResult, exerciseMode: Boolean, halfBasalExerciseTarget: Int, isTempTarget: Boolean): Array<IobTotal> {
        // predict IOB out to DIA plus 30m
        val now = dateUtil.now()
        val len = 4 * 60 / 5
        val array = Array(len) { IobTotal(0) }
        for ((pos, i) in (0 until len).withIndex()) {
            val t = now + i * 5 * 60000
            val iob = calculateFromTreatmentsAndTemps(t, lastAutosensResult, exerciseMode, halfBasalExerciseTarget, isTempTarget)
            array[pos] = iob
        }
        return array
    }

    override fun iobArrayToString(array: Array<IobTotal>): String {
        val sb = StringBuilder()
        sb.append("[")
        for (i in array) {
            sb.append(decimalFormatter.to2Decimal(i.iob))
            sb.append(", ")
        }
        sb.append("]")
        return sb.toString()
    }

    /**
     * Debounces history changes and lets a running calculation finish before the next one starts.
     * See [HistoryChangeScheduler] for why.
     */
    private val historyScheduler = HistoryChangeScheduler(
        aapsLogger = aapsLogger,
        scope = { scope },
        awaitCalculationIdle = { timeout -> calculationWorkflow.awaitCalculationIdle(CalculationWorkflow.MAIN_CALCULATION, timeout) },
        onRunFailed = {
            clearCache()
            ads.reset()
        }
    ) { oldDataTimestamp, reloadBgData, triggeredByNewBG ->
        // The TDD cache clear is inside newHistoryData, so it still happens here, before the
        // recalculation and inside the scheduler's lock - and the EffectiveProfileSwitch path, which
        // calls newHistoryData directly, gets it too.
        newHistoryData(oldDataTimestamp, reloadBgData, triggeredByNewBG)
    }

    suspend fun scheduleHistoryDataChange(oldDataTimestamp: Long, reloadBgData: Boolean, triggeredByNewBG: Boolean = false) =
        historyScheduler.schedule(oldDataTimestamp, reloadBgData, triggeredByNewBG)

    // When historical data is changed (coming from NS etc.) finished calculations after this date must be invalidated
    private suspend fun newHistoryData(oldDataTimestamp: Long, bgDataReload: Boolean, triggeredByNewBG: Boolean) {
        calculationWorkflow.stopCalculation(CalculationWorkflow.MAIN_CALCULATION, "onEventNewHistoryData")
        // The persisted TDD cache is invalidated here rather than in the caller, because it is part of
        // the same invalidation as the in-memory tables below and every path that reaches this function
        // has just been told that history changed.
        //
        // It used to sit in `scheduleHistoryDataChange`, which six of the seven database observers go
        // through - but `EffectiveProfileSwitch` calls this function directly and so cleared nothing.
        // A TDD row holds a basal figure computed per five-minute slot from the profile in force, so a
        // profile switch arriving after the row was cached leaves that row wrong with nothing to
        // remove it. A full sync guarantees that order: the load chain runs TREATMENTS before
        // PROFILE_STORE, so the treatments cache the day and the profiles land afterwards. Only
        // "Recalculate" in the statistics screen fixed it, because that wipes the table outright.
        //
        // Midnight, not `oldDataTimestamp`: a TDD row is keyed on the start of its day, so anything
        // later in that day has to take the whole day with it.
        persistenceLayer.clearCachedTddData(MidnightTime.calc(oldDataTimestamp))
        dataLock.withLock {

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
            ads.newHistoryData(time, aapsLogger, dateUtil)
        }
        calculationWorkflow.runCalculation(
            job = CalculationWorkflow.MAIN_CALCULATION,
            iobCobCalculator = this,
            overviewData = overviewData,
            cache = cache(),
            signals = signals,
            reason = if (triggeredByNewBG) "NewBG" else "DBChange",
            end = dateUtil.now(),
            bgDataReload = bgDataReload,
            triggeredByNewBG = triggeredByNewBG
        )
    }

    /**
     *  Time range to the past for IOB calculation
     *  @return milliseconds
     */
    private suspend fun range(): Long = ((profileFunction.getProfile()?.iCfg?.dia ?: Constants.DEFAULT_DIA) * 60 * 60 * 1000).toLong()

    override suspend fun calculateIobFromBolus(): IobTotal = calculateIobFromBolusToTime(dateUtil.now())

    /**
     * Calculate IobTotal from boluses and extended to provided timestamp.
     * NOTE: Only isValid == true boluses are included
     * NOTE: if faking by TBR by extended boluses is enabled, extended boluses are not included
     *  and are calculated towards temporary basals
     *
     * @param toTime timestamp in milliseconds
     * @return calculated iob
     */
    private suspend fun calculateIobFromBolusToTime(toTime: Long): IobTotal {
        val total = IobTotal(toTime)
        val divisor = preferences.get(DoubleKey.ApsAmaBolusSnoozeDivisor)
        devAssert(divisor > 0)

        val boluses = persistenceLayer.getBolusesFromTime(toTime - range(), true)

        boluses.forEach { t ->
            if (t.isValid && t.timestamp < toTime) {
                val tIOB = t.iobCalc(toTime)
                total.iob += tIOB.iobContrib
                total.activity += tIOB.activityContrib
                if (t.amount > 0 && t.timestamp > total.lastBolusTime) total.lastBolusTime = t.timestamp
                if (t.type != BS.Type.SMB) {
                    // instead of dividing the DIA that only worked on the bilinear curves,
                    // multiply the time the treatment is seen active.
                    val timeSinceTreatment = toTime - t.timestamp
                    val snoozeTime = t.timestamp + (timeSinceTreatment * divisor).toLong()
                    val bIOB = t.iobCalc(snoozeTime)
                    total.bolussnooze += bIOB.iobContrib
                }
            }
        }

        total.plus(calculateIobToTimeFromExtendedBoluses(toTime))
        return total
    }

    private suspend fun calculateIobToTimeFromExtendedBoluses(toTime: Long): IobTotal {
        val total = IobTotal(toTime)
        val now = dateUtil.now()
        val pumpInterface = activePlugin.activePump
        if (!pumpInterface.isFakingTempsByExtendedBoluses) {
            val extendedBoluses = persistenceLayer.getExtendedBolusesStartingFromTimeToTime(toTime - range(), toTime, true)
            for (pos in extendedBoluses.indices) {
                val e = extendedBoluses[pos]
                if (e.timestamp > toTime) continue
                if (e.end > now) {
                    val newDuration = now - e.timestamp
                    e.amount *= newDuration.toDouble() / e.duration
                    e.duration = newDuration
                }
                val profile = profileFunction.getProfile(e.timestamp) ?: return total
                val calc = e.iobCalc(toTime, profile)
                total.plus(calc)
            }
        }
        return total
    }

    override suspend fun calculateAbsoluteIobFromBaseBasals(toTime: Long): IobTotal {
        val total = IobTotal(toTime)
        var i = toTime - range()
        while (i < toTime) {
            val profile = profileFunction.getProfile(i)
            if (profile == null) {
                i += T.mins(5).msecs()
                continue
            }
            val running = profile.getBasal(i)
            val bolus = BS(
                timestamp = i,
                amount = running * 5.0 / 60.0,
                type = BS.Type.NORMAL,
                isBasalInsulin = true,
                iCfg = profile.iCfg
            )
            val iob = bolus.iobCalc(toTime)
            total.basaliob += iob.iobContrib
            total.activity += iob.activityContrib
            i += T.mins(5).msecs()
        }
        return total
    }

    override suspend fun calculateIobFromTempBasalsIncludingConvertedExtended(): IobTotal =
        calculateIobToTimeFromTempBasalsIncludingConvertedExtended(dateUtil.now())

    override suspend fun calculateIobToTimeFromTempBasalsIncludingConvertedExtended(toTime: Long): IobTotal {
        val total = IobTotal(toTime)
        val now = dateUtil.now()
        val pumpInterface = activePlugin.activePump

        val temporaryBasals = persistenceLayer.getTemporaryBasalsStartingFromTimeToTime(toTime - range(), toTime, true)
        for (pos in temporaryBasals.indices) {
            val t = temporaryBasals[pos]
            if (t.timestamp > toTime) continue
            val profile = profileFunction.getProfile(t.timestamp) ?: continue
            if (t.end > now) t.duration = now - t.timestamp
            val calc = t.iobCalc(toTime, profile)
            //log.debug("BasalIOB " + new Date(time) + " >>> " + calc.basalIob);
            total.plus(calc)
        }
        if (pumpInterface.isFakingTempsByExtendedBoluses) {
            val totalExt = IobTotal(toTime)
            val extendedBoluses = persistenceLayer.getExtendedBolusesStartingFromTimeToTime(toTime - range(), toTime, true)
            for (pos in extendedBoluses.indices) {
                val e = extendedBoluses[pos]
                if (e.timestamp > toTime) continue
                val profile = profileFunction.getProfile(e.timestamp) ?: continue
                if (e.end > now) {
                    val newDuration = now - e.timestamp
                    e.amount *= newDuration.toDouble() / e.duration
                    e.duration = newDuration
                }
                val calc = e.iobCalc(toTime, profile)
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

    private suspend fun getCalculationToTimeTempBasals(toTime: Long, lastAutosensResult: AutosensResult, exerciseMode: Boolean, halfBasalExerciseTarget: Int, isTempTarget: Boolean): IobTotal {
        val total = IobTotal(toTime)
        val pumpInterface = activePlugin.activePump
        val now = dateUtil.now()
        val temporaryBasals = persistenceLayer.getTemporaryBasalsStartingFromTimeToTime(toTime - range(), toTime, true)
        for (pos in temporaryBasals.indices) {
            val t = temporaryBasals[pos]
            if (t.timestamp > toTime) continue
            val profile = profileFunction.getProfile(t.timestamp) ?: continue
            if (t.end > now) t.duration = now - t.timestamp
            val calc = t.iobCalc(toTime, profile, lastAutosensResult, exerciseMode, halfBasalExerciseTarget, isTempTarget)
            //log.debug("BasalIOB " + new Date(time) + " >>> " + calc.basalIob);
            total.plus(calc)
        }
        if (pumpInterface.isFakingTempsByExtendedBoluses) {
            val totalExt = IobTotal(toTime)
            val extendedBoluses = persistenceLayer.getExtendedBolusesStartingFromTimeToTime(toTime - range(), toTime, true)
            for (pos in extendedBoluses.indices) {
                val e = extendedBoluses[pos]
                if (e.timestamp > toTime) continue
                val profile = profileFunction.getProfile(e.timestamp) ?: continue
                if (e.end > now) {
                    val newDuration = now - e.timestamp
                    e.amount *= newDuration.toDouble() / e.duration
                    e.duration = newDuration
                }
                val calc = e.iobCalc(toTime, profile, lastAutosensResult, exerciseMode, halfBasalExerciseTarget, isTempTarget)
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

    internal companion object {

        /** How long cached IOB values are kept: more than the 24 h + DIA the calculation looks back. */
        const val CACHED_IOB_HOURS = 48L
    }
}
