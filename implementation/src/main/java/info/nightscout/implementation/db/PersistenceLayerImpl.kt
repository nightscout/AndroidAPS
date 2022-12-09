package info.nightscout.implementation.db

import dagger.Reusable
import dagger.android.HasAndroidInjector
import info.nightscout.database.ValueWrapper
import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.BolusCalculatorResult
import info.nightscout.database.entities.Carbs
import info.nightscout.database.entities.EffectiveProfileSwitch
import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.InsertOrUpdateBolusCalculatorResultTransaction
import info.nightscout.database.impl.transactions.InsertOrUpdateBolusTransaction
import info.nightscout.database.impl.transactions.InsertOrUpdateCarbsTransaction
import info.nightscout.interfaces.db.PersistenceLayer
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

@Reusable
class PersistenceLayerImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val repository: AppRepository
) : PersistenceLayer {

    private val disposable = CompositeDisposable()
    override fun clearDatabases() = repository.clearDatabases()
    override fun cleanupDatabase(keepDays: Long, deleteTrackedChanges: Boolean): String = cleanupDatabase(keepDays, deleteTrackedChanges)

    override fun insertOrUpdate(bolusCalculatorResult: BolusCalculatorResult) {
        disposable += repository.runTransactionForResult(InsertOrUpdateBolusCalculatorResultTransaction(bolusCalculatorResult))
            .subscribe(
                { result -> result.inserted.forEach { inserted -> aapsLogger.debug(LTag.DATABASE, "Inserted bolusCalculatorResult $inserted") } },
                { aapsLogger.error(LTag.DATABASE, "Error while saving bolusCalculatorResult", it) }
            )
    }

    override fun insertOrUpdateBolus(bolus: Bolus) {
        disposable += repository.runTransactionForResult(InsertOrUpdateBolusTransaction(bolus))
            .subscribe(
                { result -> result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted bolus $it") } },
                { aapsLogger.error(LTag.DATABASE, "Error while saving bolus", it) }
            )
    }

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

    override fun getTemporaryTargetActiveAt(timestamp: Long): Single<ValueWrapper<TemporaryTarget>> = repository.getTemporaryTargetActiveAt(timestamp)
    override fun getUserEntryFilteredDataFromTime(timestamp: Long): Single<List<UserEntry>> = repository.getUserEntryFilteredDataFromTime(timestamp)
    override fun getEffectiveProfileSwitchActiveAt(timestamp: Long): Single<ValueWrapper<EffectiveProfileSwitch>> = repository.getEffectiveProfileSwitchActiveAt(timestamp)
}
