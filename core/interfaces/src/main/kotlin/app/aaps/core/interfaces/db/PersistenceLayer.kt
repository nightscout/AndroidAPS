package app.aaps.core.interfaces.db

import app.aaps.core.data.db.BS
import app.aaps.core.data.db.CA
import app.aaps.core.data.db.DS
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
import app.aaps.database.ValueWrapper
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.EffectiveProfileSwitch
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

interface PersistenceLayer {

    fun clearDatabases()
    fun cleanupDatabase(keepDays: Long, deleteTrackedChanges: Boolean): String
    fun insertOrUpdate(bolusCalculatorResult: BolusCalculatorResult)

    // BS
    /**
     * Get last bolus
     *
     * @return bolus record
     */
    fun getLastBolus(): BS?

    /**
     * Get last bolus of specified type
     *
     * @param type bolus type
     * @return bolus record
     */
    fun getLastBolusOfType(type: BS.Type): BS?

    /**
     *  Get highest id in database
     *  @return id
     */
    fun getLastBolusId(): Long?

    /**
     *  Get bolus by NS id
     *  @return bolus
     */
    fun getBolusByNSId(nsId: String): BS?

    /**
     * Get boluses from time
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of boluses
     */
    fun getBolusesFromTime(startTime: Long, ascending: Boolean): Single<List<BS>>

    /**
     * Get boluses in time interval
     *
     * @param startTime from
     * @param endTime to
     * @param ascending sort order
     * @return List of boluses
     */
    fun getBolusesFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<BS>

    /**
     * Get boluses from time including invalidated
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of boluses
     */
    fun getBolusesFromTimeIncludingInvalid(startTime: Long, ascending: Boolean): Single<List<BS>>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    fun getNextSyncElementBolus(id: Long): Maybe<Pair<BS, BS>>

    /**
     * Insert or update if exists record
     *
     * @param bolus record
     * @param action Action for UserEntry logging
     * @param note note for UserEntry logging
     * @param source Source for UserEntry logging
     * @param listValues Values for UserEntry logging
     * @return List of inserted/updated records
     */
    fun insertOrUpdateBolus(bolus: BS, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit?>): Single<TransactionResult<BS>>

    /**
     * Insert record
     *
     * @param bolus record
     * @return List of inserted records
     */
    fun insertBolusWithTempId(bolus: BS): Single<TransactionResult<BS>>

    /**
     * Sync record coming from pump to database
     *
     * @param bolus record to sync
     * @param type record type because filed is not nullable in class
     * @return List of inserted/updated records
     */
    fun syncPumpBolus(bolus: BS, type: BS.Type?): Single<TransactionResult<BS>>

    /**
     * Sync record coming from pump to database based on pump temporary id
     *
     * @param bolus record to sync
     * @param type record type because filed is not nullable in class
     * @return List of updated records
     */
    fun syncPumpBolusWithTempId(bolus: BS, type: BS.Type?): Single<TransactionResult<BS>>

    /**
     * Store records coming from NS to database
     *
     * @param boluses list of records
     * @return List of inserted/updated/invalidated records
     */
    fun syncNsBolus(boluses: List<BS>): Single<TransactionResult<BS>>

    /**
     * Update NS id' in database
     *
     * @param boluses records containing NS id'
     * @return List of modified records
     */
    fun updateBolusesNsIds(boluses: List<BS>): Single<TransactionResult<BS>>

    // CA
    /**
     *  Get highest id in database
     *  @return id
     */
    fun getLastCarbsId(): Long?

    /**
     *  Get carbs by NS id
     *  @return carbs
     */
    fun getCarbsByNSId(nsId: String): CA?

    /**
     * Get carbs from time
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of carbs
     */
    fun getCarbsFromTime(startTime: Long, ascending: Boolean): Single<List<CA>>

    /**
     * Get carbs from time including invalidated
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of boluses
     */
    fun getCarbsFromTimeIncludingInvalid(startTime: Long, ascending: Boolean): Single<List<CA>>

    /**
     * Get carbs in time interval with expanded extended carbs to multiple records
     *
     * @param startTime from
     * @param endTime to
     * @param ascending sort order
     * @return List of carbs
     */
    fun getCarbsFromTimeToTimeExpanded(startTime: Long, endTime: Long, ascending: Boolean): List<CA>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    fun getNextSyncElementCarbs(id: Long): Maybe<Pair<CA, CA>>

    /**
     * Insert or update if exists record
     *
     * @param carbs record
     * @param action Action for UserEntry logging
     * @param note note for UserEntry logging
     * @param source Source for UserEntry logging
     * @param listValues Values for UserEntry logging
     * @return List of inserted/updated records
     */
    fun insertOrUpdateCarbs(carbs: CA, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit?>): Single<TransactionResult<CA>>

    /**
     * Insert carbs if not exists
     *
     * @param carbs record
     * @return List of inserted records
     */
    fun insertPumpCarbsIfNewByTimestamp(carbs: CA): Single<TransactionResult<CA>>

    /**
     * Store records coming from NS to database
     *
     * @param carbs list of records
     * @return List of inserted/updated/invalidated records
     */
    fun syncNsCarbs(carbs: List<CA>): Single<TransactionResult<CA>>

    /**
     * Update NS id' in database
     *
     * @param carbs records containing NS id'
     * @return List of modified records
     */
    fun updateCarbsNsIds(carbs: List<CA>): Single<TransactionResult<CA>>

    // GV
    fun getLastGlucoseValue(): GV?

    /**
     *  Get highest id in database
     *  @return id
     */
    fun getLastGlucoseValueId(): Long?

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    fun getNextSyncElementGlucoseValue(id: Long): Maybe<Pair<GV, GV>>
    fun getBgReadingsDataFromTimeToTime(start: Long, end: Long, ascending: Boolean): List<GV>
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
     *  Get highest id in database
     *  @return id
     */
    fun getLastTemporaryBasalId(): Long?

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
     * @param temporaryBasals list of records
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
     *  Get highest id in database
     *  @return id
     */
    fun getLastExtendedBolusId(): Long?

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

    /**
     *  Get highest id in database
     *  @return id
     */
    fun getLastTemporaryTargetId(): Long?

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
    /**
     *  Get highest id in database
     *  @return id
     */
    fun getLastTherapyEventId(): Long?

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

    /**
     * Insert record if not exists
     *
     * @param therapyEvent record
     * @param action Action for UserEntry logging
     * @param note note for UserEntry logging
     * @param source Source for UserEntry logging
     * @param listValues Values for UserEntry logging
     * @return List of inserted records
     */
    fun insertPumpTherapyEventIfNewByTimestamp(
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
     *  Get highest id in database
     *  @return id
     */
    fun getLastOfflineEventId(): Long?

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

    // DS
    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    fun getNextSyncElementDeviceStatus(id: Long): Maybe<DS>

    fun insert(deviceStatus: DS)

    /**
     * Update NS id' in database
     *
     * @param deviceStatuses records containing NS id'
     * @return List of modified records
     */
    fun updateDeviceStatusesNsIds(deviceStatuses: List<DS>): Single<TransactionResult<DS>>

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