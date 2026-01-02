package app.aaps.plugins.sync.nsShared

import android.os.SystemClock
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
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNSClientNewLog
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoreDataForDbImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val config: Config,
    private val nsClientSource: NSClientSource,
    private val virtualPump: VirtualPump
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

    private val pause = 300L // to slow down db operations
    private val chunk = 500

    fun <T> HashMap<T, Int>.inc(key: T) =
        synchronized(this) {
            if (containsKey(key)) merge(key, 1, Int::plus)
            else put(key, 1)
        }

    fun <T> HashMap<T, Int>.add(key: T, amount: Int) = synchronized(this) {
        if (containsKey(key)) merge(key, amount, Int::plus)
        else put(key, amount)
    }

    fun <T> HashMap<T, Int>.removeClass(key: T) = synchronized(this) { remove(key) }

    override fun storeGlucoseValuesToDb() {
        synchronized(glucoseValues) {
            if (glucoseValues.isNotEmpty()) {
                glucoseValues.chunked(chunk).forEach {
                    persistenceLayer.insertCgmSourceData(Sources.NSClient, it.toMutableList(), emptyList(), null)
                        .blockingGet()
                        .also { result ->
                            result.updated.forEach { gv ->
                                nsClientSource.detectSource(gv)
                                updated.inc(GV::class.java.simpleName)
                            }
                            result.inserted.forEach { gv ->
                                nsClientSource.detectSource(gv)
                                inserted.inc(GV::class.java.simpleName)
                            }
                            result.updatedNsId.forEach { gv ->
                                nsClientSource.detectSource(gv)
                                nsIdUpdated.inc(GV::class.java.simpleName)
                            }
                            sendLog("GlucoseValue", GV::class.java.simpleName)
                        }
                    SystemClock.sleep(pause)
                }
                glucoseValues.clear()
            }
        }
        rxBus.send(EventNSClientNewLog("● DONE PROCESSING BG", ""))
    }

    override fun storeFoodsToDb() {
        synchronized(foods) {
            if (foods.isNotEmpty()) {
                persistenceLayer.syncNsFood(foods.toMutableList()).blockingGet().also { result ->
                    updated.add(FD::class.java.simpleName, result.updated.size)
                    inserted.add(FD::class.java.simpleName, result.inserted.size)
                    nsIdUpdated.add(FD::class.java.simpleName, result.invalidated.size)
                    sendLog("Food", FD::class.java.simpleName)
                }
                SystemClock.sleep(pause)
                foods.clear()
            }
        }

        SystemClock.sleep(pause)
        rxBus.send(EventNSClientNewLog("● DONE PROCESSING FOOD", ""))
    }

    override fun storeTreatmentsToDb(fullSync: Boolean) {
        synchronized(boluses) {
            if (boluses.isNotEmpty()) {
                boluses.chunked(chunk).forEach {
                    persistenceLayer.syncNsBolus(it.toMutableList(), doLog = !fullSync).blockingGet().also { result ->
                        inserted.add(BS::class.java.simpleName, result.inserted.size)
                        invalidated.add(BS::class.java.simpleName, result.invalidated.size)
                        nsIdUpdated.add(BS::class.java.simpleName, result.updatedNsId.size)
                        updated.add(BS::class.java.simpleName, result.updated.size)
                        sendLog("Bolus", BS::class.java.simpleName)
                    }
                    SystemClock.sleep(pause)
                }
                boluses.clear()
            }
        }

        synchronized(carbs) {
            if (carbs.isNotEmpty()) {
                carbs.chunked(chunk).forEach {
                    persistenceLayer.syncNsCarbs(it.toMutableList(), doLog = !fullSync).blockingGet().also { result ->
                        inserted.add(CA::class.java.simpleName, result.inserted.size)
                        invalidated.add(CA::class.java.simpleName, result.invalidated.size)
                        updated.add(CA::class.java.simpleName, result.updated.size)
                        nsIdUpdated.add(CA::class.java.simpleName, result.updatedNsId.size)
                        sendLog("Carbs", CA::class.java.simpleName)
                    }
                    SystemClock.sleep(pause)
                }
                carbs.clear()
            }
        }

        synchronized(temporaryTargets) {
            if (temporaryTargets.isNotEmpty()) {
                temporaryTargets.chunked(chunk).forEach {
                    persistenceLayer.syncNsTemporaryTargets(it.toMutableList(), doLog = !fullSync).blockingGet().also { result ->
                        inserted.add(TT::class.java.simpleName, result.inserted.size)
                        invalidated.add(TT::class.java.simpleName, result.invalidated.size)
                        ended.add(TT::class.java.simpleName, result.ended.size)
                        nsIdUpdated.add(TT::class.java.simpleName, result.updatedNsId.size)
                        durationUpdated.add(TT::class.java.simpleName, result.updatedDuration.size)
                        sendLog("TemporaryTarget", TT::class.java.simpleName)
                    }
                    SystemClock.sleep(pause)
                }
                temporaryTargets.clear()
            }
        }

        synchronized(temporaryBasals) {
            if (temporaryBasals.isNotEmpty()) {
                temporaryBasals.chunked(chunk).forEach {
                    persistenceLayer.syncNsTemporaryBasals(it.toMutableList(), doLog = !fullSync).blockingGet().also { result ->
                        inserted.add(TB::class.java.simpleName, result.inserted.size)
                        invalidated.add(TB::class.java.simpleName, result.invalidated.size)
                        ended.add(TB::class.java.simpleName, result.ended.size)
                        nsIdUpdated.add(TB::class.java.simpleName, result.updatedNsId.size)
                        durationUpdated.add(TB::class.java.simpleName, result.updatedDuration.size)
                        sendLog("TemporaryBasal", TB::class.java.simpleName)
                    }
                    SystemClock.sleep(pause)
                }
                temporaryBasals.clear()
            }
        }

        synchronized(effectiveProfileSwitches) {
            if (effectiveProfileSwitches.isNotEmpty()) {
                effectiveProfileSwitches.chunked(chunk).forEach {
                    persistenceLayer.syncNsEffectiveProfileSwitches(it.toMutableList(), doLog = !fullSync).blockingGet().also { result ->
                        inserted.add(EPS::class.java.simpleName, result.inserted.size)
                        invalidated.add(EPS::class.java.simpleName, result.invalidated.size)
                        nsIdUpdated.add(EPS::class.java.simpleName, result.updatedNsId.size)
                        sendLog("EffectiveProfileSwitch", EPS::class.java.simpleName)
                    }
                    SystemClock.sleep(pause)
                }
                effectiveProfileSwitches.clear()
            }
        }

        synchronized(profileSwitches) {
            if (profileSwitches.isNotEmpty()) {
                profileSwitches.chunked(chunk).forEach {
                    persistenceLayer.syncNsProfileSwitches(it.toMutableList(), doLog = !fullSync).blockingGet().also { result ->
                        inserted.add(PS::class.java.simpleName, result.inserted.size)
                        invalidated.add(PS::class.java.simpleName, result.invalidated.size)
                        nsIdUpdated.add(PS::class.java.simpleName, result.updatedNsId.size)
                        sendLog("ProfileSwitch", PS::class.java.simpleName)
                    }
                    SystemClock.sleep(pause)
                }
                profileSwitches.clear()
            }
        }

        synchronized(bolusCalculatorResults) {
            if (bolusCalculatorResults.isNotEmpty()) {
                bolusCalculatorResults.chunked(chunk).forEach {
                    persistenceLayer.syncNsBolusCalculatorResults(it.toMutableList()).blockingGet().also { result ->
                        inserted.add(BCR::class.java.simpleName, result.inserted.size)
                        invalidated.add(BCR::class.java.simpleName, result.invalidated.size)
                        nsIdUpdated.add(BCR::class.java.simpleName, result.updatedNsId.size)
                        sendLog("BolusCalculatorResult", BCR::class.java.simpleName)
                    }
                    SystemClock.sleep(pause)
                }
                bolusCalculatorResults.clear()
            }
        }

        synchronized(therapyEvents) {
            if (therapyEvents.isNotEmpty()) {
                therapyEvents.chunked(chunk).forEach {
                    persistenceLayer.syncNsTherapyEvents(it.toMutableList(), doLog = !fullSync).blockingGet().also { result ->
                        inserted.add(TE::class.java.simpleName, result.inserted.size)
                        invalidated.add(TE::class.java.simpleName, result.invalidated.size)
                        nsIdUpdated.add(TE::class.java.simpleName, result.updatedNsId.size)
                        durationUpdated.add(TE::class.java.simpleName, result.updatedDuration.size)
                        sendLog("TherapyEvent", TE::class.java.simpleName)
                    }
                    SystemClock.sleep(pause)
                }
                therapyEvents.clear()
            }
        }

        SystemClock.sleep(pause)

        synchronized(runningModes) {
            if (runningModes.isNotEmpty()) {
                runningModes.chunked(chunk).forEach {
                    persistenceLayer.syncNsRunningModes(it.toMutableList(), doLog = !fullSync).blockingGet().also { result ->
                        inserted.add(RM::class.java.simpleName, result.inserted.size)
                        invalidated.add(RM::class.java.simpleName, result.invalidated.size)
                        ended.add(RM::class.java.simpleName, result.ended.size)
                        nsIdUpdated.add(RM::class.java.simpleName, result.updatedNsId.size)
                        durationUpdated.add(RM::class.java.simpleName, result.updatedDuration.size)
                        sendLog("RunningMode", RM::class.java.simpleName)
                    }
                    SystemClock.sleep(pause)
                }
                runningModes.clear()
            }
        }

        synchronized(extendedBoluses) {
            if (extendedBoluses.isNotEmpty()) {
                extendedBoluses.chunked(chunk).forEach {
                    persistenceLayer.syncNsExtendedBoluses(it.toMutableList(), doLog = !fullSync).blockingGet().also { result ->
                        result.inserted.forEach { eb ->
                            if (eb.isEmulatingTempBasal) virtualPump.fakeDataDetected = true
                            inserted.inc(EB::class.java.simpleName)
                        }
                        invalidated.add(EB::class.java.simpleName, result.invalidated.size)
                        ended.add(EB::class.java.simpleName, result.ended.size)
                        nsIdUpdated.add(EB::class.java.simpleName, result.updatedNsId.size)
                        durationUpdated.add(EB::class.java.simpleName, result.updatedDuration.size)
                        sendLog("ExtendedBolus", EB::class.java.simpleName)
                    }
                    SystemClock.sleep(pause)
                }
                extendedBoluses.clear()
            }
        }

        rxBus.send(EventNSClientNewLog("● DONE PROCESSING TR", ""))
    }

    private val eventWorker = Executors.newSingleThreadScheduledExecutor()
    @VisibleForTesting var scheduledEventPost: ScheduledFuture<*>? = null

    @Synchronized
    override fun scheduleNsIdUpdate() {
        class PostRunnable : Runnable {

            override fun run() {
                aapsLogger.debug(LTag.CORE, "Firing updateNsIds")
                scheduledEventPost = null
                updateNsIds()
            }
        }
        // cancel waiting task to prevent sending multiple posts
        scheduledEventPost?.cancel(false)
        val task: Runnable = PostRunnable()
        scheduledEventPost = eventWorker.schedule(task, 10, TimeUnit.SECONDS)
    }

    @Synchronized
    override fun updateNsIds() {
        synchronized(nsIdTemporaryTargets) {
            if (nsIdTemporaryTargets.isNotEmpty())
                persistenceLayer.updateTemporaryTargetsNsIds(nsIdTemporaryTargets).blockingGet().also { result ->
                    nsIdTemporaryTargets.clear()
                    nsIdUpdated.add(TT::class.java.simpleName, result.updatedNsId.size)
                }
        }

        synchronized(nsIdGlucoseValues) {
            if (nsIdGlucoseValues.isNotEmpty())
                persistenceLayer.updateGlucoseValuesNsIds(nsIdGlucoseValues).blockingGet().also { result ->
                    nsIdGlucoseValues.clear()
                    nsIdUpdated.add(GV::class.java.simpleName, result.updatedNsId.size)
                }
        }

        synchronized(nsIdFoods) {
            if (nsIdFoods.isNotEmpty())
                persistenceLayer.updateFoodsNsIds(nsIdFoods).blockingGet().also { result ->
                    nsIdFoods.clear()
                    nsIdUpdated.add(FD::class.java.simpleName, result.updatedNsId.size)
                }
        }

        synchronized(nsIdTherapyEvents) {
            if (nsIdTherapyEvents.isNotEmpty())
                persistenceLayer.updateTherapyEventsNsIds(nsIdTherapyEvents).blockingGet().also { result ->
                    nsIdTherapyEvents.clear()
                    nsIdUpdated.add(TE::class.java.simpleName, result.updatedNsId.size)
                }
        }

        synchronized(nsIdBoluses) {
            if (nsIdBoluses.isNotEmpty())
                persistenceLayer.updateBolusesNsIds(nsIdBoluses).blockingGet().also { result ->
                    nsIdBoluses.clear()
                    nsIdUpdated.add(BS::class.java.simpleName, result.updatedNsId.size)
                }
        }

        synchronized(nsIdCarbs) {
            if (nsIdCarbs.isNotEmpty())
                persistenceLayer.updateCarbsNsIds(nsIdCarbs).blockingGet().also { result ->
                    nsIdCarbs.clear()
                    nsIdUpdated.add(CA::class.java.simpleName, result.updatedNsId.size)
                }
        }

        synchronized(nsIdBolusCalculatorResults) {
            if (nsIdBolusCalculatorResults.isNotEmpty())
                persistenceLayer.updateBolusCalculatorResultsNsIds(nsIdBolusCalculatorResults).blockingGet().also { result ->
                    nsIdBolusCalculatorResults.clear()
                    nsIdUpdated.add(BCR::class.java.simpleName, result.updatedNsId.size)
                }
        }

        synchronized(nsIdTemporaryBasals) {
            if (nsIdTemporaryBasals.isNotEmpty())
                persistenceLayer.updateTemporaryBasalsNsIds(nsIdTemporaryBasals).blockingGet().also { result ->
                    nsIdTemporaryBasals.clear()
                    nsIdUpdated.add(TB::class.java.simpleName, result.updatedNsId.size)
                }
        }

        synchronized(nsIdExtendedBoluses) {
            if (nsIdExtendedBoluses.isNotEmpty())
                persistenceLayer.updateExtendedBolusesNsIds(nsIdExtendedBoluses).blockingGet().also { result ->
                    nsIdExtendedBoluses.clear()
                    nsIdUpdated.add(EB::class.java.simpleName, result.updatedNsId.size)
                }
        }

        synchronized(nsIdProfileSwitches) {
            if (nsIdProfileSwitches.isNotEmpty())
                persistenceLayer.updateProfileSwitchesNsIds(nsIdProfileSwitches).blockingGet().also { result ->
                    nsIdProfileSwitches.clear()
                    nsIdUpdated.add(PS::class.java.simpleName, result.updatedNsId.size)
                }
        }

        synchronized(nsIdEffectiveProfileSwitches) {
            if (nsIdEffectiveProfileSwitches.isNotEmpty())
                persistenceLayer.updateEffectiveProfileSwitchesNsIds(nsIdEffectiveProfileSwitches).blockingGet().also { result ->
                    nsIdEffectiveProfileSwitches.clear()
                    nsIdUpdated.add(EPS::class.java.simpleName, result.updatedNsId.size)
                }
        }

        synchronized(nsIdDeviceStatuses) {
            if (nsIdDeviceStatuses.isNotEmpty())
                persistenceLayer.updateDeviceStatusesNsIds(nsIdDeviceStatuses).blockingGet().also { result ->
                    nsIdDeviceStatuses.clear()
                    nsIdUpdated.add(DS::class.java.simpleName, result.updatedNsId.size)
                }
        }

        synchronized(nsIdRunningModes) {
            if (nsIdRunningModes.isNotEmpty())
                persistenceLayer.updateRunningModesNsIds(nsIdRunningModes).blockingGet().also { result ->
                    nsIdRunningModes.clear()
                    nsIdUpdated.add(RM::class.java.simpleName, result.updatedNsId.size)
                }
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
        rxBus.send(EventNSClientNewLog("● DONE NSIDs", ""))
    }

    override fun updateDeletedTreatmentsInDb() {
        synchronized(deleteTreatment) {
            deleteTreatment.forEach { id ->
                if (preferences.get(BooleanKey.NsClientAcceptInsulin) || config.AAPSCLIENT)
                    persistenceLayer.getBolusByNSId(id)?.let { bolus ->
                        persistenceLayer.invalidateBolus(bolus.id, Action.BOLUS_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(bolus.timestamp))).blockingGet().also { result ->
                            invalidated.add(BS::class.java.simpleName, result.invalidated.size)
                            sendLog("Bolus", BS::class.java.simpleName)
                        }
                    }
                if (preferences.get(BooleanKey.NsClientAcceptCarbs) || config.AAPSCLIENT)
                    persistenceLayer.getCarbsByNSId(id)?.let { carb ->
                        persistenceLayer.invalidateCarbs(carb.id, Action.CARBS_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(carb.timestamp))).blockingGet().also { result ->
                            invalidated.add(CA::class.java.simpleName, result.invalidated.size)
                            sendLog("Carbs", CA::class.java.simpleName)
                        }
                    }
                if (preferences.get(BooleanKey.NsClientAcceptTempTarget) || config.AAPSCLIENT)
                    persistenceLayer.getTemporaryTargetByNSId(id)?.let { tt ->
                        persistenceLayer.invalidateTemporaryTarget(tt.id, Action.TT_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(tt.timestamp))).blockingGet().also { result ->
                            invalidated.add(TT::class.java.simpleName, result.invalidated.size)
                            sendLog("TemporaryTarget", TT::class.java.simpleName)
                        }
                    }
                if (preferences.get(BooleanKey.NsClientAcceptTbrEb) || config.AAPSCLIENT)
                    persistenceLayer.getTemporaryBasalByNSId(id)?.let { tb ->
                        persistenceLayer.invalidateTemporaryBasal(tb.id, Action.TEMP_BASAL_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(tb.timestamp))).blockingGet().also { result ->
                            invalidated.add(TB::class.java.simpleName, result.invalidated.size)
                            sendLog("TemporaryBasal", TB::class.java.simpleName)
                        }
                    }
                if (preferences.get(BooleanKey.NsClientAcceptProfileSwitch) || config.AAPSCLIENT)
                    persistenceLayer.getEffectiveProfileSwitchByNSId(id)?.let { eps ->
                        persistenceLayer.invalidateEffectiveProfileSwitch(eps.id, Action.PROFILE_SWITCH_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(eps.timestamp))).blockingGet().also { result ->
                            invalidated.add(EPS::class.java.simpleName, result.invalidated.size)
                            sendLog("EffectiveProfileSwitch", EPS::class.java.simpleName)
                        }
                    }
                if (preferences.get(BooleanKey.NsClientAcceptProfileSwitch) || config.AAPSCLIENT)
                    persistenceLayer.getProfileSwitchByNSId(id)?.let { ps ->
                        persistenceLayer.invalidateProfileSwitch(ps.id, Action.PROFILE_SWITCH_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(ps.timestamp))).blockingGet().also { result ->
                            invalidated.add(PS::class.java.simpleName, result.invalidated.size)
                            sendLog("ProfileSwitch", PS::class.java.simpleName)
                        }
                    }
                persistenceLayer.getBolusCalculatorResultByNSId(id)?.let { bcr ->
                    persistenceLayer.invalidateBolusCalculatorResult(bcr.id, Action.BOLUS_CALCULATOR_RESULT_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(bcr.timestamp))).blockingGet().also { result ->
                        invalidated.add(BCR::class.java.simpleName, result.invalidated.size)
                        sendLog("BolusCalculatorResult", BCR::class.java.simpleName)
                    }
                }
                if (preferences.get(BooleanKey.NsClientAcceptTherapyEvent) || config.AAPSCLIENT)
                    persistenceLayer.getTherapyEventByNSId(id)?.let { te ->
                        persistenceLayer.invalidateTherapyEvent(te.id, Action.TREATMENT_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(te.timestamp))).blockingGet().also { result ->
                            invalidated.add(TE::class.java.simpleName, result.invalidated.size)
                            sendLog("TherapyEvent", TE::class.java.simpleName)
                        }
                    }
                if (preferences.get(BooleanKey.NsClientAcceptRunningMode) && config.isEngineeringMode() || config.AAPSCLIENT)
                    persistenceLayer.getRunningModeByNSId(id)?.let { rm ->
                        persistenceLayer.invalidateRunningMode(rm.id, Action.TREATMENT_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(rm.timestamp))).blockingGet().also { result ->
                            invalidated.add(RM::class.java.simpleName, result.invalidated.size)
                            sendLog("RunningMode", RM::class.java.simpleName)
                        }
                    }
                if (preferences.get(BooleanKey.NsClientAcceptTbrEb) || config.AAPSCLIENT)
                    persistenceLayer.getExtendedBolusByNSId(id)?.let { eb ->
                        persistenceLayer.invalidateExtendedBolus(eb.id, Action.EXTENDED_BOLUS_REMOVED, Sources.NSClient, null, listValues = listOf(ValueWithUnit.Timestamp(eb.timestamp))).blockingGet().also { result ->
                            invalidated.add(EB::class.java.simpleName, result.invalidated.size)
                            sendLog("EB", EB::class.java.simpleName)
                        }
                    }
            }
            deleteTreatment.clear()
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

    override fun updateDeletedGlucoseValuesInDb() {
        synchronized(deleteGlucoseValue) {
            deleteGlucoseValue.forEach { id ->
                persistenceLayer.getBgReadingByNSId(id)?.let { gv ->
                    persistenceLayer.invalidateGlucoseValue(id = gv.id, action = Action.BG_REMOVED, source = Sources.NSClient, note = null, listValues = listOf(ValueWithUnit.Timestamp(gv.timestamp))).blockingGet().also { result ->
                        invalidated.add(GV::class.java.simpleName, result.invalidated.size)
                        sendLog("GlucoseValue", GV::class.java.simpleName)
                    }
                }
            }
            deleteGlucoseValue.clear()
        }
    }

    private fun sendLog(item: String, clazz: String) {
        inserted[clazz]?.let {
            if (it > 0) rxBus.send(EventNSClientNewLog("◄ INSERT", "$item $it"))
        }
        inserted.removeClass(clazz)
        updated[clazz]?.let {
            if (it > 0) rxBus.send(EventNSClientNewLog("◄ UPDATE", "$item $it"))
        }
        updated.removeClass(clazz)
        invalidated[clazz]?.let {
            if (it > 0) rxBus.send(EventNSClientNewLog("◄ INVALIDATE", "$item $it"))
        }
        invalidated.removeClass(clazz)
        nsIdUpdated[clazz]?.let {
            if (it > 0) rxBus.send(EventNSClientNewLog("◄ NS_ID", "$item $it"))
        }
        nsIdUpdated.removeClass(clazz)
        durationUpdated[clazz]?.let {
            if (it > 0) rxBus.send(EventNSClientNewLog("◄ DURATION", "$item $it"))
        }
        durationUpdated.removeClass(clazz)
        ended[clazz]?.let {
            if (it > 0) rxBus.send(EventNSClientNewLog("◄ CUT", "$item $it"))
        }
        ended.removeClass(clazz)
    }
}