package info.nightscout.androidaps.plugins.sync.nsShared

import android.content.Context
import android.os.SystemClock
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
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
import info.nightscout.androidaps.database.transactions.SyncNsProfileSwitchTransaction
import info.nightscout.androidaps.database.transactions.SyncNsTemporaryBasalTransaction
import info.nightscout.androidaps.database.transactions.SyncNsTemporaryTargetTransaction
import info.nightscout.androidaps.database.transactions.SyncNsTherapyEventTransaction
import info.nightscout.androidaps.database.transactions.UserEntryTransaction
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.interfaces.NsClient
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.source.NSClientSourcePlugin
import info.nightscout.androidaps.plugins.sync.nsShared.events.EventNSClientNewLog
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.XDripBroadcast
import info.nightscout.sdk.localmodel.treatment.NSBolus
import info.nightscout.sdk.localmodel.treatment.NSBolusWizard
import info.nightscout.sdk.localmodel.treatment.NSCarbs
import info.nightscout.sdk.localmodel.treatment.NSEffectiveProfileSwitch
import info.nightscout.sdk.localmodel.treatment.NSProfileSwitch
import info.nightscout.sdk.localmodel.treatment.NSTemporaryBasal
import info.nightscout.sdk.localmodel.treatment.NSTemporaryTarget
import info.nightscout.sdk.localmodel.treatment.NSTherapyEvent
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoreDataForDb @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val repository: AppRepository,
    private val uel: UserEntryLogger,
    private val dateUtil: DateUtil,
    private val activePlugin: ActivePlugin,
    private val config: Config,
    private val nsClientSourcePlugin: NSClientSourcePlugin,
    private val xDripBroadcast: XDripBroadcast
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
                        updated[GlucoseValue::class.java.simpleName] = (updated[GlucoseValue::class.java.simpleName] ?: 0) + 1
                    }
                    result.inserted.forEach {
                        xDripBroadcast.send(it)
                        nsClientSourcePlugin.detectSource(it)
                        aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                        inserted[GlucoseValue::class.java.simpleName] = (inserted[GlucoseValue::class.java.simpleName] ?: 0) + 1
                    }
                    result.updatedNsId.forEach {
                        xDripBroadcast.send(it)
                        nsClientSourcePlugin.detectSource(it)
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId bg $it")
                        nsIdUpdated[GlucoseValue::class.java.simpleName] = (nsIdUpdated[GlucoseValue::class.java.simpleName] ?: 0) + 1
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
                        inserted[NSBolus::class.java.simpleName] = (inserted[NSBolus::class.java.simpleName] ?: 0) + 1
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
                        invalidated[NSBolus::class.java.simpleName] = (invalidated[NSBolus::class.java.simpleName] ?: 0) + 1
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId of bolus $it")
                        nsIdUpdated[NSBolus::class.java.simpleName] = (nsIdUpdated[NSBolus::class.java.simpleName] ?: 0) + 1
                    }
                    result.updated.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated amount of bolus $it")
                        updated[NSBolus::class.java.simpleName] = (updated[NSBolus::class.java.simpleName] ?: 0) + 1
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
                        inserted[NSCarbs::class.java.simpleName] = (inserted[NSCarbs::class.java.simpleName] ?: 0) + 1
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
                        invalidated[NSCarbs::class.java.simpleName] = (invalidated[NSCarbs::class.java.simpleName] ?: 0) + 1
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
                        updated[NSCarbs::class.java.simpleName] = (updated[NSCarbs::class.java.simpleName] ?: 0) + 1
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId carbs $it")
                        nsIdUpdated[NSCarbs::class.java.simpleName] = (nsIdUpdated[NSCarbs::class.java.simpleName] ?: 0) + 1
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
                        inserted[NSTemporaryTarget::class.java.simpleName] = (inserted[NSTemporaryTarget::class.java.simpleName] ?: 0) + 1
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
                        invalidated[NSTemporaryTarget::class.java.simpleName] = (invalidated[NSTemporaryTarget::class.java.simpleName] ?: 0) + 1
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
                        ended[NSTemporaryTarget::class.java.simpleName] = (ended[NSTemporaryTarget::class.java.simpleName] ?: 0) + 1
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId TemporaryTarget $it")
                        nsIdUpdated[NSTemporaryTarget::class.java.simpleName] = (nsIdUpdated[NSTemporaryTarget::class.java.simpleName] ?: 0) + 1
                    }
                    result.updatedDuration.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated duration TemporaryTarget $it")
                        durationUpdated[NSTemporaryTarget::class.java.simpleName] = (durationUpdated[NSTemporaryTarget::class.java.simpleName] ?: 0) + 1
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
                        inserted[NSTemporaryBasal::class.java.simpleName] = (inserted[NSTemporaryBasal::class.java.simpleName] ?: 0) + 1
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
                        invalidated[NSTemporaryBasal::class.java.simpleName] = (invalidated[NSTemporaryBasal::class.java.simpleName] ?: 0) + 1
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
                        ended[NSTemporaryBasal::class.java.simpleName] = (ended[NSTemporaryBasal::class.java.simpleName] ?: 0) + 1
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId TemporaryBasal $it")
                        nsIdUpdated[NSTemporaryBasal::class.java.simpleName] = (nsIdUpdated[NSTemporaryBasal::class.java.simpleName] ?: 0) + 1
                    }
                    result.updatedDuration.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated duration TemporaryBasal $it")
                        durationUpdated[NSTemporaryBasal::class.java.simpleName] = (durationUpdated[NSTemporaryBasal::class.java.simpleName] ?: 0) + 1
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
                        inserted[NSEffectiveProfileSwitch::class.java.simpleName] = (inserted[NSEffectiveProfileSwitch::class.java.simpleName] ?: 0) + 1
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
                        invalidated[NSEffectiveProfileSwitch::class.java.simpleName] = (invalidated[NSEffectiveProfileSwitch::class.java.simpleName] ?: 0) + 1
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId EffectiveProfileSwitch $it")
                        nsIdUpdated[NSEffectiveProfileSwitch::class.java.simpleName] = (nsIdUpdated[NSEffectiveProfileSwitch::class.java.simpleName] ?: 0) + 1
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
                        inserted[NSProfileSwitch::class.java.simpleName] = (inserted[NSProfileSwitch::class.java.simpleName] ?: 0) + 1
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
                        invalidated[NSProfileSwitch::class.java.simpleName] = (invalidated[NSProfileSwitch::class.java.simpleName] ?: 0) + 1
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId ProfileSwitch $it")
                        nsIdUpdated[NSProfileSwitch::class.java.simpleName] = (nsIdUpdated[NSProfileSwitch::class.java.simpleName] ?: 0) + 1
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
                        inserted[NSBolusWizard::class.java.simpleName] = (inserted[NSBolusWizard::class.java.simpleName] ?: 0) + 1
                    }
                    result.invalidated.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Invalidated BolusCalculatorResult $it")
                        invalidated[NSBolusWizard::class.java.simpleName] = (invalidated[NSBolusWizard::class.java.simpleName] ?: 0) + 1
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId BolusCalculatorResult $it")
                        nsIdUpdated[NSBolusWizard::class.java.simpleName] = (nsIdUpdated[NSBolusWizard::class.java.simpleName] ?: 0) + 1
                    }
                }

        sendLog("BolusCalculatorResult", NSBolusWizard::class.java.simpleName)
        SystemClock.sleep(pause)

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
                        inserted[NSTherapyEvent::class.java.simpleName] = (inserted[NSTherapyEvent::class.java.simpleName] ?: 0) + 1
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
                        invalidated[NSTherapyEvent::class.java.simpleName] = (invalidated[NSTherapyEvent::class.java.simpleName] ?: 0) + 1
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId TherapyEvent $it")
                        nsIdUpdated[NSTherapyEvent::class.java.simpleName] = (nsIdUpdated[NSTherapyEvent::class.java.simpleName] ?: 0) + 1
                    }
                    result.updatedDuration.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId TherapyEvent $it")
                        durationUpdated[NSTherapyEvent::class.java.simpleName] = (durationUpdated[NSTherapyEvent::class.java.simpleName] ?: 0) + 1
                    }
                }

        sendLog("TherapyEvent", NSTherapyEvent::class.java.simpleName)
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