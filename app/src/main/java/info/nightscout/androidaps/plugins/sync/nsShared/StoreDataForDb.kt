package info.nightscout.androidaps.plugins.sync.nsShared

import android.os.SystemClock
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.entities.BolusCalculatorResult
import info.nightscout.androidaps.database.entities.Carbs
import info.nightscout.androidaps.database.entities.EffectiveProfileSwitch
import info.nightscout.androidaps.database.entities.ExtendedBolus
import info.nightscout.androidaps.database.entities.OfflineEvent
import info.nightscout.androidaps.database.entities.ProfileSwitch
import info.nightscout.androidaps.database.entities.TemporaryBasal
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.transactions.SyncNsBolusTransaction
import info.nightscout.androidaps.database.transactions.SyncNsCarbsTransaction
import info.nightscout.androidaps.database.transactions.SyncNsEffectiveProfileSwitchTransaction
import info.nightscout.androidaps.database.transactions.SyncNsProfileSwitchTransaction
import info.nightscout.androidaps.database.transactions.SyncNsTemporaryBasalTransaction
import info.nightscout.androidaps.database.transactions.SyncNsTemporaryTargetTransaction
import info.nightscout.androidaps.database.transactions.UserEntryTransaction
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.interfaces.NsClient
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.sync.nsShared.events.EventNSClientNewLog
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.sdk.localmodel.treatment.NSBolus
import info.nightscout.sdk.localmodel.treatment.NSCarbs
import info.nightscout.sdk.localmodel.treatment.NSEffectiveProfileSwitch
import info.nightscout.sdk.localmodel.treatment.NSProfileSwitch
import info.nightscout.sdk.localmodel.treatment.NSTemporaryBasal
import info.nightscout.sdk.localmodel.treatment.NSTemporaryTarget
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
    private val config: Config
) {

    data class PreparedData(
        val boluses: MutableList<Bolus> = mutableListOf(),
        val carbs: MutableList<Carbs> = mutableListOf(),
        val temporaryTargets: MutableList<TemporaryTarget> = mutableListOf(),
        val effectiveProfileSwitches: MutableList<EffectiveProfileSwitch> = mutableListOf(),
        val bolusCalculatorResults: MutableList<BolusCalculatorResult> = mutableListOf(),
        val therapyEvents: MutableList<TherapyEvent> = mutableListOf(),
        val extendedBoluses: MutableList<ExtendedBolus> = mutableListOf(),
        val temporaryBasals: MutableList<TemporaryBasal> = mutableListOf(),
        val profileSwitches: MutableList<ProfileSwitch> = mutableListOf(),
        val offlineEvents: MutableList<OfflineEvent> = mutableListOf(),

        val userEntries: MutableList<UserEntryTransaction.Entry> = mutableListOf()
    )

    val preparedData = PreparedData()
    private val pause = 1000L // to slow down db operations

    fun storeToDb() {
        val processed = HashMap<String, Long>()
        rxBus.send(EventNSClientNewLog("PROCESSING", "", activePlugin.activeNsClient?.version ?: NsClient.Version.V3))

        if (preparedData.boluses.isNotEmpty())
            repository.runTransactionForResult(SyncNsBolusTransaction(preparedData.boluses))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving bolus", it)
                }
                .blockingGet()
                .also { result ->
                    preparedData.boluses.clear()
                    result.inserted.forEach {
                        if (config.NSCLIENT.not()) preparedData.userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.BOLUS, UserEntry.Sources.NSClient, it.notes ?: "",
                                listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Insulin(it.amount))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted bolus $it")
                        processed[NSBolus::class.java.simpleName] = (processed[NSBolus::class.java.simpleName] ?: 0) + 1
                    }
                    result.invalidated.forEach {
                        if (config.NSCLIENT.not()) preparedData.userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.BOLUS_REMOVED, UserEntry.Sources.NSClient, "",
                                listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Insulin(it.amount))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated bolus $it")
                        processed[NSBolus::class.java.simpleName] = (processed[NSBolus::class.java.simpleName] ?: 0) + 1
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId of bolus $it")
                        processed[NSBolus::class.java.simpleName] = (processed[NSBolus::class.java.simpleName] ?: 0) + 1
                    }
                    result.updated.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated amount of bolus $it")
                        processed[NSBolus::class.java.simpleName] = (processed[NSBolus::class.java.simpleName] ?: 0) + 1
                    }
                }

        sendLog("Bolus", processed[NSBolus::class.java.simpleName])
        SystemClock.sleep(pause)

        if (preparedData.carbs.isNotEmpty())
            repository.runTransactionForResult(SyncNsCarbsTransaction(preparedData.carbs))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving carbs", it)
                }
                .blockingGet()
                .also { result ->
                    preparedData.carbs.clear()
                    result.inserted.forEach {
                        if (config.NSCLIENT.not()) preparedData.userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.CARBS, UserEntry.Sources.NSClient, it.notes ?: "",
                                listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Gram(it.amount.toInt()))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted carbs $it")
                        processed[NSCarbs::class.java.simpleName] = (processed[NSCarbs::class.java.simpleName] ?: 0) + 1
                    }
                    result.invalidated.forEach {
                        if (config.NSCLIENT.not()) preparedData.userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.CARBS_REMOVED, UserEntry.Sources.NSClient, "",
                                listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Gram(it.amount.toInt()))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated carbs $it")
                        processed[NSCarbs::class.java.simpleName] = (processed[NSCarbs::class.java.simpleName] ?: 0) + 1
                    }
                    result.updated.forEach {
                        if (config.NSCLIENT.not()) preparedData.userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.CARBS, UserEntry.Sources.NSClient, it.notes ?: "",
                                listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Gram(it.amount.toInt()))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Updated carbs $it")
                        processed[NSCarbs::class.java.simpleName] = (processed[NSCarbs::class.java.simpleName] ?: 0) + 1
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId carbs $it")
                        processed[NSCarbs::class.java.simpleName] = (processed[NSCarbs::class.java.simpleName] ?: 0) + 1
                    }

                }

        sendLog("Carbs", processed[NSCarbs::class.java.simpleName])
        SystemClock.sleep(pause)

        if (preparedData.temporaryTargets.isNotEmpty())
            repository.runTransactionForResult(SyncNsTemporaryTargetTransaction(preparedData.temporaryTargets))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
                }
                .blockingGet()
                .also { result ->
                    preparedData.temporaryTargets.clear()
                    result.inserted.forEach { tt ->
                        if (config.NSCLIENT.not()) preparedData.userEntries.add(
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
                        processed[NSTemporaryTarget::class.java.simpleName] = (processed[NSTemporaryTarget::class.java.simpleName] ?: 0) + 1
                    }
                    result.invalidated.forEach { tt ->
                        if (config.NSCLIENT.not()) preparedData.userEntries.add(
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
                        processed[NSTemporaryTarget::class.java.simpleName] = (processed[NSTemporaryTarget::class.java.simpleName] ?: 0) + 1
                    }
                    result.ended.forEach { tt ->
                        if (config.NSCLIENT.not()) preparedData.userEntries.add(
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
                        processed[NSTemporaryTarget::class.java.simpleName] = (processed[NSTemporaryTarget::class.java.simpleName] ?: 0) + 1
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId TemporaryTarget $it")
                        processed[NSTemporaryTarget::class.java.simpleName] = (processed[NSTemporaryTarget::class.java.simpleName] ?: 0) + 1
                    }
                    result.updatedDuration.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated duration TemporaryTarget $it")
                        processed[NSTemporaryTarget::class.java.simpleName] = (processed[NSTemporaryTarget::class.java.simpleName] ?: 0) + 1
                    }
                }

        sendLog("TemporaryTarget", processed[NSTemporaryTarget::class.java.simpleName])
        SystemClock.sleep(pause)

        if (preparedData.temporaryBasals.isNotEmpty())
            repository.runTransactionForResult(SyncNsTemporaryBasalTransaction(preparedData.temporaryBasals))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving temporary basal", it)
                }
                .blockingGet()
                .also { result ->
                    preparedData.temporaryBasals.clear()
                    result.inserted.forEach {
                        if (config.NSCLIENT.not()) preparedData.userEntries.add(
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
                        processed[NSTemporaryBasal::class.java.simpleName] = (processed[NSTemporaryBasal::class.java.simpleName] ?: 0) + 1
                    }
                    result.invalidated.forEach {
                        if (config.NSCLIENT.not()) preparedData.userEntries.add(
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
                        processed[NSTemporaryBasal::class.java.simpleName] = (processed[NSTemporaryBasal::class.java.simpleName] ?: 0) + 1
                    }
                    result.ended.forEach {
                        if (config.NSCLIENT.not()) preparedData.userEntries.add(
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
                        processed[NSTemporaryBasal::class.java.simpleName] = (processed[NSTemporaryBasal::class.java.simpleName] ?: 0) + 1
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId TemporaryBasal $it")
                        processed[NSTemporaryBasal::class.java.simpleName] = (processed[NSTemporaryBasal::class.java.simpleName] ?: 0) + 1
                    }
                    result.updatedDuration.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated duration TemporaryBasal $it")
                        processed[NSTemporaryBasal::class.java.simpleName] = (processed[NSTemporaryBasal::class.java.simpleName] ?: 0) + 1
                    }
                }

        sendLog("TemporaryBasal", processed[NSTemporaryBasal::class.java.simpleName])
        SystemClock.sleep(pause)

        if (preparedData.effectiveProfileSwitches.isNotEmpty())
            repository.runTransactionForResult(SyncNsEffectiveProfileSwitchTransaction(preparedData.effectiveProfileSwitches))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving EffectiveProfileSwitch", it)
                }
                .blockingGet()
                .also { result ->
                    preparedData.effectiveProfileSwitches.clear()
                    result.inserted.forEach {
                        if (config.NSCLIENT.not()) preparedData.userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.PROFILE_SWITCH, UserEntry.Sources.NSClient, "",
                                listOf(ValueWithUnit.Timestamp(it.timestamp))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted EffectiveProfileSwitch $it")
                        processed[NSEffectiveProfileSwitch::class.java.simpleName] = (processed[NSEffectiveProfileSwitch::class.java.simpleName] ?: 0) + 1
                    }
                    result.invalidated.forEach {
                        if (config.NSCLIENT.not()) preparedData.userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.PROFILE_SWITCH_REMOVED, UserEntry.Sources.NSClient, "",
                                listOf(ValueWithUnit.Timestamp(it.timestamp))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated EffectiveProfileSwitch $it")
                        processed[NSEffectiveProfileSwitch::class.java.simpleName] = (processed[NSEffectiveProfileSwitch::class.java.simpleName] ?: 0) + 1
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId EffectiveProfileSwitch $it")
                        processed[NSEffectiveProfileSwitch::class.java.simpleName] = (processed[NSEffectiveProfileSwitch::class.java.simpleName] ?: 0) + 1
                    }
                }

        sendLog("EffectiveProfileSwitch", processed[NSEffectiveProfileSwitch::class.java.simpleName])
        SystemClock.sleep(pause)

        if (preparedData.profileSwitches.isNotEmpty())
            repository.runTransactionForResult(SyncNsProfileSwitchTransaction(preparedData.profileSwitches))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving ProfileSwitch", it)
                }
                .blockingGet()
                .also { result ->
                    preparedData.profileSwitches.clear()
                    result.inserted.forEach {
                        if (config.NSCLIENT.not()) preparedData.userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.PROFILE_SWITCH, UserEntry.Sources.NSClient, "",
                                listOf(ValueWithUnit.Timestamp(it.timestamp))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Inserted ProfileSwitch $it")
                        processed[NSProfileSwitch::class.java.simpleName] = (processed[NSProfileSwitch::class.java.simpleName] ?: 0) + 1
                    }
                    result.invalidated.forEach {
                        if (config.NSCLIENT.not()) preparedData.userEntries.add(
                            UserEntryTransaction.Entry(
                                dateUtil.now(),
                                UserEntry.Action.PROFILE_SWITCH_REMOVED, UserEntry.Sources.NSClient, "",
                                listOf(ValueWithUnit.Timestamp(it.timestamp))
                            )
                        )
                        aapsLogger.debug(LTag.DATABASE, "Invalidated ProfileSwitch $it")
                        processed[NSProfileSwitch::class.java.simpleName] = (processed[NSProfileSwitch::class.java.simpleName] ?: 0) + 1
                    }
                    result.updatedNsId.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Updated nsId ProfileSwitch $it")
                        processed[NSProfileSwitch::class.java.simpleName] = (processed[NSProfileSwitch::class.java.simpleName] ?: 0) + 1
                    }
                }

        sendLog("ProfileSwitch", processed[NSProfileSwitch::class.java.simpleName])
        SystemClock.sleep(pause)

        uel.log(preparedData.userEntries)
        rxBus.send(EventNSClientNewLog("DONE", "", activePlugin.activeNsClient?.version ?: NsClient.Version.V3))
    }

    private fun sendLog(item: String, count: Long?) {
        count?.let {
            rxBus.send(EventNSClientNewLog("PROCESSED", "$item $it", activePlugin.activeNsClient?.version ?: NsClient.Version.V3))
        }
    }
}