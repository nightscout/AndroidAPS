package app.aaps.plugins.sync.nsShared

import android.os.SystemClock
import app.aaps.core.data.db.BS
import app.aaps.core.data.db.CA
import app.aaps.core.data.db.EB
import app.aaps.core.data.db.GV
import app.aaps.core.data.db.OE
import app.aaps.core.data.db.TB
import app.aaps.core.data.db.TE
import app.aaps.core.data.db.TT
import app.aaps.core.data.db.UE
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
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.DeviceStatus
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.Food
import app.aaps.database.entities.ProfileSwitch
import app.aaps.database.impl.AppRepository
import app.aaps.database.impl.transactions.InvalidateBolusCalculatorResultTransaction
import app.aaps.database.impl.transactions.InvalidateBolusTransaction
import app.aaps.database.impl.transactions.InvalidateCarbsTransaction
import app.aaps.database.impl.transactions.InvalidateEffectiveProfileSwitchTransaction
import app.aaps.database.impl.transactions.InvalidateExtendedBolusTransaction
import app.aaps.database.impl.transactions.InvalidateOfflineEventTransaction
import app.aaps.database.impl.transactions.InvalidateProfileSwitchTransaction
import app.aaps.database.impl.transactions.InvalidateTemporaryBasalTransaction
import app.aaps.database.impl.transactions.InvalidateTemporaryTargetTransaction
import app.aaps.database.impl.transactions.InvalidateTherapyEventTransaction
import app.aaps.database.impl.transactions.SyncNsBolusCalculatorResultTransaction
import app.aaps.database.impl.transactions.SyncNsEffectiveProfileSwitchTransaction
import app.aaps.database.impl.transactions.SyncNsFoodTransaction
import app.aaps.database.impl.transactions.SyncNsProfileSwitchTransaction
import app.aaps.database.impl.transactions.UpdateNsIdBolusCalculatorResultTransaction
import app.aaps.database.impl.transactions.UpdateNsIdDeviceStatusTransaction
import app.aaps.database.impl.transactions.UpdateNsIdEffectiveProfileSwitchTransaction
import app.aaps.database.impl.transactions.UpdateNsIdFoodTransaction
import app.aaps.database.impl.transactions.UpdateNsIdProfileSwitchTransaction
import app.aaps.plugins.sync.R
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
    private val repository: AppRepository,
    private val persistenceLayer: PersistenceLayer,
    private val sp: SP,
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
    override val effectiveProfileSwitches: MutableList<EffectiveProfileSwitch> = mutableListOf()
    override val bolusCalculatorResults: MutableList<BolusCalculatorResult> = mutableListOf()
    override val therapyEvents: MutableList<TE> = mutableListOf()
    override val extendedBoluses: MutableList<EB> = mutableListOf()
    override val temporaryBasals: MutableList<TB> = mutableListOf()
    override val profileSwitches: MutableList<ProfileSwitch> = mutableListOf()
    override val offlineEvents: MutableList<OE> = mutableListOf()
    override val foods: MutableList<Food> = mutableListOf()

    override val nsIdGlucoseValues: MutableList<GV> = mutableListOf()
    override val nsIdBoluses: MutableList<BS> = mutableListOf()
    override val nsIdCarbs: MutableList<CA> = mutableListOf()
    override val nsIdTemporaryTargets: MutableList<TT> = mutableListOf()
    override val nsIdEffectiveProfileSwitches: MutableList<EffectiveProfileSwitch> = mutableListOf()
    override val nsIdBolusCalculatorResults: MutableList<BolusCalculatorResult> = mutableListOf()
    override val nsIdTherapyEvents: MutableList<TE> = mutableListOf()
    override val nsIdExtendedBoluses: MutableList<EB> = mutableListOf()
    override val nsIdTemporaryBasals: MutableList<TB> = mutableListOf()
    override val nsIdProfileSwitches: MutableList<ProfileSwitch> = mutableListOf()
    override val nsIdOfflineEvents: MutableList<OE> = mutableListOf()
    override val nsIdDeviceStatuses: MutableList<DeviceStatus> = mutableListOf()
    override val nsIdFoods: MutableList<Food> = mutableListOf()

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
        if (containsKey(key)) merge(key, 1, Long::plus)
        else put(key, 1)

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
                }

        sendLog("GlucoseValue", GV::class.java.simpleName)
        SystemClock.sleep(pause)
        rxBus.send(EventNSClientNewLog("● DONE PROCESSING BG", ""))
    }

    override fun storeFoodsToDb() {
        if (foods.isNotEmpty())
            repository.runTransactionForResult(SyncNsFoodTransaction(foods))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving foods", it)
                }
                .blockingGet()
                .also { result ->
                    foods.clear()
                    result.updated.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated food $it")
                        updated.inc(Food::class.java.simpleName)
                    }
                    result.inserted.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Inserted food $it")
                        inserted.inc(Food::class.java.simpleName)
                    }
                    result.invalidated.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Invalidated food $it")
                        nsIdUpdated.inc(Food::class.java.simpleName)
                    }
                }

        sendLog("Food", Food::class.java.simpleName)
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
            repository.runTransactionForResult(SyncNsEffectiveProfileSwitchTransaction(effectiveProfileSwitches))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving EffectiveProfileSwitch", it)
                }
                .blockingGet()
                .also { result ->
                    effectiveProfileSwitches.clear()
                    result.inserted.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UE(
                                timestamp = dateUtil.now(),
                                action = Action.PROFILE_SWITCH,
                                source = Sources.NSClient,
                                note = "",
                                values = listOf(ValueWithUnit.Timestamp(it.timestamp))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted EffectiveProfileSwitch $it")
                        inserted.inc(EffectiveProfileSwitch::class.java.simpleName)
                    }
                    result.invalidated.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UE(
                                timestamp = dateUtil.now(),
                                action = Action.PROFILE_SWITCH_REMOVED,
                                source = Sources.NSClient,
                                note = "",
                                values = listOf(ValueWithUnit.Timestamp(it.timestamp))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated EffectiveProfileSwitch $it")
                        invalidated.inc(EffectiveProfileSwitch::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId EffectiveProfileSwitch $it")
                        nsIdUpdated.inc(EffectiveProfileSwitch::class.java.simpleName)
                    }
                }

        sendLog("EffectiveProfileSwitch", EffectiveProfileSwitch::class.java.simpleName)
        SystemClock.sleep(pause)

        if (profileSwitches.isNotEmpty())
            repository.runTransactionForResult(SyncNsProfileSwitchTransaction(profileSwitches))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving ProfileSwitch", it)
                }
                .blockingGet()
                .also { result ->
                    profileSwitches.clear()
                    result.inserted.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UE(
                                timestamp = dateUtil.now(),
                                action = Action.PROFILE_SWITCH,
                                source = Sources.NSClient,
                                note = "",
                                values = listOf(ValueWithUnit.Timestamp(it.timestamp))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted ProfileSwitch $it")
                        inserted.inc(ProfileSwitch::class.java.simpleName)
                    }
                    result.invalidated.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UE(
                                timestamp = dateUtil.now(),
                                action = Action.PROFILE_SWITCH_REMOVED,
                                source = Sources.NSClient,
                                note = "",
                                values = listOf(ValueWithUnit.Timestamp(it.timestamp))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated ProfileSwitch $it")
                        invalidated.inc(ProfileSwitch::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId ProfileSwitch $it")
                        nsIdUpdated.inc(ProfileSwitch::class.java.simpleName)
                    }
                }

        sendLog("ProfileSwitch", ProfileSwitch::class.java.simpleName)
        SystemClock.sleep(pause)

        if (bolusCalculatorResults.isNotEmpty())
            repository.runTransactionForResult(SyncNsBolusCalculatorResultTransaction(bolusCalculatorResults))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving BolusCalculatorResult", it)
                }
                .blockingGet()
                .also { result ->
                    bolusCalculatorResults.clear()
                    result.inserted.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Inserted BolusCalculatorResult $it")
                        inserted.inc(BolusCalculatorResult::class.java.simpleName)
                    }
                    result.invalidated.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Invalidated BolusCalculatorResult $it")
                        invalidated.inc(BolusCalculatorResult::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId BolusCalculatorResult $it")
                        nsIdUpdated.inc(BolusCalculatorResult::class.java.simpleName)
                    }
                }

        sendLog("BolusCalculatorResult", BolusCalculatorResult::class.java.simpleName)
        SystemClock.sleep(pause)

        if (sp.getBoolean(app.aaps.core.utils.R.string.key_ns_receive_therapy_events, false) || config.NSCLIENT)
            therapyEvents.filter { it.type == TE.Type.ANNOUNCEMENT }.forEach {
                if (it.timestamp > dateUtil.now() - 15 * 60 * 1000L &&
                    it.note?.isNotEmpty() == true &&
                    it.enteredBy != sp.getString("careportal_enteredby", "AndroidAPS")
                ) {
                    if (sp.getBoolean(app.aaps.core.utils.R.string.key_ns_announcements, config.NSCLIENT))
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

        repository.runTransactionForResult(UpdateNsIdFoodTransaction(nsIdFoods))
            .doOnError { error ->
                aapsLogger.error(LTag.DATABASE, "Updated nsId of Food failed", error)
            }
            .blockingGet()
            .also { result ->
                nsIdFoods.clear()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of Food $it")
                    nsIdUpdated.inc(Food::class.java.simpleName)
                }
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

        repository.runTransactionForResult(UpdateNsIdBolusCalculatorResultTransaction(nsIdBolusCalculatorResults))
            .doOnError { error ->
                aapsLogger.error(LTag.DATABASE, "Updated nsId of BolusCalculatorResult failed", error)
            }
            .blockingGet()
            .also { result ->
                nsIdBolusCalculatorResults.clear()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of BolusCalculatorResult $it")
                    nsIdUpdated.inc(BolusCalculatorResult::class.java.simpleName)
                }
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

        repository.runTransactionForResult(UpdateNsIdProfileSwitchTransaction(nsIdProfileSwitches))
            .doOnError { error ->
                aapsLogger.error(LTag.DATABASE, "Updated nsId of ProfileSwitch failed", error)
            }
            .blockingGet()
            .also { result ->
                nsIdProfileSwitches.clear()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of ProfileSwitch $it")
                    nsIdUpdated.inc(ProfileSwitch::class.java.simpleName)
                }
            }

        repository.runTransactionForResult(UpdateNsIdEffectiveProfileSwitchTransaction(nsIdEffectiveProfileSwitches))
            .doOnError { error ->
                aapsLogger.error(LTag.DATABASE, "Updated nsId of EffectiveProfileSwitch failed", error)
            }
            .blockingGet()
            .also { result ->
                nsIdEffectiveProfileSwitches.clear()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of EffectiveProfileSwitch $it")
                    nsIdUpdated.inc(EffectiveProfileSwitch::class.java.simpleName)
                }
            }

        repository.runTransactionForResult(UpdateNsIdDeviceStatusTransaction(nsIdDeviceStatuses))
            .doOnError { error ->
                aapsLogger.error(LTag.DATABASE, "Updated nsId of DeviceStatus failed", error)
            }
            .blockingGet()
            .also { result ->
                nsIdDeviceStatuses.clear()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of DeviceStatus $it")
                    nsIdUpdated.inc(DeviceStatus::class.java.simpleName)
                }
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
        sendLog("EffectiveProfileSwitch", EffectiveProfileSwitch::class.java.simpleName)
        sendLog("ProfileSwitch", ProfileSwitch::class.java.simpleName)
        sendLog("BolusCalculatorResult", BolusCalculatorResult::class.java.simpleName)
        sendLog("TherapyEvent", TE::class.java.simpleName)
        sendLog("OfflineEvent", OE::class.java.simpleName)
        sendLog("EB", EB::class.java.simpleName)
        rxBus.send(EventNSClientNewLog("● DONE NSIDs", ""))
    }

    override fun updateDeletedTreatmentsInDb() {
        deleteTreatment.forEach { id ->
            if (sp.getBoolean(app.aaps.core.utils.R.string.key_ns_receive_insulin, false) || config.NSCLIENT)
                persistenceLayer.getBolusByNSId(id)?.let { bolus ->
                    repository.runTransactionForResult(InvalidateBolusTransaction(bolus.id))
                        .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating Bolus", it) }
                        .blockingGet()
                        .also { result ->
                            result.invalidated.forEach {
                                aapsLogger.debug(LTag.DATABASE, "Invalidated Bolus $it")
                                invalidated.inc(BS::class.java.simpleName)
                            }
                        }
                }
            if (sp.getBoolean(app.aaps.core.utils.R.string.key_ns_receive_carbs, false) || config.NSCLIENT)
                repository.getCarbsByNSId(id)?.let { carb ->
                    repository.runTransactionForResult(InvalidateCarbsTransaction(carb.id))
                        .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating Carbs", it) }
                        .blockingGet()
                        .also { result ->
                            result.invalidated.forEach {
                                aapsLogger.debug(LTag.DATABASE, "Invalidated Carbs $it")
                                invalidated.inc(CA::class.java.simpleName)
                            }
                        }
                }
            if (sp.getBoolean(app.aaps.core.utils.R.string.key_ns_receive_temp_target, false) || config.NSCLIENT)
                repository.findTemporaryTargetByNSId(id)?.let { gv ->
                    repository.runTransactionForResult(InvalidateTemporaryTargetTransaction(gv.id))
                        .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating TemporaryTarget", it) }
                        .blockingGet()
                        .also { result ->
                            result.invalidated.forEach {
                                aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryTarget $it")
                                invalidated.inc(TT::class.java.simpleName)
                            }
                        }
                }
            if (config.isEngineeringMode() && sp.getBoolean(R.string.key_ns_receive_tbr_eb, false) || config.NSCLIENT)
                repository.findTemporaryBasalByNSId(id)?.let { gv ->
                    repository.runTransactionForResult(InvalidateTemporaryBasalTransaction(gv.id))
                        .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating TemporaryBasal", it) }
                        .blockingGet()
                        .also { result ->
                            result.invalidated.forEach {
                                aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryBasal $it")
                                invalidated.inc(TB::class.java.simpleName)
                            }
                        }
                }
            if (sp.getBoolean(app.aaps.core.utils.R.string.key_ns_receive_profile_switch, false) || config.NSCLIENT)
                repository.findEffectiveProfileSwitchByNSId(id)?.let { gv ->
                    repository.runTransactionForResult(InvalidateEffectiveProfileSwitchTransaction(gv.id))
                        .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating EffectiveProfileSwitch", it) }
                        .blockingGet()
                        .also { result ->
                            result.invalidated.forEach {
                                aapsLogger.debug(LTag.DATABASE, "Invalidated EffectiveProfileSwitch $it")
                                invalidated.inc(EffectiveProfileSwitch::class.java.simpleName)
                            }
                        }
                }
            if (sp.getBoolean(app.aaps.core.utils.R.string.key_ns_receive_profile_switch, false) || config.NSCLIENT)
                repository.findProfileSwitchByNSId(id)?.let { gv ->
                    repository.runTransactionForResult(InvalidateProfileSwitchTransaction(gv.id))
                        .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating ProfileSwitch", it) }
                        .blockingGet()
                        .also { result ->
                            result.invalidated.forEach {
                                aapsLogger.debug(LTag.DATABASE, "Invalidated ProfileSwitch $it")
                                invalidated.inc(ProfileSwitch::class.java.simpleName)
                            }
                        }
                }
            repository.findBolusCalculatorResultByNSId(id)?.let { gv ->
                repository.runTransactionForResult(InvalidateBolusCalculatorResultTransaction(gv.id))
                    .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating BolusCalculatorResult", it) }
                    .blockingGet()
                    .also { result ->
                        result.invalidated.forEach {
                            aapsLogger.debug(LTag.DATABASE, "Invalidated BolusCalculatorResult $it")
                            invalidated.inc(BolusCalculatorResult::class.java.simpleName)
                        }
                    }
            }
            if (sp.getBoolean(app.aaps.core.utils.R.string.key_ns_receive_therapy_events, false) || config.NSCLIENT)
                repository.findTherapyEventByNSId(id)?.let { gv ->
                    repository.runTransactionForResult(InvalidateTherapyEventTransaction(gv.id))
                        .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating TherapyEvent", it) }
                        .blockingGet()
                        .also { result ->
                            result.invalidated.forEach {
                                aapsLogger.debug(LTag.DATABASE, "Invalidated TherapyEvent $it")
                                invalidated.inc(TE::class.java.simpleName)
                            }
                        }
                }
            if (sp.getBoolean(app.aaps.core.utils.R.string.key_ns_receive_offline_event, false) && config.isEngineeringMode() || config.NSCLIENT)
                repository.findOfflineEventByNSId(id)?.let { gv ->
                    repository.runTransactionForResult(InvalidateOfflineEventTransaction(gv.id))
                        .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating OfflineEvent", it) }
                        .blockingGet()
                        .also { result ->
                            result.invalidated.forEach {
                                aapsLogger.debug(LTag.DATABASE, "Invalidated OfflineEvent $it")
                                invalidated.inc(OE::class.java.simpleName)
                            }
                        }
                }
            if (config.isEngineeringMode() && sp.getBoolean(R.string.key_ns_receive_tbr_eb, false) || config.NSCLIENT)
                repository.findExtendedBolusByNSId(id)?.let { gv ->
                    repository.runTransactionForResult(InvalidateExtendedBolusTransaction(gv.id))
                        .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating EB", it) }
                        .blockingGet()
                        .also { result ->
                            result.invalidated.forEach {
                                aapsLogger.debug(LTag.DATABASE, "Invalidated EB $it")
                                invalidated.inc(EB::class.java.simpleName)
                            }
                        }
                }
        }
        sendLog("Bolus", BS::class.java.simpleName)
        sendLog("Carbs", CA::class.java.simpleName)
        sendLog("TemporaryTarget", TT::class.java.simpleName)
        sendLog("TemporaryBasal", TB::class.java.simpleName)
        sendLog("EffectiveProfileSwitch", EffectiveProfileSwitch::class.java.simpleName)
        sendLog("ProfileSwitch", ProfileSwitch::class.java.simpleName)
        sendLog("BolusCalculatorResult", BolusCalculatorResult::class.java.simpleName)
        sendLog("TherapyEvent", TE::class.java.simpleName)
        sendLog("OfflineEvent", OE::class.java.simpleName)
        sendLog("EB", EB::class.java.simpleName)
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