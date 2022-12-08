package info.nightscout.interfaces.db

import dagger.android.HasAndroidInjector
import info.nightscout.database.ValueWrapper
import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.BolusCalculatorResult
import info.nightscout.database.entities.Carbs
import info.nightscout.database.entities.EffectiveProfileSwitch
import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.database.entities.UserEntry
import info.nightscout.interfaces.queue.Callback
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