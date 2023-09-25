package app.aaps.core.interfaces.db

import app.aaps.core.interfaces.queue.Callback
import app.aaps.database.ValueWrapper
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.UserEntry
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.core.Single

interface PersistenceLayer {

    fun clearDatabases()
    fun cleanupDatabase(keepDays: Long, deleteTrackedChanges: Boolean): String
    fun insertOrUpdate(bolusCalculatorResult: BolusCalculatorResult)
    fun insertOrUpdateCarbs(carbs: Carbs, callback: Callback? = null, injector: HasAndroidInjector? = null)
    fun insertOrUpdateBolus(bolus: Bolus)

    fun getTemporaryTargetActiveAt(timestamp: Long): Single<ValueWrapper<TemporaryTarget>>
    fun getUserEntryFilteredDataFromTime(timestamp: Long): Single<List<UserEntry>>
    fun getEffectiveProfileSwitchActiveAt(timestamp: Long): Single<ValueWrapper<EffectiveProfileSwitch>>
}