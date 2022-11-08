package info.nightscout.androidaps.plugins.sync.nsShared

import android.content.Context
import android.os.SystemClock
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.entities.BolusCalculatorResult
import info.nightscout.androidaps.database.entities.Carbs
import info.nightscout.androidaps.database.entities.EffectiveProfileSwitch
import info.nightscout.androidaps.database.entities.ExtendedBolus
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.OfflineEvent
import info.nightscout.androidaps.database.entities.ProfileSwitch
import info.nightscout.androidaps.database.entities.TemporaryBasal
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.transactions.CgmSourceTransaction
import info.nightscout.androidaps.database.transactions.SyncNsBolusCalculatorResultTransaction
import info.nightscout.androidaps.database.transactions.SyncNsBolusTransaction
import info.nightscout.androidaps.database.transactions.SyncNsCarbsTransaction
import info.nightscout.androidaps.database.transactions.SyncNsEffectiveProfileSwitchTransaction
import info.nightscout.androidaps.database.transactions.SyncNsExtendedBolusTransaction
import info.nightscout.androidaps.database.transactions.SyncNsOfflineEventTransaction
import info.nightscout.androidaps.database.transactions.SyncNsProfileSwitchTransaction
import info.nightscout.androidaps.database.transactions.SyncNsTemporaryBasalTransaction
import info.nightscout.androidaps.database.transactions.SyncNsTemporaryTargetTransaction
import info.nightscout.androidaps.database.transactions.SyncNsTherapyEventTransaction
import info.nightscout.androidaps.database.transactions.UserEntryTransaction
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.NsClient
import info.nightscout.androidaps.interfaces.XDripBroadcast
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.plugins.source.NSClientSourcePlugin
import info.nightscout.androidaps.plugins.sync.nsShared.events.EventNSClientNewLog
import info.nightscout.shared.utils.DateUtil
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.sdk.localmodel.treatment.NSBolus
import info.nightscout.sdk.localmodel.treatment.NSBolusWizard
import info.nightscout.sdk.localmodel.treatment.NSCarbs
import info.nightscout.sdk.localmodel.treatment.NSEffectiveProfileSwitch
import info.nightscout.sdk.localmodel.treatment.NSExtendedBolus
import info.nightscout.sdk.localmodel.treatment.NSOfflineEvent
import info.nightscout.sdk.localmodel.treatment.NSProfileSwitch
import info.nightscout.sdk.localmodel.treatment.NSTemporaryBasal
import info.nightscout.sdk.localmodel.treatment.NSTemporaryTarget
import info.nightscout.sdk.localmodel.treatment.NSTherapyEvent
import info.nightscout.shared.sharedPreferences.SP
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoreDataForDb @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val repository: AppRepository,
    private val sp: SP,
    private val uel: UserEntryLogger,
    private val dateUtil: DateUtil,
    private val activePlugin: ActivePlugin,
    private val config: Config,
    private val nsClientSourcePlugin: NSClientSourcePlugin,
    private val xDripBroadcast: XDripBroadcast,
    private val virtualPumpPlugin: VirtualPumpPlugin
) {

    val glucoseValues: MutableList<CgmSourceTransaction.TransactionGlucoseValue> = mutableListOf()

    val boluses: MutableList<Bolus> = mutableListOf()
    val carbs: MutableList<Carbs> = mutableListOf()
    val temporaryTargets: MutableList<TemporaryTarget> = mutableListOf()
    val effectiveProfileSwitches: MutableList<EffectiveProfileSwitch> = mutableListOf()
    val bolusCalculatorResults: MutableList<BolusCalculatorResult> = mutableListOf()
    val therapyEvents: MutableList<TherapyEvent> = mutableListOf()
    val extendedBoluses: MutableList<ExtendedBolus> = mutableListOf()
    val temporaryBasals: MutableList<TemporaryBasal> = mutableListOf()
    val profileSwitches: MutableList<ProfileSwitch> = mutableListOf()
    val offlineEvents: MutableList<OfflineEvent> = mutableListOf()

    private val userEntries: MutableList<UserEntryTransaction.Entry> = mutableListOf()

    private val inserted = HashMap<String, Long>()
    private val updated = HashMap<String, Long>()
    private val invalidated = HashMap<String, Long>()
    private val nsIdUpdated = HashMap<String, Long>()
    private val durationUpdated = HashMap<String, Long>()
    private val ended = HashMap<String, Long>()

    private val pause = 1000L // to slow down db operations

    class StoreBgWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var storeDataForDb: StoreDataForDb

        override fun doWork(): Result {
            storeDataForDb.storeGlucoseValuesToDb()
            return Result.success()
        }

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }
    }

    fun <T> HashMap<T, Long>.inc(key: T) =
        if (containsKey(key)) merge(key, 1, Long::plus)
        else put(key, 1)

    private fun storeGlucoseValuesToDb() {
        rxBus.send(EventNSClientNewLog("PROCESSING BG", "", activePlugin.activeNsClient?.version ?: NsClient.Version.V3))

        if (glucoseValues.isNotEmpty())
            repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving values from NSClient App", it)
                }
                .blockingGet()
                .also { result ->
                    glucoseValues.clear()
                    result.updated.forEach {
                        xDripBroadcast.send(it)
                        nsClientSourcePlugin.detectSource(it)
                        aapsLogger.debug(LTag.DATABASE, "Updated bg $it")
                        updated.inc(GlucoseValue::class.java.simpleName)
                    }
                    result.inserted.forEach {
                        xDripBroadcast.send(it)
                        nsClientSourcePlugin.detectSource(it)
                        aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                        inserted.inc(GlucoseValue::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        xDripBroadcast.send(it)
                        nsClientSourcePlugin.detectSource(it)
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId bg $it")
                        nsIdUpdated.inc(GlucoseValue::class.java.simpleName)
                    }
                }

        sendLog("GlucoseValue", GlucoseValue::class.java.simpleName)
        SystemClock.sleep(pause)
        rxBus.send(EventNSClientNewLog("DONE BG", "", activePlugin.activeNsClient?.version ?: NsClient.Version.V3))
    }

    fun storeTreatmentsToDb() {
        rxBus.send(EventNSClientNewLog("PROCESSING TR", "", activePlugin.activeNsClient?.version ?: NsClient.Version.V3))

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
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.BOLUS, UserEntry.Sources.NSClient, it.notes ?: "",
                                listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Insulin(it.amount))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted bolus $it")
                        inserted.inc(NSBolus::class.java.simpleName)
                    }
                    result.invalidated.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.BOLUS_REMOVED, UserEntry.Sources.NSClient, "",
                                listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Insulin(it.amount))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated bolus $it")
                        invalidated.inc(NSBolus::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId of bolus $it")
                        nsIdUpdated.inc(NSBolus::class.java.simpleName)
                    }
                    result.updated.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated amount of bolus $it")
                        updated.inc(NSBolus::class.java.simpleName)
                    }
                }

        sendLog("Bolus", NSBolus::class.java.simpleName)
        SystemClock.sleep(pause)

        if (carbs.isNotEmpty())
            repository.runTransactionForResult(SyncNsCarbsTransaction(carbs))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving carbs", it)
                }
                .blockingGet()
                .also { result ->
                    carbs.clear()
                    result.inserted.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.CARBS, UserEntry.Sources.NSClient, it.notes ?: "",
                                listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Gram(it.amount.toInt()))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted carbs $it")
                        inserted.inc(NSCarbs::class.java.simpleName)
                    }
                    result.invalidated.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.CARBS_REMOVED, UserEntry.Sources.NSClient, "",
                                listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Gram(it.amount.toInt()))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated carbs $it")
                        invalidated.inc(NSCarbs::class.java.simpleName)
                    }
                    result.updated.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.CARBS, UserEntry.Sources.NSClient, it.notes ?: "",
                                listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Gram(it.amount.toInt()))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Updated carbs $it")
                        updated.inc(NSCarbs::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId carbs $it")
                        nsIdUpdated.inc(NSCarbs::class.java.simpleName)
                    }

                }

        sendLog("Carbs", NSCarbs::class.java.simpleName)
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
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.TT, UserEntry.Sources.NSClient, "",
                                listOf(
                                    ValueWithUnit.TherapyEventTTReason(tt.reason),
                                    ValueWithUnit.fromGlucoseUnit(tt.lowTarget, Constants.MGDL),
                                    ValueWithUnit.fromGlucoseUnit(tt.highTarget, Constants.MGDL).takeIf { tt.lowTarget != tt.highTarget },
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tt.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted TemporaryTarget $tt")
                        inserted.inc(NSTemporaryTarget::class.java.simpleName)
                    }
                    result.invalidated.forEach { tt ->
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.TT_REMOVED, UserEntry.Sources.NSClient, "",
                                listOf(
                                    ValueWithUnit.TherapyEventTTReason(tt.reason),
                                    ValueWithUnit.Mgdl(tt.lowTarget),
                                    ValueWithUnit.Mgdl(tt.highTarget).takeIf { tt.lowTarget != tt.highTarget },
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tt.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryTarget $tt")
                        invalidated.inc(NSTemporaryTarget::class.java.simpleName)
                    }
                    result.ended.forEach { tt ->
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.CANCEL_TT, UserEntry.Sources.NSClient, "",
                                listOf(
                                    ValueWithUnit.TherapyEventTTReason(tt.reason),
                                    ValueWithUnit.Mgdl(tt.lowTarget),
                                    ValueWithUnit.Mgdl(tt.highTarget).takeIf { tt.lowTarget != tt.highTarget },
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tt.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Updated TemporaryTarget $tt")
                        ended.inc(NSTemporaryTarget::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId TemporaryTarget $it")
                        nsIdUpdated.inc(NSTemporaryTarget::class.java.simpleName)
                    }
                    result.updatedDuration.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated duration TemporaryTarget $it")
                        durationUpdated.inc(NSTemporaryTarget::class.java.simpleName)
                    }
                }

        sendLog("TemporaryTarget", NSTemporaryTarget::class.java.simpleName)
        SystemClock.sleep(pause)

        if (temporaryBasals.isNotEmpty())
            repository.runTransactionForResult(SyncNsTemporaryBasalTransaction(temporaryBasals))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving temporary basal", it)
                }
                .blockingGet()
                .also { result ->
                    temporaryBasals.clear()
                    result.inserted.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.TEMP_BASAL, UserEntry.Sources.NSClient, "",
                                listOf(
                                    ValueWithUnit.Timestamp(it.timestamp),
                                    if (it.isAbsolute) ValueWithUnit.UnitPerHour(it.rate) else ValueWithUnit.Percent(it.rate.toInt()),
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted TemporaryBasal $it")
                        inserted.inc(NSTemporaryBasal::class.java.simpleName)
                    }
                    result.invalidated.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.TEMP_BASAL_REMOVED, UserEntry.Sources.NSClient, "",
                                listOf(
                                    ValueWithUnit.Timestamp(it.timestamp),
                                    if (it.isAbsolute) ValueWithUnit.UnitPerHour(it.rate) else ValueWithUnit.Percent(it.rate.toInt()),
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryBasal $it")
                        invalidated.inc(NSTemporaryBasal::class.java.simpleName)
                    }
                    result.ended.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.CANCEL_TEMP_BASAL, UserEntry.Sources.NSClient, "",
                                listOf(
                                    ValueWithUnit.Timestamp(it.timestamp),
                                    if (it.isAbsolute) ValueWithUnit.UnitPerHour(it.rate) else ValueWithUnit.Percent(it.rate.toInt()),
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Ended TemporaryBasal $it")
                        ended.inc(NSTemporaryBasal::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId TemporaryBasal $it")
                        nsIdUpdated.inc(NSTemporaryBasal::class.java.simpleName)
                    }
                    result.updatedDuration.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated duration TemporaryBasal $it")
                        durationUpdated.inc(NSTemporaryBasal::class.java.simpleName)
                    }
                }

        sendLog("TemporaryBasal", NSTemporaryBasal::class.java.simpleName)
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
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.PROFILE_SWITCH, UserEntry.Sources.NSClient, "",
                                listOf(ValueWithUnit.Timestamp(it.timestamp))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted EffectiveProfileSwitch $it")
                        inserted.inc(NSEffectiveProfileSwitch::class.java.simpleName)
                    }
                    result.invalidated.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.PROFILE_SWITCH_REMOVED, UserEntry.Sources.NSClient, "",
                                listOf(ValueWithUnit.Timestamp(it.timestamp))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated EffectiveProfileSwitch $it")
                        invalidated.inc(NSEffectiveProfileSwitch::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId EffectiveProfileSwitch $it")
                        nsIdUpdated.inc(NSEffectiveProfileSwitch::class.java.simpleName)
                    }
                }

        sendLog("EffectiveProfileSwitch", NSEffectiveProfileSwitch::class.java.simpleName)
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
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.PROFILE_SWITCH, UserEntry.Sources.NSClient, "",
                                listOf(ValueWithUnit.Timestamp(it.timestamp))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted ProfileSwitch $it")
                        inserted.inc(NSProfileSwitch::class.java.simpleName)
                    }
                    result.invalidated.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.PROFILE_SWITCH_REMOVED, UserEntry.Sources.NSClient, "",
                                listOf(ValueWithUnit.Timestamp(it.timestamp))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated ProfileSwitch $it")
                        invalidated.inc(NSProfileSwitch::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId ProfileSwitch $it")
                        nsIdUpdated.inc(NSProfileSwitch::class.java.simpleName)
                    }
                }

        sendLog("ProfileSwitch", NSProfileSwitch::class.java.simpleName)
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
                        inserted.inc(NSBolusWizard::class.java.simpleName)
                    }
                    result.invalidated.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Invalidated BolusCalculatorResult $it")
                        invalidated.inc(NSBolusWizard::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId BolusCalculatorResult $it")
                        nsIdUpdated.inc(NSBolusWizard::class.java.simpleName)
                    }
                }

        sendLog("BolusCalculatorResult", NSBolusWizard::class.java.simpleName)
        SystemClock.sleep(pause)

        if (sp.getBoolean(R.string.key_ns_receive_therapy_events, false) || config.NSCLIENT)
            therapyEvents.filter { it.type == TherapyEvent.Type.ANNOUNCEMENT }.forEach {
                if (it.timestamp > dateUtil.now() - 15 * 60 * 1000L &&
                    it.note?.isNotEmpty() == true &&
                    it.enteredBy != sp.getString("careportal_enteredby", "AndroidAPS")
                ) {
                    if (sp.getBoolean(R.string.key_ns_announcements, config.NSCLIENT))
                        rxBus.send(EventNewNotification(Notification(Notification.NS_ANNOUNCEMENT, it.note ?: "", Notification.ANNOUNCEMENT, 60)))
                }
            }
        if (therapyEvents.isNotEmpty())
            repository.runTransactionForResult(SyncNsTherapyEventTransaction(therapyEvents))
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
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                action, UserEntry.Sources.NSClient, therapyEvent.note ?: "",
                                listOf(ValueWithUnit.Timestamp(therapyEvent.timestamp),
                                       ValueWithUnit.TherapyEventType(therapyEvent.type),
                                       ValueWithUnit.fromGlucoseUnit(therapyEvent.glucose ?: 0.0, therapyEvent.glucoseUnit.toString).takeIf { therapyEvent.glucose != null })
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted TherapyEvent $therapyEvent")
                        inserted.inc(NSTherapyEvent::class.java.simpleName)
                    }
                    result.invalidated.forEach { therapyEvent ->
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.CAREPORTAL_REMOVED, UserEntry.Sources.NSClient, therapyEvent.note ?: "",
                                listOf(ValueWithUnit.Timestamp(therapyEvent.timestamp),
                                       ValueWithUnit.TherapyEventType(therapyEvent.type),
                                       ValueWithUnit.fromGlucoseUnit(therapyEvent.glucose ?: 0.0, therapyEvent.glucoseUnit.toString).takeIf { therapyEvent.glucose != null })
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated TherapyEvent $therapyEvent")
                        invalidated.inc(NSTherapyEvent::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId TherapyEvent $it")
                        nsIdUpdated.inc(NSTherapyEvent::class.java.simpleName)
                    }
                    result.updatedDuration.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId TherapyEvent $it")
                        durationUpdated.inc(NSTherapyEvent::class.java.simpleName)
                    }
                }

        sendLog("TherapyEvent", NSTherapyEvent::class.java.simpleName)
        SystemClock.sleep(pause)

        if (offlineEvents.isNotEmpty())
            repository.runTransactionForResult(SyncNsOfflineEventTransaction(offlineEvents))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving OfflineEvent", it)
                }
                .blockingGet()
                .also { result ->
                    result.inserted.forEach { oe ->
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.LOOP_CHANGE, UserEntry.Sources.NSClient, "",
                                listOf(
                                    ValueWithUnit.OfflineEventReason(oe.reason),
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(oe.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted OfflineEvent $oe")
                        inserted.inc(NSOfflineEvent::class.java.simpleName)
                    }
                    result.invalidated.forEach { oe ->
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.LOOP_REMOVED, UserEntry.Sources.NSClient, "",
                                listOf(
                                    ValueWithUnit.OfflineEventReason(oe.reason),
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(oe.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated OfflineEvent $oe")
                        invalidated.inc(NSOfflineEvent::class.java.simpleName)
                    }
                    result.ended.forEach { oe ->
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.LOOP_CHANGE, UserEntry.Sources.NSClient, "",
                                listOf(
                                    ValueWithUnit.OfflineEventReason(oe.reason),
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(oe.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Updated OfflineEvent $oe")
                        ended.inc(NSOfflineEvent::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId OfflineEvent $it")
                        nsIdUpdated.inc(NSOfflineEvent::class.java.simpleName)
                    }
                    result.updatedDuration.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated duration OfflineEvent $it")
                        durationUpdated.inc(NSOfflineEvent::class.java.simpleName)
                    }
                }

        sendLog("OfflineEvent", NSOfflineEvent::class.java.simpleName)
        SystemClock.sleep(pause)

        if (extendedBoluses.isNotEmpty())
            repository.runTransactionForResult(SyncNsExtendedBolusTransaction(extendedBoluses))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving extended bolus", it)
                }
                .blockingGet()
                .also { result ->
                    result.inserted.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.EXTENDED_BOLUS, UserEntry.Sources.NSClient, "",
                                listOf(
                                    ValueWithUnit.Timestamp(it.timestamp),
                                    ValueWithUnit.Insulin(it.amount),
                                    ValueWithUnit.UnitPerHour(it.rate),
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                )
                            )
                        )
                        if (it.isEmulatingTempBasal) virtualPumpPlugin.fakeDataDetected = true
                        aapsLogger.debug(LTag.DATABASE, "Inserted ExtendedBolus $it")
                        inserted.inc(NSExtendedBolus::class.java.simpleName)
                    }
                    result.invalidated.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.EXTENDED_BOLUS_REMOVED, UserEntry.Sources.NSClient, "",
                                listOf(
                                    ValueWithUnit.Timestamp(it.timestamp),
                                    ValueWithUnit.Insulin(it.amount),
                                    ValueWithUnit.UnitPerHour(it.rate),
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated ExtendedBolus $it")
                        invalidated.inc(NSExtendedBolus::class.java.simpleName)
                    }
                    result.ended.forEach {
                        if (config.NSCLIENT.not()) userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.CANCEL_EXTENDED_BOLUS, UserEntry.Sources.NSClient, "",
                                listOf(
                                    ValueWithUnit.Timestamp(it.timestamp),
                                    ValueWithUnit.Insulin(it.amount),
                                    ValueWithUnit.UnitPerHour(it.rate),
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                )
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Updated ExtendedBolus $it")
                        ended.inc(NSExtendedBolus::class.java.simpleName)
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId ExtendedBolus $it")
                        nsIdUpdated.inc(NSExtendedBolus::class.java.simpleName)
                    }
                    result.updatedDuration.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated duration ExtendedBolus $it")
                        durationUpdated.inc(NSExtendedBolus::class.java.simpleName)
                    }
                }

        sendLog("ExtendedBolus", NSExtendedBolus::class.java.simpleName)
        SystemClock.sleep(pause)

        uel.log(userEntries)
        rxBus.send(EventNSClientNewLog("DONE TR", "", activePlugin.activeNsClient?.version ?: NsClient.Version.V3))
    }

    private fun sendLog(item: String, clazz: String) {
        inserted[clazz]?.let {
            rxBus.send(EventNSClientNewLog("INSERT", "$item $it", activePlugin.activeNsClient?.version ?: NsClient.Version.V3))
        }
        inserted.remove(clazz)
        updated[clazz]?.let {
            rxBus.send(EventNSClientNewLog("UPDATE", "$item $it", activePlugin.activeNsClient?.version ?: NsClient.Version.V3))
        }
        updated.remove(clazz)
        invalidated[clazz]?.let {
            rxBus.send(EventNSClientNewLog("INVALIDATE", "$item $it", activePlugin.activeNsClient?.version ?: NsClient.Version.V3))
        }
        invalidated.remove(clazz)
        nsIdUpdated[clazz]?.let {
            rxBus.send(EventNSClientNewLog("NS_ID", "$item $it", activePlugin.activeNsClient?.version ?: NsClient.Version.V3))
        }
        nsIdUpdated.remove(clazz)
        durationUpdated[clazz]?.let {
            rxBus.send(EventNSClientNewLog("DURATION", "$item $it", activePlugin.activeNsClient?.version ?: NsClient.Version.V3))
        }
        durationUpdated.remove(clazz)
        ended[clazz]?.let {
            rxBus.send(EventNSClientNewLog("CUT", "$item $it", activePlugin.activeNsClient?.version ?: NsClient.Version.V3))
        }
        ended.remove(clazz)
    }
}