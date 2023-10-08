package app.aaps.core.interfaces.db

import app.aaps.core.data.db.GV
import app.aaps.core.data.db.GlucoseUnit
import app.aaps.core.data.db.TE
import app.aaps.core.data.db.TT
import app.aaps.core.data.db.UE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.queue.Callback
import app.aaps.database.ValueWrapper
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.EffectiveProfileSwitch
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

interface PersistenceLayer {

    fun clearDatabases()
    fun cleanupDatabase(keepDays: Long, deleteTrackedChanges: Boolean): String
    fun insertOrUpdate(bolusCalculatorResult: BolusCalculatorResult)

    // BO
    fun insertOrUpdateBolus(bolus: Bolus)

    // CB
    fun insertOrUpdateCarbs(carbs: Carbs, callback: Callback? = null, injector: HasAndroidInjector? = null)

    // GV
    fun getLastGlucoseValue(): Single<ValueWrapper<GV>>
    fun getNextSyncElementGlucoseValue(id: Long): Maybe<Pair<GV, GV>>
    fun getBgReadingsDataFromTimeToTime(start: Long, end: Long, ascending: Boolean): Single<List<GV>>
    fun getBgReadingsDataFromTime(timestamp: Long, ascending: Boolean): Single<List<GV>>
    fun getBgReadingByNSId(nsId: String): GV?

    fun invalidateGlucoseValue(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit?>): Single<TransactionResult<GV>>
    fun insertCgmSourceData(caller: Sources, glucoseValues: List<GV>, calibrations: List<Calibration>, sensorInsertionTime: Long?): Single<TransactionResult<GV>>
    fun updateGlucoseValuesNsIds(glucoseValues: List<GV>): Single<TransactionResult<GV>>

    // EPS
    fun getEffectiveProfileSwitchActiveAt(timestamp: Long): Single<ValueWrapper<EffectiveProfileSwitch>>

    // TT
    fun getTemporaryTargetActiveAt(timestamp: Long): TT?
    fun getTemporaryTargetDataFromTime(timestamp: Long, ascending: Boolean): Single<List<TT>>
    fun getTemporaryTargetDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<TT>>
    fun invalidateTemporaryTarget(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit?>): Single<TransactionResult<TT>>
    fun insertAndCancelCurrentTemporaryTarget(temporaryTarget: TT, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit?>): Single<TransactionResult<TT>>

    // TE
    fun getLastTherapyRecordUpToNow(type: TE.Type): Single<ValueWrapper<TE>>
    fun getTherapyEventDataFromToTime(from: Long, to: Long): Single<List<TE>>
    fun getTherapyEventDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<TE>>
    fun getTherapyEventDataFromTime(timestamp: Long, ascending: Boolean): Single<List<TE>>
    fun getNextSyncElementTherapyEvent(id: Long): Maybe<Pair<TE, TE>>
    fun insertIfNewByTimestampTherapyEvent(therapyEvent: TE, action: Action, source: Sources, note: String, listValues: List<ValueWithUnit?>): Single<TransactionResult<TE>>
    fun invalidateTherapyEvent(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit?>): Single<TransactionResult<TE>>
    fun invalidateTherapyEventsWithNote(note: String, action: Action, source: Sources): Single<TransactionResult<TE>>

    // UE
    fun insertUserEntries(entries: List<UE>): Single<TransactionResult<UE>>
    fun getUserEntryDataFromTime(timestamp: Long): Single<List<UE>>
    fun getUserEntryFilteredDataFromTime(timestamp: Long): Single<List<UE>>

    class TransactionResult<T> {

        val inserted = mutableListOf<T>()
        val updated = mutableListOf<T>()
        val invalidated = mutableListOf<T>()
        val updatedNsId = mutableListOf<T>()

        val calibrationsInserted = mutableListOf<TE>()
        val sensorInsertionsInserted = mutableListOf<TE>()

        fun all(): MutableList<T> =
            mutableListOf<T>().also { result ->
                result.addAll(inserted)
                result.addAll(updated)
            }
    }

    data class Calibration(
        val timestamp: Long,
        val value: Double,
        val glucoseUnit: GlucoseUnit
    )
}