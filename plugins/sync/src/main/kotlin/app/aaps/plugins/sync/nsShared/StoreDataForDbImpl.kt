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
import app.aaps.core.data.model.OE
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.UE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNSClientNewLog
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.Preferences
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
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
    private val sp: SP,
    private val preferences: Preferences,
    private val uel: UserEntryLogger,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val nsClientSource: NSClientSource,
    private val virtualPump: VirtualPump,
    private val uiInteraction: UiInteraction
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
    private val offlineEvents: MutableList<OE> = mutableListOf()
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
    @VisibleForTesting val nsIdOfflineEvents: MutableList<OE> = mutableListOf()
    @VisibleForTesting val nsIdDeviceStatuses: MutableList<DS> = mutableListOf()
    @VisibleForTesting val nsIdFoods: MutableList<FD> = mutableListOf()

    private val deleteTreatment: MutableList<String> = mutableListOf()
    private val deleteGlucoseValue: MutableList<String> = mutableListOf()
    private val userEntries: MutableList<UE> = mutableListOf()

    private val inserted = HashMap<String, Long>()
    private val updated = HashMap<String, Long>()
    private val invalidated = HashMap<String, Long>()
    private val nsIdUpdated = HashMap<String, Long>()
    private val durationUpdated = HashMap<String, Long>()
    private val ended = HashMap<String, Long>()

    private val pause = 1000L // to slow down db operations

    fun <T> HashMap<T, Long>.inc(key: T) =
        synchronized(this) {
            if (containsKey(key)) merge(key, 1, Long::plus)
            else put(key, 1)
        }

    private val disposable = CompositeDisposable()
    override fun storeGlucoseValuesToDb() {
        synchronized(glucoseValues) {
            if (glucoseValues.isNotEmpty()) {
                persistenceLayer.insertCgmSourceData(Sources.NSClient, glucoseValues.toMutableList(), emptyList(), null)
                    .blockingGet()
                    .also { result ->
                        glucoseValues.clear()
                        result.updated.forEach {
                            nsClientSource.detectSource(it)
                            updated.inc(GV::class.java.simpleName)
                        }
                        result.inserted.forEach {
                            nsClientSource.detectSource(it)
                            inserted.inc(GV::class.java.simpleName)
                        }
                        result.updatedNsId.forEach {
                            nsClientSource.detectSource(it)
                            nsIdUpdated.inc(GV::class.java.simpleName)
                        }
                        sendLog("GlucoseValue", GV::class.java.simpleName)
                    }
                glucoseValues.clear()
            }
        }
        SystemClock.sleep(pause)
        rxBus.send(EventNSClientNewLog("● DONE PROCESSING BG", ""))
    }

    override fun storeFoodsToDb() {
        synchronized(foods) {
            if (foods.isNotEmpty()) {
                disposable += persistenceLayer.syncNsFood(foods.toMutableList())
                    .subscribeBy { result ->
                        repeat(result.updated.size) { updated.inc(FD::class.java.simpleName) }
                        repeat(result.inserted.size) { inserted.inc(FD::class.java.simpleName) }
                        repeat(result.invalidated.size) { nsIdUpdated.inc(FD::class.java.simpleName) }
                        sendLog("Food", FD::class.java.simpleName)
                    }
                foods.clear()
            }
        }

        SystemClock.sleep(pause)
        rxBus.send(EventNSClientNewLog("● DONE PROCESSING FOOD", ""))
    }

    override fun storeTreatmentsToDb() {
        synchronized(boluses) {
            if (boluses.isNotEmpty()) {
                disposable += persistenceLayer.syncNsBolus(boluses.toMutableList())
                    .subscribeBy { result ->
                        repeat(result.inserted.size) { inserted.inc(BS::class.java.simpleName) }
                        repeat(result.invalidated.size) { invalidated.inc(BS::class.java.simpleName) }
                        repeat(result.updatedNsId.size) { nsIdUpdated.inc(BS::class.java.simpleName) }
                        repeat(result.updated.size) { updated.inc(BS::class.java.simpleName) }
                        sendLog("Bolus", BS::class.java.simpleName)
                    }
                boluses.clear()
            }
        }

        SystemClock.sleep(pause)

        synchronized(carbs) {
            if (carbs.isNotEmpty()) {
                disposable += persistenceLayer.syncNsCarbs(carbs.toMutableList())
                    .subscribeBy { result ->
                        repeat(result.inserted.size) { inserted.inc(CA::class.java.simpleName) }
                        repeat(result.invalidated.size) { invalidated.inc(CA::class.java.simpleName) }
                        repeat(result.updated.size) { updated.inc(CA::class.java.simpleName) }
                        repeat(result.updatedNsId.size) { nsIdUpdated.inc(CA::class.java.simpleName) }
                        sendLog("Carbs", CA::class.java.simpleName)
                    }
                carbs.clear()
            }
        }

        SystemClock.sleep(pause)

        synchronized(temporaryTargets) {
            if (temporaryTargets.isNotEmpty()) {
                disposable += persistenceLayer.syncNsTemporaryTargets(temporaryTargets.toMutableList())
                    .subscribeBy { result ->
                        repeat(result.inserted.size) { inserted.inc(TT::class.java.simpleName) }
                        repeat(result.invalidated.size) { invalidated.inc(TT::class.java.simpleName) }
                        repeat(result.ended.size) { ended.inc(TT::class.java.simpleName) }
                        repeat(result.updatedNsId.size) { nsIdUpdated.inc(TT::class.java.simpleName) }
                        repeat(result.updatedDuration.size) { durationUpdated.inc(TT::class.java.simpleName) }
                        sendLog("TemporaryTarget", TT::class.java.simpleName)
                    }
                temporaryTargets.clear()
            }
        }

        SystemClock.sleep(pause)

        synchronized(temporaryBasals) {
            if (temporaryBasals.isNotEmpty()) {
                disposable += persistenceLayer.syncNsTemporaryBasals(temporaryBasals.toMutableList())
                    .subscribeBy { result ->
                        repeat(result.inserted.size) { inserted.inc(TB::class.java.simpleName) }
                        repeat(result.invalidated.size) { invalidated.inc(TB::class.java.simpleName) }
                        repeat(result.ended.size) { ended.inc(TB::class.java.simpleName) }
                        repeat(result.updatedNsId.size) { nsIdUpdated.inc(TB::class.java.simpleName) }
                        repeat(result.updatedDuration.size) { durationUpdated.inc(TB::class.java.simpleName) }
                        sendLog("TemporaryBasal", TB::class.java.simpleName)
                    }
                temporaryBasals.clear()
            }
        }

        SystemClock.sleep(pause)

        synchronized(effectiveProfileSwitches) {
            if (effectiveProfileSwitches.isNotEmpty()) {
                disposable += persistenceLayer.syncNsEffectiveProfileSwitches(effectiveProfileSwitches.toMutableList())
                    .subscribeBy { result ->
                        repeat(result.inserted.size) { inserted.inc(EPS::class.java.simpleName) }
                        repeat(result.invalidated.size) { invalidated.inc(EPS::class.java.simpleName) }
                        repeat(result.updatedNsId.size) { nsIdUpdated.inc(EPS::class.java.simpleName) }
                        sendLog("EffectiveProfileSwitch", EPS::class.java.simpleName)
                    }
                effectiveProfileSwitches.clear()
            }
        }

        SystemClock.sleep(pause)

        synchronized(profileSwitches) {
            if (profileSwitches.isNotEmpty()) {
                disposable += persistenceLayer.syncNsProfileSwitches(profileSwitches.toMutableList())
                    .subscribeBy { result ->
                        repeat(result.inserted.size) { inserted.inc(PS::class.java.simpleName) }
                        repeat(result.invalidated.size) { invalidated.inc(PS::class.java.simpleName) }
                        repeat(result.updatedNsId.size) { nsIdUpdated.inc(PS::class.java.simpleName) }
                        sendLog("ProfileSwitch", PS::class.java.simpleName)
                    }
                profileSwitches.clear()
            }
        }

        SystemClock.sleep(pause)

        synchronized(bolusCalculatorResults) {
            if (bolusCalculatorResults.isNotEmpty()) {
                disposable += persistenceLayer.syncNsBolusCalculatorResults(bolusCalculatorResults.toMutableList())
                    .subscribeBy { result ->
                        repeat(result.inserted.size) { inserted.inc(BCR::class.java.simpleName) }
                        repeat(result.invalidated.size) { invalidated.inc(BCR::class.java.simpleName) }
                        repeat(result.updatedNsId.size) { nsIdUpdated.inc(BCR::class.java.simpleName) }
                        sendLog("BolusCalculatorResult", BCR::class.java.simpleName)
                    }
                bolusCalculatorResults.clear()
            }
        }

        SystemClock.sleep(pause)

        synchronized(therapyEvents) {
            if (therapyEvents.isNotEmpty()) {
                disposable += persistenceLayer.syncNsTherapyEvents(therapyEvents.toMutableList())
                    .subscribeBy { result ->
                        repeat(result.inserted.size) { inserted.inc(TE::class.java.simpleName) }
                        repeat(result.invalidated.size) { invalidated.inc(TE::class.java.simpleName) }
                        repeat(result.updatedNsId.size) { nsIdUpdated.inc(TE::class.java.simpleName) }
                        repeat(result.updatedDuration.size) { durationUpdated.inc(TE::class.java.simpleName) }
                        sendLog("TherapyEvent", TE::class.java.simpleName)
                    }
                therapyEvents.clear()
            }
        }

        SystemClock.sleep(pause)

        synchronized(offlineEvents) {
            if (offlineEvents.isNotEmpty()) {
                disposable += persistenceLayer.syncNsOfflineEvents(offlineEvents.toMutableList())
                    .subscribeBy { result ->
                        repeat(result.inserted.size) { inserted.inc(OE::class.java.simpleName) }
                        repeat(result.invalidated.size) { invalidated.inc(OE::class.java.simpleName) }
                        repeat(result.ended.size) { ended.inc(OE::class.java.simpleName) }
                        repeat(result.updatedNsId.size) { nsIdUpdated.inc(OE::class.java.simpleName) }
                        repeat(result.updatedDuration.size) { durationUpdated.inc(OE::class.java.simpleName) }
                        sendLog("OfflineEvent", OE::class.java.simpleName)
                    }
                offlineEvents.clear()
            }
        }

        SystemClock.sleep(pause)

        synchronized(extendedBoluses) {
            if (extendedBoluses.isNotEmpty()) {
                disposable += persistenceLayer.syncNsExtendedBoluses(extendedBoluses.toMutableList())
                    .subscribeBy { result ->
                        result.inserted.forEach {
                            if (it.isEmulatingTempBasal) virtualPump.fakeDataDetected = true
                            inserted.inc(EB::class.java.simpleName)
                        }
                        repeat(result.invalidated.size) { invalidated.inc(EB::class.java.simpleName) }
                        repeat(result.ended.size) { ended.inc(EB::class.java.simpleName) }
                        repeat(result.updatedNsId.size) { nsIdUpdated.inc(EB::class.java.simpleName) }
                        repeat(result.updatedDuration.size) { durationUpdated.inc(EB::class.java.simpleName) }
                        sendLog("ExtendedBolus", EB::class.java.simpleName)
                    }
                extendedBoluses.clear()
            }
        }

        SystemClock.sleep(pause)

        uel.log(userEntries)
        rxBus.send(EventNSClientNewLog("● DONE PROCESSING TR", ""))
    }

    private val eventWorker = Executors.newSingleThreadScheduledExecutor()
    private var scheduledEventPost: ScheduledFuture<*>? = null

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
        disposable += persistenceLayer.updateTemporaryTargetsNsIds(nsIdTemporaryTargets)
            .subscribeBy { result ->
                nsIdTemporaryTargets.clear()
                repeat(result.updatedNsId.size) { nsIdUpdated.inc(TT::class.java.simpleName) }
            }

        disposable += persistenceLayer.updateGlucoseValuesNsIds(nsIdGlucoseValues)
            .subscribeBy { result ->
                nsIdGlucoseValues.clear()
                repeat(result.updatedNsId.size) { nsIdUpdated.inc(GV::class.java.simpleName) }
            }

        disposable += persistenceLayer.updateFoodsNsIds(nsIdFoods)
            .subscribeBy { result ->
                nsIdFoods.clear()
                repeat(result.updatedNsId.size) { nsIdUpdated.inc(FD::class.java.simpleName) }
            }

        disposable += persistenceLayer.updateTherapyEventsNsIds(nsIdTherapyEvents)
            .subscribeBy { result ->
                nsIdTherapyEvents.clear()
                repeat(result.updatedNsId.size) { nsIdUpdated.inc(TE::class.java.simpleName) }
            }

        disposable += persistenceLayer.updateBolusesNsIds(nsIdBoluses)
            .subscribeBy { result ->
                nsIdBoluses.clear()
                repeat(result.updatedNsId.size) { nsIdUpdated.inc(BS::class.java.simpleName) }
            }

        disposable += persistenceLayer.updateCarbsNsIds(nsIdCarbs)
            .subscribeBy { result ->
                nsIdCarbs.clear()
                repeat(result.updatedNsId.size) { nsIdUpdated.inc(CA::class.java.simpleName) }
            }

        disposable += persistenceLayer.updateBolusCalculatorResultsNsIds(nsIdBolusCalculatorResults)
            .subscribeBy { result ->
                nsIdBolusCalculatorResults.clear()
                repeat(result.updatedNsId.size) { nsIdUpdated.inc(BCR::class.java.simpleName) }
            }

        disposable += persistenceLayer.updateTemporaryBasalsNsIds(nsIdTemporaryBasals)
            .subscribeBy { result ->
                nsIdTemporaryBasals.clear()
                repeat(result.updatedNsId.size) { nsIdUpdated.inc(TB::class.java.simpleName) }
            }

        disposable += persistenceLayer.updateExtendedBolusesNsIds(nsIdExtendedBoluses)
            .subscribeBy { result ->
                nsIdExtendedBoluses.clear()
                repeat(result.updatedNsId.size) { nsIdUpdated.inc(EB::class.java.simpleName) }
            }

        disposable += persistenceLayer.updateProfileSwitchesNsIds(nsIdProfileSwitches)
            .subscribeBy { result ->
                nsIdProfileSwitches.clear()
                repeat(result.updatedNsId.size) { nsIdUpdated.inc(PS::class.java.simpleName) }
            }

        disposable += persistenceLayer.updateEffectiveProfileSwitchesNsIds(nsIdEffectiveProfileSwitches)
            .subscribeBy { result ->
                nsIdEffectiveProfileSwitches.clear()
                repeat(result.updatedNsId.size) { nsIdUpdated.inc(EPS::class.java.simpleName) }
            }

        disposable += persistenceLayer.updateDeviceStatusesNsIds(nsIdDeviceStatuses)
            .subscribeBy { result ->
                nsIdDeviceStatuses.clear()
                repeat(result.updatedNsId.size) { nsIdUpdated.inc(DS::class.java.simpleName) }
            }

        disposable += persistenceLayer.updateOfflineEventsNsIds(nsIdOfflineEvents)
            .subscribeBy { result ->
                nsIdOfflineEvents.clear()
                repeat(result.updatedNsId.size) { nsIdUpdated.inc(OE::class.java.simpleName) }
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
        sendLog("OfflineEvent", OE::class.java.simpleName)
        sendLog("ExtendedBolus", EB::class.java.simpleName)
        sendLog("DeviceStatus", DS::class.java.simpleName)
        rxBus.send(EventNSClientNewLog("● DONE NSIDs", ""))
    }

    override fun updateDeletedTreatmentsInDb() {
        deleteTreatment.forEach { id ->
            if (preferences.get(BooleanKey.NsClientAcceptInsulin) || config.AAPSCLIENT)
                persistenceLayer.getBolusByNSId(id)?.let { bolus ->
                    disposable += persistenceLayer.invalidateBolus(
                        bolus.id,
                        Action.BOLUS_REMOVED,
                        Sources.NSClient,
                        null,
                        listValues = listOf(ValueWithUnit.Timestamp(bolus.timestamp))
                    ).subscribeBy { result ->
                        repeat(result.invalidated.size) { invalidated.inc(BS::class.java.simpleName) }
                        sendLog("Bolus", BS::class.java.simpleName)
                    }
                }
            if (preferences.get(BooleanKey.NsClientAcceptCarbs) || config.AAPSCLIENT)
                persistenceLayer.getCarbsByNSId(id)?.let { carb ->
                    disposable += persistenceLayer.invalidateCarbs(
                        carb.id,
                        Action.CARBS_REMOVED,
                        Sources.NSClient,
                        null,
                        listValues = listOf(ValueWithUnit.Timestamp(carb.timestamp))
                    ).subscribeBy { result ->
                        repeat(result.invalidated.size) { invalidated.inc(CA::class.java.simpleName) }
                        sendLog("Carbs", CA::class.java.simpleName)
                    }
                }
            if (preferences.get(BooleanKey.NsClientAcceptTempTarget) || config.AAPSCLIENT)
                persistenceLayer.getTemporaryTargetByNSId(id)?.let { tt ->
                    disposable += persistenceLayer.invalidateTemporaryTarget(
                        tt.id,
                        Action.TT_REMOVED,
                        Sources.NSClient,
                        null,
                        listValues = listOf(ValueWithUnit.Timestamp(tt.timestamp))
                    ).subscribeBy { result ->
                        repeat(result.invalidated.size) { invalidated.inc(TT::class.java.simpleName) }
                        sendLog("TemporaryTarget", TT::class.java.simpleName)
                    }
                }
            if (preferences.get(BooleanKey.NsClientAcceptTbrEb) || config.AAPSCLIENT)
                persistenceLayer.getTemporaryBasalByNSId(id)?.let { tb ->
                    disposable += persistenceLayer.invalidateTemporaryBasal(
                        tb.id,
                        Action.TEMP_BASAL_REMOVED,
                        Sources.NSClient,
                        null,
                        listValues = listOf(ValueWithUnit.Timestamp(tb.timestamp))
                    ).subscribeBy { result ->
                        repeat(result.invalidated.size) { invalidated.inc(TB::class.java.simpleName) }
                        sendLog("TemporaryBasal", TB::class.java.simpleName)
                    }
                }
            if (preferences.get(BooleanKey.NsClientAcceptProfileSwitch) || config.AAPSCLIENT)
                persistenceLayer.getEffectiveProfileSwitchByNSId(id)?.let { eps ->
                    disposable += persistenceLayer.invalidateEffectiveProfileSwitch(
                        eps.id,
                        Action.PROFILE_SWITCH_REMOVED,
                        Sources.NSClient,
                        null,
                        listValues = listOf(ValueWithUnit.Timestamp(eps.timestamp))
                    ).subscribeBy { result ->
                        repeat(result.invalidated.size) { invalidated.inc(EPS::class.java.simpleName) }
                        sendLog("EffectiveProfileSwitch", EPS::class.java.simpleName)
                    }
                }
            if (preferences.get(BooleanKey.NsClientAcceptProfileSwitch) || config.AAPSCLIENT)
                persistenceLayer.getProfileSwitchByNSId(id)?.let { ps ->
                    disposable += persistenceLayer.invalidateProfileSwitch(
                        ps.id,
                        Action.PROFILE_SWITCH_REMOVED,
                        Sources.NSClient,
                        null,
                        listValues = listOf(ValueWithUnit.Timestamp(ps.timestamp))
                    ).subscribeBy { result ->
                        repeat(result.invalidated.size) { invalidated.inc(PS::class.java.simpleName) }
                        sendLog("ProfileSwitch", PS::class.java.simpleName)
                    }
                }
            persistenceLayer.getBolusCalculatorResultByNSId(id)?.let { bcr ->
                disposable += persistenceLayer.invalidateBolusCalculatorResult(
                    bcr.id,
                    Action.BOLUS_CALCULATOR_RESULT_REMOVED,
                    Sources.NSClient,
                    null,
                    listValues = listOf(ValueWithUnit.Timestamp(bcr.timestamp))
                ).subscribeBy { result ->
                    repeat(result.invalidated.size) { invalidated.inc(BCR::class.java.simpleName) }
                    sendLog("BolusCalculatorResult", BCR::class.java.simpleName)
                }
            }
            if (preferences.get(BooleanKey.NsClientAcceptTherapyEvent) || config.AAPSCLIENT)
                persistenceLayer.getTherapyEventByNSId(id)?.let { te ->
                    disposable += persistenceLayer.invalidateTherapyEvent(
                        te.id,
                        Action.TREATMENT_REMOVED,
                        Sources.NSClient,
                        null,
                        listValues = listOf(ValueWithUnit.Timestamp(te.timestamp))
                    ).subscribeBy { result ->
                        repeat(result.invalidated.size) { invalidated.inc(TE::class.java.simpleName) }
                        sendLog("TherapyEvent", TE::class.java.simpleName)
                    }
                }
            if (preferences.get(BooleanKey.NsClientAcceptOfflineEvent) && config.isEngineeringMode() || config.AAPSCLIENT)
                persistenceLayer.getOfflineEventByNSId(id)?.let { oe ->
                    disposable += persistenceLayer.invalidateOfflineEvent(
                        oe.id,
                        Action.TREATMENT_REMOVED,
                        Sources.NSClient,
                        null,
                        listValues = listOf(ValueWithUnit.Timestamp(oe.timestamp))
                    ).subscribeBy { result ->
                        repeat(result.invalidated.size) { invalidated.inc(OE::class.java.simpleName) }
                        sendLog("OfflineEvent", OE::class.java.simpleName)
                    }
                }
            if (preferences.get(BooleanKey.NsClientAcceptTbrEb) || config.AAPSCLIENT)
                persistenceLayer.getExtendedBolusByNSId(id)?.let { eb ->
                    disposable += persistenceLayer.invalidateExtendedBolus(
                        eb.id,
                        Action.EXTENDED_BOLUS_REMOVED,
                        Sources.NSClient,
                        null,
                        listValues = listOf(ValueWithUnit.Timestamp(eb.timestamp))
                    ).subscribeBy { result ->
                        repeat(result.invalidated.size) { invalidated.inc(EB::class.java.simpleName) }
                        sendLog("EB", EB::class.java.simpleName)
                    }
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
    override fun addToOfflineEvents(payload: OE): Boolean = synchronized(offlineEvents) { offlineEvents.add(payload) }
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
    override fun addToNsIdOfflineEvents(payload: OE): Boolean = synchronized(nsIdOfflineEvents) { nsIdOfflineEvents.add(payload) }
    override fun addToNsIdDeviceStatuses(payload: DS): Boolean = synchronized(nsIdDeviceStatuses) { nsIdDeviceStatuses.add(payload) }
    override fun addToNsIdFoods(payload: FD): Boolean = synchronized(nsIdFoods) { nsIdFoods.add(payload) }
    override fun addToDeleteTreatment(payload: String): Boolean = synchronized(deleteTreatment) { deleteTreatment.add(payload) }
    override fun addToDeleteGlucoseValue(payload: String): Boolean = synchronized(deleteGlucoseValue) { deleteGlucoseValue.add(payload) }

    override fun updateDeletedGlucoseValuesInDb() {
        deleteGlucoseValue.forEach { id ->
            persistenceLayer.getBgReadingByNSId(id)?.let { gv ->
                disposable += persistenceLayer.invalidateGlucoseValue(
                    id = gv.id,
                    action = Action.BG_REMOVED,
                    source = Sources.NSClient,
                    note = null,
                    listValues = listOf(ValueWithUnit.Timestamp(gv.timestamp))
                )
                    .subscribeBy { result ->
                        repeat(result.invalidated.size) { invalidated.inc(GV::class.java.simpleName) }
                        sendLog("GlucoseValue", GV::class.java.simpleName)
                    }
            }
        }
    }

    private fun sendLog(item: String, clazz: String) {
        inserted[clazz]?.let {
            rxBus.send(EventNSClientNewLog("◄ INSERT", "$item $it"))
        }
        inserted.remove(clazz)
        updated[clazz]?.let {
            rxBus.send(EventNSClientNewLog("◄ UPDATE", "$item $it"))
        }
        updated.remove(clazz)
        invalidated[clazz]?.let {
            rxBus.send(EventNSClientNewLog("◄ INVALIDATE", "$item $it"))
        }
        invalidated.remove(clazz)
        nsIdUpdated[clazz]?.let {
            rxBus.send(EventNSClientNewLog("◄ NS_ID", "$item $it"))
        }
        nsIdUpdated.remove(clazz)
        durationUpdated[clazz]?.let {
            rxBus.send(EventNSClientNewLog("◄ DURATION", "$item $it"))
        }
        durationUpdated.remove(clazz)
        ended[clazz]?.let {
            rxBus.send(EventNSClientNewLog("◄ CUT", "$item $it"))
        }
        ended.remove(clazz)
    }
}