package app.aaps.plugins.sync.nsclientV3

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.CAL
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
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The type's simple name, as the counter keys and log labels have always shown it.
 *
 * This replaces `::class.java.simpleName`, which is JVM only. `KClass.simpleName` is nullable because
 * anonymous and local classes have no name; every type used here is a named data class, and
 * `StoreDataForDbLabelsTest` pins both that the two forms agree and what each string is.
 */
private val KClass<*>.label: String get() = simpleName ?: ""

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class StoreDataForDbImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val config: Config,
    private val nsClientRepository: NSClientRepository,
    // Plain CoroutineScope, not @ApplicationScope: that qualifier is javax and cannot appear in
    // commonMain. AppCoroutineBindings.unqualifiedAppScope binds the very same scope without it, so
    // this is still the one application scope, not a second one.
    private val appScope: CoroutineScope
) : StoreDataForDb {

    private val glucoseValues = SyncedList<GV>()
    private val calibrationEntries = SyncedList<CAL>()
    private val boluses = SyncedList<BS>()
    private val carbs = SyncedList<CA>()
    private val temporaryTargets = SyncedList<TT>()
    private val effectiveProfileSwitches = SyncedList<EPS>()
    private val bolusCalculatorResults = SyncedList<BCR>()
    private val therapyEvents = SyncedList<TE>()
    private val extendedBoluses = SyncedList<EB>()
    private val temporaryBasals = SyncedList<TB>()
    private val profileSwitches = SyncedList<PS>()
    private val runningModes = SyncedList<RM>()
    private val foods = SyncedList<FD>()

    internal val nsIdGlucoseValues = SyncedList<GV>()
    internal val nsIdCalibrationEntries = SyncedList<CAL>()
    internal val nsIdBoluses = SyncedList<BS>()
    internal val nsIdCarbs = SyncedList<CA>()
    internal val nsIdTemporaryTargets = SyncedList<TT>()
    internal val nsIdEffectiveProfileSwitches = SyncedList<EPS>()
    internal val nsIdBolusCalculatorResults = SyncedList<BCR>()
    internal val nsIdTherapyEvents = SyncedList<TE>()
    internal val nsIdExtendedBoluses = SyncedList<EB>()
    internal val nsIdTemporaryBasals = SyncedList<TB>()
    internal val nsIdProfileSwitches = SyncedList<PS>()
    internal val nsIdRunningModes = SyncedList<RM>()
    internal val nsIdDeviceStatuses = SyncedList<DS>()
    internal val nsIdFoods = SyncedList<FD>()

    internal val deleteTreatment = SyncedList<String>()
    private val deleteGlucoseValue = SyncedList<String>()

    private val inserted = SyncedCounters()
    private val updated = SyncedCounters()
    private val invalidated = SyncedCounters()
    private val nsIdUpdated = SyncedCounters()
    private val durationUpdated = SyncedCounters()
    private val ended = SyncedCounters()

    // Throttle between DB chunks; non-blocking suspend so the WS / worker thread is free.
    private val pause = 300L
    private val chunk = 500

    // The ns id update was debounced by 10 s on a ScheduledExecutorService. Same wait, coroutine now.
    private val nsIdUpdateDelay = 10_000L

    // Per-pipeline mutexes so BG ingest can run while a long treatments sync is in progress.
    private val bgMutex = Mutex()
    private val calibrationMutex = Mutex()
    private val treatmentsMutex = Mutex()
    private val nsIdMutex = Mutex()

    // Coalescing channels for fire-and-forget request* methods. CONFLATED so a burst
    // of N WS arrivals collapses into 1 (or 2) collector runs instead of queueing N
    // coroutines on the mutex. The buffer is shared, so the single drain catches all.
    private val glucoseRequests = Channel<Unit>(Channel.CONFLATED)
    private val calibrationRequests = Channel<Unit>(Channel.CONFLATED)
    private val treatmentsRequests = Channel<Boolean>(Channel.CONFLATED)
    private val foodsRequests = Channel<Unit>(Channel.CONFLATED)
    private val deletedTreatmentsRequests = Channel<Unit>(Channel.CONFLATED)
    private val deletedGlucoseRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        appScope.launch { glucoseRequests.consumeEach { storeGlucoseValuesToDb() } }
        appScope.launch { calibrationRequests.consumeEach { storeCalibrationEntriesToDb() } }
        appScope.launch { treatmentsRequests.consumeEach { fullSync -> storeTreatmentsToDb(fullSync) } }
        appScope.launch { foodsRequests.consumeEach { storeFoodsToDb() } }
        appScope.launch { deletedTreatmentsRequests.consumeEach { updateDeletedTreatmentsInDb() } }
        appScope.launch { deletedGlucoseRequests.consumeEach { updateDeletedGlucoseValuesInDb() } }
    }

    override fun requestStoreGlucoseValues() {
        glucoseRequests.trySend(Unit)
    }

    override fun requestStoreCalibrationEntries() {
        calibrationRequests.trySend(Unit)
    }

    override fun requestStoreTreatments(fullSync: Boolean) {
        treatmentsRequests.trySend(fullSync)
    }

    override fun requestStoreFoods() {
        foodsRequests.trySend(Unit)
    }

    override fun requestUpdateDeletedTreatments() {
        deletedTreatmentsRequests.trySend(Unit)
    }

    override fun requestUpdateDeletedGlucoseValues() {
        deletedGlucoseRequests.trySend(Unit)
    }


    override suspend fun storeGlucoseValuesToDb() = bgMutex.withLock {
        glucoseValues.snapshotAndClear()?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.insertCgmSourceData(Sources.NSClient, batch.toMutableList(), emptyList(), null)
            updated.add(GV::class.label, result.updated.size)
            inserted.add(GV::class.label, result.inserted.size)
            nsIdUpdated.add(GV::class.label, result.updatedNsId.size)
            sendLog("GlucoseValue", GV::class.label)
            delay(pause)
        }
        nsClientRepository.addLog("● DONE PROCESSING BG", "")
    }

    override suspend fun storeCalibrationEntriesToDb() {
        calibrationMutex.withLock {
            calibrationEntries.snapshotAndClear()?.chunked(chunk)?.forEach { batch ->
                val result = persistenceLayer.syncNsCalibrationEntries(batch.toMutableList())
                updated.add(CAL::class.label, result.updated.size)
                inserted.add(CAL::class.label, result.inserted.size)
                invalidated.add(CAL::class.label, result.invalidated.size)
                sendLog("CalibrationEntry", CAL::class.label)
                delay(pause)
            }
        }
    }

    override suspend fun storeFoodsToDb() = treatmentsMutex.withLock {
        foods.snapshotAndClear()?.let { batch ->
            val result = persistenceLayer.syncNsFood(batch.toMutableList())
            updated.add(FD::class.label, result.updated.size)
            inserted.add(FD::class.label, result.inserted.size)
            nsIdUpdated.add(FD::class.label, result.invalidated.size)
            sendLog("Food", FD::class.label)
            delay(pause)
        }
        nsClientRepository.addLog("● DONE PROCESSING FOOD", "")
    }

    override suspend fun storeTreatmentsToDb(fullSync: Boolean) = treatmentsMutex.withLock {
        boluses.snapshotAndClear()?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.syncNsBolus(batch.toMutableList(), doLog = !fullSync)
            inserted.add(BS::class.label, result.inserted.size)
            invalidated.add(BS::class.label, result.invalidated.size)
            nsIdUpdated.add(BS::class.label, result.updatedNsId.size)
            updated.add(BS::class.label, result.updated.size)
            sendLog("Bolus", BS::class.label)
            delay(pause)
        }

        carbs.snapshotAndClear()?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.syncNsCarbs(batch.toMutableList(), doLog = !fullSync)
            inserted.add(CA::class.label, result.inserted.size)
            invalidated.add(CA::class.label, result.invalidated.size)
            updated.add(CA::class.label, result.updated.size)
            nsIdUpdated.add(CA::class.label, result.updatedNsId.size)
            sendLog("Carbs", CA::class.label)
            delay(pause)
        }

        temporaryTargets.snapshotAndClear()?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.syncNsTemporaryTargets(batch.toMutableList(), doLog = !fullSync)
            inserted.add(TT::class.label, result.inserted.size)
            invalidated.add(TT::class.label, result.invalidated.size)
            ended.add(TT::class.label, result.ended.size)
            nsIdUpdated.add(TT::class.label, result.updatedNsId.size)
            durationUpdated.add(TT::class.label, result.updatedDuration.size)
            sendLog("TemporaryTarget", TT::class.label)
            delay(pause)
        }

        temporaryBasals.snapshotAndClear()?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.syncNsTemporaryBasals(batch.toMutableList(), doLog = !fullSync)
            inserted.add(TB::class.label, result.inserted.size)
            invalidated.add(TB::class.label, result.invalidated.size)
            ended.add(TB::class.label, result.ended.size)
            nsIdUpdated.add(TB::class.label, result.updatedNsId.size)
            durationUpdated.add(TB::class.label, result.updatedDuration.size)
            sendLog("TemporaryBasal", TB::class.label)
            delay(pause)
        }

        effectiveProfileSwitches.snapshotAndClear()?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.syncNsEffectiveProfileSwitches(batch.toMutableList(), doLog = !fullSync)
            inserted.add(EPS::class.label, result.inserted.size)
            invalidated.add(EPS::class.label, result.invalidated.size)
            nsIdUpdated.add(EPS::class.label, result.updatedNsId.size)
            sendLog("EffectiveProfileSwitch", EPS::class.label)
            delay(pause)
        }

        profileSwitches.snapshotAndClear()?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.syncNsProfileSwitches(batch.toMutableList(), doLog = !fullSync)
            inserted.add(PS::class.label, result.inserted.size)
            invalidated.add(PS::class.label, result.invalidated.size)
            nsIdUpdated.add(PS::class.label, result.updatedNsId.size)
            sendLog("ProfileSwitch", PS::class.label)
            delay(pause)
        }

        bolusCalculatorResults.snapshotAndClear()?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.syncNsBolusCalculatorResults(batch.toMutableList())
            inserted.add(BCR::class.label, result.inserted.size)
            invalidated.add(BCR::class.label, result.invalidated.size)
            nsIdUpdated.add(BCR::class.label, result.updatedNsId.size)
            sendLog("BolusCalculatorResult", BCR::class.label)
            delay(pause)
        }

        therapyEvents.snapshotAndClear()?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.syncNsTherapyEvents(batch.toMutableList(), doLog = !fullSync)
            inserted.add(TE::class.label, result.inserted.size)
            invalidated.add(TE::class.label, result.invalidated.size)
            nsIdUpdated.add(TE::class.label, result.updatedNsId.size)
            durationUpdated.add(TE::class.label, result.updatedDuration.size)
            sendLog("TherapyEvent", TE::class.label)
            delay(pause)
        }

        delay(pause)

        runningModes.snapshotAndClear()?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.syncNsRunningModes(batch.toMutableList(), doLog = !fullSync)
            inserted.add(RM::class.label, result.inserted.size)
            invalidated.add(RM::class.label, result.invalidated.size)
            ended.add(RM::class.label, result.ended.size)
            nsIdUpdated.add(RM::class.label, result.updatedNsId.size)
            durationUpdated.add(RM::class.label, result.updatedDuration.size)
            sendLog("RunningMode", RM::class.label)
            delay(pause)
        }

        extendedBoluses.snapshotAndClear()?.chunked(chunk)?.forEach { batch ->
            val result = persistenceLayer.syncNsExtendedBoluses(batch.toMutableList(), doLog = !fullSync)
            // (The client no longer derives "pump fakes temps via EB" from synced data — it is mirrored read-only from
            // the master's RunningConfiguration onto VirtualPump.fakeDataDetected; see RunningConfigurationImpl.)
            inserted.add(EB::class.label, result.inserted.size)
            invalidated.add(EB::class.label, result.invalidated.size)
            ended.add(EB::class.label, result.ended.size)
            nsIdUpdated.add(EB::class.label, result.updatedNsId.size)
            durationUpdated.add(EB::class.label, result.updatedDuration.size)
            sendLog("ExtendedBolus", EB::class.label)
            delay(pause)
        }

        nsClientRepository.addLog("● DONE PROCESSING TR", "")
    }

    /**
     * Guards [scheduledEventPost]. Replaces `@Synchronized`, which is JVM only; the contract is the
     * same reentrant, blocking one - see [AapsLock].
     */
    private val scheduleLock = AapsLock()
    internal var scheduledEventPost: Job? = null

    override fun scheduleNsIdUpdate() = scheduleLock.withLock {
        // cancel waiting task to prevent sending multiple posts
        scheduledEventPost?.cancel()
        scheduledEventPost = appScope.launch {
            delay(nsIdUpdateDelay)
            aapsLogger.debug(LTag.CORE, "Firing updateNsIds")
            scheduledEventPost = null
            // Launched separately on purpose. `ScheduledFuture.cancel(false)` could not stop a task
            // that had already started, so a later schedule never interrupted an update in flight.
            // Calling updateNsIds() inline here would make it cancellable and change that.
            appScope.launch { updateNsIds() }
        }
    }

    override suspend fun updateNsIds() = nsIdMutex.withLock {
        nsIdTemporaryTargets.snapshotAndClear()?.let { batch ->
            val result = persistenceLayer.updateTemporaryTargetsNsIds(batch)
            nsIdUpdated.add(TT::class.label, result.updatedNsId.size)
        }

        nsIdGlucoseValues.snapshotAndClear()?.let { batch ->
            val result = persistenceLayer.updateGlucoseValuesNsIds(batch)
            nsIdUpdated.add(GV::class.label, result.updatedNsId.size)
        }

        nsIdCalibrationEntries.snapshotAndClear()?.let { batch ->
            val result = persistenceLayer.updateCalibrationEntriesNsIds(batch)
            nsIdUpdated.add(CAL::class.label, result.updatedNsId.size)
        }

        nsIdFoods.snapshotAndClear()?.let { batch ->
            val result = persistenceLayer.updateFoodsNsIds(batch)
            nsIdUpdated.add(FD::class.label, result.updatedNsId.size)
        }

        nsIdTherapyEvents.snapshotAndClear()?.let { batch ->
            val result = persistenceLayer.updateTherapyEventsNsIds(batch)
            nsIdUpdated.add(TE::class.label, result.updatedNsId.size)
        }

        nsIdBoluses.snapshotAndClear()?.let { batch ->
            val result = persistenceLayer.updateBolusesNsIds(batch)
            nsIdUpdated.add(BS::class.label, result.updatedNsId.size)
        }

        nsIdCarbs.snapshotAndClear()?.let { batch ->
            val result = persistenceLayer.updateCarbsNsIds(batch)
            nsIdUpdated.add(CA::class.label, result.updatedNsId.size)
        }

        nsIdBolusCalculatorResults.snapshotAndClear()?.let { batch ->
            val result = persistenceLayer.updateBolusCalculatorResultsNsIds(batch)
            nsIdUpdated.add(BCR::class.label, result.updatedNsId.size)
        }

        nsIdTemporaryBasals.snapshotAndClear()?.let { batch ->
            val result = persistenceLayer.updateTemporaryBasalsNsIds(batch)
            nsIdUpdated.add(TB::class.label, result.updatedNsId.size)
        }

        nsIdExtendedBoluses.snapshotAndClear()?.let { batch ->
            val result = persistenceLayer.updateExtendedBolusesNsIds(batch)
            nsIdUpdated.add(EB::class.label, result.updatedNsId.size)
        }

        nsIdProfileSwitches.snapshotAndClear()?.let { batch ->
            val result = persistenceLayer.updateProfileSwitchesNsIds(batch)
            nsIdUpdated.add(PS::class.label, result.updatedNsId.size)
        }

        nsIdEffectiveProfileSwitches.snapshotAndClear()?.let { batch ->
            val result = persistenceLayer.updateEffectiveProfileSwitchesNsIds(batch)
            nsIdUpdated.add(EPS::class.label, result.updatedNsId.size)
        }

        nsIdDeviceStatuses.snapshotAndClear()?.let { batch ->
            val result = persistenceLayer.updateDeviceStatusesNsIds(batch)
            nsIdUpdated.add(DS::class.label, result.updatedNsId.size)
        }

        nsIdRunningModes.snapshotAndClear()?.let { batch ->
            val result = persistenceLayer.updateRunningModesNsIds(batch)
            nsIdUpdated.add(RM::class.label, result.updatedNsId.size)
        }

        sendLog("GlucoseValue", GV::class.label)
        sendLog("Bolus", BS::class.label)
        sendLog("Carbs", CA::class.label)
        sendLog("TemporaryTarget", TT::class.label)
        sendLog("TemporaryBasal", TB::class.label)
        sendLog("EffectiveProfileSwitch", EPS::class.label)
        sendLog("ProfileSwitch", PS::class.label)
        sendLog("BolusCalculatorResult", BCR::class.label)
        sendLog("TherapyEvent", TE::class.label)
        sendLog("RunningMode", RM::class.label)
        sendLog("ExtendedBolus", EB::class.label)
        sendLog("DeviceStatus", DS::class.label)
        nsClientRepository.addLog("● DONE NSIDs", "")
    }

    override suspend fun updateDeletedTreatmentsInDb() = treatmentsMutex.withLock {
        val ids = deleteTreatment.snapshotAndClear() ?: return@withLock
        ids.forEach { id ->
            if (preferences.get(BooleanKey.NsClientAcceptInsulin) || config.AAPSCLIENT)
                persistenceLayer.getBolusByNSId(id)?.let { bolus ->
                    val result = persistenceLayer.invalidateBolus(bolus.id, Action.BOLUS_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(bolus.timestamp)))
                    invalidated.add(BS::class.label, result.invalidated.size)
                    sendLog("Bolus", BS::class.label)
                }
            if (preferences.get(BooleanKey.NsClientAcceptCarbs) || config.AAPSCLIENT)
                persistenceLayer.getCarbsByNSId(id)?.let { carb ->
                    val result = persistenceLayer.invalidateCarbs(carb.id, Action.CARBS_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(carb.timestamp)))
                    invalidated.add(CA::class.label, result.invalidated.size)
                    sendLog("Carbs", CA::class.label)
                }
            if (preferences.get(BooleanKey.NsClientAcceptTempTarget) || config.AAPSCLIENT)
                persistenceLayer.getTemporaryTargetByNSId(id)?.let { tt ->
                    val result = persistenceLayer.invalidateTemporaryTarget(tt.id, Action.TT_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(tt.timestamp)))
                    invalidated.add(TT::class.label, result.invalidated.size)
                    sendLog("TemporaryTarget", TT::class.label)
                }
            if (preferences.get(BooleanKey.NsClientAcceptTbrEb) || config.AAPSCLIENT)
                persistenceLayer.getTemporaryBasalByNSId(id)?.let { tb ->
                    val result = persistenceLayer.invalidateTemporaryBasal(tb.id, Action.TEMP_BASAL_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(tb.timestamp)))
                    invalidated.add(TB::class.label, result.invalidated.size)
                    sendLog("TemporaryBasal", TB::class.label)
                }
            if (preferences.get(BooleanKey.NsClientAcceptProfileSwitch) || config.AAPSCLIENT)
                persistenceLayer.getEffectiveProfileSwitchByNSId(id)?.let { eps ->
                    val result = persistenceLayer.invalidateEffectiveProfileSwitch(eps.id, Action.PROFILE_SWITCH_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(eps.timestamp)))
                    invalidated.add(EPS::class.label, result.invalidated.size)
                    sendLog("EffectiveProfileSwitch", EPS::class.label)
                }
            if (preferences.get(BooleanKey.NsClientAcceptProfileSwitch) || config.AAPSCLIENT)
                persistenceLayer.getProfileSwitchByNSId(id)?.let { ps ->
                    val result = persistenceLayer.invalidateProfileSwitch(ps.id, Action.PROFILE_SWITCH_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(ps.timestamp)))
                    invalidated.add(PS::class.label, result.invalidated.size)
                    sendLog("ProfileSwitch", PS::class.label)
                }
            persistenceLayer.getBolusCalculatorResultByNSId(id)?.let { bcr ->
                val result = persistenceLayer.invalidateBolusCalculatorResult(bcr.id, Action.BOLUS_CALCULATOR_RESULT_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(bcr.timestamp)))
                invalidated.add(BCR::class.label, result.invalidated.size)
                sendLog("BolusCalculatorResult", BCR::class.label)
            }
            if (preferences.get(BooleanKey.NsClientAcceptTherapyEvent) || config.AAPSCLIENT)
                persistenceLayer.getTherapyEventByNSId(id)?.let { te ->
                    val result = persistenceLayer.invalidateTherapyEvent(te.id, Action.TREATMENT_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(te.timestamp)))
                    invalidated.add(TE::class.label, result.invalidated.size)
                    sendLog("TherapyEvent", TE::class.label)
                }
            if (preferences.get(BooleanKey.NsClientAcceptRunningMode) && config.isEngineeringMode() || config.AAPSCLIENT)
                persistenceLayer.getRunningModeByNSId(id)?.let { rm ->
                    val result = persistenceLayer.invalidateRunningMode(rm.id, Action.TREATMENT_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(rm.timestamp)))
                    invalidated.add(RM::class.label, result.invalidated.size)
                    sendLog("RunningMode", RM::class.label)
                }
            if (preferences.get(BooleanKey.NsClientAcceptTbrEb) || config.AAPSCLIENT)
                persistenceLayer.getExtendedBolusByNSId(id)?.let { eb ->
                    val result = persistenceLayer.invalidateExtendedBolus(eb.id, Action.EXTENDED_BOLUS_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(eb.timestamp)))
                    invalidated.add(EB::class.label, result.invalidated.size)
                    sendLog("EB", EB::class.label)
                }
        }
    }

    override fun addToGlucoseValues(payload: MutableList<GV>): Boolean = glucoseValues.addAll(payload)
    override fun addToCalibrationEntries(payload: MutableList<CAL>): Boolean = calibrationEntries.addAll(payload)
    override fun addToBoluses(payload: BS): Boolean = boluses.add(payload)
    override fun addToCarbs(payload: CA): Boolean = carbs.add(payload)
    override fun addToTemporaryTargets(payload: TT): Boolean = temporaryTargets.add(payload)
    override fun addToEffectiveProfileSwitches(payload: EPS): Boolean = effectiveProfileSwitches.add(payload)
    override fun addToBolusCalculatorResults(payload: BCR): Boolean = bolusCalculatorResults.add(payload)
    override fun addToTherapyEvents(payload: TE): Boolean = therapyEvents.add(payload)
    override fun addToExtendedBoluses(payload: EB): Boolean = extendedBoluses.add(payload)
    override fun addToTemporaryBasals(payload: TB): Boolean = temporaryBasals.add(payload)
    override fun addToProfileSwitches(payload: PS): Boolean = profileSwitches.add(payload)
    override fun addToRunningModes(payload: RM): Boolean = runningModes.add(payload)
    override fun addToFoods(payload: MutableList<FD>): Boolean = foods.addAll(payload)
    override fun addToNsIdGlucoseValues(payload: GV): Boolean = nsIdGlucoseValues.add(payload)
    override fun addToNsIdCalibrationEntries(payload: CAL): Boolean = nsIdCalibrationEntries.add(payload)
    override fun addToNsIdBoluses(payload: BS): Boolean = nsIdBoluses.add(payload)
    override fun addToNsIdCarbs(payload: CA): Boolean = nsIdCarbs.add(payload)
    override fun addToNsIdTemporaryTargets(payload: TT): Boolean = nsIdTemporaryTargets.add(payload)
    override fun addToNsIdEffectiveProfileSwitches(payload: EPS): Boolean = nsIdEffectiveProfileSwitches.add(payload)
    override fun addToNsIdBolusCalculatorResults(payload: BCR): Boolean = nsIdBolusCalculatorResults.add(payload)
    override fun addToNsIdTherapyEvents(payload: TE): Boolean = nsIdTherapyEvents.add(payload)
    override fun addToNsIdExtendedBoluses(payload: EB): Boolean = nsIdExtendedBoluses.add(payload)
    override fun addToNsIdTemporaryBasals(payload: TB): Boolean = nsIdTemporaryBasals.add(payload)
    override fun addToNsIdProfileSwitches(payload: PS): Boolean = nsIdProfileSwitches.add(payload)
    override fun addToNsIdRunningModes(payload: RM): Boolean = nsIdRunningModes.add(payload)
    override fun addToNsIdDeviceStatuses(payload: DS): Boolean = nsIdDeviceStatuses.add(payload)
    override fun addToNsIdFoods(payload: FD): Boolean = nsIdFoods.add(payload)
    override fun addToDeleteTreatment(payload: String): Boolean = deleteTreatment.add(payload)
    override fun addToDeleteGlucoseValue(payload: String): Boolean = deleteGlucoseValue.add(payload)

    override suspend fun updateDeletedGlucoseValuesInDb() = bgMutex.withLock {
        val ids = deleteGlucoseValue.snapshotAndClear() ?: return@withLock
        ids.forEach { id ->
            persistenceLayer.getBgReadingByNSId(id)?.let { gv ->
                val result = persistenceLayer.invalidateGlucoseValue(id = gv.id, action = Action.BG_REMOVED, source = Sources.NSClient, note = null, listValues = listOf(ValueWithUnit.Timestamp(gv.timestamp)))
                invalidated.add(GV::class.label, result.invalidated.size)
                sendLog("GlucoseValue", GV::class.label)
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
