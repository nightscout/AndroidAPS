package app.aaps.core.interfaces.db

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
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

interface PersistenceLayer {

    /**
     *  Clear all DB tables
     */
    fun clearDatabases()

    /**
     *  Clear ApsResults table
     */
    fun clearApsResults()

    /**
     * Perform database maintenance
     * @param keepDays remove all records older than
     * @param deleteTrackedChanges delete tracked changes from all tables
     */
    fun cleanupDatabase(keepDays: Long, deleteTrackedChanges: Boolean): String

    // BS
    /**
     * Get last bolus
     *
     * @return bolus record
     */
    fun getNewestBolus(): BS?

    /**
     * Get oldest bolus
     *
     * @return bolus record
     */
    fun getOldestBolus(): BS?

    /**
     * Get last bolus of specified type
     *
     * @param type bolus type
     * @return bolus record
     */
    fun getNewestBolusOfType(type: BS.Type): BS?

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
     * @return List of inserted/updated records
     */
    fun insertOrUpdateBolus(bolus: BS, action: Action, source: Sources, note: String? = null): Single<TransactionResult<BS>>

    /**
     * Insert record
     *
     * @param bolus record
     * @return List of inserted records
     */
    fun insertBolusWithTempId(bolus: BS): Single<TransactionResult<BS>>

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
    fun invalidateBolus(id: Long, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>): Single<TransactionResult<BS>>

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
     * @param doLog create UserEntry if true
     * @return List of inserted/updated/invalidated records
     */
    fun syncNsBolus(boluses: List<BS>, doLog: Boolean): Single<TransactionResult<BS>>

    /**
     * Update NS id' in database
     *
     * @param boluses records containing NS id'
     * @return List of modified records
     */
    fun updateBolusesNsIds(boluses: List<BS>): Single<TransactionResult<BS>>

    // CA
    /**
     *  Get carbs record with highest timestamp
     *  @return carbs
     */
    fun getNewestCarbs(): CA?

    /**
     *  Get carbs record with lowest timestamp
     *  @return carbs
     */
    fun getOldestCarbs(): CA?

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
     * Get carbs from time with expanded extended carbs to multiple records
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of carbs
     */
    fun getCarbsFromTimeExpanded(startTime: Long, ascending: Boolean): List<CA>

    /**
     * Get carbs records from time
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of carbs
     */
    fun getCarbsFromTimeNotExpanded(startTime: Long, ascending: Boolean): Single<List<CA>>

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
     * @return List of inserted/updated records
     */
    fun insertOrUpdateCarbs(carbs: CA, action: Action, source: Sources, note: String? = null): Single<TransactionResult<CA>>

    /**
     * Insert carbs if not exists
     *
     * @param carbs record
     * @return List of inserted records
     */
    fun insertPumpCarbsIfNewByTimestamp(carbs: CA): Single<TransactionResult<CA>>

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
    fun invalidateCarbs(id: Long, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>): Single<TransactionResult<CA>>

    /**
     * Invalidate record with id
     *
     * @param id record id
     * @return List of changed records
     */
    fun cutCarbs(id: Long, timestamp: Long): Single<TransactionResult<CA>>

    /**
     * Store records coming from NS to database
     *
     * @param carbs list of records
     * @param doLog create UserEntry if true
     * @return List of inserted/updated/invalidated records
     */
    fun syncNsCarbs(carbs: List<CA>, doLog: Boolean): Single<TransactionResult<CA>>

    /**
     * Update NS id' in database
     *
     * @param carbs records containing NS id'
     * @return List of modified records
     */
    fun updateCarbsNsIds(carbs: List<CA>): Single<TransactionResult<CA>>

    // BCR
    /**
     *  Get bolus calculator result by NS id
     *  @return bolus calculator result
     */
    fun getBolusCalculatorResultByNSId(nsId: String): BCR?

    /**
     * Get BCRs starting from time
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of BCRs as Single
     */
    fun getBolusCalculatorResultsFromTime(startTime: Long, ascending: Boolean): Single<List<BCR>>

    /**
     * Get BCRs starting from time including invalided records
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of BCRs as Single
     */
    fun getBolusCalculatorResultsIncludingInvalidFromTime(startTime: Long, ascending: Boolean): Single<List<BCR>>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    fun getNextSyncElementBolusCalculatorResult(id: Long): Maybe<Pair<BCR, BCR>>

    /**
     * Get record with highest id
     *
     * @return database record id
     */
    fun getLastBolusCalculatorResultId(): Long?

    /**
     * Insert or update if exists record
     *
     * @param bolusCalculatorResult record
     * @return List of inserted/updated records
     */
    fun insertOrUpdateBolusCalculatorResult(bolusCalculatorResult: BCR): Single<TransactionResult<BCR>>

    /**
     * Store records coming from NS to database
     *
     * @param bolusCalculatorResults list of records
     * @return List of inserted/updated/invalidated records
     */
    fun syncNsBolusCalculatorResults(bolusCalculatorResults: List<BCR>): Single<TransactionResult<BCR>>

    /**
     * Update NS id' in database
     *
     * @param bolusCalculatorResults records containing NS id'
     * @return List of modified records
     */
    fun updateBolusCalculatorResultsNsIds(bolusCalculatorResults: List<BCR>): Single<TransactionResult<BCR>>

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
    fun invalidateBolusCalculatorResult(id: Long, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>): Single<TransactionResult<BCR>>

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
    fun invalidateGlucoseValue(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): Single<TransactionResult<GV>>
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
     *  Get effective profile switch record with lowest timestamp
     *  @return effective profile switch
     */
    fun getOldestEffectiveProfileSwitch(): EPS?

    /**
     * Get running effective profile switch at time
     *
     * @param timestamp time
     * @return running effective profile switch or null if none is running
     */
    fun getEffectiveProfileSwitchActiveAt(timestamp: Long): EPS?

    /**
     *  Get bolus by NS id
     *  @return effective profile switch
     */
    fun getEffectiveProfileSwitchByNSId(nsId: String): EPS?

    /**
     * Get effective profile switches from time
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of effective profile switches
     */
    fun getEffectiveProfileSwitchesFromTime(startTime: Long, ascending: Boolean): Single<List<EPS>>

    /**
     * Get effective profile switches from time including invalid records
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of effective profile switches
     */
    fun getEffectiveProfileSwitchesIncludingInvalidFromTime(startTime: Long, ascending: Boolean): Single<List<EPS>>

    /**
     * Get effective profile switches in time interval
     *
     * @param startTime from
     * @param endTime to
     * @param ascending sort order
     * @return List effective profile switches
     */
    fun getEffectiveProfileSwitchesFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<EPS>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    fun getNextSyncElementEffectiveProfileSwitch(id: Long): Maybe<Pair<EPS, EPS>>

    /**
     * Get record with highest id
     *
     * @return database record id
     */
    fun getLastEffectiveProfileSwitchId(): Long?

    /**
     * Insert new record to database
     *
     * @param effectiveProfileSwitch record
     */
    fun insertEffectiveProfileSwitch(effectiveProfileSwitch: EPS): Single<TransactionResult<EPS>>

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
    fun invalidateEffectiveProfileSwitch(id: Long, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>): Single<TransactionResult<EPS>>

    /**
     * Store records coming from NS to database
     *
     * @param effectiveProfileSwitches list of records
     * @param doLog create UserEntry if true
     * @return List of inserted/updated/invalidated records
     */
    fun syncNsEffectiveProfileSwitches(effectiveProfileSwitches: List<EPS>, doLog: Boolean): Single<TransactionResult<EPS>>

    /**
     * Update NS id' in database
     *
     * @param effectiveProfileSwitches records containing NS id'
     * @return List of modified records
     */
    fun updateEffectiveProfileSwitchesNsIds(effectiveProfileSwitches: List<EPS>): Single<TransactionResult<EPS>>

    // PS
    /**
     * Get running profile switch at time
     *
     * @param timestamp time
     * @return running profile switch or null if none is running
     */
    fun getProfileSwitchActiveAt(timestamp: Long): PS?

    /**
     *  Get profile switch by NS id
     *  @return profile switch
     */
    fun getProfileSwitchByNSId(nsId: String): PS?

    /**
     * Get running profile switch at time with duration == 0 (infinite)
     *
     * @param timestamp time
     * @return running profile switch or null if none is running
     */
    fun getPermanentProfileSwitchActiveAt(timestamp: Long): PS?

    /**
     * Get all profile switches from db
     *
     * @return List of profile switches
     */
    fun getProfileSwitches(): List<PS>

    /**
     * Get profile switches from time
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of profile switches
     */
    fun getProfileSwitchesFromTime(startTime: Long, ascending: Boolean): Single<List<PS>>

    /**
     * Get profile switches from time including invalidated records
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of profile switches
     */
    fun getProfileSwitchesIncludingInvalidFromTime(startTime: Long, ascending: Boolean): Single<List<PS>>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    fun getNextSyncElementProfileSwitch(id: Long): Maybe<Pair<PS, PS>>

    /**
     * Get record with highest id
     *
     * @return database record id
     */
    fun getLastProfileSwitchId(): Long?

    /**
     * Insert or update new record in database
     *
     * @param profileSwitch record
     * @param action Action for UserEntry logging
     * @param source Source for UserEntry logging
     * @param note Note for UserEntry logging
     * @param listValues Values for UserEntry logging
     * @return List of inserted/updated records
     */
    fun insertOrUpdateProfileSwitch(profileSwitch: PS, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>): Single<TransactionResult<PS>>

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
    fun invalidateProfileSwitch(id: Long, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>): Single<TransactionResult<PS>>

    /**
     * Store records coming from NS to database
     *
     * @param profileSwitches list of records
     * @param doLog create UserEntry if true
     * @return List of inserted/updated/invalidated records
     */
    fun syncNsProfileSwitches(profileSwitches: List<PS>, doLog: Boolean): Single<TransactionResult<PS>>

    /**
     * Update NS id' in database
     *
     * @param profileSwitches records containing NS id'
     * @return List of modified records
     */
    fun updateProfileSwitchesNsIds(profileSwitches: List<PS>): Single<TransactionResult<PS>>

    // RM
    /**
     * Get running running mode at time
     *
     * @param timestamp time
     * @return running running mode or default
     */
    fun getRunningModeActiveAt(timestamp: Long): RM

    /**
     *  Get running mode by NS id
     *  @return running mode
     */
    fun getRunningModeByNSId(nsId: String): RM?

    /**
     * Get running running mode at time with duration == 0 (infinite)
     *
     * @param timestamp time
     * @return running running mode or default
     */
    fun getPermanentRunningModeActiveAt(timestamp: Long): RM

    /**
     * Get all running modes from db
     *
     * @return List of running modes
     */
    fun getRunningModes(): List<RM>

    /**
     * Get running modes from time
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of running modes
     */
    fun getRunningModesFromTime(startTime: Long, ascending: Boolean): Single<List<RM>>

    /**
     * Get running modes from time to time
     *
     * @param startTime from
     * @param endTime from
     * @param ascending sort order
     * @return List of running modes
     */
    fun getRunningModesFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<RM>
    /**
     * Get running modes from time including invalidated records
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of running modes
     */
    fun getRunningModesIncludingInvalidFromTime(startTime: Long, ascending: Boolean): Single<List<RM>>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    fun getNextSyncElementRunningMode(id: Long): Maybe<Pair<RM, RM>>

    /**
     * Get record with highest id
     *
     * @return database record id
     */
    fun getLastRunningModeId(): Long?

    /**
     * Cancel temporary running mode if there is some running at provided timestamp
     *
     * @param timestamp time
     * @param action Action for UserEntry logging
     * @param source Source for UserEntry logging
     * @param listValues Values for UserEntry logging
     */
    fun cancelCurrentRunningMode(timestamp: Long, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit> = listOf()): Single<TransactionResult<RM>>

    /**
     * Insert or update new record in database
     *
     * @param runningMode record
     * @param action Action for UserEntry logging
     * @param source Source for UserEntry logging
     * @param note Note for UserEntry logging
     * @param listValues Values for UserEntry logging
     * @return List of inserted/updated records
     */
    fun insertOrUpdateRunningMode(runningMode: RM, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>): Single<TransactionResult<RM>>

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
    fun invalidateRunningMode(id: Long, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>): Single<TransactionResult<RM>>

    /**
     * Store records coming from NS to database
     *
     * @param runningModes list of records
     * @param doLog create UserEntry if true
     * @return List of inserted/updated/invalidated records
     */
    fun syncNsRunningModes(runningModes: List<RM>, doLog: Boolean): Single<TransactionResult<RM>>

    /**
     * Update NS id' in database
     *
     * @param runningModes records containing NS id'
     * @return List of modified records
     */
    fun updateRunningModesNsIds(runningModes: List<RM>): Single<TransactionResult<RM>>

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
     *  Get temporary basal by NS id
     *  @return temporary basal
     */
    fun getTemporaryBasalByNSId(nsId: String): TB?

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
    fun invalidateTemporaryBasal(id: Long, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>): Single<TransactionResult<TB>>

    /**
     * Store records coming from NS to database
     *
     * @param temporaryBasals list of records
     * @param doLog create UserEntry if true
     * @return List of inserted/updated/invalidated records
     */
    fun syncNsTemporaryBasals(temporaryBasals: List<TB>, doLog: Boolean): Single<TransactionResult<TB>>

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
     * Sync end of temporary basal coming from pump to database
     *
     * @param timestamp end temporary basal to this timme
     * @param endPumpId id from pump
     * @param pumpType PumpType
     * @param pumpSerial pump serial number
     * @return List of updated records
     */
    fun syncPumpCancelTemporaryBasalIfAny(timestamp: Long, endPumpId: Long, pumpType: PumpType, pumpSerial: String): Single<TransactionResult<TB>>

    /**
     * Invalidate temporary basal coming from pump in database
     *
     * @param temporaryId temporary id of record
     * @return List of invalidated records
     */
    fun syncPumpInvalidateTemporaryBasalWithTempId(temporaryId: Long): Single<TransactionResult<TB>>

    /**
     * Invalidate temporary basal coming from pump in database
     *
     * @param pumpId id from pump
     * @param pumpType PumpType
     * @param pumpSerial pump serial number
     * @return List of invalidated records
     */
    fun syncPumpInvalidateTemporaryBasalWithPumpId(pumpId: Long, pumpType: PumpType, pumpSerial: String): Single<TransactionResult<TB>>

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
     *  Get extended bolus by NS id
     *  @return extended bolus
     */
    fun getExtendedBolusByNSId(nsId: String): EB?

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
    fun invalidateExtendedBolus(id: Long, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>): Single<TransactionResult<EB>>

    /**
     * Store records coming from NS to database
     *
     * @param extendedBoluses list of records
     * @param doLog create UserEntry if true
     * @return List of inserted/updated/invalidated records
     */
    fun syncNsExtendedBoluses(extendedBoluses: List<EB>, doLog: Boolean): Single<TransactionResult<EB>>

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

    /**
     * Sync end of extended bolus coming from pump to database
     *
     * @param timestamp time
     * @param endPumpId pump id of end
     * @param pumpType PumpType
     * @param pumpSerial pump serial number
     * @return List of updated records
     */
    fun syncPumpStopExtendedBolusWithPumpId(timestamp: Long, endPumpId: Long, pumpType: PumpType, pumpSerial: String): Single<TransactionResult<EB>>

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

    /**
     *  Get temporary target by NS id
     *  @return temporary target
     */
    fun getTemporaryTargetByNSId(nsId: String): TT?

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
    fun invalidateTemporaryTarget(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): Single<TransactionResult<TT>>
    fun insertAndCancelCurrentTemporaryTarget(temporaryTarget: TT, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): Single<TransactionResult<TT>>
    fun cancelCurrentTemporaryTargetIfAny(timestamp: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): Single<TransactionResult<TT>>

    /**
     * Store records coming from NS to database
     *
     * @param temporaryTargets list of records
     * @param doLog create UserEntry if true
     * @return List of inserted/updated/invalidated records
     */
    fun syncNsTemporaryTargets(temporaryTargets: List<TT>, doLog: Boolean): Single<TransactionResult<TT>>

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

    /**
     *  Get therapy event by NS id
     *  @return therapy event
     */
    fun getTherapyEventByNSId(nsId: String): TE?

    fun getLastTherapyRecordUpToNow(type: TE.Type): TE?
    fun getTherapyEventDataFromToTime(from: Long, to: Long): Single<List<TE>>
    fun getTherapyEventDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<TE>>
    fun getTherapyEventDataFromTime(timestamp: Long, ascending: Boolean): Single<List<TE>>
    fun getTherapyEventDataFromTime(timestamp: Long, type: TE.Type, ascending: Boolean): List<TE>

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
        listValues: List<ValueWithUnit>
    ): Single<TransactionResult<TE>>

    /**
     * Insert or update if exists record
     *
     * Create new scratch file from selection
     * @return List of inserted/updated records
     */
    fun insertOrUpdateTherapyEvent(therapyEvent: TE): Single<TransactionResult<TE>>


    /**
     * Invalidate record with id
     *
     * @param id record id
     * @param action Action for UserEntry logging
     * @param source Source for UserEntry logging
     * @param listValues Values for UserEntry logging
     * @return List of changed records
     */
    fun invalidateTherapyEvent(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): Single<TransactionResult<TE>>

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
     * @param doLog create UserEntry if true
     * @return List of inserted/updated/invalidated records
     */
    fun syncNsTherapyEvents(therapyEvents: List<TE>, doLog: Boolean): Single<TransactionResult<TE>>

    /**
     * Update NS id' in database
     *
     * @param therapyEvents records containing NS id'
     * @return List of modified records
     */
    fun updateTherapyEventsNsIds(therapyEvents: List<TE>): Single<TransactionResult<TE>>

    // DS
    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    fun getNextSyncElementDeviceStatus(id: Long): Maybe<DS>

    /**
     * Get record with highest id
     *
     * @return database record id
     */
    fun getLastDeviceStatusId(): Long?

    fun insertDeviceStatus(deviceStatus: DS)

    /**
     * Update NS id' in database
     *
     * @param deviceStatuses records containing NS id'
     * @return List of modified records
     */
    fun updateDeviceStatusesNsIds(deviceStatuses: List<DS>): Single<TransactionResult<DS>>

    // HR

    /**
     * Get heart rates from specified time
     *
     * @param startTime from
     * @return List of heart rates
     */
    fun getHeartRatesFromTime(startTime: Long): List<HR>

    /**
     * Get heart rates in time interval
     *
     * @param startTime from
     * @param endTime to
     * @return List of heart rates
     */
    fun getHeartRatesFromTimeToTime(startTime: Long, endTime: Long): List<HR>

    /**
     * Insert or update if exists record
     *
     * @param heartRate record
     * @return List of inserted/updated records
     */
    fun insertOrUpdateHeartRate(heartRate: HR): Single<TransactionResult<HR>>

    // FD
    /**
     * Get all food records
     *
     * @return List of records
     */
    fun getFoods(): Single<List<FD>>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    fun getNextSyncElementFood(id: Long): Maybe<Pair<FD, FD>>

    /**
     * Get record with highest id
     *
     * @return database record id
     */
    fun getLastFoodId(): Long?

    /**
     * Invalidate record with id
     *
     * @param id record id
     * @param action Action for UserEntry logging
     * @param source Source for UserEntry logging
     * @return List of changed records
     */
    fun invalidateFood(id: Long, action: Action, source: Sources): Single<TransactionResult<FD>>

    /**
     * Store records coming from NS to database
     *
     * @param foods list of records
     * @return List of inserted/updated/invalidated records
     */
    fun syncNsFood(foods: List<FD>): Single<TransactionResult<FD>>

    /**
     * Update NS id' in database
     *
     * @param foods records containing NS id'
     * @return List of modified records
     */
    fun updateFoodsNsIds(foods: List<FD>): Single<TransactionResult<FD>>

    // UE
    fun insertUserEntries(entries: List<UE>): Single<TransactionResult<UE>>
    fun getUserEntryDataFromTime(timestamp: Long): Single<List<UE>>
    fun getUserEntryFilteredDataFromTime(timestamp: Long): Single<List<UE>>

    // TDD

    /**
     * Remove data older than timestamp
     *
     * @param timestamp from
     */
    fun clearCachedTddData(timestamp: Long)

    /**
     * Get newest 'count' records from database
     *
     * @param count amount
     * @param ascending sorted ascending if true
     * @return List of tdds
     */
    fun getLastTotalDailyDoses(count: Int, ascending: Boolean): List<TDD>

    /**
     * Get cached TDD for specified time
     *
     * @param timestamp time
     * @return tdd or null
     */
    fun getCalculatedTotalDailyDose(timestamp: Long): TDD?

    /**
     * Insert or update record
     */
    fun insertOrUpdateCachedTotalDailyDose(totalDailyDose: TDD): Single<TransactionResult<TDD>>

    /**
     * Insert or update if exists record
     *
     * @param totalDailyDose record
     * @return List of inserted/updated records
     */
    fun insertOrUpdateTotalDailyDose(totalDailyDose: TDD): Single<TransactionResult<TDD>>

    // SC

    /**
     * Get step counts records from time
     *
     * @param from time
     * @return list of step count records
     */
    fun getStepsCountFromTime(from: Long): List<SC>

    /**
     * Get step counts records from interval
     *
     * @param startTime from
     * @param endTime to
     * @return list of step count records
     */
    fun getStepsCountFromTimeToTime(startTime: Long, endTime: Long): List<SC>

    /**
     * Get latest step counts record from interval
     *
     * @param startTime from
     * @param endTime to
     * @return step count record
     */
    fun getLastStepsCountFromTimeToTime(startTime: Long, endTime: Long): SC?

    /**
     * Insert or update if exists record
     *
     * @param stepsCount record
     * @return List of inserted/updated records
     */
    fun insertOrUpdateStepsCount(stepsCount: SC): Single<TransactionResult<SC>>

    // VersionChange

    /**
     * Insert new record to db if version has changed since last run
     *
     * @param versionName versionName (ie 3.3.0)
     * @param versionCode versionCode (ie 1500)
     * @param gitRemote gitRemote (shortened)
     * @param commitHash commitHash
     */
    fun insertVersionChangeIfChanged(versionName: String, versionCode: Int, gitRemote: String?, commitHash: String?): Completable

    /**
     * Get list of db changed records in db since time
     *
     * @param since from
     * @param until to
     * @param limit max amount
     * @param offset
     * @return List of arrays of records
     */
    fun collectNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): NE
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

    /**
     * Get nearest older APSResult (max age is 5 min)
     *
     * @param timestamp time
     * @return APSResult or null
     */
    fun getApsResultCloseTo(timestamp: Long): APSResult?

    /**
     * Get list of APSResults for interval
     *
     * @param start from
     * @param end to
     * @return List of APSResult
     */
    fun getApsResults(start: Long, end: Long): List<APSResult>

    /**
     * Insert or update ApsResult record
     *
     * @param apsResult record
     * @return List of inserted records
     */
    fun insertOrUpdateApsResult(apsResult: APSResult): Single<TransactionResult<APSResult>>

}