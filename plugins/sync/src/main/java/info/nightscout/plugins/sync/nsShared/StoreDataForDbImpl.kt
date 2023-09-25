package info.nightscout.plugins.sync.nsShared

import android.os.SystemClock
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.GlucoseUnit
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
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.DeviceStatus
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.Food
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.OfflineEvent
import app.aaps.database.entities.ProfileSwitch
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.ValueWithUnit
import app.aaps.database.transactions.TransactionGlucoseValue
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.CgmSourceTransaction
import info.nightscout.database.impl.transactions.InvalidateBolusCalculatorResultTransaction
import info.nightscout.database.impl.transactions.InvalidateBolusTransaction
import info.nightscout.database.impl.transactions.InvalidateCarbsTransaction
import info.nightscout.database.impl.transactions.InvalidateEffectiveProfileSwitchTransaction
import info.nightscout.database.impl.transactions.InvalidateExtendedBolusTransaction
import info.nightscout.database.impl.transactions.InvalidateGlucoseValueTransaction
import info.nightscout.database.impl.transactions.InvalidateOfflineEventTransaction
import info.nightscout.database.impl.transactions.InvalidateProfileSwitchTransaction
import info.nightscout.database.impl.transactions.InvalidateTemporaryBasalTransaction
import info.nightscout.database.impl.transactions.InvalidateTemporaryTargetTransaction
import info.nightscout.database.impl.transactions.InvalidateTherapyEventTransaction
import info.nightscout.database.impl.transactions.SyncNsBolusCalculatorResultTransaction
import info.nightscout.database.impl.transactions.SyncNsBolusTransaction
import info.nightscout.database.impl.transactions.SyncNsCarbsTransaction
import info.nightscout.database.impl.transactions.SyncNsEffectiveProfileSwitchTransaction
import info.nightscout.database.impl.transactions.SyncNsExtendedBolusTransaction
import info.nightscout.database.impl.transactions.SyncNsFoodTransaction
import info.nightscout.database.impl.transactions.SyncNsOfflineEventTransaction
import info.nightscout.database.impl.transactions.SyncNsProfileSwitchTransaction
import info.nightscout.database.impl.transactions.SyncNsTemporaryBasalTransaction
import info.nightscout.database.impl.transactions.SyncNsTemporaryTargetTransaction
import info.nightscout.database.impl.transactions.SyncNsTherapyEventTransaction
import info.nightscout.database.impl.transactions.UpdateNsIdBolusCalculatorResultTransaction
import info.nightscout.database.impl.transactions.UpdateNsIdBolusTransaction
import info.nightscout.database.impl.transactions.UpdateNsIdCarbsTransaction
import info.nightscout.database.impl.transactions.UpdateNsIdDeviceStatusTransaction
import info.nightscout.database.impl.transactions.UpdateNsIdEffectiveProfileSwitchTransaction
import info.nightscout.database.impl.transactions.UpdateNsIdExtendedBolusTransaction
import info.nightscout.database.impl.transactions.UpdateNsIdFoodTransaction
import info.nightscout.database.impl.transactions.UpdateNsIdGlucoseValueTransaction
import info.nightscout.database.impl.transactions.UpdateNsIdOfflineEventTransaction
import info.nightscout.database.impl.transactions.UpdateNsIdProfileSwitchTransaction
import info.nightscout.database.impl.transactions.UpdateNsIdTemporaryBasalTransaction
import info.nightscout.database.impl.transactions.UpdateNsIdTemporaryTargetTransaction
import info.nightscout.database.impl.transactions.UpdateNsIdTherapyEventTransaction
import info.nightscout.plugins.sync.R
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
    private val sp: SP,
    private val uel: UserEntryLogger,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val nsClientSource: NSClientSource,
    private val virtualPump: VirtualPump,
    private val uiInteraction: UiInteraction
) : StoreDataForDb {

    override val glucoseValues: MutableList<TransactionGlucoseValue> = mutableListOf()
    override val boluses: MutableList<Bolus> = mutableListOf()
    override val carbs: MutableList<Carbs> = mutableListOf()
    override val temporaryTargets: MutableList<TemporaryTarget> = mutableListOf()
    override val effectiveProfileSwitches: MutableList<EffectiveProfileSwitch> = mutableListOf()
    override val bolusCalculatorResults: MutableList<BolusCalculatorResult> = mutableListOf()
    override val therapyEvents: MutableList<TherapyEvent> = mutableListOf()
    override val extendedBoluses: MutableList<ExtendedBolus> = mutableListOf()
    override val temporaryBasals: MutableList<TemporaryBasal> = mutableListOf()
    override val profileSwitches: MutableList<ProfileSwitch> = mutableListOf()
    override val offlineEvents: MutableList<OfflineEvent> = mutableListOf()
    override val foods: MutableList<Food> = mutableListOf()

    override val nsIdGlucoseValues: MutableList<GlucoseValue> = mutableListOf()
    override val nsIdBoluses: MutableList<Bolus> = mutableListOf()
    override val nsIdCarbs: MutableList<Carbs> = mutableListOf()
    override val nsIdTemporaryTargets: MutableList<TemporaryTarget> = mutableListOf()
    override val nsIdEffectiveProfileSwitches: MutableList<EffectiveProfileSwitch> = mutableListOf()
    override val nsIdBolusCalculatorResults: MutableList<BolusCalculatorResult> = mutableListOf()
    override val nsIdTherapyEvents: MutableList<TherapyEvent> = mutableListOf()
    override val nsIdExtendedBoluses: MutableList<ExtendedBolus> = mutableListOf()
    override val nsIdTemporaryBasals: MutableList<TemporaryBasal> = mutableListOf()
    override val nsIdProfileSwitches: MutableList<ProfileSwitch> = mutableListOf()
    override val nsIdOfflineEvents: MutableList<OfflineEvent> = mutableListOf()
    override val nsIdDeviceStatuses: MutableList<DeviceStatus> = mutableListOf()
    override val nsIdFoods: MutableList<Food> = mutableListOf()

    override val deleteTreatment: MutableList<String> = mutableListOf()
    override val deleteGlucoseValue: MutableList<String> = mutableListOf()
    private val userEntries: MutableList<UserEntry> = mutableListOf()

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

    override fun storeGlucoseValuesToDb() {
        if (glucoseValues.isNotEmpty())
            repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving values from NSClient App", it)
                }
                .blockingGet()
                .also { result ->
                    glucoseValues.clear()
                    result.updated.forEach {
                        nsClientSource.detectSource(it)
                        aapsLogger.debug(LTag.DATABASE, "Updated bg $it")
                        updated.inc(GlucoseValue::class.java.simpleName)
                    }
                    result.inserted.forEach {
                        nsClientSource.detectSource(it)
                        aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                        inserted.inc(GlucoseValue::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        nsClientSource.detectSource(it)
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId bg $it")
                        nsIdUpdated.inc(GlucoseValue::class.java.simpleName)
                    }
                }

        sendLog("GlucoseValue", GlucoseValue::class.java.simpleName)
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
            repository.runTransactionForResult(SyncNsBolusTransaction(boluses))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving bolus", it)
                }
                .blockingGet()
                .also { result ->
                    boluses.clear()
                    result.inserted.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.BOLUS,
                                source = UserEntry.Sources.NSClient,
                                note = it.notes ?: "",
                                values = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Insulin(it.amount))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted bolus $it")
                        inserted.inc(Bolus::class.java.simpleName)
                    }
                    result.invalidated.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.BOLUS_REMOVED,
                                source = UserEntry.Sources.NSClient,
                                note = "",
                                values = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Insulin(it.amount))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated bolus $it")
                        invalidated.inc(Bolus::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId of bolus $it")
                        nsIdUpdated.inc(Bolus::class.java.simpleName)
                    }
                    result.updated.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated amount of bolus $it")
                        updated.inc(Bolus::class.java.simpleName)
                    }
                }

        sendLog("Bolus", Bolus::class.java.simpleName)
        SystemClock.sleep(pause)

        if (carbs.isNotEmpty())
            repository.runTransactionForResult(SyncNsCarbsTransaction(carbs, config.NSCLIENT))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving carbs", it)
                }
                .blockingGet()
                .also { result ->
                    carbs.clear()
                    result.inserted.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.CARBS,
                                source = UserEntry.Sources.NSClient,
                                note = it.notes ?: "",
                                values = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Gram(it.amount.toInt()))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted carbs $it")
                        inserted.inc(Carbs::class.java.simpleName)
                    }
                    result.invalidated.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.CARBS_REMOVED,
                                source = UserEntry.Sources.NSClient,
                                note = "",
                                values = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Gram(it.amount.toInt()))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated carbs $it")
                        invalidated.inc(Carbs::class.java.simpleName)
                    }
                    result.updated.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.CARBS,
                                source = UserEntry.Sources.NSClient,
                                note = it.notes ?: "",
                                values = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Gram(it.amount.toInt()))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Updated carbs $it")
                        updated.inc(Carbs::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId carbs $it")
                        nsIdUpdated.inc(Carbs::class.java.simpleName)
                    }

                }

        sendLog("Carbs", Carbs::class.java.simpleName)
        SystemClock.sleep(pause)

        if (temporaryTargets.isNotEmpty())
            repository.runTransactionForResult(SyncNsTemporaryTargetTransaction(temporaryTargets))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
                }
                .blockingGet()
                .also { result ->
                    temporaryTargets.clear()
                    result.inserted.forEach { tt ->
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.TT,
                                source = UserEntry.Sources.NSClient,
                                note = "",
                                values = listOf(
                                    ValueWithUnit.TherapyEventTTReason(tt.reason),
                                    ValueWithUnit.fromGlucoseUnit(tt.lowTarget, GlucoseUnit.MGDL.asText),
                                    ValueWithUnit.fromGlucoseUnit(tt.highTarget, GlucoseUnit.MGDL.asText).takeIf { tt.lowTarget != tt.highTarget },
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tt.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted TemporaryTarget $tt")
                        inserted.inc(TemporaryTarget::class.java.simpleName)
                    }
                    result.invalidated.forEach { tt ->
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.TT_REMOVED,
                                source = UserEntry.Sources.NSClient,
                                note = "",
                                values = listOf(
                                    ValueWithUnit.TherapyEventTTReason(tt.reason),
                                    ValueWithUnit.Mgdl(tt.lowTarget),
                                    ValueWithUnit.Mgdl(tt.highTarget).takeIf { tt.lowTarget != tt.highTarget },
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tt.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryTarget $tt")
                        invalidated.inc(TemporaryTarget::class.java.simpleName)
                    }
                    result.ended.forEach { tt ->
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.CANCEL_TT,
                                source = UserEntry.Sources.NSClient,
                                note = "",
                                values = listOf(
                                    ValueWithUnit.TherapyEventTTReason(tt.reason),
                                    ValueWithUnit.Mgdl(tt.lowTarget),
                                    ValueWithUnit.Mgdl(tt.highTarget).takeIf { tt.lowTarget != tt.highTarget },
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tt.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Updated TemporaryTarget $tt")
                        ended.inc(TemporaryTarget::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId TemporaryTarget $it")
                        nsIdUpdated.inc(TemporaryTarget::class.java.simpleName)
                    }
                    result.updatedDuration.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated duration TemporaryTarget $it")
                        durationUpdated.inc(TemporaryTarget::class.java.simpleName)
                    }
                }

        sendLog("TemporaryTarget", TemporaryTarget::class.java.simpleName)
        SystemClock.sleep(pause)

        if (temporaryBasals.isNotEmpty())
            repository.runTransactionForResult(SyncNsTemporaryBasalTransaction(temporaryBasals, config.NSCLIENT))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving temporary basal", it)
                }
                .blockingGet()
                .also { result ->
                    temporaryBasals.clear()
                    result.inserted.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.TEMP_BASAL,
                                source = UserEntry.Sources.NSClient,
                                note = "",
                                values = listOf(
                                    ValueWithUnit.Timestamp(it.timestamp),
                                    if (it.isAbsolute) ValueWithUnit.UnitPerHour(it.rate) else ValueWithUnit.Percent(it.rate.toInt()),
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted TemporaryBasal $it")
                        inserted.inc(TemporaryBasal::class.java.simpleName)
                    }
                    result.invalidated.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.TEMP_BASAL_REMOVED,
                                source = UserEntry.Sources.NSClient,
                                note = "",
                                values = listOf(
                                    ValueWithUnit.Timestamp(it.timestamp),
                                    if (it.isAbsolute) ValueWithUnit.UnitPerHour(it.rate) else ValueWithUnit.Percent(it.rate.toInt()),
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryBasal $it")
                        invalidated.inc(TemporaryBasal::class.java.simpleName)
                    }
                    result.ended.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.CANCEL_TEMP_BASAL,
                                source = UserEntry.Sources.NSClient,
                                note = "",
                                values = listOf(
                                    ValueWithUnit.Timestamp(it.timestamp),
                                    if (it.isAbsolute) ValueWithUnit.UnitPerHour(it.rate) else ValueWithUnit.Percent(it.rate.toInt()),
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Ended TemporaryBasal $it")
                        ended.inc(TemporaryBasal::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId TemporaryBasal $it")
                        nsIdUpdated.inc(TemporaryBasal::class.java.simpleName)
                    }
                    result.updatedDuration.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated duration TemporaryBasal $it")
                        durationUpdated.inc(TemporaryBasal::class.java.simpleName)
                    }
                }

        sendLog("TemporaryBasal", TemporaryBasal::class.java.simpleName)
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
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.PROFILE_SWITCH,
                                source = UserEntry.Sources.NSClient,
                                note = "",
                                values = listOf(ValueWithUnit.Timestamp(it.timestamp))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted EffectiveProfileSwitch $it")
                        inserted.inc(EffectiveProfileSwitch::class.java.simpleName)
                    }
                    result.invalidated.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.PROFILE_SWITCH_REMOVED,
                                source = UserEntry.Sources.NSClient,
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
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.PROFILE_SWITCH,
                                source = UserEntry.Sources.NSClient,
                                note = "",
                                values = listOf(ValueWithUnit.Timestamp(it.timestamp))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted ProfileSwitch $it")
                        inserted.inc(ProfileSwitch::class.java.simpleName)
                    }
                    result.invalidated.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.PROFILE_SWITCH_REMOVED,
                                source = UserEntry.Sources.NSClient,
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

        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_therapy_events, false) || config.NSCLIENT)
            therapyEvents.filter { it.type == TherapyEvent.Type.ANNOUNCEMENT }.forEach {
                if (it.timestamp > dateUtil.now() - 15 * 60 * 1000L &&
                    it.note?.isNotEmpty() == true &&
                    it.enteredBy != sp.getString("careportal_enteredby", "AndroidAPS")
                ) {
                    if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_announcements, config.NSCLIENT))
                        uiInteraction.addNotificationValidFor(Notification.NS_ANNOUNCEMENT, it.note ?: "", Notification.ANNOUNCEMENT, 60)
                }
            }
        if (therapyEvents.isNotEmpty())
            repository.runTransactionForResult(SyncNsTherapyEventTransaction(therapyEvents, config.NSCLIENT))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving therapy event", it)
                }
                .blockingGet()
                .also { result ->
                    therapyEvents.clear()
                    result.inserted.forEach { therapyEvent ->
                        val action = when (therapyEvent.type) {
                            TherapyEvent.Type.CANNULA_CHANGE -> UserEntry.Action.SITE_CHANGE
                            TherapyEvent.Type.INSULIN_CHANGE -> UserEntry.Action.RESERVOIR_CHANGE
                            else                             -> UserEntry.Action.CAREPORTAL
                        }
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = action,
                                source = UserEntry.Sources.NSClient,
                                note = therapyEvent.note ?: "",
                                values = listOf(
                                    ValueWithUnit.Timestamp(therapyEvent.timestamp),
                                    ValueWithUnit.TherapyEventType(therapyEvent.type),
                                    ValueWithUnit.fromGlucoseUnit(therapyEvent.glucose ?: 0.0, therapyEvent.glucoseUnit.toString).takeIf { therapyEvent.glucose != null })
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted TherapyEvent $therapyEvent")
                        inserted.inc(TherapyEvent::class.java.simpleName)
                    }
                    result.invalidated.forEach { therapyEvent ->
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.CAREPORTAL_REMOVED,
                                source = UserEntry.Sources.NSClient,
                                note = therapyEvent.note ?: "",
                                values = listOf(
                                    ValueWithUnit.Timestamp(therapyEvent.timestamp),
                                    ValueWithUnit.TherapyEventType(therapyEvent.type),
                                    ValueWithUnit.fromGlucoseUnit(therapyEvent.glucose ?: 0.0, therapyEvent.glucoseUnit.toString).takeIf { therapyEvent.glucose != null })
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated TherapyEvent $therapyEvent")
                        invalidated.inc(TherapyEvent::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId TherapyEvent $it")
                        nsIdUpdated.inc(TherapyEvent::class.java.simpleName)
                    }
                    result.updatedDuration.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId TherapyEvent $it")
                        durationUpdated.inc(TherapyEvent::class.java.simpleName)
                    }
                }

        sendLog("TherapyEvent", TherapyEvent::class.java.simpleName)
        SystemClock.sleep(pause)

        if (offlineEvents.isNotEmpty())
            repository.runTransactionForResult(SyncNsOfflineEventTransaction(offlineEvents, config.NSCLIENT))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving OfflineEvent", it)
                }
                .blockingGet()
                .also { result ->
                    offlineEvents.clear()
                    result.inserted.forEach { oe ->
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.LOOP_CHANGE,
                                source = UserEntry.Sources.NSClient,
                                note = "",
                                values = listOf(
                                    ValueWithUnit.OfflineEventReason(oe.reason),
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(oe.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted OfflineEvent $oe")
                        inserted.inc(OfflineEvent::class.java.simpleName)
                    }
                    result.invalidated.forEach { oe ->
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.LOOP_REMOVED,
                                source = UserEntry.Sources.NSClient,
                                note = "",
                                values = listOf(
                                    ValueWithUnit.OfflineEventReason(oe.reason),
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(oe.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated OfflineEvent $oe")
                        invalidated.inc(OfflineEvent::class.java.simpleName)
                    }
                    result.ended.forEach { oe ->
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.LOOP_CHANGE,
                                source = UserEntry.Sources.NSClient,
                                note = "",
                                values = listOf(
                                    ValueWithUnit.OfflineEventReason(oe.reason),
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(oe.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Updated OfflineEvent $oe")
                        ended.inc(OfflineEvent::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId OfflineEvent $it")
                        nsIdUpdated.inc(OfflineEvent::class.java.simpleName)
                    }
                    result.updatedDuration.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated duration OfflineEvent $it")
                        durationUpdated.inc(OfflineEvent::class.java.simpleName)
                    }
                }

        sendLog("OfflineEvent", OfflineEvent::class.java.simpleName)
        SystemClock.sleep(pause)

        if (extendedBoluses.isNotEmpty())
            repository.runTransactionForResult(SyncNsExtendedBolusTransaction(extendedBoluses, config.NSCLIENT))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving extended bolus", it)
                }
                .blockingGet()
                .also { result ->
                    extendedBoluses.clear()
                    result.inserted.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.EXTENDED_BOLUS,
                                source = UserEntry.Sources.NSClient,
                                note = "",
                                values = listOf(
                                    ValueWithUnit.Timestamp(it.timestamp),
                                    ValueWithUnit.Insulin(it.amount),
                                    ValueWithUnit.UnitPerHour(it.rate),
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                )
                            )
                        )
                        if (it.isEmulatingTempBasal) virtualPump.fakeDataDetected = true
                        aapsLogger.debug(LTag.DATABASE, "Inserted ExtendedBolus $it")
                        inserted.inc(ExtendedBolus::class.java.simpleName)
                    }
                    result.invalidated.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.EXTENDED_BOLUS_REMOVED,
                                source = UserEntry.Sources.NSClient,
                                note = "",
                                values = listOf(
                                    ValueWithUnit.Timestamp(it.timestamp),
                                    ValueWithUnit.Insulin(it.amount),
                                    ValueWithUnit.UnitPerHour(it.rate),
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated ExtendedBolus $it")
                        invalidated.inc(ExtendedBolus::class.java.simpleName)
                    }
                    result.ended.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntry(
                                timestamp = dateUtil.now(),
                                action = UserEntry.Action.CANCEL_EXTENDED_BOLUS,
                                source = UserEntry.Sources.NSClient,
                                note = "",
                                values = listOf(
                                    ValueWithUnit.Timestamp(it.timestamp),
                                    ValueWithUnit.Insulin(it.amount),
                                    ValueWithUnit.UnitPerHour(it.rate),
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Updated ExtendedBolus $it")
                        ended.inc(ExtendedBolus::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId ExtendedBolus $it")
                        nsIdUpdated.inc(ExtendedBolus::class.java.simpleName)
                    }
                    result.updatedDuration.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated duration ExtendedBolus $it")
                        durationUpdated.inc(ExtendedBolus::class.java.simpleName)
                    }
                }

        sendLog("ExtendedBolus", ExtendedBolus::class.java.simpleName)
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
        repository.runTransactionForResult(UpdateNsIdTemporaryTargetTransaction(nsIdTemporaryTargets))
            .doOnError { error ->
                aapsLogger.error(LTag.DATABASE, "Updated nsId of TemporaryTarget failed", error)
            }
            .blockingGet()
            .also { result ->
                nsIdTemporaryTargets.clear()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of TemporaryTarget $it")
                    nsIdUpdated.inc(TemporaryTarget::class.java.simpleName)
                }
            }

        repository.runTransactionForResult(UpdateNsIdGlucoseValueTransaction(nsIdGlucoseValues))
            .doOnError { error ->
                aapsLogger.error(LTag.DATABASE, "Updated nsId of GlucoseValue failed", error)
            }
            .blockingGet()
            .also { result ->
                nsIdGlucoseValues.clear()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of GlucoseValue $it")
                    nsIdUpdated.inc(GlucoseValue::class.java.simpleName)
                }
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

        repository.runTransactionForResult(UpdateNsIdTherapyEventTransaction(nsIdTherapyEvents))
            .doOnError { error ->
                aapsLogger.error(LTag.DATABASE, "Updated nsId of TherapyEvent failed", error)
            }
            .blockingGet()
            .also { result ->
                nsIdTherapyEvents.clear()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of TherapyEvent $it")
                    nsIdUpdated.inc(TherapyEvent::class.java.simpleName)
                }
            }

        repository.runTransactionForResult(UpdateNsIdBolusTransaction(nsIdBoluses))
            .doOnError { error ->
                aapsLogger.error(LTag.DATABASE, "Updated nsId of Bolus failed", error)
            }
            .blockingGet()
            .also { result ->
                nsIdBoluses.clear()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of Bolus $it")
                    nsIdUpdated.inc(Bolus::class.java.simpleName)
                }
            }

        repository.runTransactionForResult(UpdateNsIdCarbsTransaction(nsIdCarbs))
            .doOnError { error ->
                aapsLogger.error(LTag.DATABASE, "Updated nsId of Carbs failed", error)
            }
            .blockingGet()
            .also { result ->
                nsIdCarbs.clear()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of Carbs $it")
                    nsIdUpdated.inc(Carbs::class.java.simpleName)
                }
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

        repository.runTransactionForResult(UpdateNsIdTemporaryBasalTransaction(nsIdTemporaryBasals))
            .doOnError { error ->
                aapsLogger.error(LTag.DATABASE, "Updated nsId of TemporaryBasal failed", error)
            }
            .blockingGet()
            .also { result ->
                nsIdTemporaryBasals.clear()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of TemporaryBasal $it")
                    nsIdUpdated.inc(TemporaryBasal::class.java.simpleName)
                }
            }

        repository.runTransactionForResult(UpdateNsIdExtendedBolusTransaction(nsIdExtendedBoluses))
            .doOnError { error ->
                aapsLogger.error(LTag.DATABASE, "Updated nsId of ExtendedBolus failed", error)
            }
            .blockingGet()
            .also { result ->
                nsIdExtendedBoluses.clear()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of ExtendedBolus $it")
                    nsIdUpdated.inc(ExtendedBolus::class.java.simpleName)
                }
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

        repository.runTransactionForResult(UpdateNsIdOfflineEventTransaction(nsIdOfflineEvents))
            .doOnError { error ->
                aapsLogger.error(LTag.DATABASE, "Updated nsId of OfflineEvent failed", error)
            }
            .blockingGet()
            .also { result ->
                nsIdOfflineEvents.clear()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of OfflineEvent $it")
                    nsIdUpdated.inc(OfflineEvent::class.java.simpleName)
                }
            }
        sendLog("GlucoseValue", GlucoseValue::class.java.simpleName)
        sendLog("Bolus", Bolus::class.java.simpleName)
        sendLog("Carbs", Carbs::class.java.simpleName)
        sendLog("TemporaryTarget", TemporaryTarget::class.java.simpleName)
        sendLog("TemporaryBasal", TemporaryBasal::class.java.simpleName)
        sendLog("EffectiveProfileSwitch", EffectiveProfileSwitch::class.java.simpleName)
        sendLog("ProfileSwitch", ProfileSwitch::class.java.simpleName)
        sendLog("BolusCalculatorResult", BolusCalculatorResult::class.java.simpleName)
        sendLog("TherapyEvent", TherapyEvent::class.java.simpleName)
        sendLog("OfflineEvent", OfflineEvent::class.java.simpleName)
        sendLog("ExtendedBolus", ExtendedBolus::class.java.simpleName)
        rxBus.send(EventNSClientNewLog("● DONE NSIDs", ""))
    }

    override fun updateDeletedTreatmentsInDb() {
        deleteTreatment.forEach { id ->
            if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_insulin, false) || config.NSCLIENT)
                repository.findBolusByNSId(id)?.let { bolus ->
                    repository.runTransactionForResult(InvalidateBolusTransaction(bolus.id))
                        .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating Bolus", it) }
                        .blockingGet()
                        .also { result ->
                            result.invalidated.forEach {
                                aapsLogger.debug(LTag.DATABASE, "Invalidated Bolus $it")
                                invalidated.inc(Bolus::class.java.simpleName)
                            }
                        }
                }
            if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_carbs, false) || config.NSCLIENT)
                repository.findCarbsByNSId(id)?.let { carb ->
                    repository.runTransactionForResult(InvalidateCarbsTransaction(carb.id))
                        .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating Carbs", it) }
                        .blockingGet()
                        .also { result ->
                            result.invalidated.forEach {
                                aapsLogger.debug(LTag.DATABASE, "Invalidated Carbs $it")
                                invalidated.inc(Carbs::class.java.simpleName)
                            }
                        }
                }
            if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_temp_target, false) || config.NSCLIENT)
                repository.findTemporaryTargetByNSId(id)?.let { gv ->
                    repository.runTransactionForResult(InvalidateTemporaryTargetTransaction(gv.id))
                        .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating TemporaryTarget", it) }
                        .blockingGet()
                        .also { result ->
                            result.invalidated.forEach {
                                aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryTarget $it")
                                invalidated.inc(TemporaryTarget::class.java.simpleName)
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
                                invalidated.inc(TemporaryBasal::class.java.simpleName)
                            }
                        }
                }
            if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_profile_switch, false) || config.NSCLIENT)
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
            if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_profile_switch, false) || config.NSCLIENT)
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
            if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_therapy_events, false) || config.NSCLIENT)
                repository.findTherapyEventByNSId(id)?.let { gv ->
                    repository.runTransactionForResult(InvalidateTherapyEventTransaction(gv.id))
                        .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating TherapyEvent", it) }
                        .blockingGet()
                        .also { result ->
                            result.invalidated.forEach {
                                aapsLogger.debug(LTag.DATABASE, "Invalidated TherapyEvent $it")
                                invalidated.inc(TherapyEvent::class.java.simpleName)
                            }
                        }
                }
            if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_offline_event, false) && config.isEngineeringMode() || config.NSCLIENT)
                repository.findOfflineEventByNSId(id)?.let { gv ->
                    repository.runTransactionForResult(InvalidateOfflineEventTransaction(gv.id))
                        .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating OfflineEvent", it) }
                        .blockingGet()
                        .also { result ->
                            result.invalidated.forEach {
                                aapsLogger.debug(LTag.DATABASE, "Invalidated OfflineEvent $it")
                                invalidated.inc(OfflineEvent::class.java.simpleName)
                            }
                        }
                }
            if (config.isEngineeringMode() && sp.getBoolean(R.string.key_ns_receive_tbr_eb, false) || config.NSCLIENT)
                repository.findExtendedBolusByNSId(id)?.let { gv ->
                    repository.runTransactionForResult(InvalidateExtendedBolusTransaction(gv.id))
                        .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating ExtendedBolus", it) }
                        .blockingGet()
                        .also { result ->
                            result.invalidated.forEach {
                                aapsLogger.debug(LTag.DATABASE, "Invalidated ExtendedBolus $it")
                                invalidated.inc(ExtendedBolus::class.java.simpleName)
                            }
                        }
                }
        }
        sendLog("Bolus", Bolus::class.java.simpleName)
        sendLog("Carbs", Carbs::class.java.simpleName)
        sendLog("TemporaryTarget", TemporaryTarget::class.java.simpleName)
        sendLog("TemporaryBasal", TemporaryBasal::class.java.simpleName)
        sendLog("EffectiveProfileSwitch", EffectiveProfileSwitch::class.java.simpleName)
        sendLog("ProfileSwitch", ProfileSwitch::class.java.simpleName)
        sendLog("BolusCalculatorResult", BolusCalculatorResult::class.java.simpleName)
        sendLog("TherapyEvent", TherapyEvent::class.java.simpleName)
        sendLog("OfflineEvent", OfflineEvent::class.java.simpleName)
        sendLog("ExtendedBolus", ExtendedBolus::class.java.simpleName)
    }

    override fun updateDeletedGlucoseValuesInDb() {
        deleteGlucoseValue.forEach { id ->
            repository.findBgReadingByNSId(id)?.let { gv ->
                repository.runTransactionForResult(InvalidateGlucoseValueTransaction(gv.id))
                    .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating GlucoseValue", it) }
                    .blockingGet()
                    .also { result ->
                        result.invalidated.forEach {
                            aapsLogger.debug(LTag.DATABASE, "Invalidated GlucoseValue $it")
                            invalidated.inc(GlucoseValue::class.java.simpleName)
                        }
                    }
            }
        }
        sendLog("GlucoseValue", GlucoseValue::class.java.simpleName)
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