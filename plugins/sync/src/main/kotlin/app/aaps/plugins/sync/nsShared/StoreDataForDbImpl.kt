package app.aaps.plugins.sync.nsShared

import android.os.SystemClock
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
import app.aaps.core.interfaces.notifications.Notification
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

    override val glucoseValues: MutableList<GV> = mutableListOf()
    override val boluses: MutableList<BS> = mutableListOf()
    override val carbs: MutableList<CA> = mutableListOf()
    override val temporaryTargets: MutableList<TT> = mutableListOf()
    override val effectiveProfileSwitches: MutableList<EPS> = mutableListOf()
    override val bolusCalculatorResults: MutableList<BCR> = mutableListOf()
    override val therapyEvents: MutableList<TE> = mutableListOf()
    override val extendedBoluses: MutableList<EB> = mutableListOf()
    override val temporaryBasals: MutableList<TB> = mutableListOf()
    override val profileSwitches: MutableList<PS> = mutableListOf()
    override val offlineEvents: MutableList<OE> = mutableListOf()
    override val foods: MutableList<FD> = mutableListOf()

    override val nsIdGlucoseValues: MutableList<GV> = mutableListOf()
    override val nsIdBoluses: MutableList<BS> = mutableListOf()
    override val nsIdCarbs: MutableList<CA> = mutableListOf()
    override val nsIdTemporaryTargets: MutableList<TT> = mutableListOf()
    override val nsIdEffectiveProfileSwitches: MutableList<EPS> = mutableListOf()
    override val nsIdBolusCalculatorResults: MutableList<BCR> = mutableListOf()
    override val nsIdTherapyEvents: MutableList<TE> = mutableListOf()
    override val nsIdExtendedBoluses: MutableList<EB> = mutableListOf()
    override val nsIdTemporaryBasals: MutableList<TB> = mutableListOf()
    override val nsIdProfileSwitches: MutableList<PS> = mutableListOf()
    override val nsIdOfflineEvents: MutableList<OE> = mutableListOf()
    override val nsIdDeviceStatuses: MutableList<DS> = mutableListOf()
    override val nsIdFoods: MutableList<FD> = mutableListOf()

    override val deleteTreatment: MutableList<String> = mutableListOf()
    override val deleteGlucoseValue: MutableList<String> = mutableListOf()
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
        if (glucoseValues.isNotEmpty())
            persistenceLayer.insertCgmSourceData(Sources.NSClient, glucoseValues, emptyList(), null)
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

        SystemClock.sleep(pause)
        rxBus.send(EventNSClientNewLog("● DONE PROCESSING BG", ""))
    }

    override fun storeFoodsToDb() {
        if (foods.isNotEmpty())
            disposable += persistenceLayer.syncNsFood(foods)
                .subscribeBy { result ->
                    foods.clear()
                    repeat(result.updated.size) { updated.inc(FD::class.java.simpleName) }
                    repeat(result.inserted.size) { inserted.inc(FD::class.java.simpleName) }
                    repeat(result.invalidated.size) { nsIdUpdated.inc(FD::class.java.simpleName) }
                    sendLog("Food", FD::class.java.simpleName)
                }

        SystemClock.sleep(pause)
        rxBus.send(EventNSClientNewLog("● DONE PROCESSING FOOD", ""))
    }

    override fun storeTreatmentsToDb() {
        if (boluses.isNotEmpty())
            disposable += persistenceLayer.syncNsBolus(boluses)
                .subscribeBy { result ->
                    boluses.clear()
                    repeat(result.inserted.size) { inserted.inc(BS::class.java.simpleName) }
                    repeat(result.invalidated.size) { invalidated.inc(BS::class.java.simpleName) }
                    repeat(result.updatedNsId.size) { nsIdUpdated.inc(BS::class.java.simpleName) }
                    repeat(result.updated.size) { updated.inc(BS::class.java.simpleName) }
                    sendLog("Bolus", BS::class.java.simpleName)
                }

        SystemClock.sleep(pause)

        if (carbs.isNotEmpty())
            disposable += persistenceLayer.syncNsCarbs(carbs)
                .subscribeBy { result ->
                    carbs.clear()
                    repeat(result.inserted.size) { inserted.inc(CA::class.java.simpleName) }
                    repeat(result.invalidated.size) { invalidated.inc(CA::class.java.simpleName) }
                    repeat(result.updated.size) { updated.inc(CA::class.java.simpleName) }
                    repeat(result.updatedNsId.size) { nsIdUpdated.inc(CA::class.java.simpleName) }
                    sendLog("Carbs", CA::class.java.simpleName)
                }

        SystemClock.sleep(pause)

        if (temporaryTargets.isNotEmpty())
            disposable += persistenceLayer.syncNsTemporaryTargets(temporaryTargets = temporaryTargets)
                .subscribeBy { result ->
                    temporaryTargets.clear()
                    repeat(result.inserted.size) { inserted.inc(TT::class.java.simpleName) }
                    repeat(result.invalidated.size) { invalidated.inc(TT::class.java.simpleName) }
                    repeat(result.ended.size) { ended.inc(TT::class.java.simpleName) }
                    repeat(result.updatedNsId.size) { nsIdUpdated.inc(TT::class.java.simpleName) }
                    repeat(result.updatedDuration.size) { durationUpdated.inc(TT::class.java.simpleName) }
                    sendLog("TemporaryTarget", TT::class.java.simpleName)
                }

        SystemClock.sleep(pause)

        if (temporaryBasals.isNotEmpty())
            disposable += persistenceLayer.syncNsTemporaryBasals(temporaryBasals)
                .subscribeBy { result ->
                    temporaryBasals.clear()
                    repeat(result.inserted.size) { inserted.inc(TB::class.java.simpleName) }
                    repeat(result.invalidated.size) { invalidated.inc(TB::class.java.simpleName) }
                    repeat(result.ended.size) { ended.inc(TB::class.java.simpleName) }
                    repeat(result.updatedNsId.size) { nsIdUpdated.inc(TB::class.java.simpleName) }
                    repeat(result.updatedDuration.size) { durationUpdated.inc(TB::class.java.simpleName) }
                    sendLog("TemporaryBasal", TB::class.java.simpleName)
                }

        SystemClock.sleep(pause)

        if (effectiveProfileSwitches.isNotEmpty())
            disposable += persistenceLayer.syncNsEffectiveProfileSwitches(effectiveProfileSwitches)
                .subscribeBy { result ->
                    effectiveProfileSwitches.clear()
                    repeat(result.inserted.size) { inserted.inc(EPS::class.java.simpleName) }
                    repeat(result.invalidated.size) { invalidated.inc(EPS::class.java.simpleName) }
                    repeat(result.updatedNsId.size) { nsIdUpdated.inc(EPS::class.java.simpleName) }
                    sendLog("EffectiveProfileSwitch", EPS::class.java.simpleName)
                }

        SystemClock.sleep(pause)

        if (profileSwitches.isNotEmpty())
            disposable += persistenceLayer.syncNsProfileSwitches(profileSwitches)
                .subscribeBy { result ->
                    profileSwitches.clear()
                    repeat(result.inserted.size) { inserted.inc(PS::class.java.simpleName) }
                    repeat(result.invalidated.size) { invalidated.inc(PS::class.java.simpleName) }
                    repeat(result.updatedNsId.size) { nsIdUpdated.inc(PS::class.java.simpleName) }
                    sendLog("ProfileSwitch", PS::class.java.simpleName)
                }

        SystemClock.sleep(pause)

        if (bolusCalculatorResults.isNotEmpty())
            disposable += persistenceLayer.syncNsBolusCalculatorResults(bolusCalculatorResults)
                .subscribeBy { result ->
                    bolusCalculatorResults.clear()
                    repeat(result.inserted.size) { inserted.inc(BCR::class.java.simpleName) }
                    repeat(result.invalidated.size) { invalidated.inc(BCR::class.java.simpleName) }
                    repeat(result.updatedNsId.size) { nsIdUpdated.inc(BCR::class.java.simpleName) }
                    sendLog("BolusCalculatorResult", BCR::class.java.simpleName)
                }

        SystemClock.sleep(pause)

        if (preferences.get(BooleanKey.NsClientAcceptTherapyEvent) || config.NSCLIENT)
            therapyEvents.filter { it.type == TE.Type.ANNOUNCEMENT }.forEach {
                if (it.timestamp > dateUtil.now() - 15 * 60 * 1000L &&
                    it.note?.isNotEmpty() == true &&
                    it.enteredBy != sp.getString("careportal_enteredby", "AndroidAPS")
                ) {
                    if (preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements))
                        uiInteraction.addNotificationValidFor(Notification.NS_ANNOUNCEMENT, it.note ?: "", Notification.ANNOUNCEMENT, 60)
                }
            }
        if (therapyEvents.isNotEmpty())
            disposable += persistenceLayer.syncNsTherapyEvents(therapyEvents)
                .subscribeBy { result ->
                    therapyEvents.clear()
                    repeat(result.inserted.size) { inserted.inc(TE::class.java.simpleName) }
                    repeat(result.invalidated.size) { invalidated.inc(TE::class.java.simpleName) }
                    repeat(result.updatedNsId.size) { nsIdUpdated.inc(TE::class.java.simpleName) }
                    repeat(result.updatedDuration.size) { durationUpdated.inc(TE::class.java.simpleName) }
                    sendLog("TherapyEvent", TE::class.java.simpleName)
                }

        SystemClock.sleep(pause)

        if (offlineEvents.isNotEmpty())
            disposable += persistenceLayer.syncNsOfflineEvents(offlineEvents)
                .subscribeBy { result ->
                    offlineEvents.clear()
                    repeat(result.inserted.size) { inserted.inc(OE::class.java.simpleName) }
                    repeat(result.invalidated.size) { invalidated.inc(OE::class.java.simpleName) }
                    repeat(result.ended.size) { ended.inc(OE::class.java.simpleName) }
                    repeat(result.updatedNsId.size) { nsIdUpdated.inc(OE::class.java.simpleName) }
                    repeat(result.updatedDuration.size) { durationUpdated.inc(OE::class.java.simpleName) }
                    sendLog("OfflineEvent", OE::class.java.simpleName)
                }

        SystemClock.sleep(pause)

        if (extendedBoluses.isNotEmpty())
            disposable += persistenceLayer.syncNsExtendedBoluses(extendedBoluses)
                .subscribeBy { result ->
                    extendedBoluses.clear()
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
            if (preferences.get(BooleanKey.NsClientAcceptInsulin) || config.NSCLIENT)
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
            if (preferences.get(BooleanKey.NsClientAcceptCarbs) || config.NSCLIENT)
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
            if (preferences.get(BooleanKey.NsClientAcceptTempTarget) || config.NSCLIENT)
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
            if (preferences.get(BooleanKey.NsClientAcceptTbrEb) || config.NSCLIENT)
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
            if (preferences.get(BooleanKey.NsClientAcceptProfileSwitch) || config.NSCLIENT)
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
            if (preferences.get(BooleanKey.NsClientAcceptProfileSwitch) || config.NSCLIENT)
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
            if (preferences.get(BooleanKey.NsClientAcceptTherapyEvent) || config.NSCLIENT)
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
            if (preferences.get(BooleanKey.NsClientAcceptOfflineEvent) && config.isEngineeringMode() || config.NSCLIENT)
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
            if (preferences.get(BooleanKey.NsClientAcceptTbrEb) || config.NSCLIENT)
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