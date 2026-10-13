package app.aaps.database.persistence

import android.os.SystemClock
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
import app.aaps.database.ValueWrapper
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.persistence.converters.fromDb
import app.aaps.database.persistence.converters.toDb
import app.aaps.database.transactions.CancelCurrentTemporaryRunningModeIfAnyTransaction
import app.aaps.database.transactions.CancelCurrentTemporaryTargetIfAnyTransaction
import app.aaps.database.transactions.CgmSourceTransaction
import app.aaps.database.transactions.CutCarbsTransaction
import app.aaps.database.transactions.InsertAndCancelCurrentTemporaryTargetTransaction
import app.aaps.database.transactions.InsertBolusWithTempIdTransaction
import app.aaps.database.transactions.InsertEffectiveProfileSwitchTransaction
import app.aaps.database.transactions.InsertIfNewByTimestampCarbsTransaction
import app.aaps.database.transactions.InsertIfNewByTimestampTherapyEventTransaction
import app.aaps.database.transactions.InsertOrUpdateApsResultTransaction
import app.aaps.database.transactions.InsertOrUpdateBolusCalculatorResultTransaction
import app.aaps.database.transactions.InsertOrUpdateBolusTransaction
import app.aaps.database.transactions.InsertOrUpdateCachedTotalDailyDoseTransaction
import app.aaps.database.transactions.InsertOrUpdateCarbsTransaction
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
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.Collections.emptyList
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class PersistenceLayerImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val repository: AppRepository,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val apsResultProvider: Provider<APSResult>
) : PersistenceLayer {

    @Suppress("unused")
    private fun <S, D> Single<ValueWrapper<S>>.fromDb(converter: S.() -> D): Single<ValueWrapper<D>> =
        this.map { wrapper ->
            when (wrapper) {
                is ValueWrapper.Existing -> ValueWrapper.Existing(wrapper.value.converter())
                is ValueWrapper.Absent   -> ValueWrapper.Absent()
            }
        }

    private val compositeDisposable = CompositeDisposable()
    private fun log(entries: List<UE>) {
        if (config.AAPSCLIENT.not())
            if (entries.isNotEmpty()) {
                compositeDisposable += insertUserEntries(entries).subscribe()
                SystemClock.sleep(entries.size * 10L)
            }
    }

    override fun clearDatabases() = repository.clearDatabases()
    override fun clearApsResults() = repository.clearApsResults()
    override fun cleanupDatabase(keepDays: Long, deleteTrackedChanges: Boolean): String = repository.cleanupDatabase(keepDays, deleteTrackedChanges)

    // BS
    override fun getNewestBolus(): BS? = repository.getNewestBolus().blockingGet()?.fromDb()
    override fun getOldestBolus(): BS? = repository.getOldestBolus().blockingGet()?.fromDb()

    override fun getNewestBolusOfType(type: BS.Type): BS? =
        repository.getLastBolusRecordOfType(type.toDb()).blockingGet()?.fromDb()

    override fun getLastBolusId(): Long? = repository.getLastBolusId()
    override fun getBolusByNSId(nsId: String): BS? = repository.getBolusByNSId(nsId)?.fromDb()

    override fun getBolusesFromTime(startTime: Long, ascending: Boolean): Single<List<BS>> =
        repository.getBolusesDataFromTime(startTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getBolusesFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<BS> =
        repository.getBolusesDataFromTimeToTime(startTime, endTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }
            .blockingGet()

    override fun getBolusesFromTimeIncludingInvalid(startTime: Long, ascending: Boolean): Single<List<BS>> =
        repository.getBolusesIncludingInvalidFromTime(startTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getNextSyncElementBolus(id: Long): Maybe<Pair<BS, BS>> =
        repository.getNextSyncElementBolus(id)
            .map { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }

    override fun insertOrUpdateBolus(bolus: BS, action: Action, source: Sources, note: String?): Single<PersistenceLayer.TransactionResult<BS>> =
        repository.runTransactionForResult(InsertOrUpdateBolusTransaction(bolus.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving Bolus", it) }
            .map { result ->
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
            }

    override fun insertBolusWithTempId(bolus: BS): Single<PersistenceLayer.TransactionResult<BS>> =
        repository.runTransactionForResult(InsertBolusWithTempIdTransaction(bolus.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving Bolus", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<BS>()
                result.inserted.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Inserted Bolus $it")
                    transactionResult.inserted.add(it.fromDb())
                }
                transactionResult
            }

    override fun invalidateBolus(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): Single<PersistenceLayer.TransactionResult<BS>> =
        repository.runTransactionForResult(InvalidateBolusTransaction(id))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating Bolus", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<BS>()
                val ueValues = mutableListOf<UE>()
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated Bolus from ${source.name} $it")
                    transactionResult.invalidated.add(it.fromDb())
                    ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
                }
                log(ueValues)
                transactionResult
            }

    override fun syncPumpBolus(bolus: BS, type: BS.Type?): Single<PersistenceLayer.TransactionResult<BS>> =
        repository.runTransactionForResult(SyncPumpBolusTransaction(bolus.toDb(), type?.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving Bolus", it) }
            .map { result ->
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
            }

    override fun syncPumpBolusWithTempId(bolus: BS, type: BS.Type?): Single<PersistenceLayer.TransactionResult<BS>> =
        repository.runTransactionForResult(SyncBolusWithTempIdTransaction(bolus.toDb(), type?.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving Bolus", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<BS>()
                result.updated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated Bolus $it")
                    transactionResult.updated.add(it.fromDb())
                }
                transactionResult
            }

    override fun syncNsBolus(boluses: List<BS>, doLog: Boolean): Single<PersistenceLayer.TransactionResult<BS>> =
        repository.runTransactionForResult(SyncNsBolusTransaction(boluses.asSequence().map { it.toDb() }.toList()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving bolus", it) }
            .map { result ->
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
            }

    override fun updateBolusesNsIds(boluses: List<BS>): Single<PersistenceLayer.TransactionResult<BS>> =
        repository.runTransactionForResult(UpdateNsIdBolusTransaction(boluses.asSequence().map { it.toDb() }.toList()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Updated nsId of Bolus failed", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<BS>()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of Bolus $it")
                    transactionResult.updatedNsId.add(it.fromDb())
                }
                transactionResult
            }

    override fun getNewestCarbs(): CA? = repository.getLastCarbs().blockingGet()?.fromDb()
    override fun getOldestCarbs(): CA? = repository.getOldestCarbs().blockingGet()?.fromDb()

    // CA
    override fun getLastCarbsId(): Long? = repository.getLastCarbsId()
    override fun getCarbsByNSId(nsId: String): CA? = repository.getCarbsByNSId(nsId)?.fromDb()

    override fun getCarbsFromTime(startTime: Long, ascending: Boolean): Single<List<CA>> =
        repository.getCarbsDataFromTime(startTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getCarbsFromTimeIncludingInvalid(startTime: Long, ascending: Boolean): Single<List<CA>> =
        repository.getCarbsIncludingInvalidFromTime(startTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getCarbsFromTimeExpanded(startTime: Long, ascending: Boolean): List<CA> =
        repository.getCarbsDataFromTimeExpanded(startTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }
            .blockingGet()

    override fun getCarbsFromTimeNotExpanded(startTime: Long, ascending: Boolean): Single<List<CA>> =
        repository.getCarbsDataFromTimeNotExpanded(startTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getCarbsFromTimeToTimeExpanded(startTime: Long, endTime: Long, ascending: Boolean): List<CA> =
        repository.getCarbsDataFromTimeToTimeExpanded(startTime, endTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }
            .blockingGet()

    override fun getNextSyncElementCarbs(id: Long): Maybe<Pair<CA, CA>> =
        repository.getNextSyncElementCarbs(id)
            .map { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }

    override fun insertOrUpdateCarbs(carbs: CA, action: Action, source: Sources, note: String?): Single<PersistenceLayer.TransactionResult<CA>> =
        repository.runTransactionForResult(InsertOrUpdateCarbsTransaction(carbs.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving Carbs", it) }
            .map { result ->
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
            }

    override fun insertPumpCarbsIfNewByTimestamp(carbs: CA): Single<PersistenceLayer.TransactionResult<CA>> =
        repository.runTransactionForResult(InsertIfNewByTimestampCarbsTransaction(carbs.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving Carbs", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<CA>()
                result.inserted.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Inserted Carbs $it")
                    transactionResult.inserted.add(it.fromDb())
                }
                transactionResult
            }

    override fun invalidateCarbs(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): Single<PersistenceLayer.TransactionResult<CA>> =
        repository.runTransactionForResult(InvalidateCarbsTransaction(id))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating Carbs", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<CA>()
                val ueValues = mutableListOf<UE>()
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated Carbs from ${source.name} $it")
                    transactionResult.invalidated.add(it.fromDb())
                    ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
                }
                log(ueValues)
                transactionResult
            }

    override fun cutCarbs(id: Long, timestamp: Long): Single<PersistenceLayer.TransactionResult<CA>> =
        repository.runTransactionForResult(CutCarbsTransaction(id, timestamp))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while cutting Carbs", it) }
            .map { result ->
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
            }

    override fun syncNsCarbs(carbs: List<CA>, doLog: Boolean): Single<PersistenceLayer.TransactionResult<CA>> =
        repository.runTransactionForResult(SyncNsCarbsTransaction(carbs.asSequence().map { it.toDb() }.toList(), config.AAPSCLIENT))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving carbs", it) }
            .map { result ->
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
            }

    override fun updateCarbsNsIds(carbs: List<CA>): Single<PersistenceLayer.TransactionResult<CA>> =
        repository.runTransactionForResult(UpdateNsIdCarbsTransaction(carbs.asSequence().map { it.toDb() }.toList()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Updated nsId of Carbs failed", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<CA>()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of Carbs $it")
                    transactionResult.updatedNsId.add(it.fromDb())
                }
                transactionResult
            }

    override fun getBolusCalculatorResultByNSId(nsId: String): BCR? = repository.findBolusCalculatorResultByNSId(nsId)?.fromDb()

    // BCR
    override fun getBolusCalculatorResultsFromTime(startTime: Long, ascending: Boolean): Single<List<BCR>> =
        repository.getBolusCalculatorResultsDataFromTime(startTime, ascending).map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getBolusCalculatorResultsIncludingInvalidFromTime(startTime: Long, ascending: Boolean): Single<List<BCR>> =
        repository.getBolusCalculatorResultsIncludingInvalidFromTime(startTime, ascending).map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getNextSyncElementBolusCalculatorResult(id: Long): Maybe<Pair<BCR, BCR>> =
        repository.getNextSyncElementBolusCalculatorResult(id)
            .map { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }

    override fun getLastBolusCalculatorResultId(): Long? = repository.getLastBolusCalculatorResultId()

    override fun insertOrUpdateBolusCalculatorResult(bolusCalculatorResult: BCR): Single<PersistenceLayer.TransactionResult<BCR>> =
        repository.runTransactionForResult(InsertOrUpdateBolusCalculatorResultTransaction(bolusCalculatorResult.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving BolusCalculatorResult", it) }
            .map { result ->
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
            }

    override fun syncNsBolusCalculatorResults(bolusCalculatorResults: List<BCR>): Single<PersistenceLayer.TransactionResult<BCR>> =
        repository.runTransactionForResult(SyncNsBolusCalculatorResultTransaction(bolusCalculatorResults.asSequence().map { it.toDb() }.toList()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving BolusCalculatorResult", it) }
            .map { result ->
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
            }

    override fun updateBolusCalculatorResultsNsIds(bolusCalculatorResults: List<BCR>): Single<PersistenceLayer.TransactionResult<BCR>> =
        repository.runTransactionForResult(UpdateNsIdBolusCalculatorResultTransaction(bolusCalculatorResults.asSequence().map { it.toDb() }.toList()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving BolusCalculatorResult", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<BCR>()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId BolusCalculatorResult $it")
                    transactionResult.updatedNsId.add(it.fromDb())
                }
                transactionResult
            }

    override fun invalidateBolusCalculatorResult(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): Single<PersistenceLayer.TransactionResult<BCR>> =
        repository.runTransactionForResult(InvalidateBolusCalculatorResultTransaction(id))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating BolusCalculatorResult", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<BCR>()
                val ueValues = mutableListOf<UE>()
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated BolusCalculatorResult from ${source.name} $it")
                    transactionResult.invalidated.add(it.fromDb())
                    ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
                }
                log(ueValues)
                transactionResult
            }

    // GV
    override fun getLastGlucoseValue(): GV? =
        repository.getLastGlucoseValue()?.fromDb()

    override fun getLastGlucoseValueId(): Long? = repository.getLastGlucoseValueId()

    override fun getNextSyncElementGlucoseValue(id: Long): Maybe<Pair<GV, GV>> =
        repository.getNextSyncElementGlucoseValue(id)
            .map { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }

    override fun getBgReadingsDataFromTimeToTime(start: Long, end: Long, ascending: Boolean): List<GV> =
        repository.compatGetBgReadingsDataFromTime(start, end, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }
            .blockingGet()

    override fun getBgReadingsDataFromTime(timestamp: Long, ascending: Boolean): Single<List<GV>> =
        repository.compatGetBgReadingsDataFromTime(timestamp, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getBgReadingByNSId(nsId: String): GV? =
        repository.findBgReadingByNSId(nsId)?.fromDb()

    override fun invalidateGlucoseValue(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): Single<PersistenceLayer.TransactionResult<GV>> =
        repository.runTransactionForResult(InvalidateGlucoseValueTransaction(id))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating GlucoseValue", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<GV>()
                val ueValues = mutableListOf<UE>()
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated GlucoseValue from ${source.name} $it")
                    transactionResult.invalidated.add(it.fromDb())
                    ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
                }
                log(ueValues)
                transactionResult
            }

    private fun PersistenceLayer.Calibration.toDb() = CgmSourceTransaction.Calibration(timestamp, value, glucoseUnit.toDb())
    override fun insertCgmSourceData(caller: Sources, glucoseValues: List<GV>, calibrations: List<PersistenceLayer.Calibration>, sensorInsertionTime: Long?)
        : Single<PersistenceLayer.TransactionResult<GV>> =
        repository.runTransactionForResult(CgmSourceTransaction(glucoseValues.asSequence().map { it.toDb() }.toList(), calibrations.asSequence().map { it.toDb() }.toList(), sensorInsertionTime))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving cgm values from ${caller.name}", it) }
            .map { result ->
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
            }

    override fun updateGlucoseValuesNsIds(glucoseValues: List<GV>): Single<PersistenceLayer.TransactionResult<GV>> =
        repository.runTransactionForResult(UpdateNsIdGlucoseValueTransaction(glucoseValues.asSequence().map { it.toDb() }.toList()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Updated nsId of GlucoseValue failed", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<GV>()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of GlucoseValue $it")
                    transactionResult.updatedNsId.add(it.fromDb())
                }
                transactionResult
            }

    override fun getOldestEffectiveProfileSwitch(): EPS? = repository.getOldestEffectiveProfileSwitchRecord().blockingGet()?.fromDb()

    override fun updateExtendedBolusesNsIds(extendedBoluses: List<EB>): Single<PersistenceLayer.TransactionResult<EB>> =
        repository.runTransactionForResult(UpdateNsIdExtendedBolusTransaction(extendedBoluses.asSequence().map { it.toDb() }.toList()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Updated nsId of ExtendedBolus failed", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<EB>()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of ExtendedBolus $it")
                    transactionResult.updatedNsId.add(it.fromDb())
                }
                transactionResult
            }

    override fun syncPumpExtendedBolus(extendedBolus: EB): Single<PersistenceLayer.TransactionResult<EB>> =
        repository.runTransactionForResult(SyncPumpExtendedBolusTransaction(extendedBolus.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while syncing ExtendedBolus", it) }
            .map { result ->
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
            }

    override fun syncPumpStopExtendedBolusWithPumpId(timestamp: Long, endPumpId: Long, pumpType: PumpType, pumpSerial: String): Single<PersistenceLayer.TransactionResult<EB>> =
        repository.runTransactionForResult(SyncPumpCancelExtendedBolusIfAnyTransaction(timestamp, endPumpId, pumpType.toDb(), pumpSerial))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while syncing ExtendedBolus", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<EB>()
                result.updated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated ExtendedBolus $it")
                    transactionResult.updated.add(it.fromDb())
                }
                transactionResult
            }

    // EPS
    override fun getEffectiveProfileSwitchActiveAt(timestamp: Long): EPS? =
        repository.getEffectiveProfileSwitchActiveAt(timestamp).blockingGet()?.fromDb()

    override fun getEffectiveProfileSwitchByNSId(nsId: String): EPS? = repository.findEffectiveProfileSwitchByNSId(nsId)?.fromDb()

    override fun getEffectiveProfileSwitchesFromTime(startTime: Long, ascending: Boolean): Single<List<EPS>> =
        repository.getEffectiveProfileSwitchesFromTime(startTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getEffectiveProfileSwitchesIncludingInvalidFromTime(startTime: Long, ascending: Boolean): Single<List<EPS>> =
        repository.getEffectiveProfileSwitchesIncludingInvalidFromTime(startTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getEffectiveProfileSwitchesFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<EPS> =
        repository.getEffectiveProfileSwitchesFromTimeToTime(startTime, endTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }
            .blockingGet()

    override fun getNextSyncElementEffectiveProfileSwitch(id: Long): Maybe<Pair<EPS, EPS>> =
        repository.getNextSyncElementEffectiveProfileSwitch(id)
            .map { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }

    override fun getLastEffectiveProfileSwitchId(): Long? = repository.getLastEffectiveProfileSwitchId()
    override fun insertEffectiveProfileSwitch(effectiveProfileSwitch: EPS): Single<PersistenceLayer.TransactionResult<EPS>> =
        repository.runTransactionForResult(InsertEffectiveProfileSwitchTransaction(effectiveProfileSwitch.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while inserting EffectiveProfileSwitch", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<EPS>()
                result.inserted.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Inserted EffectiveProfileSwitch $it")
                    transactionResult.inserted.add(it.fromDb())
                }
                transactionResult
            }

    override fun invalidateEffectiveProfileSwitch(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): Single<PersistenceLayer.TransactionResult<EPS>> =
        repository.runTransactionForResult(InvalidateEffectiveProfileSwitchTransaction(id))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating EffectiveProfileSwitch", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<EPS>()
                val ueValues = mutableListOf<UE>()
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated EffectiveProfileSwitch from ${source.name} $it")
                    transactionResult.invalidated.add(it.fromDb())
                    ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
                }
                log(ueValues)
                transactionResult
            }

    override fun syncNsEffectiveProfileSwitches(effectiveProfileSwitches: List<EPS>, doLog: Boolean): Single<PersistenceLayer.TransactionResult<EPS>> =
        repository.runTransactionForResult(SyncNsEffectiveProfileSwitchTransaction(effectiveProfileSwitches.map { it.toDb() }))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving EffectiveProfileSwitch", it) }
            .map { result ->
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
            }

    override fun updateEffectiveProfileSwitchesNsIds(effectiveProfileSwitches: List<EPS>): Single<PersistenceLayer.TransactionResult<EPS>> =
        repository.runTransactionForResult(UpdateNsIdEffectiveProfileSwitchTransaction(effectiveProfileSwitches.asSequence().map { it.toDb() }.toList()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Updated nsId of EffectiveProfileSwitch failed", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<EPS>()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of EffectiveProfileSwitch $it")
                    transactionResult.updatedNsId.add(it.fromDb())
                }
                transactionResult
            }

    override fun getProfileSwitchActiveAt(timestamp: Long): PS? = repository.getProfileSwitchActiveAt(timestamp)?.fromDb()
    override fun getProfileSwitchByNSId(nsId: String): PS? = repository.findProfileSwitchByNSId(nsId)?.fromDb()

    override fun getPermanentProfileSwitchActiveAt(timestamp: Long): PS? =
        repository.getPermanentProfileSwitchActiveAt(timestamp).blockingGet()?.fromDb()

    override fun getProfileSwitches(): List<PS> =
        repository.getAllProfileSwitches()
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }
            .blockingGet()

    // RUNNING MODE
    override fun getRunningModesFromTime(startTime: Long, ascending: Boolean): Single<List<RM>> =
        repository.getRunningModesFromTime(startTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getRunningModesFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<RM> =
        repository.getRunningModesFromTimeToTime(startTime, endTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }
            .blockingGet()

    override fun getRunningModesIncludingInvalidFromTime(startTime: Long, ascending: Boolean): Single<List<RM>> =
        repository.getRunningModesIncludingInvalidFromTime(startTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getNextSyncElementRunningMode(id: Long): Maybe<Pair<RM, RM>> =
        repository.getNextSyncElementRunningMode(id)
            .map { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }

    override fun getLastRunningModeId(): Long? = repository.getLastRunningModeId()
    override fun insertOrUpdateRunningMode(runningMode: RM, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): Single<PersistenceLayer.TransactionResult<RM>> =
        repository.runTransactionForResult(InsertOrUpdateRunningModeTransaction(runningMode.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while inserting RunningMode", it) }
            .map { result ->
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
            }

    override fun invalidateRunningMode(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): Single<PersistenceLayer.TransactionResult<RM>> =
        repository.runTransactionForResult(InvalidateRunningModeTransaction(id))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating RunningMode", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<RM>()
                val ueValues = mutableListOf<UE>()
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated RunningMode from ${source.name} $it")
                    transactionResult.invalidated.add(it.fromDb())
                    ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
                }
                log(ueValues)
                transactionResult
            }

    override fun cancelCurrentRunningMode(timestamp: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): Single<PersistenceLayer.TransactionResult<RM>> =
        repository.runTransactionForResult(CancelCurrentTemporaryRunningModeIfAnyTransaction(timestamp))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while canceling RunningMode", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<RM>()
                result.updated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated RunningMode from ${source.name} $it")
                    transactionResult.updated.add(it.fromDb())
                }
                transactionResult
            }

    override fun syncNsRunningModes(runningModes: List<RM>, doLog: Boolean): Single<PersistenceLayer.TransactionResult<RM>> =
        repository.runTransactionForResult(SyncNsRunningModeTransaction(runningModes.map { it.toDb() }))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving RunningMode", it) }
            .map { result ->
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
            }

    override fun updateRunningModesNsIds(runningModes: List<RM>): Single<PersistenceLayer.TransactionResult<RM>> =
        repository.runTransactionForResult(UpdateNsIdRunningModeTransaction(runningModes.asSequence().map { it.toDb() }.toList()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Updated nsId of RunningMode failed", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<RM>()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of RunningMode $it")
                    transactionResult.updatedNsId.add(it.fromDb())
                }
                transactionResult
            }

    override fun getRunningModeActiveAt(timestamp: Long): RM =
        repository.getRunningModeActiveAt(timestamp)?.fromDb()
            ?: RM(timestamp = 0, mode = RM.DEFAULT_MODE, duration = 0)

    override fun getRunningModeByNSId(nsId: String): RM? = repository.findRunningModeByNSId(nsId)?.fromDb()

    override fun getPermanentRunningModeActiveAt(timestamp: Long): RM =
        repository.getPermanentRunningModeActiveAt(timestamp).blockingGet()?.fromDb()
            ?: RM(timestamp = 0, mode = RM.DEFAULT_MODE, duration = 0)

    override fun getRunningModes(): List<RM> =
        repository.getAllRunningModes()
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }
            .blockingGet()

    // PS
    override fun getProfileSwitchesFromTime(startTime: Long, ascending: Boolean): Single<List<PS>> =
        repository.getProfileSwitchesFromTime(startTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getProfileSwitchesIncludingInvalidFromTime(startTime: Long, ascending: Boolean): Single<List<PS>> =
        repository.getProfileSwitchesIncludingInvalidFromTime(startTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getNextSyncElementProfileSwitch(id: Long): Maybe<Pair<PS, PS>> =
        repository.getNextSyncElementProfileSwitch(id)
            .map { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }

    override fun getLastProfileSwitchId(): Long? = repository.getLastProfileSwitchId()
    override fun insertOrUpdateProfileSwitch(profileSwitch: PS, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): Single<PersistenceLayer.TransactionResult<PS>> =
        repository.runTransactionForResult(InsertOrUpdateProfileSwitchTransaction(profileSwitch.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while inserting ProfileSwitch", it) }
            .map { result ->
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
            }

    override fun invalidateProfileSwitch(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): Single<PersistenceLayer.TransactionResult<PS>> =
        repository.runTransactionForResult(InvalidateProfileSwitchTransaction(id))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating ProfileSwitch", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<PS>()
                val ueValues = mutableListOf<UE>()
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated ProfileSwitch from ${source.name} $it")
                    transactionResult.invalidated.add(it.fromDb())
                    ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
                }
                log(ueValues)
                transactionResult
            }

    override fun syncNsProfileSwitches(profileSwitches: List<PS>, doLog: Boolean): Single<PersistenceLayer.TransactionResult<PS>> =
        repository.runTransactionForResult(SyncNsProfileSwitchTransaction(profileSwitches.map { it.toDb() }))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving ProfileSwitch", it) }
            .map { result ->
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
            }

    override fun updateProfileSwitchesNsIds(profileSwitches: List<PS>): Single<PersistenceLayer.TransactionResult<PS>> =
        repository.runTransactionForResult(UpdateNsIdProfileSwitchTransaction(profileSwitches.asSequence().map { it.toDb() }.toList()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Updated nsId of ProfileSwitch failed", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<PS>()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of ProfileSwitch $it")
                    transactionResult.updatedNsId.add(it.fromDb())
                }
                transactionResult
            }

    // TB
    override fun getTemporaryBasalActiveAt(timestamp: Long): TB? =
        repository.getTemporaryBasalActiveAt(timestamp).blockingGet()?.fromDb()

    override fun getOldestTemporaryBasalRecord(): TB? =
        repository.getOldestTemporaryBasalRecord().blockingGet()?.fromDb()

    override fun getLastTemporaryBasalId(): Long? = repository.getLastTemporaryBasalId()
    override fun getTemporaryBasalByNSId(nsId: String): TB? = repository.findTemporaryBasalByNSId(nsId)?.fromDb()

    override fun getTemporaryBasalsActiveBetweenTimeAndTime(startTime: Long, endTime: Long): List<TB> =
        repository.getTemporaryBasalsActiveBetweenTimeAndTime(startTime, endTime).blockingGet().asSequence().map { it.fromDb() }.toList()

    override fun getTemporaryBasalsStartingFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<TB> =
        repository.getTemporaryBasalsStartingFromTimeToTime(startTime, endTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }
            .blockingGet()

    override fun getTemporaryBasalsStartingFromTime(startTime: Long, ascending: Boolean): Single<List<TB>> =
        repository.getTemporaryBasalsStartingFromTime(startTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getTemporaryBasalsStartingFromTimeIncludingInvalid(startTime: Long, ascending: Boolean): Single<List<TB>> =
        repository.getTemporaryBasalsStartingFromTimeIncludingInvalid(startTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getNextSyncElementTemporaryBasal(id: Long): Maybe<Pair<TB, TB>> =
        repository.getNextSyncElementTemporaryBasal(id)
            .map { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }

    override fun invalidateTemporaryBasal(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): Single<PersistenceLayer.TransactionResult<TB>> =
        repository.runTransactionForResult(InvalidateTemporaryBasalTransaction(id))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating TemporaryBasal", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TB>()
                val ueValues = mutableListOf<UE>()
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryBasal from ${source.name} $it")
                    transactionResult.invalidated.add(it.fromDb())
                    ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
                }
                log(ueValues)
                transactionResult
            }

    override fun syncNsTemporaryBasals(temporaryBasals: List<TB>, doLog: Boolean): Single<PersistenceLayer.TransactionResult<TB>> =
        repository.runTransactionForResult(SyncNsTemporaryBasalTransaction(temporaryBasals.asSequence().map { it.toDb() }.toList(), config.AAPSCLIENT))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving TemporaryBasal", it) }
            .map { result ->
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
            }

    override fun updateTemporaryBasalsNsIds(temporaryBasals: List<TB>): Single<PersistenceLayer.TransactionResult<TB>> =
        repository.runTransactionForResult(UpdateNsIdTemporaryBasalTransaction(temporaryBasals.asSequence().map { it.toDb() }.toList()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Updated nsId of TemporaryBasal failed", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TB>()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of TemporaryBasal $it")
                    transactionResult.updatedNsId.add(it.fromDb())
                }
                transactionResult
            }

    override fun syncPumpTemporaryBasal(temporaryBasal: TB, type: TB.Type?): Single<PersistenceLayer.TransactionResult<TB>> =
        repository.runTransactionForResult(SyncPumpTemporaryBasalTransaction(temporaryBasal.toDb(), type?.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while syncing TemporaryBasal", it) }
            .map { result ->
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
            }

    override fun syncPumpCancelTemporaryBasalIfAny(timestamp: Long, endPumpId: Long, pumpType: PumpType, pumpSerial: String): Single<PersistenceLayer.TransactionResult<TB>> =
        repository.runTransactionForResult(SyncPumpCancelTemporaryBasalIfAnyTransaction(timestamp, endPumpId, pumpType.toDb(), pumpSerial))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while syncing TemporaryBasal", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TB>()
                result.updated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated TemporaryBasal ${it.first} New: ${it.second}")
                    transactionResult.updated.add(it.second.fromDb())
                }
                transactionResult
            }

    override fun syncPumpInvalidateTemporaryBasalWithTempId(temporaryId: Long): Single<PersistenceLayer.TransactionResult<TB>> =
        repository.runTransactionForResult(InvalidateTemporaryBasalWithTempIdTransaction(temporaryId))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while syncing TemporaryBasal", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TB>()
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryBasal $it")
                    transactionResult.invalidated.add(it.fromDb())
                }
                transactionResult
            }

    override fun syncPumpInvalidateTemporaryBasalWithPumpId(pumpId: Long, pumpType: PumpType, pumpSerial: String): Single<PersistenceLayer.TransactionResult<TB>> =
        repository.runTransactionForResult(InvalidateTemporaryBasalTransactionWithPumpId(pumpId, pumpType.toDb(), pumpSerial))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while syncing TemporaryBasal", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TB>()
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryBasal $it")
                    transactionResult.invalidated.add(it.fromDb())
                }
                transactionResult
            }

    override fun syncPumpTemporaryBasalWithTempId(temporaryBasal: TB, type: TB.Type?): Single<PersistenceLayer.TransactionResult<TB>> =
        repository.runTransactionForResult(SyncTemporaryBasalWithTempIdTransaction(temporaryBasal.toDb(), type?.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving TemporaryBasal", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TB>()
                result.updated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated TemporaryBasal ${it.first} New: ${it.second}")
                    transactionResult.updated.add(it.second.fromDb())
                }
                transactionResult
            }

    override fun insertTemporaryBasalWithTempId(temporaryBasal: TB): Single<PersistenceLayer.TransactionResult<TB>> =
        repository.runTransactionForResult(InsertTemporaryBasalWithTempIdTransaction(temporaryBasal.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving TemporaryBasal", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TB>()
                result.inserted.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Inserted TemporaryBasal $it")
                    transactionResult.inserted.add(it.fromDb())
                }
                transactionResult
            }

    // EB
    override fun getExtendedBolusActiveAt(timestamp: Long): EB? =
        repository.getExtendedBolusActiveAt(timestamp).blockingGet()?.fromDb()

    override fun getOldestExtendedBolusRecord(): EB? =
        repository.getOldestExtendedBolusRecord().blockingGet()?.fromDb()

    override fun getLastExtendedBolusId(): Long? = repository.getLastExtendedBolusId()
    override fun getExtendedBolusByNSId(nsId: String): EB? = repository.findExtendedBolusByNSId(nsId)?.fromDb()

    override fun getExtendedBolusesStartingFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<EB> =
        repository.getExtendedBolusesStartingFromTimeToTime(startTime, endTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }
            .blockingGet()

    override fun getExtendedBolusesStartingFromTime(startTime: Long, ascending: Boolean): Single<List<EB>> =
        repository.getExtendedBolusesStartingFromTime(startTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getExtendedBolusStartingFromTimeIncludingInvalid(startTime: Long, ascending: Boolean): Single<List<EB>> =
        repository.getExtendedBolusStartingFromTimeIncludingInvalid(startTime, ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getNextSyncElementExtendedBolus(id: Long): Maybe<Pair<EB, EB>> =
        repository.getNextSyncElementExtendedBolus(id)
            .map { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }

    override fun invalidateExtendedBolus(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): Single<PersistenceLayer.TransactionResult<EB>> =
        repository.runTransactionForResult(InvalidateExtendedBolusTransaction(id))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating ExtendedBolus", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<EB>()
                val ueValues = mutableListOf<UE>()
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated ExtendedBolus from ${source.name} $it")
                    transactionResult.invalidated.add(it.fromDb())
                    ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
                }
                log(ueValues)
                transactionResult
            }

    override fun syncNsExtendedBoluses(extendedBoluses: List<EB>, doLog: Boolean): Single<PersistenceLayer.TransactionResult<EB>> =
        repository.runTransactionForResult(SyncNsExtendedBolusTransaction(extendedBoluses.asSequence().map { it.toDb() }.toList(), config.AAPSCLIENT))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving ExtendedBolus", it) }
            .map { result ->
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
            }

    // TT
    override fun getTemporaryTargetActiveAt(timestamp: Long): TT? =
        repository.getTemporaryTargetActiveAt(timestamp).blockingGet()?.fromDb()

    override fun getLastTemporaryTargetId(): Long? = repository.getLastTempTargetId()
    override fun getTemporaryTargetByNSId(nsId: String): TT? = repository.findTemporaryTargetByNSId(nsId)?.fromDb()

    override fun getTemporaryTargetDataFromTime(timestamp: Long, ascending: Boolean): Single<List<TT>> =
        repository.getTemporaryTargetDataFromTime(timestamp, ascending).map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getTemporaryTargetDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<TT>> =
        repository.getTemporaryTargetDataIncludingInvalidFromTime(timestamp, ascending).map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getNextSyncElementTemporaryTarget(id: Long): Maybe<Pair<TT, TT>> =
        repository.getNextSyncElementTemporaryTarget(id)
            .map { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }

    override fun invalidateTemporaryTarget(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>)
        : Single<PersistenceLayer.TransactionResult<TT>> =
        repository.runTransactionForResult(InvalidateTemporaryTargetTransaction(id))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating TemporaryTarget", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TT>()
                val ueValues = mutableListOf<UE>()
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryTarget from ${source.name} $it")
                    transactionResult.invalidated.add(it.fromDb())
                    ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
                }
                log(ueValues)
                transactionResult
            }

    override fun insertAndCancelCurrentTemporaryTarget(temporaryTarget: TT, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>)
        : Single<PersistenceLayer.TransactionResult<TT>> =
        repository.runTransactionForResult(InsertAndCancelCurrentTemporaryTargetTransaction(temporaryTarget.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while inserting TemporaryTarget", it) }
            .map { result ->
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
            }

    override fun cancelCurrentTemporaryTargetIfAny(timestamp: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>)
        : Single<PersistenceLayer.TransactionResult<TT>> =
        repository.runTransactionForResult(CancelCurrentTemporaryTargetIfAnyTransaction(timestamp))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while updating TemporaryTarget", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TT>()
                result.updated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated TemporaryTarget from ${source.name} $it")
                    transactionResult.updated.add(it.fromDb())
                }
                transactionResult
            }

    override fun syncNsTemporaryTargets(temporaryTargets: List<TT>, doLog: Boolean): Single<PersistenceLayer.TransactionResult<TT>> =
        repository.runTransactionForResult(SyncNsTemporaryTargetTransaction(temporaryTargets.asSequence().map { it.toDb() }.toList()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving TemporaryTarget", it) }
            .map { result ->
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
            }

    override fun updateTemporaryTargetsNsIds(temporaryTargets: List<TT>): Single<PersistenceLayer.TransactionResult<TT>> =
        repository.runTransactionForResult(UpdateNsIdTemporaryTargetTransaction(temporaryTargets.asSequence().map { it.toDb() }.toList()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while updating nsId TemporaryTarget", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TT>()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of TemporaryTarget $it")
                    transactionResult.updatedNsId.add(it.fromDb())
                }
                transactionResult
            }

    override fun getLastTherapyEventId(): Long? = repository.getLastTherapyEventId()
    override fun getTherapyEventByNSId(nsId: String): TE? = repository.findTherapyEventByNSId(nsId)?.fromDb()

    // TE
    override fun getLastTherapyRecordUpToNow(type: TE.Type): TE? =
        repository.getLastTherapyRecordUpToNow(type.toDb()).blockingGet()?.fromDb()

    override fun getTherapyEventDataFromToTime(from: Long, to: Long): Single<List<TE>> =
        repository.compatGetTherapyEventDataFromToTime(from, to).map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getTherapyEventDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<TE>> =
        repository.getTherapyEventDataIncludingInvalidFromTime(timestamp, ascending).map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getTherapyEventDataFromTime(timestamp: Long, ascending: Boolean): Single<List<TE>> =
        repository.getTherapyEventDataFromTime(timestamp, ascending).map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getTherapyEventDataFromTime(timestamp: Long, type: TE.Type, ascending: Boolean): List<TE> =
        repository.getTherapyEventDataFromTime(timestamp, type.toDb(), ascending)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }
            .blockingGet()

    override fun getNextSyncElementTherapyEvent(id: Long): Maybe<Pair<TE, TE>> =
        repository.getNextSyncElementTherapyEvent(id)
            .map { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }

    override fun insertPumpTherapyEventIfNewByTimestamp(therapyEvent: TE, timestamp: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>)
        : Single<PersistenceLayer.TransactionResult<TE>> =
        repository.runTransactionForResult(InsertIfNewByTimestampTherapyEventTransaction(therapyEvent.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving TherapyEvent $therapyEvent", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TE>()
                val ueValues = mutableListOf<UE>()
                result.inserted.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Inserted TherapyEvent from ${source.name} $it")
                    transactionResult.inserted.add(it.fromDb())
                    ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
                }
                log(ueValues)
                transactionResult
            }

    override fun insertOrUpdateTherapyEvent(therapyEvent: TE): Single<PersistenceLayer.TransactionResult<TE>> =
        repository.runTransactionForResult(InsertOrUpdateTherapyEventTransaction(therapyEvent.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving HeartRate", it) }
            .map { result ->
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
            }

    override fun invalidateTherapyEvent(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>)
        : Single<PersistenceLayer.TransactionResult<TE>> =
        repository.runTransactionForResult(InvalidateTherapyEventTransaction(id))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating TherapyEvent", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TE>()
                val ueValues = mutableListOf<UE>()
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated TherapyEvent from ${source.name} $it")
                    transactionResult.invalidated.add(it.fromDb())
                    ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note ?: "", values = listValues))
                }
                log(ueValues)
                transactionResult
            }

    override fun invalidateTherapyEventsWithNote(note: String, action: Action, source: Sources): Single<PersistenceLayer.TransactionResult<TE>> =
        repository.runTransactionForResult(InvalidateTherapyEventsWithNoteTransaction(note))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating TherapyEvent", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TE>()
                val ueValues = mutableListOf<UE>()
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated TherapyEvent from ${source.name} $it")
                    transactionResult.invalidated.add(it.fromDb())
                    ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = note, values = emptyList()))
                }
                log(ueValues)
                transactionResult
            }

    override fun syncNsTherapyEvents(therapyEvents: List<TE>, doLog: Boolean): Single<PersistenceLayer.TransactionResult<TE>> =
        repository.runTransactionForResult(SyncNsTherapyEventTransaction(therapyEvents.asSequence().map { it.toDb() }.toList(), config.AAPSCLIENT))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving TherapyEvent", it) }
            .map { result ->
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
            }

    override fun updateTherapyEventsNsIds(therapyEvents: List<TE>): Single<PersistenceLayer.TransactionResult<TE>> =
        repository.runTransactionForResult(UpdateNsIdTherapyEventTransaction(therapyEvents.asSequence().map { it.toDb() }.toList()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Updated nsId of TherapyEvent failed", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TE>()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of TherapyEvent $it")
                    transactionResult.updatedNsId.add(it.fromDb())
                }
                transactionResult
            }

    // DS
    override fun getNextSyncElementDeviceStatus(id: Long): Maybe<DS> =
        repository.getNextSyncElementDeviceStatus(id).map { it.fromDb() }

    override fun getLastDeviceStatusId(): Long? = repository.getLastDeviceStatusId()

    override fun insertDeviceStatus(deviceStatus: DS) {
        repository.insert(deviceStatus.toDb())
        aapsLogger.debug(LTag.DATABASE, "Inserted DeviceStatus $deviceStatus")
    }

    override fun updateDeviceStatusesNsIds(deviceStatuses: List<DS>): Single<PersistenceLayer.TransactionResult<DS>> =
        repository.runTransactionForResult(UpdateNsIdDeviceStatusTransaction(deviceStatuses.asSequence().map { it.toDb() }.toList()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Updated nsId of DeviceStatus failed", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<DS>()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of DeviceStatus $it")
                    transactionResult.updatedNsId.add(it.fromDb())
                }
                transactionResult
            }

    // HR
    override fun getHeartRatesFromTime(startTime: Long): List<HR> =
        repository.getHeartRatesFromTime(startTime).asSequence().map { it.fromDb() }.toList()

    override fun getHeartRatesFromTimeToTime(startTime: Long, endTime: Long): List<HR> =
        repository.getHeartRatesFromTimeToTime(startTime, endTime)
            .map { list -> list.asSequence().map { it.fromDb() }.toList() }
            .blockingGet()

    override fun insertOrUpdateHeartRate(heartRate: HR): Single<PersistenceLayer.TransactionResult<HR>> =
        repository.runTransactionForResult(InsertOrUpdateHeartRateTransaction(heartRate.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving HeartRate", it) }
            .map { result ->
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
            }

    override fun getFoods(): Single<List<FD>> =
        repository.getFoodData().map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getNextSyncElementFood(id: Long): Maybe<Pair<FD, FD>> =
        repository.getNextSyncElementFood(id)
            .map { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }

    override fun getLastFoodId(): Long? = repository.getLastFoodId()

    override fun invalidateFood(id: Long, action: Action, source: Sources): Single<PersistenceLayer.TransactionResult<FD>> =
        repository.runTransactionForResult(InvalidateFoodTransaction(id))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating Food", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<FD>()
                val ueValues = mutableListOf<UE>()
                result.invalidated.forEach {
                    ueValues.add(UE(timestamp = dateUtil.now(), action = action, source = source, note = it.name, values = emptyList()))
                    aapsLogger.debug(LTag.DATABASE, "Invalidated Food from ${source.name} $it")
                    transactionResult.invalidated.add(it.fromDb())
                }
                log(ueValues)
                transactionResult
            }

    override fun syncNsFood(foods: List<FD>): Single<PersistenceLayer.TransactionResult<FD>> =
        repository.runTransactionForResult(SyncNsFoodTransaction(foods.asSequence().map { it.toDb() }.toList()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving Food", it) }
            .map { result ->
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
            }

    override fun updateFoodsNsIds(foods: List<FD>): Single<PersistenceLayer.TransactionResult<FD>> =
        repository.runTransactionForResult(UpdateNsIdFoodTransaction(foods.asSequence().map { it.toDb() }.toList()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Updated nsId of Food failed", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<FD>()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of Food $it")
                    transactionResult.updatedNsId.add(it.fromDb())
                }
                transactionResult
            }

    // UE
    override fun insertUserEntries(entries: List<UE>): Single<PersistenceLayer.TransactionResult<UE>> =
        repository.runTransactionForResult(UserEntryTransaction(entries.asSequence().map { it.toDb() }.toList()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving UserEntries $entries", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<UE>()
                result.forEach {
                    aapsLogger.debug("USER ENTRY: ${dateUtil.dateAndTimeAndSecondsString(it.timestamp)} ${it.action} ${it.source} ${it.note} ${it.values}")
                    transactionResult.inserted.add(it.fromDb())
                }
                transactionResult
            }

    override fun getUserEntryDataFromTime(timestamp: Long): Single<List<UE>> =
        repository.getUserEntryDataFromTime(timestamp).map { list -> list.asSequence().map { it.fromDb() }.toList() }

    override fun getUserEntryFilteredDataFromTime(timestamp: Long): Single<List<UE>> =
        repository.getUserEntryFilteredDataFromTime(timestamp).map { list -> list.asSequence().map { it.fromDb() }.toList() }

    // TDD
    override fun clearCachedTddData(timestamp: Long) = repository.clearCachedTddData(timestamp)
    override fun getLastTotalDailyDoses(count: Int, ascending: Boolean): List<TDD> =
        repository.getLastTotalDailyDoses(count, ascending).map { list -> list.asSequence().map { it.fromDb() }.toList() }.blockingGet()

    override fun getCalculatedTotalDailyDose(timestamp: Long): TDD? =
        repository.getCalculatedTotalDailyDose(timestamp).map { it.fromDb() }.blockingGet()

    override fun insertOrUpdateCachedTotalDailyDose(totalDailyDose: TDD): Single<PersistenceLayer.TransactionResult<TDD>> =
        repository.runTransactionForResult(InsertOrUpdateCachedTotalDailyDoseTransaction(totalDailyDose.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving TotalDailyDose $it") }
            .map { result ->
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
            }

    override fun insertOrUpdateTotalDailyDose(totalDailyDose: TDD): Single<PersistenceLayer.TransactionResult<TDD>> =
        repository.runTransactionForResult(SyncPumpTotalDailyDoseTransaction(totalDailyDose.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving TotalDailyDose $it") }
            .map { result ->
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
            }

    // SC
    override fun getStepsCountFromTime(from: Long): List<SC> =
        repository.getStepsCountFromTime(from).map { list -> list.asSequence().map { it.fromDb() }.toList() }.blockingGet()

    override fun getStepsCountFromTimeToTime(startTime: Long, endTime: Long): List<SC> =
        repository.getStepsCountFromTimeToTime(startTime, endTime).map { list -> list.asSequence().map { it.fromDb() }.toList() }.blockingGet()

    override fun getLastStepsCountFromTimeToTime(startTime: Long, endTime: Long): SC? =
        repository.getLastStepsCountFromTimeToTime(startTime, endTime).blockingGet()?.fromDb()

    override fun insertOrUpdateStepsCount(stepsCount: SC): Single<PersistenceLayer.TransactionResult<SC>> =
        repository.runTransactionForResult(InsertOrUpdateStepsCountTransaction(stepsCount.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving StepsCount $it") }
            .map { result ->
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
            }

    // VersionChange
    override fun insertVersionChangeIfChanged(versionName: String, versionCode: Int, gitRemote: String?, commitHash: String?): Completable =
        repository.runTransaction(VersionChangeTransaction(versionName, versionCode, gitRemote, commitHash))

    override fun collectNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): NE =
        repository.collectNewEntriesSince(since, until, limit, offset).fromDb()

    override fun getApsResultCloseTo(timestamp: Long): APSResult? =
        repository.getApsResultCloseTo(timestamp).blockingGet()?.fromDb(apsResultProvider)

    override fun getApsResults(start: Long, end: Long): List<APSResult> =
        repository.getApsResults(start, end).map { list -> list.asSequence().map { it.fromDb(apsResultProvider) }.toList() }.blockingGet()

    override fun insertOrUpdateApsResult(apsResult: APSResult): Single<PersistenceLayer.TransactionResult<APSResult>> =
        repository.runTransactionForResult(InsertOrUpdateApsResultTransaction(apsResult.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving APSResult", it) }
            .map { result ->
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
            }
}
