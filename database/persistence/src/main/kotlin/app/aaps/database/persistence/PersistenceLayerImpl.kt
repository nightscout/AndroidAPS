package app.aaps.database.persistence

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.DS
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.HR
import app.aaps.core.data.model.NE
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.SC
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TDD
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.UE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.database.AppRepository
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.DeviceStatus
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.Food
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.HeartRate
import app.aaps.database.entities.ProfileSwitch
import app.aaps.database.entities.RunningMode
import app.aaps.database.entities.StepsCount
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.UserEntry
import app.aaps.database.persistence.converters.fromDb
import app.aaps.database.persistence.converters.toDb
import app.aaps.database.transactions.CancelCurrentTemporaryRunningModeIfAnyTransaction
import app.aaps.database.transactions.CancelCurrentTemporaryTargetIfAnyTransaction
import app.aaps.database.transactions.CgmSourceTransaction
import app.aaps.database.transactions.CutCarbsTransaction
import app.aaps.database.transactions.InsertAndCancelCurrentTemporaryTargetTransaction
import app.aaps.database.transactions.InsertBolusWithTempIdTransaction
import app.aaps.database.transactions.InsertIfNewByTimestampCarbsTransaction
import app.aaps.database.transactions.InsertIfNewByTimestampTherapyEventTransaction
import app.aaps.database.transactions.InsertOrUpdateApsResultTransaction
import app.aaps.database.transactions.InsertOrUpdateBolusCalculatorResultTransaction
import app.aaps.database.transactions.InsertOrUpdateBolusTransaction
import app.aaps.database.transactions.InsertOrUpdateCachedTotalDailyDoseTransaction
import app.aaps.database.transactions.InsertOrUpdateCarbsTransaction
import app.aaps.database.transactions.InsertOrUpdateEffectiveProfileSwitchTransaction
import app.aaps.database.transactions.InsertOrUpdateHeartRateTransaction
import app.aaps.database.transactions.InsertOrUpdateProfileSwitchTransaction
import app.aaps.database.transactions.InsertOrUpdateRunningModeTransaction
import app.aaps.database.transactions.InsertOrUpdateStepsCountTransaction
import app.aaps.database.transactions.InsertOrUpdateTherapyEventTransaction
import app.aaps.database.transactions.InsertTemporaryBasalWithTempIdTransaction
import app.aaps.database.transactions.InvalidateBolusCalculatorResultTransaction
import app.aaps.database.transactions.InvalidateBolusTransaction
import app.aaps.database.transactions.InvalidateCarbsTransaction
import app.aaps.database.transactions.InvalidateEffectiveProfileSwitchTransaction
import app.aaps.database.transactions.InvalidateExtendedBolusTransaction
import app.aaps.database.transactions.InvalidateFoodTransaction
import app.aaps.database.transactions.InvalidateGlucoseValueTransaction
import app.aaps.database.transactions.InvalidateProfileSwitchTransaction
import app.aaps.database.transactions.InvalidateRunningModeTransaction
import app.aaps.database.transactions.InvalidateTemporaryBasalTransaction
import app.aaps.database.transactions.InvalidateTemporaryBasalTransactionWithPumpId
import app.aaps.database.transactions.InvalidateTemporaryBasalWithTempIdTransaction
import app.aaps.database.transactions.InvalidateTemporaryTargetTransaction
import app.aaps.database.transactions.InvalidateTherapyEventTransaction
import app.aaps.database.transactions.InvalidateTherapyEventsWithNoteTransaction
import app.aaps.database.transactions.SyncBolusWithTempIdTransaction
import app.aaps.database.transactions.SyncNsBolusCalculatorResultTransaction
import app.aaps.database.transactions.SyncNsBolusTransaction
import app.aaps.database.transactions.SyncNsCarbsTransaction
import app.aaps.database.transactions.SyncNsEffectiveProfileSwitchTransaction
import app.aaps.database.transactions.SyncNsExtendedBolusTransaction
import app.aaps.database.transactions.SyncNsFoodTransaction
import app.aaps.database.transactions.SyncNsProfileSwitchTransaction
import app.aaps.database.transactions.SyncNsRunningModeTransaction
import app.aaps.database.transactions.SyncNsTemporaryBasalTransaction
import app.aaps.database.transactions.SyncNsTemporaryTargetTransaction
import app.aaps.database.transactions.SyncNsTherapyEventTransaction
import app.aaps.database.transactions.SyncPumpBolusTransaction
import app.aaps.database.transactions.SyncPumpCancelExtendedBolusIfAnyTransaction
import app.aaps.database.transactions.SyncPumpCancelTemporaryBasalIfAnyTransaction
import app.aaps.database.transactions.SyncPumpExtendedBolusTransaction
import app.aaps.database.transactions.SyncPumpTemporaryBasalTransaction
import app.aaps.database.transactions.SyncPumpTotalDailyDoseTransaction
import app.aaps.database.transactions.SyncTemporaryBasalWithTempIdTransaction
import app.aaps.database.transactions.UpdateNsIdBolusCalculatorResultTransaction
import app.aaps.database.transactions.UpdateNsIdBolusTransaction
import app.aaps.database.transactions.UpdateNsIdCarbsTransaction
import app.aaps.database.transactions.UpdateNsIdDeviceStatusTransaction
import app.aaps.database.transactions.UpdateNsIdEffectiveProfileSwitchTransaction
import app.aaps.database.transactions.UpdateNsIdExtendedBolusTransaction
import app.aaps.database.transactions.UpdateNsIdFoodTransaction
import app.aaps.database.transactions.UpdateNsIdGlucoseValueTransaction
import app.aaps.database.transactions.UpdateNsIdProfileSwitchTransaction
import app.aaps.database.transactions.UpdateNsIdRunningModeTransaction
import app.aaps.database.transactions.UpdateNsIdTemporaryBasalTransaction
import app.aaps.database.transactions.UpdateNsIdTemporaryTargetTransaction
import app.aaps.database.transactions.UpdateNsIdTherapyEventTransaction
import app.aaps.database.transactions.UserEntryTransaction
import app.aaps.database.transactions.VersionChangeTransaction
import dagger.Reusable
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Collections.emptyList
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass

@Reusable
class PersistenceLayerImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val repository: AppRepository,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val apsResultProvider: Provider<APSResult>
) : PersistenceLayer {

    private val compositeDisposable = CompositeDisposable()
    private suspend fun log(entries: List<UE>) {
        if (config.AAPSCLIENT.not())
            if (entries.isNotEmpty()) {
                insertUserEntries(entries)
                delay(entries.size * 10L)
            }
    }

    override fun clearDatabases() = repository.clearDatabases()
    override fun clearApsResults() = repository.clearApsResults()
    override suspend fun cleanupDatabase(keepDays: Long, deleteTrackedChanges: Boolean): String = withContext(Dispatchers.IO) {
        repository.cleanupDatabase(keepDays, deleteTrackedChanges)
    }

    // Flow-based change observation
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> observeChanges(type: Class<T>): Flow<List<T>> {
        // Map database entity changes to domain types
        return when (type) {
            BS::class.java  -> repository.changesOfType<Bolus>()
                .map { list -> list.map { it.fromDb() } }

            CA::class.java  -> repository.changesOfType<Carbs>()
                .map { list -> list.map { it.fromDb() } }

            BCR::class.java -> repository.changesOfType<BolusCalculatorResult>()
                .map { list -> list.map { it.fromDb() } }

            EB::class.java  -> repository.changesOfType<ExtendedBolus>()
                .map { list -> list.map { it.fromDb() } }

            TB::class.java  -> repository.changesOfType<TemporaryBasal>()
                .map { list -> list.map { it.fromDb() } }

            TT::class.java  -> repository.changesOfType<TemporaryTarget>()
                .map { list -> list.map { it.fromDb() } }

            TE::class.java  -> repository.changesOfType<TherapyEvent>()
                .map { list -> list.map { it.fromDb() } }

            PS::class.java  -> repository.changesOfType<ProfileSwitch>()
                .map { list -> list.map { it.fromDb() } }

            EPS::class.java -> repository.changesOfType<EffectiveProfileSwitch>()
                .map { list -> list.map { it.fromDb() } }

            GV::class.java  -> repository.changesOfType<GlucoseValue>()
                .map { list -> list.map { it.fromDb() } }

            UE::class.java  -> repository.changesOfType<UserEntry>()
                .map { list -> list.map { it.fromDb() } }

            RM::class.java  -> repository.changesOfType<RunningMode>()
                .map { list -> list.map { it.fromDb() } }

            DS::class.java  -> repository.changesOfType<DeviceStatus>()
                .map { list -> list.map { it.fromDb() } }

            HR::class.java  -> repository.changesOfType<HeartRate>()
                .map { list -> list.map { it.fromDb() } }

            SC::class.java  -> repository.changesOfType<StepsCount>()
                .map { list -> list.map { it.fromDb() } }

            FD::class.java  -> repository.changesOfType<Food>()
                .map { list -> list.map { it.fromDb() } }

            else            -> throw IllegalArgumentException("Unsupported observation type: ${type.simpleName}")
        } as Flow<List<T>>
    }

    override fun observeAnyChange(): Flow<Set<KClass<*>>> =
        repository.changeFlow()
            .map { changes ->
                changes.mapNotNull { entry ->
                    when (entry) {
                        is Bolus                  -> BS::class
                        is Carbs                  -> CA::class
                        is BolusCalculatorResult  -> BCR::class
                        is ExtendedBolus          -> EB::class
                        is TemporaryBasal         -> TB::class
                        is TemporaryTarget        -> TT::class
                        is TherapyEvent           -> TE::class
                        is ProfileSwitch          -> PS::class
                        is EffectiveProfileSwitch -> EPS::class
                        is GlucoseValue           -> GV::class
                        is Food                   -> FD::class
                        is UserEntry              -> UE::class
                        is RunningMode            -> RM::class
                        is DeviceStatus           -> DS::class
                        is HeartRate              -> HR::class
                        is StepsCount             -> SC::class
                        else                      -> null
                    }
                }.toSet()
            }
            .filter { it.isNotEmpty() }

    // BS
    override suspend fun getNewestBolus(): BS? = withContext(Dispatchers.IO) {
        repository.getNewestBolus()?.fromDb()
    }

    override suspend fun getOldestBolus(): BS? = withContext(Dispatchers.IO) {
        repository.getOldestBolus()?.fromDb()
    }

    override suspend fun getNewestBolusOfType(type: BS.Type): BS? = withContext(Dispatchers.IO) {
        repository.getLastBolusRecordOfType(type.toDb())?.fromDb()
    }

    override suspend fun getLastBolusId(): Long? = withContext(Dispatchers.IO) {
        repository.getLastBolusId()
    }

    override suspend fun getBoluses(): List<BS> =
        repository.getBoluses()
            .map { it.fromDb() }.toList()

    override suspend fun getBolusByNSId(nsId: String): BS? = withContext(Dispatchers.IO) {
        repository.getBolusByNSId(nsId)?.fromDb()
    }

    override suspend fun getBolusesFromTime(startTime: Long, ascending: Boolean): List<BS> = withContext(Dispatchers.IO) {
        repository.getBolusesDataFromTime(startTime, ascending)
            .map { it.fromDb() }.toList()
    }

    override suspend fun getBolusesFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<BS> = withContext(Dispatchers.IO) {
        repository.getBolusesDataFromTimeToTime(startTime, endTime, ascending)
            .map { it.fromDb() }.toList()
    }

    override suspend fun getBolusesFromTimeIncludingInvalid(startTime: Long, ascending: Boolean): List<BS> = withContext(Dispatchers.IO) {
        repository.getBolusesIncludingInvalidFromTime(startTime, ascending)
            .map { it.fromDb() }.toList()
    }

    override suspend fun getNextSyncElementBolus(id: Long): Pair<BS, BS>? = withContext(Dispatchers.IO) {
        repository.getNextSyncElementBolus(id)?.let { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }
    }

    override suspend fun insertOrUpdateBolus(bolus: BS, action: Action, source: Sources, note: String?): PersistenceLayer.TransactionResult<BS> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InsertOrUpdateBolusTransaction(bolus.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<BS>()
            val ueValues = mutableListOf<UE>()
            result.inserted.forEach {
                ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = action,
                        source = source,
                        note = it.notes ?: note ?: "",
                        values = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Insulin(it.amount))
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Inserted Bolus $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.updated.forEach {
                ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = action,
                        source = source,
                        note = it.notes ?: note ?: "",
                        values = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Insulin(it.amount))
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Updated Bolus $it")
                transactionResult.updated.add(it.fromDb())
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving Bolus", e)
            throw e
        }
    }

    override suspend fun updateBolusNoLogging(bolus: BS): Unit = withContext(Dispatchers.IO) {
        repository.runTransactionForResultSuspend(InsertOrUpdateBolusTransaction(bolus.toDb()))
        Unit
    }

    override suspend fun insertBolusWithTempId(bolus: BS): PersistenceLayer.TransactionResult<BS> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InsertBolusWithTempIdTransaction(bolus.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<BS>()
            result.inserted.forEach {
                aapsLogger.debug(LTag.DATABASE, "Inserted Bolus $it")
                transactionResult.inserted.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving Bolus", e)
            throw e
        }
    }

    override suspend fun invalidateBolus(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): PersistenceLayer.TransactionResult<BS> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InvalidateBolusTransaction(id))
            val transactionResult = PersistenceLayer.TransactionResult<BS>()
            val ueValues = mutableListOf<UE>()
            result.invalidated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Invalidated Bolus from ${source.name} $it")
                transactionResult.invalidated.add(it.fromDb())
                ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while invalidating Bolus", e)
            throw e
        }
    }

    override suspend fun syncPumpBolus(bolus: BS, type: BS.Type?): PersistenceLayer.TransactionResult<BS> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(SyncPumpBolusTransaction(bolus.toDb(), type?.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<BS>()
            result.inserted.forEach {
                aapsLogger.debug(LTag.DATABASE, "Inserted Bolus $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated Bolus $it")
                transactionResult.updated.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving Bolus", e)
            throw e
        }
    }

    override suspend fun syncPumpBolusWithTempId(bolus: BS, type: BS.Type?): PersistenceLayer.TransactionResult<BS> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(SyncBolusWithTempIdTransaction(bolus.toDb(), type?.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<BS>()
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated Bolus $it")
                transactionResult.updated.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving Bolus", e)
            throw e
        }
    }

    override suspend fun syncNsBolus(boluses: List<BS>, doLog: Boolean): PersistenceLayer.TransactionResult<BS> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(SyncNsBolusTransaction(boluses.asSequence().map { it.toDb() }.toList()))
            val transactionResult = PersistenceLayer.TransactionResult<BS>()
            val ueValues = mutableListOf<UE>()
            result.inserted.forEach {
                if (doLog) ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = Action.BOLUS,
                        source = Sources.NSClient,
                        note = it.notes ?: "",
                        values = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Insulin(it.amount))
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Inserted bolus $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.invalidated.forEach {
                if (doLog) ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = Action.BOLUS_REMOVED,
                        source = Sources.NSClient,
                        note = "",
                        values = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Insulin(it.amount))
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Invalidated bolus $it")
                transactionResult.invalidated.add(it.fromDb())
            }
            result.updatedNsId.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated nsId of bolus $it")
                transactionResult.updatedNsId.add(it.fromDb())
            }
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated amount of bolus $it")
                transactionResult.updated.add(it.fromDb())
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving bolus", e)
            throw e
        }
    }

    override suspend fun updateBolusesNsIds(boluses: List<BS>): PersistenceLayer.TransactionResult<BS> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(UpdateNsIdBolusTransaction(boluses.asSequence().map { it.toDb() }.toList()))
            val transactionResult = PersistenceLayer.TransactionResult<BS>()
            result.updatedNsId.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated nsId of Bolus $it")
                transactionResult.updatedNsId.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Updated nsId of Bolus failed", e)
            throw e
        }
    }

    override suspend fun getNewestCarbs(): CA? = withContext(Dispatchers.IO) {
        repository.getLastCarbs()?.fromDb()
    }

    override suspend fun getOldestCarbs(): CA? = withContext(Dispatchers.IO) {
        repository.getOldestCarbs()?.fromDb()
    }

    // CA
    override suspend fun getLastCarbsId(): Long? = withContext(Dispatchers.IO) {
        repository.getLastCarbsId()
    }

    override suspend fun getCarbsByNSId(nsId: String): CA? = withContext(Dispatchers.IO) {
        repository.getCarbsByNSId(nsId)?.fromDb()
    }

    override suspend fun getCarbsFromTime(startTime: Long, ascending: Boolean): List<CA> = withContext(Dispatchers.IO) {
        repository.getCarbsDataFromTime(startTime, ascending)
            .map { it.fromDb() }.toList()
    }

    override suspend fun getCarbsFromTimeIncludingInvalid(startTime: Long, ascending: Boolean): List<CA> = withContext(Dispatchers.IO) {
        repository.getCarbsIncludingInvalidFromTime(startTime, ascending)
            .map { it.fromDb() }.toList()
    }

    override suspend fun getCarbsFromTimeExpanded(startTime: Long, ascending: Boolean): List<CA> = withContext(Dispatchers.IO) {
        repository.getCarbsDataFromTimeExpanded(startTime, ascending)
            .map { it.fromDb() }.toList()
    }

    override suspend fun getCarbsFromTimeNotExpanded(startTime: Long, ascending: Boolean): List<CA> = withContext(Dispatchers.IO) {
        repository.getCarbsDataFromTimeNotExpanded(startTime, ascending).map { it.fromDb() }
    }

    override suspend fun getCarbsFromTimeToTimeExpanded(startTime: Long, endTime: Long, ascending: Boolean): List<CA> = withContext(Dispatchers.IO) {
        repository.getCarbsDataFromTimeToTimeExpanded(startTime, endTime, ascending)
            .map { it.fromDb() }.toList()
    }

    override suspend fun getNextSyncElementCarbs(id: Long): Pair<CA, CA>? = withContext(Dispatchers.IO) {
        repository.getNextSyncElementCarbs(id)?.let { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }
    }

    override suspend fun insertOrUpdateCarbs(carbs: CA, action: Action, source: Sources, note: String?): PersistenceLayer.TransactionResult<CA> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InsertOrUpdateCarbsTransaction(carbs.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<CA>()
            val ueValues = mutableListOf<UE>()
            result.inserted.forEach {
                ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = action,
                        source = source,
                        note = note ?: "",
                        values = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Gram(it.amount.toInt()))
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Inserted Carbs $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.updated.forEach {
                ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = action,
                        source = source,
                        note = note ?: "",
                        values = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Gram(it.amount.toInt()))
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Inserted Carbs $it")
                transactionResult.updated.add(it.fromDb())
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving Carbs", e)
            throw e
        }
    }

    override suspend fun insertPumpCarbsIfNewByTimestamp(carbs: CA): PersistenceLayer.TransactionResult<CA> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InsertIfNewByTimestampCarbsTransaction(carbs.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<CA>()
            result.inserted.forEach {
                aapsLogger.debug(LTag.DATABASE, "Inserted Carbs $it")
                transactionResult.inserted.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving Carbs", e)
            throw e
        }
    }

    override suspend fun invalidateCarbs(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): PersistenceLayer.TransactionResult<CA> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InvalidateCarbsTransaction(id))
            val transactionResult = PersistenceLayer.TransactionResult<CA>()
            val ueValues = mutableListOf<UE>()
            result.invalidated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Invalidated Carbs from ${source.name} $it")
                transactionResult.invalidated.add(it.fromDb())
                ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while invalidating Carbs", e)
            throw e
        }
    }

    override suspend fun cutCarbs(id: Long, timestamp: Long): PersistenceLayer.TransactionResult<CA> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(CutCarbsTransaction(id, timestamp))
            val transactionResult = PersistenceLayer.TransactionResult<CA>()
            result.invalidated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Invalidated Carbs from $it")
                transactionResult.invalidated.add(it.fromDb())
            }
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated Carbs from $it")
                transactionResult.updated.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while cutting Carbs", e)
            throw e
        }
    }

    override suspend fun syncNsCarbs(carbs: List<CA>, doLog: Boolean): PersistenceLayer.TransactionResult<CA> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(SyncNsCarbsTransaction(carbs.asSequence().map { it.toDb() }.toList(), config.AAPSCLIENT))
            val transactionResult = PersistenceLayer.TransactionResult<CA>()
            val ueValues = mutableListOf<UE>()
            result.inserted.forEach {
                if (doLog) ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = Action.CARBS,
                        source = Sources.NSClient,
                        note = it.notes ?: "",
                        values = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Gram(it.amount.toInt()))
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Inserted carbs $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.invalidated.forEach {
                if (doLog) ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = Action.CARBS_REMOVED,
                        source = Sources.NSClient,
                        note = "",
                        values = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Gram(it.amount.toInt()))
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Invalidated carbs $it")
                transactionResult.invalidated.add(it.fromDb())
            }
            result.updated.forEach {
                if (doLog) ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = Action.CARBS,
                        source = Sources.NSClient,
                        note = it.notes ?: "",
                        values = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Gram(it.amount.toInt()))
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Updated carbs $it")
                transactionResult.updated.add(it.fromDb())
            }
            result.updatedNsId.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated nsId carbs $it")
                transactionResult.updatedNsId.add(it.fromDb())
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving carbs", e)
            throw e
        }
    }

    override suspend fun updateCarbsNsIds(carbs: List<CA>): PersistenceLayer.TransactionResult<CA> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(UpdateNsIdCarbsTransaction(carbs.asSequence().map { it.toDb() }.toList()))
            val transactionResult = PersistenceLayer.TransactionResult<CA>()
            result.updatedNsId.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated nsId of Carbs $it")
                transactionResult.updatedNsId.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Updated nsId of Carbs failed", e)
            throw e
        }
    }

    override suspend fun getBolusCalculatorResultByNSId(nsId: String): BCR? = withContext(Dispatchers.IO) {
        repository.findBolusCalculatorResultByNSId(nsId)?.fromDb()
    }

    // BCR
    override suspend fun getBolusCalculatorResultsFromTime(startTime: Long, ascending: Boolean): List<BCR> = withContext(Dispatchers.IO) {
        repository.getBolusCalculatorResultsDataFromTime(startTime, ascending).map { it.fromDb() }.toList()
    }

    override suspend fun getBolusCalculatorResultsIncludingInvalidFromTime(startTime: Long, ascending: Boolean): List<BCR> = withContext(Dispatchers.IO) {
        repository.getBolusCalculatorResultsIncludingInvalidFromTime(startTime, ascending).map { it.fromDb() }
    }

    override suspend fun getNextSyncElementBolusCalculatorResult(id: Long): Pair<BCR, BCR>? = withContext(Dispatchers.IO) {
        repository.getNextSyncElementBolusCalculatorResult(id)?.let { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }
    }

    override suspend fun getLastBolusCalculatorResultId(): Long? = withContext(Dispatchers.IO) {
        repository.getLastBolusCalculatorResultId()
    }

    override suspend fun insertOrUpdateBolusCalculatorResult(bolusCalculatorResult: BCR): PersistenceLayer.TransactionResult<BCR> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InsertOrUpdateBolusCalculatorResultTransaction(bolusCalculatorResult.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<BCR>()
            result.inserted.forEach {
                aapsLogger.debug(LTag.DATABASE, "Inserted BolusCalculatorResult $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated BolusCalculatorResult $it")
                transactionResult.updated.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving BolusCalculatorResult", e)
            throw e
        }
    }

    override suspend fun syncNsBolusCalculatorResults(bolusCalculatorResults: List<BCR>): PersistenceLayer.TransactionResult<BCR> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(SyncNsBolusCalculatorResultTransaction(bolusCalculatorResults.asSequence().map { it.toDb() }.toList()))
            val transactionResult = PersistenceLayer.TransactionResult<BCR>()
            result.inserted.forEach {
                aapsLogger.debug(LTag.DATABASE, "Inserted BolusCalculatorResult $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.invalidated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Invalidated BolusCalculatorResult $it")
                transactionResult.invalidated.add(it.fromDb())
            }
            result.updatedNsId.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated nsId BolusCalculatorResult $it")
                transactionResult.updatedNsId.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving BolusCalculatorResult", e)
            throw e
        }
    }

    override suspend fun updateBolusCalculatorResultsNsIds(bolusCalculatorResults: List<BCR>): PersistenceLayer.TransactionResult<BCR> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(UpdateNsIdBolusCalculatorResultTransaction(bolusCalculatorResults.asSequence().map { it.toDb() }.toList()))
            val transactionResult = PersistenceLayer.TransactionResult<BCR>()
            result.updatedNsId.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated nsId BolusCalculatorResult $it")
                transactionResult.updatedNsId.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving BolusCalculatorResult", e)
            throw e
        }
    }

    override suspend fun invalidateBolusCalculatorResult(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): PersistenceLayer.TransactionResult<BCR> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InvalidateBolusCalculatorResultTransaction(id))
            val transactionResult = PersistenceLayer.TransactionResult<BCR>()
            val ueValues = mutableListOf<UE>()
            result.invalidated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Invalidated BolusCalculatorResult from ${source.name} $it")
                transactionResult.invalidated.add(it.fromDb())
                ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while invalidating BolusCalculatorResult", e)
            throw e
        }
    }

    // GV
    override suspend fun getLastGlucoseValue(): GV? = withContext(Dispatchers.IO) {
        repository.getLastGlucoseValue()?.fromDb()
    }

    override suspend fun getLastGlucoseValueId(): Long? = withContext(Dispatchers.IO) {
        repository.getLastGlucoseValueId()
    }

    override suspend fun getNextSyncElementGlucoseValue(id: Long): Pair<GV, GV>? = withContext(Dispatchers.IO) {
        repository.getNextSyncElementGlucoseValue(id)?.let { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }
    }

    override suspend fun getBgReadingsDataFromTimeToTime(start: Long, end: Long, ascending: Boolean): List<GV> = withContext(Dispatchers.IO) {
        repository.compatGetBgReadingsDataFromTime(start, end, ascending).map { it.fromDb() }
    }

    override suspend fun getBgReadingsDataFromTime(timestamp: Long, ascending: Boolean): List<GV> = withContext(Dispatchers.IO) {
        repository.compatGetBgReadingsDataFromTime(timestamp, ascending).map { it.fromDb() }
    }

    override suspend fun getBgReadingByNSId(nsId: String): GV? = withContext(Dispatchers.IO) {
        repository.findBgReadingByNSId(nsId)?.fromDb()
    }

    override suspend fun invalidateGlucoseValue(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): PersistenceLayer.TransactionResult<GV> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InvalidateGlucoseValueTransaction(id))
            val transactionResult = PersistenceLayer.TransactionResult<GV>()
            val ueValues = mutableListOf<UE>()
            result.invalidated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Invalidated GlucoseValue from ${source.name} $it")
                transactionResult.invalidated.add(it.fromDb())
                ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while invalidating GlucoseValue", e)
            throw e
        }
    }

    private fun PersistenceLayer.Calibration.toDb() = CgmSourceTransaction.Calibration(timestamp, value, glucoseUnit.toDb())
    override suspend fun insertCgmSourceData(caller: Sources, glucoseValues: List<GV>, calibrations: List<PersistenceLayer.Calibration>, sensorInsertionTime: Long?)
        : PersistenceLayer.TransactionResult<GV> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(CgmSourceTransaction(glucoseValues.asSequence().map { it.toDb() }.toList(), calibrations.asSequence().map { it.toDb() }.toList(), sensorInsertionTime))
            val transactionResult = PersistenceLayer.TransactionResult<GV>()
            val ueValues = mutableListOf<UE>()
            result.inserted.forEach {
                aapsLogger.debug(LTag.DATABASE, "Inserted GlucoseValue from ${caller.name} $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated GlucoseValue from ${caller.name} $it")
                transactionResult.updated.add(it.fromDb())
            }
            result.sensorInsertionsInserted.forEach {
                ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = Action.CAREPORTAL,
                        source = caller,
                        note = "",
                        values = listOf(
                            ValueWithUnit.Timestamp(it.timestamp),
                            ValueWithUnit.TEType(it.type.fromDb())
                        )
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Inserted sensor insertion from ${caller.name} $it")
                transactionResult.sensorInsertionsInserted.add(it.fromDb())
            }
            result.calibrationsInserted.forEach { calibration ->
                calibration.glucose?.let { glucoseValue ->
                    ueValues.add(
                        UE(
                            timestamp = dateUtil.now(),
                            action = Action.CALIBRATION,
                            source = caller,
                            note = "",
                            values = listOf(
                                ValueWithUnit.Timestamp(calibration.timestamp),
                                ValueWithUnit.TEType(calibration.type.fromDb()),
                                ValueWithUnit.fromGlucoseUnit(glucoseValue, calibration.glucoseUnit.fromDb())
                            )
                        )
                    )
                }
                aapsLogger.debug(LTag.DATABASE, "Inserted calibration from ${caller.name} $calibration")
                transactionResult.calibrationsInserted.add(calibration.fromDb())
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving cgm values from ${caller.name}", e)
            throw e
        }
    }

    override suspend fun updateGlucoseValuesNsIds(glucoseValues: List<GV>): PersistenceLayer.TransactionResult<GV> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(UpdateNsIdGlucoseValueTransaction(glucoseValues.asSequence().map { it.toDb() }.toList()))
            val transactionResult = PersistenceLayer.TransactionResult<GV>()
            result.updatedNsId.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated nsId of GlucoseValue $it")
                transactionResult.updatedNsId.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Updated nsId of GlucoseValue failed", e)
            throw e
        }
    }

    override suspend fun getOldestEffectiveProfileSwitch(): EPS? = withContext(Dispatchers.IO) {
        repository.getOldestEffectiveProfileSwitchRecord()?.fromDb()
    }

    override suspend fun updateExtendedBolusesNsIds(extendedBoluses: List<EB>): PersistenceLayer.TransactionResult<EB> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(UpdateNsIdExtendedBolusTransaction(extendedBoluses.asSequence().map { it.toDb() }.toList()))
            val transactionResult = PersistenceLayer.TransactionResult<EB>()
            result.updatedNsId.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated nsId of ExtendedBolus $it")
                transactionResult.updatedNsId.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Updated nsId of ExtendedBolus failed", e)
            throw e
        }
    }

    override suspend fun syncPumpExtendedBolus(extendedBolus: EB): PersistenceLayer.TransactionResult<EB> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(SyncPumpExtendedBolusTransaction(extendedBolus.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<EB>()
            result.inserted.forEach {
                aapsLogger.debug(LTag.DATABASE, "Inserted ExtendedBolus $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated ExtendedBolus $it")
                transactionResult.updated.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while syncing ExtendedBolus", e)
            throw e
        }
    }

    override suspend fun syncPumpStopExtendedBolusWithPumpId(timestamp: Long, endPumpId: Long, pumpType: PumpType, pumpSerial: String): PersistenceLayer.TransactionResult<EB> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(SyncPumpCancelExtendedBolusIfAnyTransaction(timestamp, endPumpId, pumpType.toDb(), pumpSerial))
            val transactionResult = PersistenceLayer.TransactionResult<EB>()
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated ExtendedBolus $it")
                transactionResult.updated.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while syncing ExtendedBolus", e)
            throw e
        }
    }

    // EPS
    override suspend fun getEffectiveProfileSwitchActiveAt(timestamp: Long): EPS? = withContext(Dispatchers.IO) {
        repository.getEffectiveProfileSwitchActiveAt(timestamp)?.fromDb()
    }

    override suspend fun getEffectiveProfileSwitchByNSId(nsId: String): EPS? = withContext(Dispatchers.IO) {
        repository.findEffectiveProfileSwitchByNSId(nsId)?.fromDb()
    }

    override suspend fun getEffectiveProfileSwitchesFromTime(startTime: Long, ascending: Boolean): List<EPS> = withContext(Dispatchers.IO) {
        repository.getEffectiveProfileSwitchesFromTime(startTime, ascending)
            .map { it.fromDb() }
    }

    override suspend fun getEffectiveProfileSwitchesIncludingInvalidFromTime(startTime: Long, ascending: Boolean): List<EPS> = withContext(Dispatchers.IO) {
        repository.getEffectiveProfileSwitchesIncludingInvalidFromTime(startTime, ascending)
            .map { it.fromDb() }
    }

    override suspend fun getEffectiveProfileSwitchesFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<EPS> = withContext(Dispatchers.IO) {
        repository.getEffectiveProfileSwitchesFromTimeToTime(startTime, endTime, ascending).map { it.fromDb() }
    }

    override suspend fun getNextSyncElementEffectiveProfileSwitch(id: Long): Pair<EPS, EPS>? = withContext(Dispatchers.IO) {
        repository.getNextSyncElementEffectiveProfileSwitch(id)?.let { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }
    }

    override suspend fun getLastEffectiveProfileSwitchId(): Long? = withContext(Dispatchers.IO) {
        repository.getLastEffectiveProfileSwitchId()
    }

    override suspend fun insertOrUpdateEffectiveProfileSwitch(effectiveProfileSwitch: EPS): PersistenceLayer.TransactionResult<EPS> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InsertOrUpdateEffectiveProfileSwitchTransaction(effectiveProfileSwitch.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<EPS>()
            result.inserted.forEach {
                aapsLogger.debug(LTag.DATABASE, "Inserted EffectiveProfileSwitch $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated EffectiveProfileSwitch $it")
                transactionResult.updated.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while inserting EffectiveProfileSwitch", e)
            throw e
        }
    }

    override suspend fun updateEffectiveProfileSwitchNoLogging(effectiveProfileSwitch: EPS): Unit = withContext(Dispatchers.IO) {
        repository.runTransactionForResultSuspend(InsertOrUpdateEffectiveProfileSwitchTransaction(effectiveProfileSwitch.toDb()))
        Unit
    }

    override suspend fun invalidateEffectiveProfileSwitch(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): PersistenceLayer.TransactionResult<EPS> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InvalidateEffectiveProfileSwitchTransaction(id))
            val transactionResult = PersistenceLayer.TransactionResult<EPS>()
            val ueValues = mutableListOf<UE>()
            result.invalidated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Invalidated EffectiveProfileSwitch from ${source.name} $it")
                transactionResult.invalidated.add(it.fromDb())
                ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while invalidating EffectiveProfileSwitch", e)
            throw e
        }
    }

    override suspend fun syncNsEffectiveProfileSwitches(effectiveProfileSwitches: List<EPS>, doLog: Boolean): PersistenceLayer.TransactionResult<EPS> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(SyncNsEffectiveProfileSwitchTransaction(effectiveProfileSwitches.map { it.toDb() }))
            val transactionResult = PersistenceLayer.TransactionResult<EPS>()
            val ueValues = mutableListOf<UE>()
            result.inserted.forEach {
                if (config.AAPSCLIENT.not())
                    if (doLog) ueValues.add(
                        UE(
                            timestamp = dateUtil.now(),
                            action = Action.PROFILE_SWITCH,
                            source = Sources.NSClient,
                            note = "",
                            values = listOf(ValueWithUnit.Timestamp(it.timestamp))
                        )
                    )
                aapsLogger.debug(LTag.DATABASE, "Inserted EffectiveProfileSwitch $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.invalidated.forEach {
                if (config.AAPSCLIENT.not())
                    if (doLog) ueValues.add(
                        UE(
                            timestamp = dateUtil.now(),
                            action = Action.PROFILE_SWITCH_REMOVED,
                            source = Sources.NSClient,
                            note = "",
                            values = listOf(ValueWithUnit.Timestamp(it.timestamp))
                        )
                    )
                aapsLogger.debug(LTag.DATABASE, "Invalidated EffectiveProfileSwitch $it")
                transactionResult.invalidated.add(it.fromDb())
            }
            result.updatedNsId.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated nsId EffectiveProfileSwitch $it")
                transactionResult.updatedNsId.add(it.fromDb())
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving EffectiveProfileSwitch", e)
            throw e
        }
    }

    override suspend fun updateEffectiveProfileSwitchesNsIds(effectiveProfileSwitches: List<EPS>): PersistenceLayer.TransactionResult<EPS> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(UpdateNsIdEffectiveProfileSwitchTransaction(effectiveProfileSwitches.asSequence().map { it.toDb() }.toList()))
            val transactionResult = PersistenceLayer.TransactionResult<EPS>()
            result.updatedNsId.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated nsId of EffectiveProfileSwitch $it")
                transactionResult.updatedNsId.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Updated nsId of EffectiveProfileSwitch failed", e)
            throw e
        }
    }

    override suspend fun getProfileSwitchActiveAt(timestamp: Long): PS? = withContext(Dispatchers.IO) {
        repository.getProfileSwitchActiveAt(timestamp)?.fromDb()
    }

    override suspend fun getEffectiveProfileSwitches(): List<EPS> =
        repository.getAllEffectiveProfileSwitches().map { it.fromDb() }

    override suspend fun getProfileSwitchByNSId(nsId: String): PS? = withContext(Dispatchers.IO) {
        repository.findProfileSwitchByNSId(nsId)?.fromDb()
    }

    override suspend fun getPermanentProfileSwitchActiveAt(timestamp: Long): PS? = withContext(Dispatchers.IO) {
        repository.getPermanentProfileSwitchActiveAt(timestamp)?.fromDb()
    }

    override suspend fun getProfileSwitches(): List<PS> = withContext(Dispatchers.IO) {
        repository.getAllProfileSwitches().map { it.fromDb() }
    }

    // RUNNING MODE
    override suspend fun getRunningModesFromTime(startTime: Long, ascending: Boolean): List<RM> = withContext(Dispatchers.IO) {
        repository.getRunningModesFromTime(startTime, ascending)
            .map { it.fromDb() }
    }

    override suspend fun getRunningModesFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<RM> = withContext(Dispatchers.IO) {
        repository.getRunningModesFromTimeToTime(startTime, endTime, ascending).map { it.fromDb() }
    }

    override suspend fun getRunningModesIncludingInvalidFromTime(startTime: Long, ascending: Boolean): List<RM> = withContext(Dispatchers.IO) {
        repository.getRunningModesIncludingInvalidFromTime(startTime, ascending)
            .map { it.fromDb() }
    }

    override suspend fun getNextSyncElementRunningMode(id: Long): Pair<RM, RM>? = withContext(Dispatchers.IO) {
        repository.getNextSyncElementRunningMode(id)?.let { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }
    }

    override suspend fun getLastRunningModeId(): Long? = withContext(Dispatchers.IO) {
        repository.getLastRunningModeId()
    }

    override suspend fun insertOrUpdateRunningMode(runningMode: RM, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): PersistenceLayer.TransactionResult<RM> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InsertOrUpdateRunningModeTransaction(runningMode.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<RM>()
            val ueValues = mutableListOf<UE>()
            result.inserted.forEach {
                aapsLogger.debug(LTag.DATABASE, "Inserted RunningMode from ${source.name} $it")
                ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
                transactionResult.inserted.add(it.fromDb())
            }
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated RunningMode from ${source.name} $it")
                ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
                transactionResult.updated.add(it.fromDb())
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while inserting RunningMode", e)
            throw e
        }
    }

    override suspend fun invalidateRunningMode(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): PersistenceLayer.TransactionResult<RM> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InvalidateRunningModeTransaction(id))
            val transactionResult = PersistenceLayer.TransactionResult<RM>()
            val ueValues = mutableListOf<UE>()
            result.invalidated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Invalidated RunningMode from ${source.name} $it")
                transactionResult.invalidated.add(it.fromDb())
                ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while invalidating RunningMode", e)
            throw e
        }
    }

    override suspend fun cancelCurrentRunningMode(timestamp: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): PersistenceLayer.TransactionResult<RM> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(CancelCurrentTemporaryRunningModeIfAnyTransaction(timestamp))
            val transactionResult = PersistenceLayer.TransactionResult<RM>()
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated RunningMode from ${source.name} $it")
                transactionResult.updated.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while canceling RunningMode", e)
            throw e
        }
    }

    override suspend fun syncNsRunningModes(runningModes: List<RM>, doLog: Boolean): PersistenceLayer.TransactionResult<RM> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(SyncNsRunningModeTransaction(runningModes.map { it.toDb() }))
            val transactionResult = PersistenceLayer.TransactionResult<RM>()
            val ueValues = mutableListOf<UE>()
            result.inserted.forEach {
                if (config.AAPSCLIENT.not())
                    if (doLog) ueValues.add(
                        UE(
                            timestamp = dateUtil.now(),
                            action = Action.RUNNING_MODE,
                            source = Sources.NSClient,
                            note = "",
                            values = listOf(ValueWithUnit.Timestamp(it.timestamp))
                        )
                    )
                aapsLogger.debug(LTag.DATABASE, "Inserted RunningMode $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.invalidated.forEach {
                if (config.AAPSCLIENT.not())
                    if (doLog) ueValues.add(
                        UE(
                            timestamp = dateUtil.now(),
                            action = Action.RUNNING_MODE_REMOVED,
                            source = Sources.NSClient,
                            note = "",
                            values = listOf(ValueWithUnit.Timestamp(it.timestamp))
                        )
                    )
                aapsLogger.debug(LTag.DATABASE, "Invalidated RunningMode $it")
                transactionResult.invalidated.add(it.fromDb())
            }
            result.updatedDuration.forEach {
                if (config.AAPSCLIENT.not())
                    if (doLog) ueValues.add(
                        UE(
                            timestamp = dateUtil.now(),
                            action = Action.RUNNING_MODE_UPDATED,
                            source = Sources.NSClient,
                            note = "",
                            values = listOf(ValueWithUnit.Timestamp(it.timestamp))
                        )
                    )
                aapsLogger.debug(LTag.DATABASE, "Updated duration RunningMode $it")
                transactionResult.invalidated.add(it.fromDb())
            }
            result.updatedNsId.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated nsId RunningMode $it")
                transactionResult.updatedNsId.add(it.fromDb())
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving RunningMode", e)
            throw e
        }
    }

    override suspend fun updateRunningModesNsIds(runningModes: List<RM>): PersistenceLayer.TransactionResult<RM> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(UpdateNsIdRunningModeTransaction(runningModes.asSequence().map { it.toDb() }.toList()))
            val transactionResult = PersistenceLayer.TransactionResult<RM>()
            result.updatedNsId.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated nsId of RunningMode $it")
                transactionResult.updatedNsId.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Updated nsId of RunningMode failed", e)
            throw e
        }
    }

    override suspend fun getRunningModeActiveAt(timestamp: Long): RM = withContext(Dispatchers.IO) {
        repository.getRunningModeActiveAt(timestamp)?.fromDb()
            ?: RM(timestamp = 0, mode = RM.DEFAULT_MODE, duration = 0)
    }

    override suspend fun getRunningModeByNSId(nsId: String): RM? = withContext(Dispatchers.IO) {
        repository.findRunningModeByNSId(nsId)?.fromDb()
    }

    override suspend fun getPermanentRunningModeActiveAt(timestamp: Long): RM = withContext(Dispatchers.IO) {
        repository.getPermanentRunningModeActiveAt(timestamp)?.fromDb()
            ?: RM(timestamp = 0, mode = RM.DEFAULT_MODE, duration = 0)
    }

    override suspend fun getRunningModes(): List<RM> = withContext(Dispatchers.IO) {
        repository.getAllRunningModes().map { it.fromDb() }
    }

    // PS
    override suspend fun getProfileSwitchesFromTime(startTime: Long, ascending: Boolean): List<PS> = withContext(Dispatchers.IO) {
        repository.getProfileSwitchesFromTime(startTime, ascending)
            .map { it.fromDb() }
    }

    override suspend fun getProfileSwitchesIncludingInvalidFromTime(startTime: Long, ascending: Boolean): List<PS> = withContext(Dispatchers.IO) {
        repository.getProfileSwitchesIncludingInvalidFromTime(startTime, ascending)
            .map { it.fromDb() }
    }

    override suspend fun getNextSyncElementProfileSwitch(id: Long): Pair<PS, PS>? = withContext(Dispatchers.IO) {
        repository.getNextSyncElementProfileSwitch(id)?.let { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }
    }

    override suspend fun getLastProfileSwitchId(): Long? = withContext(Dispatchers.IO) {
        repository.getLastProfileSwitchId()
    }

    override suspend fun insertOrUpdateProfileSwitch(profileSwitch: PS, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): PersistenceLayer.TransactionResult<PS> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InsertOrUpdateProfileSwitchTransaction(profileSwitch.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<PS>()
            val ueValues = mutableListOf<UE>()
            result.inserted.forEach {
                aapsLogger.debug(LTag.DATABASE, "Inserted ProfileSwitch from ${source.name} $it")
                transactionResult.inserted.add(it.fromDb())
                ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
            }
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated ProfileSwitch from ${source.name} $it")
                transactionResult.updated.add(it.fromDb())
                ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while inserting ProfileSwitch", e)
            throw e
        }
    }

    override suspend fun updateProfileSwitchNoLogging(profileSwitch: PS): Unit = withContext(Dispatchers.IO) {
        repository.runTransactionForResultSuspend(InsertOrUpdateProfileSwitchTransaction(profileSwitch.toDb()))
        Unit
    }

    override suspend fun invalidateProfileSwitch(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): PersistenceLayer.TransactionResult<PS> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InvalidateProfileSwitchTransaction(id))
            val transactionResult = PersistenceLayer.TransactionResult<PS>()
            val ueValues = mutableListOf<UE>()
            result.invalidated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Invalidated ProfileSwitch from ${source.name} $it")
                transactionResult.invalidated.add(it.fromDb())
                ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while invalidating ProfileSwitch", e)
            throw e
        }
    }

    override suspend fun syncNsProfileSwitches(profileSwitches: List<PS>, doLog: Boolean): PersistenceLayer.TransactionResult<PS> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(SyncNsProfileSwitchTransaction(profileSwitches.map { it.toDb() }))
            val transactionResult = PersistenceLayer.TransactionResult<PS>()
            val ueValues = mutableListOf<UE>()
            result.inserted.forEach {
                if (config.AAPSCLIENT.not())
                    if (doLog) ueValues.add(
                        UE(
                            timestamp = dateUtil.now(),
                            action = Action.PROFILE_SWITCH,
                            source = Sources.NSClient,
                            note = "",
                            values = listOf(ValueWithUnit.Timestamp(it.timestamp))
                        )
                    )
                aapsLogger.debug(LTag.DATABASE, "Inserted ProfileSwitch $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.invalidated.forEach {
                if (config.AAPSCLIENT.not())
                    if (doLog) ueValues.add(
                        UE(
                            timestamp = dateUtil.now(),
                            action = Action.PROFILE_SWITCH_REMOVED,
                            source = Sources.NSClient,
                            note = "",
                            values = listOf(ValueWithUnit.Timestamp(it.timestamp))
                        )
                    )
                aapsLogger.debug(LTag.DATABASE, "Invalidated ProfileSwitch $it")
                transactionResult.invalidated.add(it.fromDb())
            }
            result.updatedNsId.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated nsId ProfileSwitch $it")
                transactionResult.updatedNsId.add(it.fromDb())
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving ProfileSwitch", e)
            throw e
        }
    }

    override suspend fun updateProfileSwitchesNsIds(profileSwitches: List<PS>): PersistenceLayer.TransactionResult<PS> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(UpdateNsIdProfileSwitchTransaction(profileSwitches.asSequence().map { it.toDb() }.toList()))
            val transactionResult = PersistenceLayer.TransactionResult<PS>()
            result.updatedNsId.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated nsId of ProfileSwitch $it")
                transactionResult.updatedNsId.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Updated nsId of ProfileSwitch failed", e)
            throw e
        }
    }

    // TB
    override suspend fun getTemporaryBasalActiveAt(timestamp: Long): TB? = withContext(Dispatchers.IO) {
        repository.getTemporaryBasalActiveAt(timestamp)?.fromDb()
    }

    override suspend fun getOldestTemporaryBasalRecord(): TB? = withContext(Dispatchers.IO) {
        repository.getOldestTemporaryBasalRecord()?.fromDb()
    }

    override suspend fun getLastTemporaryBasalId(): Long? = withContext(Dispatchers.IO) {
        repository.getLastTemporaryBasalId()
    }

    override suspend fun getTemporaryBasalByNSId(nsId: String): TB? = withContext(Dispatchers.IO) {
        repository.findTemporaryBasalByNSId(nsId)?.fromDb()
    }

    override suspend fun getTemporaryBasalsActiveBetweenTimeAndTime(startTime: Long, endTime: Long): List<TB> = withContext(Dispatchers.IO) {
        repository.getTemporaryBasalsActiveBetweenTimeAndTime(startTime, endTime).map { it.fromDb() }
    }

    override suspend fun getTemporaryBasalsStartingFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<TB> = withContext(Dispatchers.IO) {
        repository.getTemporaryBasalsStartingFromTimeToTime(startTime, endTime, ascending).map { it.fromDb() }
    }

    override suspend fun getTemporaryBasalsStartingFromTime(startTime: Long, ascending: Boolean): List<TB> = withContext(Dispatchers.IO) {
        repository.getTemporaryBasalsStartingFromTime(startTime, ascending)
            .map { it.fromDb() }
    }

    override suspend fun getTemporaryBasalsStartingFromTimeIncludingInvalid(startTime: Long, ascending: Boolean): List<TB> = withContext(Dispatchers.IO) {
        repository.getTemporaryBasalsStartingFromTimeIncludingInvalid(startTime, ascending)
            .map { it.fromDb() }
    }

    override suspend fun getNextSyncElementTemporaryBasal(id: Long): Pair<TB, TB>? = withContext(Dispatchers.IO) {
        repository.getNextSyncElementTemporaryBasal(id)?.let { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }
    }

    override suspend fun invalidateTemporaryBasal(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): PersistenceLayer.TransactionResult<TB> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InvalidateTemporaryBasalTransaction(id))
            val transactionResult = PersistenceLayer.TransactionResult<TB>()
            val ueValues = mutableListOf<UE>()
            result.invalidated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryBasal from ${source.name} $it")
                transactionResult.invalidated.add(it.fromDb())
                ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while invalidating TemporaryBasal", e)
            throw e
        }
    }

    override suspend fun syncNsTemporaryBasals(temporaryBasals: List<TB>, doLog: Boolean): PersistenceLayer.TransactionResult<TB> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(SyncNsTemporaryBasalTransaction(temporaryBasals.asSequence().map { it.toDb() }.toList(), config.AAPSCLIENT))
            val transactionResult = PersistenceLayer.TransactionResult<TB>()
            val ueValues = mutableListOf<UE>()
            result.inserted.forEach {
                if (doLog) ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = Action.TEMP_BASAL,
                        source = Sources.NSClient,
                        note = "",
                        values = listOf(
                            ValueWithUnit.Timestamp(it.timestamp),
                            if (it.isAbsolute) ValueWithUnit.UnitPerHour(it.rate) else ValueWithUnit.Percent(it.rate.toInt()),
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                        )
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Inserted TemporaryBasal $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.invalidated.forEach {
                if (doLog) ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = Action.TEMP_BASAL_REMOVED,
                        source = Sources.NSClient,
                        note = "",
                        values = listOf(
                            ValueWithUnit.Timestamp(it.timestamp),
                            if (it.isAbsolute) ValueWithUnit.UnitPerHour(it.rate) else ValueWithUnit.Percent(it.rate.toInt()),
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                        )
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryBasal $it")
                transactionResult.invalidated.add(it.fromDb())
            }
            result.ended.forEach {
                if (doLog) ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = Action.CANCEL_TEMP_BASAL,
                        source = Sources.NSClient,
                        note = "",
                        values = listOf(
                            ValueWithUnit.Timestamp(it.timestamp),
                            if (it.isAbsolute) ValueWithUnit.UnitPerHour(it.rate) else ValueWithUnit.Percent(it.rate.toInt()),
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                        )
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Ended TemporaryBasal $it")
                transactionResult.ended.add(it.fromDb())
            }
            result.updatedNsId.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated nsId TemporaryBasal $it")
                transactionResult.updatedNsId.add(it.fromDb())
            }
            result.updatedDuration.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated duration TemporaryBasal $it")
                transactionResult.updatedDuration.add(it.fromDb())
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving TemporaryBasal", e)
            throw e
        }
    }

    override suspend fun updateTemporaryBasalsNsIds(temporaryBasals: List<TB>): PersistenceLayer.TransactionResult<TB> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(UpdateNsIdTemporaryBasalTransaction(temporaryBasals.asSequence().map { it.toDb() }.toList()))
            val transactionResult = PersistenceLayer.TransactionResult<TB>()
            result.updatedNsId.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated nsId of TemporaryBasal $it")
                transactionResult.updatedNsId.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Updated nsId of TemporaryBasal failed", e)
            throw e
        }
    }

    override suspend fun syncPumpTemporaryBasal(temporaryBasal: TB, type: TB.Type?): PersistenceLayer.TransactionResult<TB> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(SyncPumpTemporaryBasalTransaction(temporaryBasal.toDb(), type?.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<TB>()
            result.inserted.forEach {
                aapsLogger.debug(LTag.DATABASE, "Inserted TemporaryBasal $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated TemporaryBasal ${it.first} New: ${it.second}")
                transactionResult.updated.add(it.second.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while syncing TemporaryBasal", e)
            throw e
        }
    }

    override suspend fun syncPumpCancelTemporaryBasalIfAny(timestamp: Long, endPumpId: Long, pumpType: PumpType, pumpSerial: String): PersistenceLayer.TransactionResult<TB> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(SyncPumpCancelTemporaryBasalIfAnyTransaction(timestamp, endPumpId, pumpType.toDb(), pumpSerial))
            val transactionResult = PersistenceLayer.TransactionResult<TB>()
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated TemporaryBasal ${it.first} New: ${it.second}")
                transactionResult.updated.add(it.second.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while syncing TemporaryBasal", e)
            throw e
        }
    }

    override suspend fun syncPumpInvalidateTemporaryBasalWithTempId(temporaryId: Long): PersistenceLayer.TransactionResult<TB> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InvalidateTemporaryBasalWithTempIdTransaction(temporaryId))
            val transactionResult = PersistenceLayer.TransactionResult<TB>()
            result.invalidated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryBasal $it")
                transactionResult.invalidated.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while syncing TemporaryBasal", e)
            throw e
        }
    }

    override suspend fun syncPumpInvalidateTemporaryBasalWithPumpId(pumpId: Long, pumpType: PumpType, pumpSerial: String): PersistenceLayer.TransactionResult<TB> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InvalidateTemporaryBasalTransactionWithPumpId(pumpId, pumpType.toDb(), pumpSerial))
            val transactionResult = PersistenceLayer.TransactionResult<TB>()
            result.invalidated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryBasal $it")
                transactionResult.invalidated.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while syncing TemporaryBasal", e)
            throw e
        }
    }

    override suspend fun syncPumpTemporaryBasalWithTempId(temporaryBasal: TB, type: TB.Type?): PersistenceLayer.TransactionResult<TB> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(SyncTemporaryBasalWithTempIdTransaction(temporaryBasal.toDb(), type?.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<TB>()
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated TemporaryBasal ${it.first} New: ${it.second}")
                transactionResult.updated.add(it.second.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving TemporaryBasal", e)
            throw e
        }
    }

    override suspend fun insertTemporaryBasalWithTempId(temporaryBasal: TB): PersistenceLayer.TransactionResult<TB> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InsertTemporaryBasalWithTempIdTransaction(temporaryBasal.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<TB>()
            result.inserted.forEach {
                aapsLogger.debug(LTag.DATABASE, "Inserted TemporaryBasal $it")
                transactionResult.inserted.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving TemporaryBasal", e)
            throw e
        }
    }

    // EB
    override suspend fun getExtendedBolusActiveAt(timestamp: Long): EB? = withContext(Dispatchers.IO) {
        repository.getExtendedBolusActiveAt(timestamp)?.fromDb()
    }

    override suspend fun getOldestExtendedBolusRecord(): EB? = withContext(Dispatchers.IO) {
        repository.getOldestExtendedBolusRecord()?.fromDb()
    }

    override suspend fun getLastExtendedBolusId(): Long? = withContext(Dispatchers.IO) {
        repository.getLastExtendedBolusId()
    }

    override suspend fun getExtendedBolusByNSId(nsId: String): EB? = withContext(Dispatchers.IO) {
        repository.findExtendedBolusByNSId(nsId)?.fromDb()
    }

    override suspend fun getExtendedBolusesStartingFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<EB> = withContext(Dispatchers.IO) {
        repository.getExtendedBolusesStartingFromTimeToTime(startTime, endTime, ascending).map { it.fromDb() }
    }

    override suspend fun getExtendedBolusesStartingFromTime(startTime: Long, ascending: Boolean): List<EB> = withContext(Dispatchers.IO) {
        repository.getExtendedBolusesStartingFromTime(startTime, ascending)
            .map { it.fromDb() }
    }

    override suspend fun getExtendedBolusStartingFromTimeIncludingInvalid(startTime: Long, ascending: Boolean): List<EB> = withContext(Dispatchers.IO) {
        repository.getExtendedBolusStartingFromTimeIncludingInvalid(startTime, ascending)
            .map { it.fromDb() }
    }

    override suspend fun getNextSyncElementExtendedBolus(id: Long): Pair<EB, EB>? = withContext(Dispatchers.IO) {
        repository.getNextSyncElementExtendedBolus(id)?.let { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }
    }

    override suspend fun invalidateExtendedBolus(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): PersistenceLayer.TransactionResult<EB> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InvalidateExtendedBolusTransaction(id))
            val transactionResult = PersistenceLayer.TransactionResult<EB>()
            val ueValues = mutableListOf<UE>()
            result.invalidated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Invalidated ExtendedBolus from ${source.name} $it")
                transactionResult.invalidated.add(it.fromDb())
                ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while invalidating ExtendedBolus", e)
            throw e
        }
    }

    override suspend fun syncNsExtendedBoluses(extendedBoluses: List<EB>, doLog: Boolean): PersistenceLayer.TransactionResult<EB> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(SyncNsExtendedBolusTransaction(extendedBoluses.asSequence().map { it.toDb() }.toList(), config.AAPSCLIENT))
            val transactionResult = PersistenceLayer.TransactionResult<EB>()
            val ueValues = mutableListOf<UE>()
            result.inserted.forEach {
                if (doLog) ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = Action.EXTENDED_BOLUS,
                        source = Sources.NSClient,
                        note = "",
                        values = listOf(
                            ValueWithUnit.Timestamp(it.timestamp),
                            ValueWithUnit.Insulin(it.amount),
                            ValueWithUnit.UnitPerHour(it.rate),
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                        )
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Inserted EB $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.invalidated.forEach {
                if (doLog) ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = Action.EXTENDED_BOLUS_REMOVED,
                        source = Sources.NSClient,
                        note = "",
                        values = listOf(
                            ValueWithUnit.Timestamp(it.timestamp),
                            ValueWithUnit.Insulin(it.amount),
                            ValueWithUnit.UnitPerHour(it.rate),
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                        )
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Invalidated EB $it")
                transactionResult.invalidated.add(it.fromDb())
            }
            result.ended.forEach {
                if (doLog) ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = Action.CANCEL_EXTENDED_BOLUS,
                        source = Sources.NSClient,
                        note = "",
                        values = listOf(
                            ValueWithUnit.Timestamp(it.timestamp),
                            ValueWithUnit.Insulin(it.amount),
                            ValueWithUnit.UnitPerHour(it.rate),
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                        )
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Ended EB $it")
                transactionResult.ended.add(it.fromDb())
            }
            result.updatedNsId.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated nsId EB $it")
                transactionResult.updatedNsId.add(it.fromDb())
            }
            result.updatedDuration.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated duration EB $it")
                transactionResult.updatedDuration.add(it.fromDb())
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving ExtendedBolus", e)
            throw e
        }
    }

    // TT
    override suspend fun getTemporaryTargetActiveAt(timestamp: Long): TT? =
        repository.getTemporaryTargetActiveAt(timestamp)?.fromDb()

    override suspend fun getLastTemporaryTargetId(): Long? = withContext(Dispatchers.IO) {
        repository.getLastTempTargetId()
    }

    override suspend fun getTemporaryTargetByNSId(nsId: String): TT? = withContext(Dispatchers.IO) {
        repository.findTemporaryTargetByNSId(nsId)?.fromDb()
    }

    override suspend fun getTemporaryTargetDataFromTime(timestamp: Long, ascending: Boolean): List<TT> = withContext(Dispatchers.IO) {
        repository.getTemporaryTargetDataFromTime(timestamp, ascending)
            .map { it.fromDb() }
    }

    override suspend fun getTemporaryTargetDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): List<TT> = withContext(Dispatchers.IO) {
        repository.getTemporaryTargetDataIncludingInvalidFromTime(timestamp, ascending)
            .map { it.fromDb() }
    }

    override suspend fun getNextSyncElementTemporaryTarget(id: Long): Pair<TT, TT>? = withContext(Dispatchers.IO) {
        repository.getNextSyncElementTemporaryTarget(id)?.let { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }
    }

    override suspend fun invalidateTemporaryTarget(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>)
        : PersistenceLayer.TransactionResult<TT> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InvalidateTemporaryTargetTransaction(id))
            val transactionResult = PersistenceLayer.TransactionResult<TT>()
            val ueValues = mutableListOf<UE>()
            result.invalidated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryTarget from ${source.name} $it")
                transactionResult.invalidated.add(it.fromDb())
                ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while invalidating TemporaryTarget", e)
            throw e
        }
    }

    override suspend fun insertAndCancelCurrentTemporaryTarget(temporaryTarget: TT, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>)
        : PersistenceLayer.TransactionResult<TT> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InsertAndCancelCurrentTemporaryTargetTransaction(temporaryTarget.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<TT>()
            val ueValues = mutableListOf<UE>()
            result.inserted.forEach {
                aapsLogger.debug(LTag.DATABASE, "Inserted TemporaryTarget from ${source.name} $it")
                transactionResult.inserted.add(it.fromDb())
                ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
            }
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated TemporaryTarget from ${source.name} $it")
                transactionResult.updated.add(it.fromDb())
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while inserting TemporaryTarget", e)
            throw e
        }
    }

    override suspend fun cancelCurrentTemporaryTargetIfAny(timestamp: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>)
        : PersistenceLayer.TransactionResult<TT> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(CancelCurrentTemporaryTargetIfAnyTransaction(timestamp))
            val transactionResult = PersistenceLayer.TransactionResult<TT>()
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated TemporaryTarget from ${source.name} $it")
                transactionResult.updated.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while updating TemporaryTarget", e)
            throw e
        }
    }

    override suspend fun syncNsTemporaryTargets(temporaryTargets: List<TT>, doLog: Boolean): PersistenceLayer.TransactionResult<TT> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(SyncNsTemporaryTargetTransaction(temporaryTargets.asSequence().map { it.toDb() }.toList()))
            val transactionResult = PersistenceLayer.TransactionResult<TT>()
            val ueValues = mutableListOf<UE>()
            result.inserted.forEach { tt ->
                if (doLog) ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = Action.TT,
                        source = Sources.NSClient,
                        note = "",
                        values = listOfNotNull(
                            ValueWithUnit.TETTReason(tt.reason.fromDb()),
                            ValueWithUnit.fromGlucoseUnit(tt.lowTarget, GlucoseUnit.MGDL),
                            ValueWithUnit.fromGlucoseUnit(tt.highTarget, GlucoseUnit.MGDL).takeIf { tt.lowTarget != tt.highTarget },
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tt.duration).toInt())
                        )
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Inserted TemporaryTarget from ${Sources.NSClient.name} $tt")
                transactionResult.inserted.add(tt.fromDb())
            }
            result.invalidated.forEach { tt ->
                if (doLog) ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = Action.TT_REMOVED,
                        source = Sources.NSClient,
                        note = "",
                        values = listOfNotNull(
                            ValueWithUnit.TETTReason(tt.reason.fromDb()),
                            ValueWithUnit.Mgdl(tt.lowTarget),
                            ValueWithUnit.Mgdl(tt.highTarget).takeIf { tt.lowTarget != tt.highTarget },
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tt.duration).toInt())
                        )
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryTarget from ${Sources.NSClient.name} $tt")
                transactionResult.invalidated.add(tt.fromDb())
            }
            result.ended.forEach { tt ->
                if (doLog) ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = Action.CANCEL_TT,
                        source = Sources.NSClient,
                        note = "",
                        values = listOfNotNull(
                            ValueWithUnit.TETTReason(tt.reason.fromDb()),
                            ValueWithUnit.Mgdl(tt.lowTarget),
                            ValueWithUnit.Mgdl(tt.highTarget).takeIf { tt.lowTarget != tt.highTarget },
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tt.duration).toInt())
                        )
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Ended TemporaryTarget from ${Sources.NSClient.name} $tt")
                transactionResult.ended.add(tt.fromDb())
            }
            result.updatedNsId.forEach { tt ->
                aapsLogger.debug(LTag.DATABASE, "Updated nsId TemporaryTarget from ${Sources.NSClient.name} $tt")
                transactionResult.updatedNsId.add(tt.fromDb())
            }
            result.updatedDuration.forEach { tt ->
                aapsLogger.debug(LTag.DATABASE, "Updated duration TemporaryTarget from ${Sources.NSClient.name} $tt")
                transactionResult.updatedDuration.add(tt.fromDb())
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving TemporaryTarget", e)
            throw e
        }
    }

    override suspend fun updateTemporaryTargetsNsIds(temporaryTargets: List<TT>): PersistenceLayer.TransactionResult<TT> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(UpdateNsIdTemporaryTargetTransaction(temporaryTargets.asSequence().map { it.toDb() }.toList()))
            val transactionResult = PersistenceLayer.TransactionResult<TT>()
            result.updatedNsId.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated nsId of TemporaryTarget $it")
                transactionResult.updatedNsId.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while updating nsId TemporaryTarget", e)
            throw e
        }
    }

    override suspend fun getLastTherapyEventId(): Long? = withContext(Dispatchers.IO) {
        repository.getLastTherapyEventId()
    }

    override suspend fun getTherapyEventByNSId(nsId: String): TE? = withContext(Dispatchers.IO) {
        repository.findTherapyEventByNSId(nsId)?.fromDb()
    }

    // TE
    override suspend fun getLastTherapyRecordUpToNow(type: TE.Type): TE? = withContext(Dispatchers.IO) {
        repository.getLastTherapyRecordUpToNow(type.toDb())?.fromDb()
    }

    override suspend fun getTherapyEventDataFromToTime(from: Long, to: Long): List<TE> = withContext(Dispatchers.IO) {
        repository.compatGetTherapyEventDataFromToTime(from, to).map { it.fromDb() }
    }

    override suspend fun getTherapyEventDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): List<TE> = withContext(Dispatchers.IO) {
        repository.getTherapyEventDataIncludingInvalidFromTime(timestamp, ascending)
            .map { it.fromDb() }
    }

    override suspend fun getTherapyEventDataFromTime(timestamp: Long, ascending: Boolean): List<TE> = withContext(Dispatchers.IO) {
        repository.getTherapyEventDataFromTime(timestamp, ascending)
            .map { it.fromDb() }
    }

    override suspend fun getTherapyEventDataFromTime(timestamp: Long, type: TE.Type, ascending: Boolean): List<TE> = withContext(Dispatchers.IO) {
        repository.getTherapyEventDataFromTime(timestamp, type.toDb(), ascending).map { it.fromDb() }
    }

    override suspend fun getNextSyncElementTherapyEvent(id: Long): Pair<TE, TE>? = withContext(Dispatchers.IO) {
        repository.getNextSyncElementTherapyEvent(id)?.let { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }
    }

    override suspend fun insertPumpTherapyEventIfNewByTimestamp(therapyEvent: TE, timestamp: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>)
        : PersistenceLayer.TransactionResult<TE> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InsertIfNewByTimestampTherapyEventTransaction(therapyEvent.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<TE>()
            val ueValues = mutableListOf<UE>()
            result.inserted.forEach {
                aapsLogger.debug(LTag.DATABASE, "Inserted TherapyEvent from ${source.name} $it")
                transactionResult.inserted.add(it.fromDb())
                ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving TherapyEvent $therapyEvent", e)
            throw e
        }
    }

    override suspend fun insertOrUpdateTherapyEvent(therapyEvent: TE): PersistenceLayer.TransactionResult<TE> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InsertOrUpdateTherapyEventTransaction(therapyEvent.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<TE>()
            result.inserted.forEach {
                aapsLogger.debug(LTag.DATABASE, "Inserted TherapyEvent $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated TherapyEvent $it")
                transactionResult.updated.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving HeartRate", e)
            throw e
        }
    }

    override suspend fun invalidateTherapyEvent(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>)
        : PersistenceLayer.TransactionResult<TE> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InvalidateTherapyEventTransaction(id))
            val transactionResult = PersistenceLayer.TransactionResult<TE>()
            val ueValues = mutableListOf<UE>()
            result.invalidated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Invalidated TherapyEvent from ${source.name} $it")
                transactionResult.invalidated.add(it.fromDb())
                ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while invalidating TherapyEvent", e)
            throw e
        }
    }

    override suspend fun invalidateTherapyEventsWithNote(note: String, action: Action, source: Sources): PersistenceLayer.TransactionResult<TE> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InvalidateTherapyEventsWithNoteTransaction(note))
            val transactionResult = PersistenceLayer.TransactionResult<TE>()
            val ueValues = mutableListOf<UE>()
            result.invalidated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Invalidated TherapyEvent from ${source.name} $it")
                transactionResult.invalidated.add(it.fromDb())
                ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note, values = emptyList()))
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while invalidating TherapyEvent", e)
            throw e
        }
    }

    override suspend fun syncNsTherapyEvents(therapyEvents: List<TE>, doLog: Boolean): PersistenceLayer.TransactionResult<TE> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(SyncNsTherapyEventTransaction(therapyEvents.asSequence().map { it.toDb() }.toList(), config.AAPSCLIENT))
            val transactionResult = PersistenceLayer.TransactionResult<TE>()
            val ueValues = mutableListOf<UE>()
            result.inserted.forEach { therapyEvent ->
                val action = when (therapyEvent.type) {
                    TherapyEvent.Type.CANNULA_CHANGE -> Action.SITE_CHANGE
                    TherapyEvent.Type.INSULIN_CHANGE -> Action.RESERVOIR_CHANGE
                    else                             -> Action.CAREPORTAL
                }
                if (doLog) ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = action,
                        source = Sources.NSClient,
                        note = therapyEvent.note ?: "",
                        values = listOfNotNull(
                            ValueWithUnit.Timestamp(therapyEvent.timestamp),
                            ValueWithUnit.TEType(therapyEvent.type.fromDb()),
                            ValueWithUnit.fromGlucoseUnit(therapyEvent.glucose ?: 0.0, therapyEvent.glucoseUnit.fromDb()).takeIf { therapyEvent.glucose != null }
                        )
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Inserted TherapyEvent from ${Sources.NSClient.name} $therapyEvent")
                transactionResult.inserted.add(therapyEvent.fromDb())
            }
            result.invalidated.forEach { therapyEvent ->
                if (doLog) ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = Action.CAREPORTAL_REMOVED,
                        source = Sources.NSClient,
                        note = therapyEvent.note ?: "",
                        values = listOfNotNull(
                            ValueWithUnit.Timestamp(therapyEvent.timestamp),
                            ValueWithUnit.TEType(therapyEvent.type.fromDb()),
                            ValueWithUnit.fromGlucoseUnit(therapyEvent.glucose ?: 0.0, therapyEvent.glucoseUnit.fromDb()).takeIf { therapyEvent.glucose != null }
                        )
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Invalidated TherapyEvent from ${Sources.NSClient.name} $therapyEvent")
                transactionResult.invalidated.add(therapyEvent.fromDb())
            }
            result.updatedNsId.forEach { therapyEvent ->
                aapsLogger.debug(LTag.DATABASE, "Updated nsId TherapyEvent from ${Sources.NSClient.name} $therapyEvent")
                transactionResult.updatedNsId.add(therapyEvent.fromDb())
            }
            result.updatedDuration.forEach { therapyEvent ->
                aapsLogger.debug(LTag.DATABASE, "Updated duration TherapyEvent from ${Sources.NSClient.name} $therapyEvent")
                transactionResult.updatedDuration.add(therapyEvent.fromDb())
            }
            result.updatedSite.forEach { therapyEvent ->
                aapsLogger.debug(LTag.DATABASE, "Updated Site Rotation TherapyEvent from ${Sources.NSClient.name} $therapyEvent")
                transactionResult.updated.add(therapyEvent.fromDb())
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving TherapyEvent", e)
            throw e
        }
    }

    override suspend fun updateTherapyEventsNsIds(therapyEvents: List<TE>): PersistenceLayer.TransactionResult<TE> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(UpdateNsIdTherapyEventTransaction(therapyEvents.asSequence().map { it.toDb() }.toList()))
            val transactionResult = PersistenceLayer.TransactionResult<TE>()
            result.updatedNsId.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated nsId of TherapyEvent $it")
                transactionResult.updatedNsId.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Updated nsId of TherapyEvent failed", e)
            throw e
        }
    }

    // DS
    override suspend fun getNextSyncElementDeviceStatus(id: Long): DS? = withContext(Dispatchers.IO) {
        repository.getNextSyncElementDeviceStatus(id)?.fromDb()
    }

    override suspend fun getLastDeviceStatusId(): Long? = withContext(Dispatchers.IO) {
        repository.getLastDeviceStatusId()
    }

    override fun insertDeviceStatus(deviceStatus: DS) {
        repository.insert(deviceStatus.toDb())
        aapsLogger.debug(LTag.DATABASE, "Inserted DeviceStatus $deviceStatus")
    }

    override suspend fun updateDeviceStatusesNsIds(deviceStatuses: List<DS>): PersistenceLayer.TransactionResult<DS> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(UpdateNsIdDeviceStatusTransaction(deviceStatuses.asSequence().map { it.toDb() }.toList()))
            val transactionResult = PersistenceLayer.TransactionResult<DS>()
            result.updatedNsId.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated nsId of DeviceStatus $it")
                transactionResult.updatedNsId.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Updated nsId of DeviceStatus failed", e)
            throw e
        }
    }

    // HR
    override suspend fun getHeartRatesFromTime(startTime: Long): List<HR> = withContext(Dispatchers.IO) {
        repository.getHeartRatesFromTime(startTime).map { it.fromDb() }
    }

    override suspend fun getHeartRatesFromTimeToTime(startTime: Long, endTime: Long): List<HR> = withContext(Dispatchers.IO) {
        repository.getHeartRatesFromTimeToTime(startTime, endTime).map { it.fromDb() }
    }

    override suspend fun insertOrUpdateHeartRate(heartRate: HR): PersistenceLayer.TransactionResult<HR> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InsertOrUpdateHeartRateTransaction(heartRate.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<HR>()
            result.inserted.forEach {
                aapsLogger.debug(LTag.DATABASE, "Inserted HeartRate $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated HeartRate $it")
                transactionResult.updated.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving HeartRate", e)
            throw e
        }
    }

    override suspend fun getFoods(): List<FD> = withContext(Dispatchers.IO) {
        repository.getFoodData().map { it.fromDb() }
    }

    override suspend fun getNextSyncElementFood(id: Long): Pair<FD, FD>? = withContext(Dispatchers.IO) {
        repository.getNextSyncElementFood(id)?.let { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }
    }

    override suspend fun getLastFoodId(): Long? = withContext(Dispatchers.IO) {
        repository.getLastFoodId()
    }

    override suspend fun invalidateFood(id: Long, action: Action, source: Sources): PersistenceLayer.TransactionResult<FD> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InvalidateFoodTransaction(id))
            val transactionResult = PersistenceLayer.TransactionResult<FD>()
            val ueValues = mutableListOf<UE>()
            result.invalidated.forEach {
                ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = it.name, values = emptyList()))
                aapsLogger.debug(LTag.DATABASE, "Invalidated Food from ${source.name} $it")
                transactionResult.invalidated.add(it.fromDb())
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while invalidating Food", e)
            throw e
        }
    }

    override suspend fun syncNsFood(foods: List<FD>): PersistenceLayer.TransactionResult<FD> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(SyncNsFoodTransaction(foods.asSequence().map { it.toDb() }.toList()))
            val transactionResult = PersistenceLayer.TransactionResult<FD>()
            val ueValues = mutableListOf<UE>()
            result.inserted.forEach {
                ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = Action.FOOD,
                        source = Sources.NSClient,
                        note = it.name,
                        values = emptyList()
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Inserted Food $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.invalidated.forEach {
                ueValues.add(
                    UE(
                        timestamp = dateUtil.now(),
                        action = Action.FOOD_REMOVED,
                        source = Sources.NSClient,
                        note = it.name,
                        values = emptyList()
                    )
                )
                aapsLogger.debug(LTag.DATABASE, "Invalidated Food $it")
                transactionResult.invalidated.add(it.fromDb())
            }
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated Food $it")
                transactionResult.updated.add(it.fromDb())
            }
            log(ueValues)
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving Food", e)
            throw e
        }
    }

    override suspend fun updateFoodsNsIds(foods: List<FD>): PersistenceLayer.TransactionResult<FD> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(UpdateNsIdFoodTransaction(foods.asSequence().map { it.toDb() }.toList()))
            val transactionResult = PersistenceLayer.TransactionResult<FD>()
            result.updatedNsId.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated nsId of Food $it")
                transactionResult.updatedNsId.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Updated nsId of Food failed", e)
            throw e
        }
    }

    // UE
    override suspend fun insertUserEntries(entries: List<UE>): PersistenceLayer.TransactionResult<UE> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(UserEntryTransaction(entries.asSequence().map { it.toDb() }.toList()))
            val transactionResult = PersistenceLayer.TransactionResult<UE>()
            result.forEach {
                aapsLogger.debug("USER ENTRY: ${dateUtil.dateAndTimeAndSecondsString(it.timestamp)} ${it.action} ${it.source} ${it.note} ${it.values}")
                transactionResult.inserted.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving UserEntries $entries", e)
            throw e
        }
    }

    override suspend fun getUserEntryDataFromTime(timestamp: Long): List<UE> = withContext(Dispatchers.IO) {
        repository.getUserEntryDataFromTime(timestamp).map { it.fromDb() }.toList()
    }

    override suspend fun getUserEntryFilteredDataFromTime(timestamp: Long): List<UE> = withContext(Dispatchers.IO) {
        repository.getUserEntryFilteredDataFromTime(timestamp).map { it.fromDb() }.toList()
    }

    // TDD
    override suspend fun clearCachedTddData(timestamp: Long) = withContext(Dispatchers.IO) { repository.clearCachedTddData(timestamp) }

    override suspend fun getLastTotalDailyDoses(count: Int, ascending: Boolean): List<TDD> = withContext(Dispatchers.IO) {
        repository.getLastTotalDailyDoses(count, ascending).map { it.fromDb() }
    }

    override suspend fun getCalculatedTotalDailyDose(timestamp: Long): TDD? = withContext(Dispatchers.IO) {
        repository.getCalculatedTotalDailyDose(timestamp)?.fromDb()
    }

    override suspend fun insertOrUpdateCachedTotalDailyDose(totalDailyDose: TDD): PersistenceLayer.TransactionResult<TDD> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InsertOrUpdateCachedTotalDailyDoseTransaction(totalDailyDose.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<TDD>()
            result.inserted.forEach {
                aapsLogger.debug(LTag.DATABASE, "Inserted TotalDailyDose ${dateUtil.dateString(it.timestamp)} $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated TotalDailyDose ${dateUtil.dateString(it.timestamp)} $it")
                transactionResult.updated.add(it.fromDb())
            }
            result.notUpdated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Not updated TotalDailyDose ${dateUtil.dateString(it.timestamp)} $it")
                transactionResult.updated.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving TotalDailyDose $e")
            throw e
        }
    }

    override suspend fun insertOrUpdateTotalDailyDose(totalDailyDose: TDD): PersistenceLayer.TransactionResult<TDD> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(SyncPumpTotalDailyDoseTransaction(totalDailyDose.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<TDD>()
            result.inserted.forEach {
                aapsLogger.debug(LTag.DATABASE, "Inserted TotalDailyDose $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated TotalDailyDose $it")
                transactionResult.updated.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving TotalDailyDose $e")
            throw e
        }
    }

    // SC
    override suspend fun getStepsCountFromTime(from: Long): List<SC> = withContext(Dispatchers.IO) {
        repository.getStepsCountFromTime(from).map { it.fromDb() }
    }

    override suspend fun getStepsCountFromTimeToTime(startTime: Long, endTime: Long): List<SC> = withContext(Dispatchers.IO) {
        repository.getStepsCountFromTimeToTime(startTime, endTime).map { it.fromDb() }
    }

    override suspend fun getLastStepsCountFromTimeToTime(startTime: Long, endTime: Long): SC? = withContext(Dispatchers.IO) {
        repository.getLastStepsCountFromTimeToTime(startTime, endTime)?.fromDb()
    }

    override suspend fun insertOrUpdateStepsCount(stepsCount: SC): PersistenceLayer.TransactionResult<SC> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InsertOrUpdateStepsCountTransaction(stepsCount.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<SC>()
            result.inserted.forEach {
                aapsLogger.debug(LTag.DATABASE, "Inserted StepsCount $it")
                transactionResult.inserted.add(it.fromDb())
            }
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated StepsCount $it")
                transactionResult.updated.add(it.fromDb())
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving StepsCount $e")
            throw e
        }
    }

    // VersionChange
    override fun insertVersionChangeIfChanged(versionName: String, versionCode: Int, gitRemote: String?, commitHash: String?): Completable =
        repository.runTransaction(VersionChangeTransaction(versionName, versionCode, gitRemote, commitHash))

    override suspend fun collectNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): NE = withContext(Dispatchers.IO) {
        repository.collectNewEntriesSince(since, until, limit, offset).fromDb()
    }

    override suspend fun getApsResultCloseTo(timestamp: Long): APSResult? = withContext(Dispatchers.IO) {
        repository.getApsResultCloseTo(timestamp)?.fromDb(apsResultProvider)
    }

    override suspend fun getApsResults(start: Long, end: Long): List<APSResult> = withContext(Dispatchers.IO) {
        repository.getApsResults(start, end).map { it.fromDb(apsResultProvider) }
    }

    override suspend fun insertOrUpdateApsResult(apsResult: APSResult): PersistenceLayer.TransactionResult<APSResult> = withContext(Dispatchers.IO) {
        try {
            val result = repository.runTransactionForResultSuspend(InsertOrUpdateApsResultTransaction(apsResult.toDb()))
            val transactionResult = PersistenceLayer.TransactionResult<APSResult>()
            result.inserted.forEach {
                aapsLogger.debug(LTag.DATABASE, "Inserted APSResult $it")
                transactionResult.inserted.add(it.fromDb(apsResultProvider))
            }
            result.updated.forEach {
                aapsLogger.debug(LTag.DATABASE, "Updated APSResult $it")
                transactionResult.updated.add(it.fromDb(apsResultProvider))
            }
            transactionResult
        } catch (e: Exception) {
            aapsLogger.error(LTag.DATABASE, "Error while saving APSResult", e)
            throw e
        }
    }
}
