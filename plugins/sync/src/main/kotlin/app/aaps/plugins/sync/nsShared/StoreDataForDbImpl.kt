package app.aaps.plugins.sync.nsShared

import androidx.annotation.VisibleForTesting
import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.DS
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoreDataForDbImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val config: Config,
    private val virtualPump: VirtualPump,
    private val nsClientRepository: NSClientRepository,
    @ApplicationScope private val appScope: CoroutineScope
) : StoreDataForDb {

    private val glucoseValues: MutableList<GV> = mutableListOf()
    private val boluses: MutableList<BS> = mutableListOf()
    private val carbs: MutableList<CA> = mutableListOf()
    private val temporaryTargets: MutableList<TT> = mutableListOf()
    private val effectiveProfileSwitches: MutableList<EPS> = mutableListOf()
    private val bolusCalculatorResults: MutableList<BCR> = mutableListOf()
    private val therapyEvents: MutableList<TE> = mutableListOf()
    private val extendedBoluses: MutableList<EB> = mutableListOf()
    private val temporaryBasals: MutableList<TB> = mutableListOf()
    private val profileSwitches: MutableList<PS> = mutableListOf()
    private val runningModes: MutableList<RM> = mutableListOf()
    private val foods: MutableList<FD> = mutableListOf()

    @VisibleForTesting val nsIdGlucoseValues: MutableList<GV> = mutableListOf()
    @VisibleForTesting val nsIdBoluses: MutableList<BS> = mutableListOf()
    @VisibleForTesting val nsIdCarbs: MutableList<CA> = mutableListOf()
    @VisibleForTesting val nsIdTemporaryTargets: MutableList<TT> = mutableListOf()
    @VisibleForTesting val nsIdEffectiveProfileSwitches: MutableList<EPS> = mutableListOf()
    @VisibleForTesting val nsIdBolusCalculatorResults: MutableList<BCR> = mutableListOf()
    @VisibleForTesting val nsIdTherapyEvents: MutableList<TE> = mutableListOf()
    @VisibleForTesting val nsIdExtendedBoluses: MutableList<EB> = mutableListOf()
    @VisibleForTesting val nsIdTemporaryBasals: MutableList<TB> = mutableListOf()
    @VisibleForTesting val nsIdProfileSwitches: MutableList<PS> = mutableListOf()
    @VisibleForTesting val nsIdRunningModes: MutableList<RM> = mutableListOf()
    @VisibleForTesting val nsIdDeviceStatuses: MutableList<DS> = mutableListOf()
    @VisibleForTesting val nsIdFoods: MutableList<FD> = mutableListOf()

    @VisibleForTesting val deleteTreatment: MutableList<String> = mutableListOf()
    private val deleteGlucoseValue: MutableList<String> = mutableListOf()

    private val inserted = HashMap<String, Int>()
    private val updated = HashMap<String, Int>()
    private val invalidated = HashMap<String, Int>()
    private val nsIdUpdated = HashMap<String, Int>()
    private val durationUpdated = HashMap<String, Int>()
    private val ended = HashMap<String, Int>()

    // Throttle between DB chunks; non-blocking suspend so the WS / worker thread is free.
    private val pause = 300L
    private val chunk = 500

    // Per-pipeline mutexes so BG ingest can run while a long treatments sync is in progress.
    private val bgMutex = Mutex()
    private val treatmentsMutex = Mutex()
    private val nsIdMutex = Mutex()

    // Coalescing channels for fire-and-forget request* methods. CONFLATED so a burst
    // of N WS arrivals collapses into 1 (or 2) collector runs instead of queueing N
    // coroutines on the mutex. The buffer is shared, so the single drain catches all.
    private val glucoseRequests = Channel<Unit>(Channel.CONFLATED)
    private val treatmentsRequests = Channel<Boolean>(Channel.CONFLATED)
    private val foodsRequests = Channel<Unit>(Channel.CONFLATED)
    private val deletedTreatmentsRequests = Channel<Unit>(Channel.CONFLATED)
    private val deletedGlucoseRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        appScope.launch { glucoseRequests.consumeEach { storeGlucoseValuesToDb() } }
        appScope.launch { treatmentsRequests.consumeEach { fullSync -> storeTreatmentsToDb(fullSync) } }
        appScope.launch { foodsRequests.consumeEach { storeFoodsToDb() } }
        appScope.launch { deletedTreatmentsRequests.consumeEach { updateDeletedTreatmentsInDb() } }
        appScope.launch { deletedGlucoseRequests.consumeEach { updateDeletedGlucoseValuesInDb() } }
    }

    override fun requestStoreGlucoseValues() { glucoseRequests.trySend(Unit) }
    override fun requestStoreTreatments(fullSync: Boolean) { treatmentsRequests.trySend(fullSync) }
    override fun requestStoreFoods() { foodsRequests.trySend(Unit) }
    override fun requestUpdateDeletedTreatments() { deletedTreatmentsRequests.trySend(Unit) }
    override fun requestUpdateDeletedGlucoseValues() { deletedGlucoseRequests.trySend(Unit) }

    fun <T> HashMap<T, Int>.add(key: T, amount: Int) = synchronized(this) {
        if (containsKey(key)) merge(key, amount, Int::plus)
        else put(key, amount)
    }

    fun <T> HashMap<T, Int>.removeClass(key: T) = synchronized(this) { remove(key) }

    /** Atomically copies and clears the buffer. Returns null if the buffer was empty. */
    private fun <T> snapshotAndClear(list: MutableList<T>): List<T>? = synchronized(list) {
        if (list.isEmpty()) null
        else {
            val copy = list.toList()
            list.clear()
            copy
        }
    }

    override suspend fun storeGlucoseValuesToDb() = bgMutex.withLock {
        snapshotAndClear(glucoseValues)?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.insertCgmSourceData(Sources.NSClient, batch.toMutableList(), emptyList(), null)
            updated.add(GV::class.java.simpleName, result.updated.size)
            inserted.add(GV::class.java.simpleName, result.inserted.size)
            nsIdUpdated.add(GV::class.java.simpleName, result.updatedNsId.size)
            sendLog("GlucoseValue", GV::class.java.simpleName)
            delay(pause)
        }
        nsClientRepository.addLog("● DONE PROCESSING BG", "")
    }

    override suspend fun storeFoodsToDb() = treatmentsMutex.withLock {
        snapshotAndClear(foods)?.let { batch ->
            val result = persistenceLayer.syncNsFood(batch.toMutableList())
            updated.add(FD::class.java.simpleName, result.updated.size)
            inserted.add(FD::class.java.simpleName, result.inserted.size)
            nsIdUpdated.add(FD::class.java.simpleName, result.invalidated.size)
            sendLog("Food", FD::class.java.simpleName)
            delay(pause)
        }
        nsClientRepository.addLog("● DONE PROCESSING FOOD", "")
    }

    override suspend fun storeTreatmentsToDb(fullSync: Boolean) = treatmentsMutex.withLock {
        snapshotAndClear(boluses)?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.syncNsBolus(batch.toMutableList(), doLog = !fullSync)
            inserted.add(BS::class.java.simpleName, result.inserted.size)
            invalidated.add(BS::class.java.simpleName, result.invalidated.size)
            nsIdUpdated.add(BS::class.java.simpleName, result.updatedNsId.size)
            updated.add(BS::class.java.simpleName, result.updated.size)
            sendLog("Bolus", BS::class.java.simpleName)
            delay(pause)
        }

        snapshotAndClear(carbs)?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.syncNsCarbs(batch.toMutableList(), doLog = !fullSync)
            inserted.add(CA::class.java.simpleName, result.inserted.size)
            invalidated.add(CA::class.java.simpleName, result.invalidated.size)
            updated.add(CA::class.java.simpleName, result.updated.size)
            nsIdUpdated.add(CA::class.java.simpleName, result.updatedNsId.size)
            sendLog("Carbs", CA::class.java.simpleName)
            delay(pause)
        }

        snapshotAndClear(temporaryTargets)?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.syncNsTemporaryTargets(batch.toMutableList(), doLog = !fullSync)
            inserted.add(TT::class.java.simpleName, result.inserted.size)
            invalidated.add(TT::class.java.simpleName, result.invalidated.size)
            ended.add(TT::class.java.simpleName, result.ended.size)
            nsIdUpdated.add(TT::class.java.simpleName, result.updatedNsId.size)
            durationUpdated.add(TT::class.java.simpleName, result.updatedDuration.size)
            sendLog("TemporaryTarget", TT::class.java.simpleName)
            delay(pause)
        }

        snapshotAndClear(temporaryBasals)?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.syncNsTemporaryBasals(batch.toMutableList(), doLog = !fullSync)
            inserted.add(TB::class.java.simpleName, result.inserted.size)
            invalidated.add(TB::class.java.simpleName, result.invalidated.size)
            ended.add(TB::class.java.simpleName, result.ended.size)
            nsIdUpdated.add(TB::class.java.simpleName, result.updatedNsId.size)
            durationUpdated.add(TB::class.java.simpleName, result.updatedDuration.size)
            sendLog("TemporaryBasal", TB::class.java.simpleName)
            delay(pause)
        }

        snapshotAndClear(effectiveProfileSwitches)?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.syncNsEffectiveProfileSwitches(batch.toMutableList(), doLog = !fullSync)
            inserted.add(EPS::class.java.simpleName, result.inserted.size)
            invalidated.add(EPS::class.java.simpleName, result.invalidated.size)
            nsIdUpdated.add(EPS::class.java.simpleName, result.updatedNsId.size)
            sendLog("EffectiveProfileSwitch", EPS::class.java.simpleName)
            delay(pause)
        }

        snapshotAndClear(profileSwitches)?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.syncNsProfileSwitches(batch.toMutableList(), doLog = !fullSync)
            inserted.add(PS::class.java.simpleName, result.inserted.size)
            invalidated.add(PS::class.java.simpleName, result.invalidated.size)
            nsIdUpdated.add(PS::class.java.simpleName, result.updatedNsId.size)
            sendLog("ProfileSwitch", PS::class.java.simpleName)
            delay(pause)
        }

        snapshotAndClear(bolusCalculatorResults)?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.syncNsBolusCalculatorResults(batch.toMutableList())
            inserted.add(BCR::class.java.simpleName, result.inserted.size)
            invalidated.add(BCR::class.java.simpleName, result.invalidated.size)
            nsIdUpdated.add(BCR::class.java.simpleName, result.updatedNsId.size)
            sendLog("BolusCalculatorResult", BCR::class.java.simpleName)
            delay(pause)
        }

        snapshotAndClear(therapyEvents)?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.syncNsTherapyEvents(batch.toMutableList(), doLog = !fullSync)
            inserted.add(TE::class.java.simpleName, result.inserted.size)
            invalidated.add(TE::class.java.simpleName, result.invalidated.size)
            nsIdUpdated.add(TE::class.java.simpleName, result.updatedNsId.size)
            durationUpdated.add(TE::class.java.simpleName, result.updatedDuration.size)
            sendLog("TherapyEvent", TE::class.java.simpleName)
            delay(pause)
        }

        delay(pause)

        snapshotAndClear(runningModes)?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.syncNsRunningModes(batch.toMutableList(), doLog = !fullSync)
            inserted.add(RM::class.java.simpleName, result.inserted.size)
            invalidated.add(RM::class.java.simpleName, result.invalidated.size)
            ended.add(RM::class.java.simpleName, result.ended.size)
            nsIdUpdated.add(RM::class.java.simpleName, result.updatedNsId.size)
            durationUpdated.add(RM::class.java.simpleName, result.updatedDuration.size)
            sendLog("RunningMode", RM::class.java.simpleName)
            delay(pause)
        }

        snapshotAndClear(extendedBoluses)?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.syncNsExtendedBoluses(batch.toMutableList(), doLog = !fullSync)
            if (result.inserted.any { it.isEmulatingTempBasal }) virtualPump.fakeDataDetected = true
            inserted.add(EB::class.java.simpleName, result.inserted.size)
            invalidated.add(EB::class.java.simpleName, result.invalidated.size)
            ended.add(EB::class.java.simpleName, result.ended.size)
            nsIdUpdated.add(EB::class.java.simpleName, result.updatedNsId.size)
            durationUpdated.add(EB::class.java.simpleName, result.updatedDuration.size)
            sendLog("ExtendedBolus", EB::class.java.simpleName)
            delay(pause)
        }

        nsClientRepository.addLog("● DONE PROCESSING TR", "")
    }

    private val eventWorker = Executors.newSingleThreadScheduledExecutor()
    @VisibleForTesting var scheduledEventPost: ScheduledFuture<*>? = null

    @Synchronized
    override fun scheduleNsIdUpdate() {
        // cancel waiting task to prevent sending multiple posts
        scheduledEventPost?.cancel(false)
        scheduledEventPost = eventWorker.schedule({
                                                      aapsLogger.debug(LTag.CORE, "Firing updateNsIds")
                                                      scheduledEventPost = null
                                                      appScope.launch { updateNsIds() }
                                                  }, 10, TimeUnit.SECONDS)
    }

    override suspend fun updateNsIds() = nsIdMutex.withLock {
        snapshotAndClear(nsIdTemporaryTargets)?.let { batch ->
            val result = persistenceLayer.updateTemporaryTargetsNsIds(batch)
            nsIdUpdated.add(TT::class.java.simpleName, result.updatedNsId.size)
        }

        snapshotAndClear(nsIdGlucoseValues)?.let { batch ->
            val result = persistenceLayer.updateGlucoseValuesNsIds(batch)
            nsIdUpdated.add(GV::class.java.simpleName, result.updatedNsId.size)
        }

        snapshotAndClear(nsIdFoods)?.let { batch ->
            val result = persistenceLayer.updateFoodsNsIds(batch)
            nsIdUpdated.add(FD::class.java.simpleName, result.updatedNsId.size)
        }

        snapshotAndClear(nsIdTherapyEvents)?.let { batch ->
            val result = persistenceLayer.updateTherapyEventsNsIds(batch)
            nsIdUpdated.add(TE::class.java.simpleName, result.updatedNsId.size)
        }

        snapshotAndClear(nsIdBoluses)?.let { batch ->
            val result = persistenceLayer.updateBolusesNsIds(batch)
            nsIdUpdated.add(BS::class.java.simpleName, result.updatedNsId.size)
        }

        snapshotAndClear(nsIdCarbs)?.let { batch ->
            val result = persistenceLayer.updateCarbsNsIds(batch)
            nsIdUpdated.add(CA::class.java.simpleName, result.updatedNsId.size)
        }

        snapshotAndClear(nsIdBolusCalculatorResults)?.let { batch ->
            val result = persistenceLayer.updateBolusCalculatorResultsNsIds(batch)
            nsIdUpdated.add(BCR::class.java.simpleName, result.updatedNsId.size)
        }

        snapshotAndClear(nsIdTemporaryBasals)?.let { batch ->
            val result = persistenceLayer.updateTemporaryBasalsNsIds(batch)
            nsIdUpdated.add(TB::class.java.simpleName, result.updatedNsId.size)
        }

        snapshotAndClear(nsIdExtendedBoluses)?.let { batch ->
            val result = persistenceLayer.updateExtendedBolusesNsIds(batch)
            nsIdUpdated.add(EB::class.java.simpleName, result.updatedNsId.size)
        }

        snapshotAndClear(nsIdProfileSwitches)?.let { batch ->
            val result = persistenceLayer.updateProfileSwitchesNsIds(batch)
            nsIdUpdated.add(PS::class.java.simpleName, result.updatedNsId.size)
        }

        snapshotAndClear(nsIdEffectiveProfileSwitches)?.let { batch ->
            val result = persistenceLayer.updateEffectiveProfileSwitchesNsIds(batch)
            nsIdUpdated.add(EPS::class.java.simpleName, result.updatedNsId.size)
        }

        snapshotAndClear(nsIdDeviceStatuses)?.let { batch ->
            val result = persistenceLayer.updateDeviceStatusesNsIds(batch)
            nsIdUpdated.add(DS::class.java.simpleName, result.updatedNsId.size)
        }

        snapshotAndClear(nsIdRunningModes)?.let { batch ->
            val result = persistenceLayer.updateRunningModesNsIds(batch)
            nsIdUpdated.add(RM::class.java.simpleName, result.updatedNsId.size)
        }

        sendLog("GlucoseValue", GV::class.java.simpleName)
        sendLog("Bolus", BS::class.java.simpleName)
        sendLog("Carbs", CA::class.java.simpleName)
        sendLog("TemporaryTarget", TT::class.java.simpleName)
        sendLog("TemporaryBasal", TB::class.java.simpleName)
        sendLog("EffectiveProfileSwitch", EPS::class.java.simpleName)
        sendLog("ProfileSwitch", PS::class.java.simpleName)
        sendLog("BolusCalculatorResult", BCR::class.java.simpleName)
        sendLog("TherapyEvent", TE::class.java.simpleName)
        sendLog("RunningMode", RM::class.java.simpleName)
        sendLog("ExtendedBolus", EB::class.java.simpleName)
        sendLog("DeviceStatus", DS::class.java.simpleName)
        nsClientRepository.addLog("● DONE NSIDs", "")
    }

    override suspend fun updateDeletedTreatmentsInDb() = treatmentsMutex.withLock {
        val ids = snapshotAndClear(deleteTreatment) ?: return@withLock
        ids.forEach { id ->
            if (preferences.get(BooleanKey.NsClientAcceptInsulin) || config.AAPSCLIENT)
                persistenceLayer.getBolusByNSId(id)?.let { bolus ->
                    val result = persistenceLayer.invalidateBolus(bolus.id, Action.BOLUS_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(bolus.timestamp)))
                    invalidated.add(BS::class.java.simpleName, result.invalidated.size)
                    sendLog("Bolus", BS::class.java.simpleName)
                }
            if (preferences.get(BooleanKey.NsClientAcceptCarbs) || config.AAPSCLIENT)
                persistenceLayer.getCarbsByNSId(id)?.let { carb ->
                    val result = persistenceLayer.invalidateCarbs(carb.id, Action.CARBS_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(carb.timestamp)))
                    invalidated.add(CA::class.java.simpleName, result.invalidated.size)
                    sendLog("Carbs", CA::class.java.simpleName)
                }
            if (preferences.get(BooleanKey.NsClientAcceptTempTarget) || config.AAPSCLIENT)
                persistenceLayer.getTemporaryTargetByNSId(id)?.let { tt ->
                    val result = persistenceLayer.invalidateTemporaryTarget(tt.id, Action.TT_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(tt.timestamp)))
                    invalidated.add(TT::class.java.simpleName, result.invalidated.size)
                    sendLog("TemporaryTarget", TT::class.java.simpleName)
                }
            if (preferences.get(BooleanKey.NsClientAcceptTbrEb) || config.AAPSCLIENT)
                persistenceLayer.getTemporaryBasalByNSId(id)?.let { tb ->
                    val result = persistenceLayer.invalidateTemporaryBasal(tb.id, Action.TEMP_BASAL_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(tb.timestamp)))
                    invalidated.add(TB::class.java.simpleName, result.invalidated.size)
                    sendLog("TemporaryBasal", TB::class.java.simpleName)
                }
            if (preferences.get(BooleanKey.NsClientAcceptProfileSwitch) || config.AAPSCLIENT)
                persistenceLayer.getEffectiveProfileSwitchByNSId(id)?.let { eps ->
                    val result = persistenceLayer.invalidateEffectiveProfileSwitch(eps.id, Action.PROFILE_SWITCH_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(eps.timestamp)))
                    invalidated.add(EPS::class.java.simpleName, result.invalidated.size)
                    sendLog("EffectiveProfileSwitch", EPS::class.java.simpleName)
                }
            if (preferences.get(BooleanKey.NsClientAcceptProfileSwitch) || config.AAPSCLIENT)
                persistenceLayer.getProfileSwitchByNSId(id)?.let { ps ->
                    val result = persistenceLayer.invalidateProfileSwitch(ps.id, Action.PROFILE_SWITCH_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(ps.timestamp)))
                    invalidated.add(PS::class.java.simpleName, result.invalidated.size)
                    sendLog("ProfileSwitch", PS::class.java.simpleName)
                }
            persistenceLayer.getBolusCalculatorResultByNSId(id)?.let { bcr ->
                val result = persistenceLayer.invalidateBolusCalculatorResult(bcr.id, Action.BOLUS_CALCULATOR_RESULT_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(bcr.timestamp)))
                invalidated.add(BCR::class.java.simpleName, result.invalidated.size)
                sendLog("BolusCalculatorResult", BCR::class.java.simpleName)
            }
            if (preferences.get(BooleanKey.NsClientAcceptTherapyEvent) || config.AAPSCLIENT)
                persistenceLayer.getTherapyEventByNSId(id)?.let { te ->
                    val result = persistenceLayer.invalidateTherapyEvent(te.id, Action.TREATMENT_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(te.timestamp)))
                    invalidated.add(TE::class.java.simpleName, result.invalidated.size)
                    sendLog("TherapyEvent", TE::class.java.simpleName)
                }
            if (preferences.get(BooleanKey.NsClientAcceptRunningMode) && config.isEngineeringMode() || config.AAPSCLIENT)
                persistenceLayer.getRunningModeByNSId(id)?.let { rm ->
                    val result = persistenceLayer.invalidateRunningMode(rm.id, Action.TREATMENT_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(rm.timestamp)))
                    invalidated.add(RM::class.java.simpleName, result.invalidated.size)
                    sendLog("RunningMode", RM::class.java.simpleName)
                }
            if (preferences.get(BooleanKey.NsClientAcceptTbrEb) || config.AAPSCLIENT)
                persistenceLayer.getExtendedBolusByNSId(id)?.let { eb ->
                    val result = persistenceLayer.invalidateExtendedBolus(eb.id, Action.EXTENDED_BOLUS_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(eb.timestamp)))
                    invalidated.add(EB::class.java.simpleName, result.invalidated.size)
                    sendLog("EB", EB::class.java.simpleName)
                }
        }
    }

    override fun addToGlucoseValues(payload: MutableList<GV>): Boolean = synchronized(glucoseValues) { glucoseValues.addAll(payload) }
    override fun addToBoluses(payload: BS): Boolean = synchronized(boluses) { boluses.add(payload) }
    override fun addToCarbs(payload: CA): Boolean = synchronized(carbs) { carbs.add(payload) }
    override fun addToTemporaryTargets(payload: TT): Boolean = synchronized(temporaryTargets) { temporaryTargets.add(payload) }
    override fun addToEffectiveProfileSwitches(payload: EPS): Boolean = synchronized(effectiveProfileSwitches) { effectiveProfileSwitches.add(payload) }
    override fun addToBolusCalculatorResults(payload: BCR): Boolean = synchronized(bolusCalculatorResults) { bolusCalculatorResults.add(payload) }
    override fun addToTherapyEvents(payload: TE): Boolean = synchronized(therapyEvents) { therapyEvents.add(payload) }
    override fun addToExtendedBoluses(payload: EB): Boolean = synchronized(extendedBoluses) { extendedBoluses.add(payload) }
    override fun addToTemporaryBasals(payload: TB): Boolean = synchronized(temporaryBasals) { temporaryBasals.add(payload) }
    override fun addToProfileSwitches(payload: PS): Boolean = synchronized(profileSwitches) { profileSwitches.add(payload) }
    override fun addToRunningModes(payload: RM): Boolean = synchronized(runningModes) { runningModes.add(payload) }
    override fun addToFoods(payload: MutableList<FD>): Boolean = synchronized(foods) { foods.addAll(payload) }
    override fun addToNsIdGlucoseValues(payload: GV): Boolean = synchronized(nsIdGlucoseValues) { nsIdGlucoseValues.add(payload) }
    override fun addToNsIdBoluses(payload: BS): Boolean = synchronized(nsIdBoluses) { nsIdBoluses.add(payload) }
    override fun addToNsIdCarbs(payload: CA): Boolean = synchronized(nsIdCarbs) { nsIdCarbs.add(payload) }
    override fun addToNsIdTemporaryTargets(payload: TT): Boolean = synchronized(nsIdTemporaryTargets) { nsIdTemporaryTargets.add(payload) }
    override fun addToNsIdEffectiveProfileSwitches(payload: EPS): Boolean = synchronized(nsIdEffectiveProfileSwitches) { nsIdEffectiveProfileSwitches.add(payload) }
    override fun addToNsIdBolusCalculatorResults(payload: BCR): Boolean = synchronized(nsIdBolusCalculatorResults) { nsIdBolusCalculatorResults.add(payload) }
    override fun addToNsIdTherapyEvents(payload: TE): Boolean = synchronized(nsIdTherapyEvents) { nsIdTherapyEvents.add(payload) }
    override fun addToNsIdExtendedBoluses(payload: EB): Boolean = synchronized(nsIdExtendedBoluses) { nsIdExtendedBoluses.add(payload) }
    override fun addToNsIdTemporaryBasals(payload: TB): Boolean = synchronized(nsIdTemporaryBasals) { nsIdTemporaryBasals.add(payload) }
    override fun addToNsIdProfileSwitches(payload: PS): Boolean = synchronized(nsIdProfileSwitches) { nsIdProfileSwitches.add(payload) }
    override fun addToNsIdRunningModes(payload: RM): Boolean = synchronized(nsIdRunningModes) { nsIdRunningModes.add(payload) }
    override fun addToNsIdDeviceStatuses(payload: DS): Boolean = synchronized(nsIdDeviceStatuses) { nsIdDeviceStatuses.add(payload) }
    override fun addToNsIdFoods(payload: FD): Boolean = synchronized(nsIdFoods) { nsIdFoods.add(payload) }
    override fun addToDeleteTreatment(payload: String): Boolean = synchronized(deleteTreatment) { deleteTreatment.add(payload) }
    override fun addToDeleteGlucoseValue(payload: String): Boolean = synchronized(deleteGlucoseValue) { deleteGlucoseValue.add(payload) }

    override suspend fun updateDeletedGlucoseValuesInDb() = bgMutex.withLock {
        val ids = snapshotAndClear(deleteGlucoseValue) ?: return@withLock
        ids.forEach { id ->
            persistenceLayer.getBgReadingByNSId(id)?.let { gv ->
                val result = persistenceLayer.invalidateGlucoseValue(id = gv.id, action = Action.BG_REMOVED, source = Sources.NSClient, note = null, listValues = listOf(ValueWithUnit.Timestamp(gv.timestamp)))
                invalidated.add(GV::class.java.simpleName, result.invalidated.size)
                sendLog("GlucoseValue", GV::class.java.simpleName)
            }
        }
    }

    private fun sendLog(item: String, clazz: String) {
        inserted[clazz]?.let {
            if (it > 0) nsClientRepository.addLog("◄ INSERT", "$item $it")
        }
        inserted.removeClass(clazz)
        updated[clazz]?.let {
            if (it > 0) nsClientRepository.addLog("◄ UPDATE", "$item $it")
        }
        updated.removeClass(clazz)
        invalidated[clazz]?.let {
            if (it > 0) nsClientRepository.addLog("◄ INVALIDATE", "$item $it")
        }
        invalidated.removeClass(clazz)
        nsIdUpdated[clazz]?.let {
            if (it > 0) nsClientRepository.addLog("◄ NS_ID", "$item $it")
        }
        nsIdUpdated.removeClass(clazz)
        durationUpdated[clazz]?.let {
            if (it > 0) nsClientRepository.addLog("◄ DURATION", "$item $it")
        }
        durationUpdated.removeClass(clazz)
        ended[clazz]?.let {
            if (it > 0) nsClientRepository.addLog("◄ CUT", "$item $it")
        }
        ended.removeClass(clazz)
    }
}
