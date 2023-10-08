package app.aaps.implementation.db

import app.aaps.core.data.db.GV
import app.aaps.core.data.db.GlucoseUnit
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
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.main.extensions.fromDb
import app.aaps.core.main.extensions.toDb
import app.aaps.database.ValueWrapper
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.impl.AppRepository
import app.aaps.database.impl.transactions.CancelCurrentTemporaryTargetIfAnyTransaction
import app.aaps.database.impl.transactions.CgmSourceTransaction
import app.aaps.database.impl.transactions.InsertAndCancelCurrentTemporaryTargetTransaction
import app.aaps.database.impl.transactions.InsertIfNewByTimestampTherapyEventTransaction
import app.aaps.database.impl.transactions.InsertOrUpdateBolusCalculatorResultTransaction
import app.aaps.database.impl.transactions.InsertOrUpdateBolusTransaction
import app.aaps.database.impl.transactions.InsertOrUpdateCarbsTransaction
import app.aaps.database.impl.transactions.InvalidateGlucoseValueTransaction
import app.aaps.database.impl.transactions.InvalidateTemporaryTargetTransaction
import app.aaps.database.impl.transactions.InvalidateTherapyEventTransaction
import app.aaps.database.impl.transactions.InvalidateTherapyEventsWithNoteTransaction
import app.aaps.database.impl.transactions.SyncNsTemporaryTargetTransaction
import app.aaps.database.impl.transactions.UpdateNsIdGlucoseValueTransaction
import app.aaps.database.impl.transactions.UpdateNsIdTemporaryTargetTransaction
import app.aaps.database.impl.transactions.UserEntryTransaction
import dagger.Reusable
import dagger.android.HasAndroidInjector
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

    // BO
    override fun insertOrUpdateBolus(bolus: Bolus) {
        disposable += repository.runTransactionForResult(InsertOrUpdateBolusTransaction(bolus))
            .subscribe(
                { result -> result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted bolus $it") } },
                { aapsLogger.error(LTag.DATABASE, "Error while saving bolus", it) }
            )
    }

    // CB
    override fun insertOrUpdateCarbs(carbs: Carbs, callback: Callback?, injector: HasAndroidInjector?) {
        disposable += repository.runTransactionForResult(InsertOrUpdateCarbsTransaction(carbs))
            .subscribe(
                { result ->
                    result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted carbs $it") }
                    injector?.let { injector ->
                        callback?.result(PumpEnactResult(injector).enacted(false).success(true))?.run()
                    }

                }, {
                    aapsLogger.error(LTag.DATABASE, "Error while saving carbs", it)
                    injector?.let { injector ->
                        callback?.result(PumpEnactResult(injector).enacted(false).success(false))?.run()
                    }
                }
            )
    }

    // GV
    override fun getLastGlucoseValue(): Single<ValueWrapper<GV>> = repository.getLastGlucoseValueWrapped().fromDb(GlucoseValue::fromDb)
    override fun getNextSyncElementGlucoseValue(id: Long): Maybe<Pair<GV, GV>> =
        repository.getNextSyncElementGlucoseValue(id)
            .map { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }

    override fun getBgReadingsDataFromTimeToTime(start: Long, end: Long, ascending: Boolean): Single<List<GV>> =
        repository.compatGetBgReadingsDataFromTime(start, end, ascending)
            .map { list -> list.map { glucoseValue -> glucoseValue.fromDb() } }

    override fun getBgReadingsDataFromTime(timestamp: Long, ascending: Boolean): Single<List<GV>> =
        repository.compatGetBgReadingsDataFromTime(timestamp, ascending)
            .map { list -> list.map { glucoseValue -> glucoseValue.fromDb() } }

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

    // EPS
    override fun getEffectiveProfileSwitchActiveAt(timestamp: Long): Single<ValueWrapper<EffectiveProfileSwitch>> = repository.getEffectiveProfileSwitchActiveAt(timestamp)

    // TT
    override fun getTemporaryTargetActiveAt(timestamp: Long): TT? =
        repository.getTemporaryTargetActiveAt(timestamp).blockingGet()?.fromDb()

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
                    aapsLogger.debug(LTag.DATABASE, "Inserted TemporaryTarget from ${action.name} $it")
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
                    aapsLogger.debug(LTag.DATABASE, "Updated TemporaryTarget from ${action.name} $it")
                    transactionResult.updated.add(it.fromDb())
                }
                transactionResult
            }

    override fun syncNsTemporaryTargets(temporaryTargets: List<TT>, action: Action, source: Sources, note: String?): Single<PersistenceLayer.TransactionResult<TT>> =
        repository.runTransactionForResult(SyncNsTemporaryTargetTransaction(temporaryTargets.map { it.toDb() }))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving TemporaryTarget", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TT>()
                result.inserted.forEach { tt ->
                    if (config.NSCLIENT.not())
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
                    aapsLogger.debug(LTag.DATABASE, "Inserted TemporaryTarget from ${action.name} $tt")
                    transactionResult.inserted.add(tt.fromDb())
                }
                result.invalidated.forEach { tt ->
                    if (config.NSCLIENT.not())
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
                    aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryTarget from ${action.name} $tt")
                    transactionResult.invalidated.add(tt.fromDb())
                }
                result.ended.forEach { tt ->
                    if (config.NSCLIENT.not())
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
                    aapsLogger.debug(LTag.DATABASE, "Updated TemporaryTarget from ${action.name} $tt")
                    transactionResult.ended.add(tt.fromDb())
                }
                result.updatedNsId.forEach { tt ->
                    aapsLogger.debug(LTag.DATABASE, "Updated nsId TemporaryTarget from ${action.name} $tt")
                    transactionResult.updatedNsId.add(tt.fromDb())
                }
                result.updatedDuration.forEach { tt ->
                    aapsLogger.debug(LTag.DATABASE, "Updated duration TemporaryTarget from ${action.name} $tt")
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

    // TE
    override fun getLastTherapyRecordUpToNow(type: TE.Type): Single<ValueWrapper<TE>> =
        repository.getLastTherapyRecordUpToNow(type.toDb()).fromDb(TherapyEvent::fromDb)

    override fun getTherapyEventDataFromToTime(from: Long, to: Long): Single<List<TE>> =
        repository.compatGetTherapyEventDataFromToTime(from, to).map { list -> list.map { it.fromDb() } }

    override fun getTherapyEventDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<TE>> =
        repository.getTherapyEventDataIncludingInvalidFromTime(timestamp, ascending).map { list -> list.map { it.fromDb() } }

    override fun getTherapyEventDataFromTime(timestamp: Long, ascending: Boolean): Single<List<TE>> =
        repository.getTherapyEventDataFromTime(timestamp, ascending).map { list -> list.map { it.fromDb() } }

    override fun getNextSyncElementTherapyEvent(id: Long): Maybe<Pair<TE, TE>> =
        repository.getNextSyncElementTherapyEvent(id)
            .map { pair -> Pair(pair.first.fromDb(), pair.second.fromDb()) }

    override fun insertIfNewByTimestampTherapyEvent(therapyEvent: TE, action: Action, source: Sources, note: String, listValues: List<ValueWithUnit?>)
        : Single<PersistenceLayer.TransactionResult<TE>> =
        repository.runTransactionForResult(InsertIfNewByTimestampTherapyEventTransaction(therapyEvent.toDb()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving TherapyEvent $therapyEvent", it) }
            .map { result ->
                val transactionResult = PersistenceLayer.TransactionResult<TE>()
                result.inserted.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Inserted TherapyEvent from ${source.name} $it")
                    transactionResult.inserted.add(it.fromDb())
                    log(action = action, source = source, note = note, listValues = listValues)
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
