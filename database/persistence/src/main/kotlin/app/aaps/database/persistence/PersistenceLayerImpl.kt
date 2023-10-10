package app.aaps.database.persistence

import app.aaps.core.data.db.BS
import app.aaps.core.data.db.CA
import app.aaps.core.data.db.EB
import app.aaps.core.data.db.GV
import app.aaps.core.data.db.GlucoseUnit
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
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.main.extensions.fromDb
import app.aaps.core.main.extensions.toDb
import app.aaps.database.ValueWrapper
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.impl.AppRepository
import app.aaps.database.impl.transactions.CancelCurrentTemporaryTargetIfAnyTransaction
import app.aaps.database.impl.transactions.CgmSourceTransaction
import app.aaps.database.impl.transactions.InsertAndCancelCurrentOfflineEventTransaction
import app.aaps.database.impl.transactions.InsertAndCancelCurrentTemporaryTargetTransaction
import app.aaps.database.impl.transactions.InsertBolusWithTempIdTransaction
import app.aaps.database.impl.transactions.InsertIfNewByTimestampCarbsTransaction
import app.aaps.database.impl.transactions.InsertIfNewByTimestampTherapyEventTransaction
import app.aaps.database.impl.transactions.InsertOrUpdateBolusCalculatorResultTransaction
import app.aaps.database.impl.transactions.InsertOrUpdateBolusTransaction
import app.aaps.database.impl.transactions.InsertOrUpdateCarbsTransaction
import app.aaps.database.impl.transactions.InsertTemporaryBasalWithTempIdTransaction
import app.aaps.database.impl.transactions.InvalidateExtendedBolusTransaction
import app.aaps.database.impl.transactions.InvalidateGlucoseValueTransaction
import app.aaps.database.impl.transactions.InvalidateTemporaryBasalTransaction
import app.aaps.database.impl.transactions.InvalidateTemporaryTargetTransaction
import app.aaps.database.impl.transactions.InvalidateTherapyEventTransaction
import app.aaps.database.impl.transactions.InvalidateTherapyEventsWithNoteTransaction
import app.aaps.database.impl.transactions.SyncBolusWithTempIdTransaction
import app.aaps.database.impl.transactions.SyncNsBolusTransaction
import app.aaps.database.impl.transactions.SyncNsCarbsTransaction
import app.aaps.database.impl.transactions.SyncNsExtendedBolusTransaction
import app.aaps.database.impl.transactions.SyncNsOfflineEventTransaction
import app.aaps.database.impl.transactions.SyncNsTemporaryBasalTransaction
import app.aaps.database.impl.transactions.SyncNsTemporaryTargetTransaction
import app.aaps.database.impl.transactions.SyncNsTherapyEventTransaction
import app.aaps.database.impl.transactions.SyncPumpBolusTransaction
import app.aaps.database.impl.transactions.SyncPumpExtendedBolusTransaction
import app.aaps.database.impl.transactions.SyncPumpTemporaryBasalTransaction
import app.aaps.database.impl.transactions.SyncTemporaryBasalWithTempIdTransaction
import app.aaps.database.impl.transactions.UpdateNsIdBolusTransaction
import app.aaps.database.impl.transactions.UpdateNsIdCarbsTransaction
import app.aaps.database.impl.transactions.UpdateNsIdExtendedBolusTransaction
import app.aaps.database.impl.transactions.UpdateNsIdGlucoseValueTransaction
import app.aaps.database.impl.transactions.UpdateNsIdOfflineEventTransaction
import app.aaps.database.impl.transactions.UpdateNsIdTemporaryBasalTransaction
import app.aaps.database.impl.transactions.UpdateNsIdTemporaryTargetTransaction
import app.aaps.database.impl.transactions.UpdateNsIdTherapyEventTransaction
import app.aaps.database.impl.transactions.UserEntryTransaction
import app.aaps.database.persistence.converters.fromDb
import app.aaps.database.persistence.converters.toDb
import dagger.Reusable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Reusable
class PersistenceLayerImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val repository: AppRepository,
    private val dateUtil: DateUtil,
    private val config: Config
) : PersistenceLayer {

    private val disposable = CompositeDisposable()
    private fun <S, D> Single<ValueWrapper<S>>.fromDb(converter: S.() -> D): Single<ValueWrapper<D>> =
        this.map { wrapper ->
            when (wrapper) {
                is ValueWrapper.Existing -> ValueWrapper.Existing(wrapper.value.converter())
                is ValueWrapper.Absent   -> ValueWrapper.Absent()
            }
        }

    private val compositeDisposable = CompositeDisposable()
    private fun log(timestamp: Long = dateUtil.now(), action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit?> = listOf()) {
        fun log(entries: List<UE>) {
            compositeDisposable += insertUserEntries(entries).subscribe()
        }
        if (config.NSCLIENT.not())
            log(listOf(UE(timestamp = timestamp, action = action, source = source, note = note ?: "", values = listValues.toList().filterNotNull())))
    }

    override fun clearDatabases() = repository.clearDatabases()
    override fun cleanupDatabase(keepDays: Long, deleteTrackedChanges: Boolean): String = repository.cleanupDatabase(keepDays, deleteTrackedChanges)

    override fun insertOrUpdate(bolusCalculatorResult: BolusCalculatorResult) {
        disposable += repository.runTransactionForResult(InsertOrUpdateBolusCalculatorResultTransaction(bolusCalculatorResult))
            .subscribe(
                { result -> result.inserted.forEach { inserted -> aapsLogger.debug(LTag.DATABASE, "Inserted bolusCalculatorResult $inserted") } },
                { aapsLogger.error(LTag.DATABASE, "Error while saving bolusCalculatorResult", it) }
            )
    }

    // BS
    override fun getLastBolus(): BS? = repository.getLastBolusRecord()?.fromDb()

    override fun getLastBolusOfType(type: BS.Type): BS? =
        repository.getLastBolusRecordOfType(type.toDb()).blockingGet()?.fromDb()

    override fun getLastBolusId(): Long? = repository.getLastBolusId()
    override fun getBolusByNSId(nsId: String): BS? = repository.getBolusByNSId(nsId)?.fromDb()

    override fun getBolusesFromTime(startTime: Long, ascending: Boolean): Single<List<BS>> =
        repository.getBolusesDataFromTime(startTime, ascending)
            .map { list -> list.map { it.fromDb() } }

    override fun getBolusesFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<BS> =
        repository.getBolusesDataFromTimeToTime(startTime, endTime, ascending)
            .map { list -> list.map { it.fromDb() } }
            .blockingGet()

    override fun getBolusesFromTimeIncludingInvalid(startTime: Long, ascending: Boolean): Single<List<BS>> =
        repository.getBolusesIncludingInvalidFromTime(startTime, ascending)
            .map { list -> list.map { it.fromDb() } }

    override fun getNextSyncElementBolus(id: Long): Maybe<Pair<BS, BS>> =
        repository.getNextSyncElementBolus(id)
            .map { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }

    override fun insertOrUpdateBolus(bolus: BS, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit?>): Single<PersistenceLayer.TransactionResult<BS>> =
        repository.runTransactionForResult(InsertOrUpdateBolusTransaction(bolus.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving Bolus", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<BS>()
                result.inserted.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Inserted Bolus $it")
                    transactionResult.inserted.add(it.fromDb())
                }
                result.updated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Inserted Bolus $it")
                    transactionResult.updated.add(it.fromDb())
                }
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

    override fun syncNsBolus(boluses: List<BS>): Single<PersistenceLayer.TransactionResult<BS>> =
        repository.runTransactionForResult(SyncNsBolusTransaction(boluses.map { it.toDb() }))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving bolus", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<BS>()
                result.inserted.forEach {
                    log(
                        timestamp = dateUtil.now(),
                        action = Action.BOLUS,
                        source = Sources.NSClient,
                        note = it.notes ?: "",
                        listValues = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Insulin(it.amount))
                    )
                    aapsLogger.debug(LTag.DATABASE, "Inserted bolus $it")
                    transactionResult.inserted.add(it.fromDb())
                }
                result.invalidated.forEach {
                    log(
                        timestamp = dateUtil.now(),
                        action = Action.BOLUS_REMOVED,
                        source = Sources.NSClient,
                        note = "",
                        listValues = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Insulin(it.amount))
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
                transactionResult
            }

    override fun updateBolusesNsIds(boluses: List<BS>): Single<PersistenceLayer.TransactionResult<BS>> =
        repository.runTransactionForResult(UpdateNsIdBolusTransaction(boluses.map { it.toDb() }))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Updated nsId of Bolus failed", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<BS>()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of Bolus $it")
                    transactionResult.updatedNsId.add(it.fromDb())
                }
                transactionResult
            }

    // CA
    override fun getLastCarbsId(): Long? = repository.getLastCarbsId()
    override fun getCarbsByNSId(nsId: String): CA? = repository.getCarbsByNSId(nsId)?.fromDb()

    override fun getCarbsFromTime(startTime: Long, ascending: Boolean): Single<List<CA>> =
        repository.getCarbsDataFromTime(startTime, ascending)
            .map { list -> list.map { it.fromDb() } }

    override fun getCarbsFromTimeIncludingInvalid(startTime: Long, ascending: Boolean): Single<List<CA>> =
        repository.getCarbsIncludingInvalidFromTime(startTime, ascending)
            .map { list -> list.map { it.fromDb() } }

    override fun getCarbsFromTimeToTimeExpanded(startTime: Long, endTime: Long, ascending: Boolean): List<CA> =
        repository.getCarbsDataFromTimeToTimeExpanded(startTime, endTime, ascending)
            .map { list -> list.map { it.fromDb() } }
            .blockingGet()

    override fun getNextSyncElementCarbs(id: Long): Maybe<Pair<CA, CA>> =
        repository.getNextSyncElementCarbs(id)
            .map { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }

    override fun insertOrUpdateCarbs(carbs: CA, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit?>): Single<PersistenceLayer.TransactionResult<CA>> =
        repository.runTransactionForResult(InsertOrUpdateCarbsTransaction(carbs.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving Carbs", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<CA>()
                result.inserted.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Inserted Carbs $it")
                    transactionResult.inserted.add(it.fromDb())
                }
                result.updated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Inserted Carbs $it")
                    transactionResult.updated.add(it.fromDb())
                }
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

    override fun syncNsCarbs(carbs: List<CA>): Single<PersistenceLayer.TransactionResult<CA>> =
        repository.runTransactionForResult(SyncNsCarbsTransaction(carbs.map { it.toDb() }, config.NSCLIENT))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving carbs", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<CA>()
                result.inserted.forEach {
                    log(
                        timestamp = dateUtil.now(),
                        action = Action.CARBS,
                        source = Sources.NSClient,
                        note = it.notes ?: "",
                        listValues = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Gram(it.amount.toInt()))
                    )
                    aapsLogger.debug(LTag.DATABASE, "Inserted carbs $it")
                    transactionResult.inserted.add(it.fromDb())
                }
                result.invalidated.forEach {
                    log(
                        timestamp = dateUtil.now(),
                        action = Action.CARBS_REMOVED,
                        source = Sources.NSClient,
                        note = "",
                        listValues = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Gram(it.amount.toInt()))
                    )
                    aapsLogger.debug(LTag.DATABASE, "Invalidated carbs $it")
                    transactionResult.invalidated.add(it.fromDb())
                }
                result.updated.forEach {
                    log(
                        timestamp = dateUtil.now(),
                        action = Action.CARBS,
                        source = Sources.NSClient,
                        note = it.notes ?: "",
                        listValues = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Gram(it.amount.toInt()))
                    )
                    aapsLogger.debug(LTag.DATABASE, "Updated carbs $it")
                    transactionResult.updated.add(it.fromDb())
                }
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId carbs $it")
                    transactionResult.updatedNsId.add(it.fromDb())
                }
                transactionResult
            }

    override fun updateCarbsNsIds(carbs: List<CA>): Single<PersistenceLayer.TransactionResult<CA>> =
        repository.runTransactionForResult(UpdateNsIdCarbsTransaction(carbs.map { it.toDb() }))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Updated nsId of Carbs failed", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<CA>()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of Carbs $it")
                    transactionResult.updatedNsId.add(it.fromDb())
                }
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
            .map { list -> list.map { it.fromDb() } }
            .blockingGet()

    override fun getBgReadingsDataFromTime(timestamp: Long, ascending: Boolean): Single<List<GV>> =
        repository.compatGetBgReadingsDataFromTime(timestamp, ascending)
            .map { list -> list.map { it.fromDb() } }

    override fun getBgReadingByNSId(nsId: String): GV? =
        repository.findBgReadingByNSId(nsId)?.fromDb()

    override fun invalidateGlucoseValue(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit?>): Single<PersistenceLayer.TransactionResult<GV>> =
        repository.runTransactionForResult(InvalidateGlucoseValueTransaction(id))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating GlucoseValue", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<GV>()
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated GlucoseValue from ${source.name} $it")
                    transactionResult.invalidated.add(it.fromDb())
                    log(action = action, source = source, note = note, listValues = listValues)
                }
                transactionResult
            }

    private fun PersistenceLayer.Calibration.toDb() = CgmSourceTransaction.Calibration(timestamp, value, glucoseUnit.toDb())
    override fun insertCgmSourceData(caller: Sources, glucoseValues: List<GV>, calibrations: List<PersistenceLayer.Calibration>, sensorInsertionTime: Long?)
        : Single<PersistenceLayer.TransactionResult<GV>> =
        repository.runTransactionForResult(CgmSourceTransaction(glucoseValues.map { it.toDb() }, calibrations.map { it.toDb() }, sensorInsertionTime))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving cgm values from ${caller.name}", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<GV>()
                result.inserted.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Inserted GlucoseValue from ${caller.name} $it")
                    transactionResult.inserted.add(it.fromDb())
                }
                result.updated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated GlucoseValue from ${caller.name} $it")
                    transactionResult.updated.add(it.fromDb())
                }
                result.sensorInsertionsInserted.forEach {
                    log(
                        action = Action.CAREPORTAL,
                        source = caller,
                        listValues = listOf(
                            ValueWithUnit.Timestamp(it.timestamp),
                            ValueWithUnit.TEType(it.type.fromDb())
                        )
                    )
                    aapsLogger.debug(LTag.DATABASE, "Inserted sensor insertion from ${caller.name} $it")
                    transactionResult.sensorInsertionsInserted.add(it.fromDb())
                }
                result.calibrationsInserted.forEach { calibration ->
                    calibration.glucose?.let { glucoseValue ->
                        log(
                            action = Action.CALIBRATION,
                            source = caller,
                            listValues = listOf(
                                ValueWithUnit.Timestamp(calibration.timestamp),
                                ValueWithUnit.TEType(calibration.type.fromDb()),
                                ValueWithUnit.fromGlucoseUnit(glucoseValue, calibration.glucoseUnit.fromDb())
                            )
                        )
                    }
                    aapsLogger.debug(LTag.DATABASE, "Inserted calibration from ${caller.name} $calibration")
                    transactionResult.calibrationsInserted.add(calibration.fromDb())
                }
                transactionResult
            }

    override fun updateGlucoseValuesNsIds(glucoseValues: List<GV>): Single<PersistenceLayer.TransactionResult<GV>> =
        repository.runTransactionForResult(UpdateNsIdGlucoseValueTransaction(glucoseValues.map { it.toDb() }))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Updated nsId of GlucoseValue failed", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<GV>()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of GlucoseValue $it")
                    transactionResult.updatedNsId.add(it.fromDb())
                }
                transactionResult
            }

    override fun updateExtendedBolusesNsIds(extendedBoluses: List<EB>): Single<PersistenceLayer.TransactionResult<EB>> =
        repository.runTransactionForResult(UpdateNsIdExtendedBolusTransaction(extendedBoluses.map { it.toDb() }))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Updated nsId of EB failed", it) }
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

    // EPS
    override fun getEffectiveProfileSwitchActiveAt(timestamp: Long): Single<ValueWrapper<EffectiveProfileSwitch>> = repository.getEffectiveProfileSwitchActiveAt(timestamp)

    // TB
    override fun getTemporaryBasalActiveAt(timestamp: Long): TB? =
        repository.getTemporaryBasalActiveAt(timestamp).blockingGet()?.fromDb()

    override fun getOldestTemporaryBasalRecord(): TB? =
        repository.getOldestTemporaryBasalRecord().blockingGet()?.fromDb()

    override fun getLastTemporaryBasalId(): Long? = repository.getLastTemporaryBasalId()

    override fun getTemporaryBasalsActiveBetweenTimeAndTime(startTime: Long, endTime: Long): List<TB> =
        repository.getTemporaryBasalsActiveBetweenTimeAndTime(startTime, endTime).blockingGet().map { it.fromDb() }

    override fun getTemporaryBasalsStartingFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<TB> =
        repository.getTemporaryBasalsStartingFromTimeToTime(startTime, endTime, ascending).blockingGet().map { it.fromDb() }

    override fun getTemporaryBasalsStartingFromTime(startTime: Long, ascending: Boolean): Single<List<TB>> =
        repository.getTemporaryBasalsStartingFromTime(startTime, ascending)
            .map { list -> list.map { it.fromDb() } }

    override fun getTemporaryBasalsStartingFromTimeIncludingInvalid(startTime: Long, ascending: Boolean): Single<List<TB>> =
        repository.getTemporaryBasalsStartingFromTimeIncludingInvalid(startTime, ascending)
            .map { list -> list.map { it.fromDb() } }

    override fun getNextSyncElementTemporaryBasal(id: Long): Maybe<Pair<TB, TB>> =
        repository.getNextSyncElementTemporaryBasal(id)
            .map { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }

    override fun invalidateTemporaryBasal(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit?>): Single<PersistenceLayer.TransactionResult<TB>> =
        repository.runTransactionForResult(InvalidateTemporaryBasalTransaction(id))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating TemporaryBasal", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TB>()
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryBasal from ${source.name} $it")
                    transactionResult.invalidated.add(it.fromDb())
                    log(action = action, source = source, note = note, listValues = listValues)
                }
                transactionResult
            }

    override fun syncNsTemporaryBasals(temporaryBasals: List<TB>): Single<PersistenceLayer.TransactionResult<TB>> =
        repository.runTransactionForResult(SyncNsTemporaryBasalTransaction(temporaryBasals.map { it.toDb() }, config.NSCLIENT))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving TemporaryBasal", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TB>()
                result.inserted.forEach {
                    log(
                        timestamp = dateUtil.now(),
                        action = Action.TEMP_BASAL,
                        source = Sources.NSClient,
                        note = "",
                        listValues = listOf(
                            ValueWithUnit.Timestamp(it.timestamp),
                            if (it.isAbsolute) ValueWithUnit.UnitPerHour(it.rate) else ValueWithUnit.Percent(it.rate.toInt()),
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                        )
                    )
                    aapsLogger.debug(LTag.DATABASE, "Inserted TemporaryBasal $it")
                    transactionResult.inserted.add(it.fromDb())
                }
                result.invalidated.forEach {
                    log(
                        timestamp = dateUtil.now(),
                        action = Action.TEMP_BASAL_REMOVED,
                        source = Sources.NSClient,
                        note = "",
                        listValues = listOf(
                            ValueWithUnit.Timestamp(it.timestamp),
                            if (it.isAbsolute) ValueWithUnit.UnitPerHour(it.rate) else ValueWithUnit.Percent(it.rate.toInt()),
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                        )
                    )
                    aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryBasal $it")
                    transactionResult.invalidated.add(it.fromDb())
                }
                result.ended.forEach {
                    log(
                        timestamp = dateUtil.now(),
                        action = Action.CANCEL_TEMP_BASAL,
                        source = Sources.NSClient,
                        note = "",
                        listValues = listOf(
                            ValueWithUnit.Timestamp(it.timestamp),
                            if (it.isAbsolute) ValueWithUnit.UnitPerHour(it.rate) else ValueWithUnit.Percent(it.rate.toInt()),
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
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
                transactionResult
            }

    override fun updateTemporaryBasalsNsIds(temporaryBasals: List<TB>): Single<PersistenceLayer.TransactionResult<TB>> =
        repository.runTransactionForResult(UpdateNsIdTemporaryBasalTransaction(temporaryBasals.map { it.toDb() }))
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

    override fun getLastExtendedBolusId(): Long = getLastExtendedBolusId()

    override fun getExtendedBolusesStartingFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<EB> =
        repository.getExtendedBolusesStartingFromTimeToTime(startTime, endTime, ascending).blockingGet().map { it.fromDb() }

    override fun getExtendedBolusesStartingFromTime(startTime: Long, ascending: Boolean): Single<List<EB>> =
        repository.getExtendedBolusesStartingFromTime(startTime, ascending)
            .map { list -> list.map { it.fromDb() } }

    override fun getExtendedBolusStartingFromTimeIncludingInvalid(startTime: Long, ascending: Boolean): Single<List<EB>> =
        repository.getExtendedBolusStartingFromTimeIncludingInvalid(startTime, ascending)
            .map { list -> list.map { it.fromDb() } }

    override fun getNextSyncElementExtendedBolus(id: Long): Maybe<Pair<EB, EB>> =
        repository.getNextSyncElementExtendedBolus(id)
            .map { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }

    override fun invalidateExtendedBolus(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit?>): Single<PersistenceLayer.TransactionResult<EB>> =
        repository.runTransactionForResult(InvalidateExtendedBolusTransaction(id))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating ExtendedBolus", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<EB>()
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated ExtendedBolus from ${source.name} $it")
                    transactionResult.invalidated.add(it.fromDb())
                    log(action = action, source = source, note = note, listValues = listValues)
                }
                transactionResult
            }

    override fun syncNsExtendedBoluses(extendedBoluses: List<EB>): Single<PersistenceLayer.TransactionResult<EB>> =
        repository.runTransactionForResult(SyncNsExtendedBolusTransaction(extendedBoluses.map { it.toDb() }, config.NSCLIENT))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving ExtendedBolus", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<EB>()
                result.inserted.forEach {
                    log(
                        timestamp = dateUtil.now(),
                        action = Action.EXTENDED_BOLUS,
                        source = Sources.NSClient,
                        note = "",
                        listValues = listOf(
                            ValueWithUnit.Timestamp(it.timestamp),
                            ValueWithUnit.Insulin(it.amount),
                            ValueWithUnit.UnitPerHour(it.rate),
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                        )
                    )
                    aapsLogger.debug(LTag.DATABASE, "Inserted EB $it")
                    transactionResult.inserted.add(it.fromDb())
                }
                result.invalidated.forEach {
                    log(
                        timestamp = dateUtil.now(),
                        action = Action.EXTENDED_BOLUS_REMOVED,
                        source = Sources.NSClient,
                        note = "",
                        listValues = listOf(
                            ValueWithUnit.Timestamp(it.timestamp),
                            ValueWithUnit.Insulin(it.amount),
                            ValueWithUnit.UnitPerHour(it.rate),
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                        )
                    )
                    aapsLogger.debug(LTag.DATABASE, "Invalidated EB $it")
                    transactionResult.invalidated.add(it.fromDb())
                }
                result.ended.forEach {
                    log(
                        timestamp = dateUtil.now(),
                        action = Action.CANCEL_EXTENDED_BOLUS,
                        source = Sources.NSClient,
                        note = "",
                        listValues = listOf(
                            ValueWithUnit.Timestamp(it.timestamp),
                            ValueWithUnit.Insulin(it.amount),
                            ValueWithUnit.UnitPerHour(it.rate),
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                        )
                    )
                    aapsLogger.debug(LTag.DATABASE, "Updated EB $it")
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
                transactionResult
            }

    // TT
    override fun getTemporaryTargetActiveAt(timestamp: Long): TT? =
        repository.getTemporaryTargetActiveAt(timestamp).blockingGet()?.fromDb()

    override fun getLastTemporaryTargetId(): Long? = repository.getLastTempTargetId()

    override fun getTemporaryTargetDataFromTime(timestamp: Long, ascending: Boolean): Single<List<TT>> =
        repository.getTemporaryTargetDataFromTime(timestamp, ascending).map { list -> list.map { it.fromDb() } }

    override fun getTemporaryTargetDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<TT>> =
        repository.getTemporaryTargetDataIncludingInvalidFromTime(timestamp, ascending).map { list -> list.map { it.fromDb() } }

    override fun getNextSyncElementTemporaryTarget(id: Long): Maybe<Pair<TT, TT>> =
        repository.getNextSyncElementTemporaryTarget(id)
            .map { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }

    override fun invalidateTemporaryTarget(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit?>)
        : Single<PersistenceLayer.TransactionResult<TT>> =
        repository.runTransactionForResult(InvalidateTemporaryTargetTransaction(id))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating TemporaryTarget", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TT>()
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryTarget from ${source.name} $it")
                    transactionResult.invalidated.add(it.fromDb())
                    log(action = action, source = source, note = note, listValues = listValues)
                }
                transactionResult
            }

    override fun insertAndCancelCurrentTemporaryTarget(temporaryTarget: TT, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit?>)
        : Single<PersistenceLayer.TransactionResult<TT>> =
        repository.runTransactionForResult(InsertAndCancelCurrentTemporaryTargetTransaction(temporaryTarget.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while inserting TemporaryTarget", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TT>()
                result.inserted.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Inserted TemporaryTarget from ${source.name} $it")
                    transactionResult.inserted.add(it.fromDb())
                    log(action = action, source = source, note = note, listValues = listValues)
                }
                result.updated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated TemporaryTarget from ${source.name} $it")
                    transactionResult.updated.add(it.fromDb())
                }
                transactionResult
            }

    override fun cancelCurrentTemporaryTargetIfAny(timestamp: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit?>)
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

    override fun syncNsTemporaryTargets(temporaryTargets: List<TT>): Single<PersistenceLayer.TransactionResult<TT>> =
        repository.runTransactionForResult(SyncNsTemporaryTargetTransaction(temporaryTargets.map { it.toDb() }))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving TemporaryTarget", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TT>()
                result.inserted.forEach { tt ->
                    log(
                        timestamp = dateUtil.now(),
                        action = Action.TT,
                        source = Sources.NSClient,
                        note = "",
                        listValues = listOf(
                            ValueWithUnit.TETTReason(tt.reason.fromDb()),
                            ValueWithUnit.fromGlucoseUnit(tt.lowTarget, GlucoseUnit.MGDL),
                            ValueWithUnit.fromGlucoseUnit(tt.highTarget, GlucoseUnit.MGDL).takeIf { tt.lowTarget != tt.highTarget },
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tt.duration).toInt())
                        )
                    )
                    aapsLogger.debug(LTag.DATABASE, "Inserted TemporaryTarget from ${Sources.NSClient.name} $tt")
                    transactionResult.inserted.add(tt.fromDb())
                }
                result.invalidated.forEach { tt ->
                    log(
                        timestamp = dateUtil.now(),
                        action = Action.TT_REMOVED,
                        source = Sources.NSClient,
                        note = "",
                        listValues = listOf(
                            ValueWithUnit.TETTReason(tt.reason.fromDb()),
                            ValueWithUnit.Mgdl(tt.lowTarget),
                            ValueWithUnit.Mgdl(tt.highTarget).takeIf { tt.lowTarget != tt.highTarget },
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tt.duration).toInt())
                        )
                    )
                    aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryTarget from ${Sources.NSClient.name} $tt")
                    transactionResult.invalidated.add(tt.fromDb())
                }
                result.ended.forEach { tt ->
                    log(
                        timestamp = dateUtil.now(),
                        action = Action.CANCEL_TT,
                        source = Sources.NSClient,
                        note = "",
                        listValues = listOf(
                            ValueWithUnit.TETTReason(tt.reason.fromDb()),
                            ValueWithUnit.Mgdl(tt.lowTarget),
                            ValueWithUnit.Mgdl(tt.highTarget).takeIf { tt.lowTarget != tt.highTarget },
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tt.duration).toInt())
                        )
                    )
                    aapsLogger.debug(LTag.DATABASE, "Updated TemporaryTarget from ${Sources.NSClient.name} $tt")
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
                transactionResult
            }

    override fun updateTemporaryTargetsNsIds(temporaryTargets: List<TT>): Single<PersistenceLayer.TransactionResult<TT>> =
        repository.runTransactionForResult(UpdateNsIdTemporaryTargetTransaction(temporaryTargets.map { it.toDb() }))
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

    // TE
    override fun getLastTherapyRecordUpToNow(type: TE.Type): Single<ValueWrapper<TE>> =
        repository.getLastTherapyRecordUpToNow(type.toDb()).fromDb(TherapyEvent::fromDb)

    override fun getTherapyEventDataFromToTime(from: Long, to: Long): Single<List<TE>> =
        repository.compatGetTherapyEventDataFromToTime(from, to).map { list -> list.map { it.fromDb() } }

    override fun getTherapyEventDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<TE>> =
        repository.getTherapyEventDataIncludingInvalidFromTime(timestamp, ascending).map { list -> list.map { it.fromDb() } }

    override fun getTherapyEventDataFromTime(timestamp: Long, ascending: Boolean): Single<List<TE>> =
        repository.getTherapyEventDataFromTime(timestamp, ascending).map { list -> list.map { it.fromDb() } }

    override fun getTherapyEventDataFromTime(timestamp: Long, type: TE.Type, ascending: Boolean): Single<List<TE>> =
        repository.getTherapyEventDataFromTime(timestamp, type.toDb(), ascending).map { list -> list.map { it.fromDb() } }

    override fun getNextSyncElementTherapyEvent(id: Long): Maybe<Pair<TE, TE>> =
        repository.getNextSyncElementTherapyEvent(id)
            .map { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }

    override fun insertPumpTherapyEventIfNewByTimestamp(therapyEvent: TE, timestamp: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit?>)
        : Single<PersistenceLayer.TransactionResult<TE>> =
        repository.runTransactionForResult(InsertIfNewByTimestampTherapyEventTransaction(therapyEvent.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving TherapyEvent $therapyEvent", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TE>()
                result.inserted.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Inserted TherapyEvent from ${source.name} $it")
                    transactionResult.inserted.add(it.fromDb())
                    log(timestamp = timestamp, action = action, source = source, note = note, listValues = listValues)
                }
                transactionResult
            }

    override fun invalidateTherapyEvent(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit?>)
        : Single<PersistenceLayer.TransactionResult<TE>> =
        repository.runTransactionForResult(InvalidateTherapyEventTransaction(id))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating TherapyEvent", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TE>()
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated TherapyEvent from ${source.name} $it")
                    transactionResult.invalidated.add(it.fromDb())
                    log(action = action, source = source, note = note, listValues = listValues)
                }
                transactionResult
            }

    override fun invalidateTherapyEventsWithNote(note: String, action: Action, source: Sources): Single<PersistenceLayer.TransactionResult<TE>> =
        repository.runTransactionForResult(InvalidateTherapyEventsWithNoteTransaction(note))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating TherapyEvent", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TE>()
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated TherapyEvent from ${source.name} $it")
                    transactionResult.invalidated.add(it.fromDb())
                    log(action = action, source = source, note = note)
                }
                transactionResult
            }

    override fun syncNsTherapyEvents(therapyEvents: List<TE>): Single<PersistenceLayer.TransactionResult<TE>> =
        repository.runTransactionForResult(SyncNsTherapyEventTransaction(therapyEvents.map { it.toDb() }, config.NSCLIENT))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving TherapyEvent", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TE>()
                result.inserted.forEach { therapyEvent ->
                    val action = when (therapyEvent.type) {
                        TherapyEvent.Type.CANNULA_CHANGE -> Action.SITE_CHANGE
                        TherapyEvent.Type.INSULIN_CHANGE -> Action.RESERVOIR_CHANGE
                        else                             -> Action.CAREPORTAL
                    }
                    log(
                        timestamp = dateUtil.now(),
                        action = action,
                        source = Sources.NSClient,
                        note = therapyEvent.note ?: "",
                        listValues = listOf(
                            ValueWithUnit.Timestamp(therapyEvent.timestamp),
                            ValueWithUnit.TEType(therapyEvent.type.fromDb()),
                            ValueWithUnit.fromGlucoseUnit(therapyEvent.glucose ?: 0.0, therapyEvent.glucoseUnit.fromDb()).takeIf { therapyEvent.glucose != null })
                    )
                    aapsLogger.debug(LTag.DATABASE, "Inserted TherapyEvent from ${Sources.NSClient.name} $therapyEvent")
                    transactionResult.inserted.add(therapyEvent.fromDb())
                }
                result.invalidated.forEach { therapyEvent ->
                    log(
                        timestamp = dateUtil.now(),
                        action = Action.CAREPORTAL_REMOVED,
                        source = Sources.NSClient,
                        note = therapyEvent.note ?: "",
                        listValues = listOf(
                            ValueWithUnit.Timestamp(therapyEvent.timestamp),
                            ValueWithUnit.TEType(therapyEvent.type.fromDb()),
                            ValueWithUnit.fromGlucoseUnit(therapyEvent.glucose ?: 0.0, therapyEvent.glucoseUnit.fromDb()).takeIf { therapyEvent.glucose != null })
                    )
                    aapsLogger.debug(LTag.DATABASE, "Invalidated TherapyEvent from ${Sources.NSClient.name} $therapyEvent")
                    transactionResult.invalidated.add(therapyEvent.fromDb())
                }
                result.updatedNsId.forEach { therapyEvent ->
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId TherapyEvent from ${Sources.NSClient.name} $therapyEvent")
                    transactionResult.updatedNsId.add(therapyEvent.fromDb())
                }
                result.updatedDuration.forEach { therapyEvent ->
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId TherapyEvent from ${Sources.NSClient.name} $therapyEvent")
                    transactionResult.updatedDuration.add(therapyEvent.fromDb())
                }
                transactionResult
            }

    override fun updateTherapyEventsNsIds(therapyEvents: List<TE>): Single<PersistenceLayer.TransactionResult<TE>> =
        repository.runTransactionForResult(UpdateNsIdTherapyEventTransaction(therapyEvents.map { it.toDb() }))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Updated nsId of TherapyEvent failed", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TE>()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of TherapyEvent $it")
                    transactionResult.updatedNsId.add(it.fromDb())
                }
                transactionResult
            }

    // OE
    override fun getOfflineEventActiveAt(timestamp: Long): OE? =
        repository.getOfflineEventActiveAt(timestamp).blockingGet()?.fromDb()

    override fun getLastOfflineEventId(): Long = getLastOfflineEventId()

    override fun getNextSyncElementOfflineEvent(id: Long): Maybe<Pair<OE, OE>> =
        repository.getNextSyncElementOfflineEvent(id)
            .map { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }

    override fun insertAndCancelCurrentOfflineEvent(offlineEvent: OE, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit?>)
        : Single<PersistenceLayer.TransactionResult<OE>> =
        repository.runTransactionForResult(InsertAndCancelCurrentOfflineEventTransaction(offlineEvent.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while inserting OfflineEvent", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<OE>()
                result.inserted.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Inserted OfflineEvent from ${source.name} $it")
                    transactionResult.inserted.add(it.fromDb())
                    log(action = action, source = source, note = note, listValues = listValues)
                }
                result.updated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated OfflineEvent from ${source.name} $it")
                    transactionResult.updated.add(it.fromDb())
                }
                transactionResult
            }

    override fun syncNsOfflineEvents(offlineEvents: List<OE>): Single<PersistenceLayer.TransactionResult<OE>> =
        repository.runTransactionForResult(SyncNsOfflineEventTransaction(offlineEvents.map { it.toDb() }, config.NSCLIENT))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving OfflineEvent", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<OE>()
                result.inserted.forEach { oe ->
                    log(
                        timestamp = dateUtil.now(),
                        action = Action.LOOP_CHANGE,
                        source = Sources.NSClient,
                        note = "",
                        listValues = listOf(
                            ValueWithUnit.OEReason(oe.reason.fromDb()),
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(oe.duration).toInt())
                        )
                    )
                    aapsLogger.debug(LTag.DATABASE, "Inserted OfflineEvent from ${Sources.NSClient.name} $oe")
                    transactionResult.inserted.add(oe.fromDb())
                }
                result.invalidated.forEach { oe ->
                    log(
                        timestamp = dateUtil.now(),
                        action = Action.LOOP_REMOVED,
                        source = Sources.NSClient,
                        note = "",
                        listValues = listOf(
                            ValueWithUnit.OEReason(oe.reason.fromDb()),
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(oe.duration).toInt())
                        )
                    )
                    aapsLogger.debug(LTag.DATABASE, "Invalidated OfflineEvent from ${Sources.NSClient.name} $oe")
                    transactionResult.invalidated.add(oe.fromDb())
                }
                result.ended.forEach { oe ->
                    log(
                        timestamp = dateUtil.now(),
                        action = Action.LOOP_CHANGE,
                        source = Sources.NSClient,
                        note = "",
                        listValues = listOf(
                            ValueWithUnit.OEReason(oe.reason.fromDb()),
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(oe.duration).toInt())
                        )
                    )
                    aapsLogger.debug(LTag.DATABASE, "Updated OfflineEvent from ${Sources.NSClient.name} $oe")
                    transactionResult.ended.add(oe.fromDb())
                }
                result.updatedNsId.forEach { oe ->
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId OfflineEvent from ${Sources.NSClient.name} $oe")
                    transactionResult.updatedNsId.add(oe.fromDb())
                }
                result.updatedDuration.forEach { oe ->
                    aapsLogger.debug(LTag.DATABASE, "Updated duration OfflineEvent from ${Sources.NSClient.name} $oe")
                    transactionResult.updatedDuration.add(oe.fromDb())
                }
                transactionResult
            }

    override fun updateOfflineEventsNsIds(offlineEvents: List<OE>): Single<PersistenceLayer.TransactionResult<OE>> =
        repository.runTransactionForResult(UpdateNsIdOfflineEventTransaction(offlineEvents.map { it.toDb() }))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Updated nsId of OfflineEvent failed", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<OE>()
                result.updatedNsId.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of OfflineEvent $it")
                    transactionResult.updatedNsId.add(it.fromDb())
                }
                transactionResult
            }

    // UE
    override fun insertUserEntries(entries: List<UE>): Single<PersistenceLayer.TransactionResult<UE>> =
        repository.runTransactionForResult(UserEntryTransaction(entries.map { it.toDb() }))
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
        repository.getUserEntryDataFromTime(timestamp).map { list -> list.map { it.fromDb() } }

    override fun getUserEntryFilteredDataFromTime(timestamp: Long): Single<List<UE>> =
        repository.getUserEntryFilteredDataFromTime(timestamp).map { list -> list.map { it.fromDb() } }

}
