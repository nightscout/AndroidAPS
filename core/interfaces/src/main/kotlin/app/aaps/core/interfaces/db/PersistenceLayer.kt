package app.aaps.core.interfaces.db

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

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    fun getNextSyncElementGlucoseValue(id: Long): Maybe<Pair<GV, GV>>
    fun getBgReadingsDataFromTimeToTime(start: Long, end: Long, ascending: Boolean): Single<List<GV>>
    fun getBgReadingsDataFromTime(timestamp: Long, ascending: Boolean): Single<List<GV>>
    fun getBgReadingByNSId(nsId: String): GV?

    fun invalidateGlucoseValue(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit?>): Single<TransactionResult<GV>>
    fun insertCgmSourceData(caller: Sources, glucoseValues: List<GV>, calibrations: List<Calibration>, sensorInsertionTime: Long?): Single<TransactionResult<GV>>

    /**
     * Update NS id' in database
     *
     * @param glucoseValues records containing NS id'
     * @return List of modified records
     */
    fun updateGlucoseValuesNsIds(glucoseValues: List<GV>): Single<TransactionResult<GV>>

    // EPS
    /**
     * Get running effective profile switch at time
     *
     * @param timestamp time
     * @return running effective profile switch or null if none is running
     */
    fun getEffectiveProfileSwitchActiveAt(timestamp: Long): Single<ValueWrapper<EffectiveProfileSwitch>>

    // TB
    /**
     * Get running temporary basal at time
     *
     * @param timestamp time
     * @return running temporary basal or null if none is running
     */
    fun getTemporaryBasalActiveAt(timestamp: Long): TB?

    /**
     * Get latest temporary basal
     *
     * @return temporary basal or null if none in db
     */
    fun getOldestTemporaryBasalRecord(): TB?

    /**
     * Get running temporary basal in time interval
     *
     * @param startTime from
     * @param endTime to
     * @return List of temporary basals
     */
    fun getTemporaryBasalsActiveBetweenTimeAndTime(startTime: Long, endTime: Long): List<TB>

    /**
     * Get running temporary basal starting in time interval
     *
     * @param startTime from
     * @param endTime to
     * @param ascending sort order
     * @return List of temporary basals
     */
    fun getTemporaryBasalsStartingFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<TB>

    /**
     * Get running temporary basal starting from time including
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of temporary basals as Single
     */
    fun getTemporaryBasalsStartingFromTime(startTime: Long, ascending: Boolean): Single<List<TB>>

    /**
     * Get running temporary basal starting from time including invalided records
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of temporary basals as Single
     */
    fun getTemporaryBasalsStartingFromTimeIncludingInvalid(startTime: Long, ascending: Boolean): Single<List<TB>>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    fun getNextSyncElementTemporaryBasal(id: Long): Maybe<Pair<TB, TB>>

    /**
     * Invalidate record with id
     *
     * @param id record id
     * @param action Action for UserEntry logging
     * @param source Source for UserEntry logging
     * @param note Note for UserEntry logging
     * @param listValues Values for UserEntry logging
     * @return List of changed records
     */
    fun invalidateTemporaryBasal(id: Long, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit?>): Single<TransactionResult<TB>>

    /**
     * Store records coming from NS to database
     *
     * @param offlineEvents list of records
     * @return List of inserted/updated/invalidated records
     */
    fun syncNsTemporaryBasals(temporaryBasals: List<TB>): Single<TransactionResult<TB>>

    /**
     * Update NS id' in database
     *
     * @param temporaryBasals records containing NS id'
     * @return List of modified records
     */
    fun updateTemporaryBasalsNsIds(temporaryBasals: List<TB>): Single<TransactionResult<TB>>

    /**
     * Sync record coming from pump to database
     *
     * @param temporaryBasal record to sync
     * @param type record type because filed is not nullable in class
     * @return List of inserted/updated records
     */
    fun syncPumpTemporaryBasal(temporaryBasal: TB, type: TB.Type?): Single<TransactionResult<TB>>

    /**
     * Sync record coming from pump to database using pump temp id
     *
     * @param temporaryBasal record to sync
     * @param type record type because filed is not nullable in class
     * @return List of updated records
     */
    fun syncPumpTemporaryBasalWithTempId(temporaryBasal: TB, type: TB.Type?): Single<TransactionResult<TB>>

    /**
     * Store record to database using temporary pump id
     *
     * @param temporaryBasal record to sync
     * @return List of inserted records
     */
    fun insertTemporaryBasalWithTempId(temporaryBasal: TB): Single<TransactionResult<TB>>

    // EB
    /**
     * Get running extended bolus at time
     *
     * @param timestamp time
     * @return running extended bolus or null if none is running
     */
    fun getExtendedBolusActiveAt(timestamp: Long): EB?

    /**
     * Get latest extended bolus
     *
     * @return extended bolus or null if none in db
     */
    fun getOldestExtendedBolusRecord(): EB?

    /**
     * Get running extended bolus starting in time interval
     *
     * @param startTime from
     * @param endTime to
     * @param ascending sort order
     * @return List of extended boluses
     */
    fun getExtendedBolusesStartingFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<EB>

    /**
     * Get running extended boluses starting from time
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of extended boluses as Single
     */
    fun getExtendedBolusesStartingFromTime(startTime: Long, ascending: Boolean): Single<List<EB>>

    /**
     * Get running extended boluses starting from time including invalided records
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of extended boluses as Single
     */
    fun getExtendedBolusStartingFromTimeIncludingInvalid(startTime: Long, ascending: Boolean): Single<List<EB>>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    fun getNextSyncElementExtendedBolus(id: Long): Maybe<Pair<EB, EB>>

    /**
     * Invalidate record with id
     *
     * @param id record id
     * @param action Action for UserEntry logging
     * @param source Source for UserEntry logging
     * @param note Note for UserEntry logging
     * @param listValues Values for UserEntry logging
     * @return List of changed records
     */
    fun invalidateExtendedBolus(id: Long, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit?>): Single<TransactionResult<EB>>

    /**
     * Store records coming from NS to database
     *
     * @param extendedBoluses list of records
     * @return List of inserted/updated/invalidated records
     */
    fun syncNsExtendedBoluses(extendedBoluses: List<EB>): Single<TransactionResult<EB>>

    /**
     * Update NS id' in database
     *
     * @param extendedBoluses records containing NS id'
     * @return List of modified records
     */
    fun updateExtendedBolusesNsIds(extendedBoluses: List<EB>): Single<TransactionResult<EB>>

    /**
     * Sync record coming from pump to database
     *
     * @param extendedBolus record to sync
     * @return List of inserted/updated records
     */
    fun syncPumpExtendedBolus(extendedBolus: EB): Single<TransactionResult<EB>>

    // TT
    /**
     * Get running temporary target at time
     *
     * @param timestamp time
     * @return running temporary target or null if none is running
     */
    fun getTemporaryTargetActiveAt(timestamp: Long): TT?
    fun getTemporaryTargetDataFromTime(timestamp: Long, ascending: Boolean): Single<List<TT>>
    fun getTemporaryTargetDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<TT>>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    fun getNextSyncElementTemporaryTarget(id: Long): Maybe<Pair<TT, TT>>

    /**
     * Invalidate record with id
     *
     * @param id record id
     * @param action Action for UserEntry logging
     * @param source Source for UserEntry logging
     * @param note Note for UserEntry logging
     * @param listValues Values for UserEntry logging
     * @return List of changed records
     */
    fun invalidateTemporaryTarget(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit?>): Single<TransactionResult<TT>>
    fun insertAndCancelCurrentTemporaryTarget(temporaryTarget: TT, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit?>): Single<TransactionResult<TT>>
    fun cancelCurrentTemporaryTargetIfAny(timestamp: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit?>): Single<TransactionResult<TT>>

    /**
     * Store records coming from NS to database
     *
     * @param temporaryTargets list of records
     * @return List of inserted/updated/invalidated records
     */
    fun syncNsTemporaryTargets(temporaryTargets: List<TT>): Single<TransactionResult<TT>>

    /**
     * Update NS id' in database
     *
     * @param temporaryTargets records containing NS id'
     * @return List of modified records
     */
    fun updateTemporaryTargetsNsIds(temporaryTargets: List<TT>): Single<TransactionResult<TT>>

    // TE
    fun getLastTherapyRecordUpToNow(type: TE.Type): Single<ValueWrapper<TE>>
    fun getTherapyEventDataFromToTime(from: Long, to: Long): Single<List<TE>>
    fun getTherapyEventDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<TE>>
    fun getTherapyEventDataFromTime(timestamp: Long, ascending: Boolean): Single<List<TE>>
    fun getTherapyEventDataFromTime(timestamp: Long, type: TE.Type, ascending: Boolean): Single<List<TE>>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    fun getNextSyncElementTherapyEvent(id: Long): Maybe<Pair<TE, TE>>
    fun insertIfNewByTimestampTherapyEvent(
        therapyEvent: TE,
        timestamp: Long = System.currentTimeMillis(),
        action: Action,
        source: Sources,
        note: String?,
        listValues: List<ValueWithUnit?>
    ): Single<TransactionResult<TE>>

    /**
     * Invalidate record with id
     *
     * @param id record id
     * @param action Action for UserEntry logging
     * @param source Source for UserEntry logging
     * @param listValues Values for UserEntry logging
     * @return List of changed records
     */
    fun invalidateTherapyEvent(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit?>): Single<TransactionResult<TE>>

    /**
     * Invalidate records with notes containing string
     *
     * @param note string to search
     * @param action Action for UserEntry logging
     * @param source Source for UserEntry logging
     * @return List of changed records
     */
    fun invalidateTherapyEventsWithNote(note: String, action: Action, source: Sources): Single<TransactionResult<TE>>

    /**
     * Store records coming from NS to database
     *
     * @param therapyEvents list of records
     * @return List of inserted/updated/invalidated records
     */
    fun syncNsTherapyEvents(therapyEvents: List<TE>): Single<TransactionResult<TE>>

    /**
     * Update NS id' in database
     *
     * @param therapyEvents records containing NS id'
     * @return List of modified records
     */
    fun updateTherapyEventsNsIds(therapyEvents: List<TE>): Single<TransactionResult<TE>>

    // OE
    fun getOfflineEventActiveAt(timestamp: Long): OE?

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    fun getNextSyncElementOfflineEvent(id: Long): Maybe<Pair<OE, OE>>
    fun insertAndCancelCurrentOfflineEvent(offlineEvent: OE, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit?> = listOf()): Single<TransactionResult<OE>>

    /**
     * Store records coming from NS to database
     *
     * @param offlineEvents list of records
     * @return List of inserted/updated/invalidated records
     */
    fun syncNsOfflineEvents(offlineEvents: List<OE>): Single<TransactionResult<OE>>

    /**
     * Update NS id' in database
     *
     * @param offlineEvents records containing NS id'
     * @return List of modified records
     */
    fun updateOfflineEventsNsIds(offlineEvents: List<OE>): Single<TransactionResult<OE>>

    // UE
    fun insertUserEntries(entries: List<UE>): Single<TransactionResult<UE>>
    fun getUserEntryDataFromTime(timestamp: Long): Single<List<UE>>
    fun getUserEntryFilteredDataFromTime(timestamp: Long): Single<List<UE>>

    class TransactionResult<T> {

        val inserted = mutableListOf<T>()
        val updated = mutableListOf<T>()
        val invalidated = mutableListOf<T>()
        val updatedNsId = mutableListOf<T>()
        val ended = mutableListOf<T>()
        val updatedDuration = mutableListOf<T>()

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