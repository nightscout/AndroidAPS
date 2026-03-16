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
import app.aaps.core.data.model.advancedFilteringSupported
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.APSResult
import io.reactivex.rxjava3.core.Completable
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

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
    suspend fun cleanupDatabase(keepDays: Long, deleteTrackedChanges: Boolean): String

    // Flow-based change observation
    /**
     * Observe changes for a specific domain type
     * @param T The domain type to observe (BS, CA, EB, TB, TT, TE, PS, EPS, etc.)
     * @return Flow that emits list of changed entities of type T
     */
    fun <T : Any> observeChanges(type: Class<T>): Flow<List<T>>

    /**
     * Observe all database changes, emitting the set of domain types that changed in each transaction
     * @return Flow that emits set of changed domain type KClasses (e.g. {BS::class, CA::class})
     */
    fun observeAnyChange(): Flow<Set<KClass<*>>>

    // BS
    /**
     * Get last bolus
     *
     * @return bolus record
     */
    suspend fun getNewestBolus(): BS?

    /**
     * Get oldest bolus
     *
     * @return bolus record
     */
    suspend fun getOldestBolus(): BS?

    /**
     * Get last bolus of specified type
     *
     * @param type bolus type
     * @return bolus record
     */
    suspend fun getNewestBolusOfType(type: BS.Type): BS?

    /**
     *  Get highest id in database
     *  @return id
     */
    suspend fun getLastBolusId(): Long?

    /**
     *  Get bolus by NS id
     *  @return bolus
     */
    suspend fun getBolusByNSId(nsId: String): BS?

    /**
     * Get all boluses
     *
     * @return List of all boluses
     */
    suspend fun getBoluses(): List<BS>

    /**
     * Get boluses from time (suspend variant)
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of boluses
     */
    suspend fun getBolusesFromTime(startTime: Long, ascending: Boolean): List<BS>

    /**
     * Get boluses in time interval
     *
     * @param startTime from
     * @param endTime to
     * @param ascending sort order
     * @return List of boluses
     */
    suspend fun getBolusesFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<BS>

    /**
     * Get boluses from time including invalidated (suspend variant)
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of boluses including invalidated ones
     */
    suspend fun getBolusesFromTimeIncludingInvalid(startTime: Long, ascending: Boolean): List<BS>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    suspend fun getNextSyncElementBolus(id: Long): Pair<BS, BS>?

    /**
     * Insert or update if exists record
     *
     * @param bolus record
     * @param action Action for UserEntry logging
     * @param note note for UserEntry logging
     * @param source Source for UserEntry logging
     * @return List of inserted/updated records
     */
    suspend fun insertOrUpdateBolus(bolus: BS, action: Action, source: Sources, note: String? = null): TransactionResult<BS>

    /**
     * Update bolus record without creating UserEntry. For data migrations only.
     */
    suspend fun updateBolusNoLogging(bolus: BS)

    /**
     * Insert record
     *
     * @param bolus record
     * @return List of inserted records
     */
    suspend fun insertBolusWithTempId(bolus: BS): TransactionResult<BS>

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
    suspend fun invalidateBolus(id: Long, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>): TransactionResult<BS>

    /**
     * Sync record coming from pump to database
     *
     * @param bolus record to sync
     * @param type record type because filed is not nullable in class
     * @return List of inserted/updated records
     */
    suspend fun syncPumpBolus(bolus: BS, type: BS.Type?): TransactionResult<BS>

    /**
     * Sync record coming from pump to database based on pump temporary id
     *
     * @param bolus record to sync
     * @param type record type because filed is not nullable in class
     * @return List of updated records
     */
    suspend fun syncPumpBolusWithTempId(bolus: BS, type: BS.Type?): TransactionResult<BS>

    /**
     * Store records coming from NS to database
     *
     * @param boluses list of records
     * @param doLog create UserEntry if true
     * @return List of inserted/updated/invalidated records
     */
    suspend fun syncNsBolus(boluses: List<BS>, doLog: Boolean): TransactionResult<BS>

    /**
     * Update NS id' in database
     *
     * @param boluses records containing NS id'
     * @return List of modified records
     */
    suspend fun updateBolusesNsIds(boluses: List<BS>): TransactionResult<BS>

    // CA
    /**
     *  Get carbs record with highest timestamp
     *  @return carbs
     */
    suspend fun getNewestCarbs(): CA?

    /**
     *  Get carbs record with lowest timestamp
     *  @return carbs
     */
    suspend fun getOldestCarbs(): CA?

    /**
     *  Get highest id in database
     *  @return id
     */
    suspend fun getLastCarbsId(): Long?

    /**
     *  Get carbs by NS id
     *  @return carbs
     */
    suspend fun getCarbsByNSId(nsId: String): CA?

    /**
     * Get carbs from time (suspend variant)
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of carbs
     */
    suspend fun getCarbsFromTime(startTime: Long, ascending: Boolean): List<CA>

    /**
     * Get carbs from time including invalidated (suspend variant)
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of carbs including invalidated ones
     */
    suspend fun getCarbsFromTimeIncludingInvalid(startTime: Long, ascending: Boolean): List<CA>

    /**
     * Get carbs from time with expanded extended carbs to multiple records
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of carbs
     */
    suspend fun getCarbsFromTimeExpanded(startTime: Long, ascending: Boolean): List<CA>

    /**
     * Get carbs records from time
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of carbs
     */
    suspend fun getCarbsFromTimeNotExpanded(startTime: Long, ascending: Boolean): List<CA>

    /**
     * Get carbs in time interval with expanded extended carbs to multiple records
     *
     * @param startTime from
     * @param endTime to
     * @param ascending sort order
     * @return List of carbs
     */
    suspend fun getCarbsFromTimeToTimeExpanded(startTime: Long, endTime: Long, ascending: Boolean): List<CA>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    suspend fun getNextSyncElementCarbs(id: Long): Pair<CA, CA>?

    /**
     * Insert or update if exists record
     *
     * @param carbs record
     * @param action Action for UserEntry logging
     * @param note note for UserEntry logging
     * @param source Source for UserEntry logging
     * @return List of inserted/updated records
     */
    suspend fun insertOrUpdateCarbs(carbs: CA, action: Action, source: Sources, note: String? = null): TransactionResult<CA>

    /**
     * Insert carbs if not exists
     *
     * @param carbs record
     * @return List of inserted records
     */
    suspend fun insertPumpCarbsIfNewByTimestamp(carbs: CA): TransactionResult<CA>

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
    suspend fun invalidateCarbs(id: Long, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>): TransactionResult<CA>

    /**
     * Invalidate record with id
     *
     * @param id record id
     * @return List of changed records
     */
    suspend fun cutCarbs(id: Long, timestamp: Long): TransactionResult<CA>

    /**
     * Store records coming from NS to database
     *
     * @param carbs list of records
     * @param doLog create UserEntry if true
     * @return List of inserted/updated/invalidated records
     */
    suspend fun syncNsCarbs(carbs: List<CA>, doLog: Boolean): TransactionResult<CA>

    /**
     * Update NS id' in database
     *
     * @param carbs records containing NS id'
     * @return List of modified records
     */
    suspend fun updateCarbsNsIds(carbs: List<CA>): TransactionResult<CA>

    // BCR
    /**
     *  Get bolus calculator result by NS id
     *  @return bolus calculator result
     */
    suspend fun getBolusCalculatorResultByNSId(nsId: String): BCR?

    /**
     * Get BCRs starting from time
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of BCRs
     */
    suspend fun getBolusCalculatorResultsFromTime(startTime: Long, ascending: Boolean): List<BCR>

    /**
     * Get BCRs starting from time including invalided records
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of BCRs
     */
    suspend fun getBolusCalculatorResultsIncludingInvalidFromTime(startTime: Long, ascending: Boolean): List<BCR>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    suspend fun getNextSyncElementBolusCalculatorResult(id: Long): Pair<BCR, BCR>?

    /**
     * Get record with highest id
     *
     * @return database record id
     */
    suspend fun getLastBolusCalculatorResultId(): Long?

    /**
     * Insert or update if exists record
     *
     * @param bolusCalculatorResult record
     * @return List of inserted/updated records
     */
    suspend fun insertOrUpdateBolusCalculatorResult(bolusCalculatorResult: BCR): TransactionResult<BCR>

    /**
     * Store records coming from NS to database
     *
     * @param bolusCalculatorResults list of records
     * @return List of inserted/updated/invalidated records
     */
    suspend fun syncNsBolusCalculatorResults(bolusCalculatorResults: List<BCR>): TransactionResult<BCR>

    /**
     * Update NS id' in database
     *
     * @param bolusCalculatorResults records containing NS id'
     * @return List of modified records
     */
    suspend fun updateBolusCalculatorResultsNsIds(bolusCalculatorResults: List<BCR>): TransactionResult<BCR>

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
    suspend fun invalidateBolusCalculatorResult(id: Long, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>): TransactionResult<BCR>

    // GV
    suspend fun getLastGlucoseValue(): GV?

    /**
     * Check if the latest glucose value's sensor supports advanced filtering.
     * Derived from [getLastGlucoseValue]'s [app.aaps.core.data.model.SourceSensor].
     */
    suspend fun isAdvancedFilteringSupported(): Boolean =
        getLastGlucoseValue()?.sourceSensor?.advancedFilteringSupported() ?: false

    /**
     *  Get highest id in database
     *  @return id
     */
    suspend fun getLastGlucoseValueId(): Long?

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    suspend fun getNextSyncElementGlucoseValue(id: Long): Pair<GV, GV>?
    suspend fun getBgReadingsDataFromTimeToTime(start: Long, end: Long, ascending: Boolean): List<GV>
    suspend fun getBgReadingsDataFromTime(timestamp: Long, ascending: Boolean): List<GV>
    suspend fun getBgReadingByNSId(nsId: String): GV?

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
    suspend fun invalidateGlucoseValue(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): TransactionResult<GV>
    suspend fun insertCgmSourceData(caller: Sources, glucoseValues: List<GV>, calibrations: List<Calibration>, sensorInsertionTime: Long?): TransactionResult<GV>

    /**
     * Update NS id' in database
     *
     * @param glucoseValues records containing NS id'
     * @return List of modified records
     */
    suspend fun updateGlucoseValuesNsIds(glucoseValues: List<GV>): TransactionResult<GV>

    // EPS
    /**
     * Get all effective profile switches from db
     *
     * @return List of effective profile switches
     */
    suspend fun getEffectiveProfileSwitches(): List<EPS>

    /**
     *  Get effective profile switch record with lowest timestamp
     *  @return effective profile switch
     */
    suspend fun getOldestEffectiveProfileSwitch(): EPS?

    /**
     * Get running effective profile switch at time
     *
     * @param timestamp time
     * @return running effective profile switch or null if none is running
     */
    suspend fun getEffectiveProfileSwitchActiveAt(timestamp: Long): EPS?

    /**
     *  Get bolus by NS id
     *  @return effective profile switch
     */
    suspend fun getEffectiveProfileSwitchByNSId(nsId: String): EPS?

    /**
     * Get effective profile switches from time
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of effective profile switches
     */
    suspend fun getEffectiveProfileSwitchesFromTime(startTime: Long, ascending: Boolean): List<EPS>

    /**
     * Get effective profile switches from time including invalid records
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of effective profile switches
     */
    suspend fun getEffectiveProfileSwitchesIncludingInvalidFromTime(startTime: Long, ascending: Boolean): List<EPS>

    /**
     * Get effective profile switches in time interval
     *
     * @param startTime from
     * @param endTime to
     * @param ascending sort order
     * @return List effective profile switches
     */
    suspend fun getEffectiveProfileSwitchesFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<EPS>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    suspend fun getNextSyncElementEffectiveProfileSwitch(id: Long): Pair<EPS, EPS>?

    /**
     * Get record with highest id
     *
     * @return database record id
     */
    suspend fun getLastEffectiveProfileSwitchId(): Long?

    /**
     * Insert new record to database
     *
     * @param effectiveProfileSwitch record
     */
    suspend fun insertOrUpdateEffectiveProfileSwitch(effectiveProfileSwitch: EPS): TransactionResult<EPS>

    /**
     * Update effective profile switch record without creating UserEntry. For data migrations only.
     */
    suspend fun updateEffectiveProfileSwitchNoLogging(effectiveProfileSwitch: EPS)

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
    suspend fun invalidateEffectiveProfileSwitch(id: Long, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>): TransactionResult<EPS>

    /**
     * Store records coming from NS to database
     *
     * @param effectiveProfileSwitches list of records
     * @param doLog create UserEntry if true
     * @return List of inserted/updated/invalidated records
     */
    suspend fun syncNsEffectiveProfileSwitches(effectiveProfileSwitches: List<EPS>, doLog: Boolean): TransactionResult<EPS>

    /**
     * Update NS id' in database
     *
     * @param effectiveProfileSwitches records containing NS id'
     * @return List of modified records
     */
    suspend fun updateEffectiveProfileSwitchesNsIds(effectiveProfileSwitches: List<EPS>): TransactionResult<EPS>

    // PS
    /**
     * Get running profile switch at time
     *
     * @param timestamp time
     * @return running profile switch or null if none is running
     */
    suspend fun getProfileSwitchActiveAt(timestamp: Long): PS?

    /**
     *  Get profile switch by NS id
     *  @return profile switch
     */
    suspend fun getProfileSwitchByNSId(nsId: String): PS?

    /**
     * Get running profile switch at time with duration == 0 (infinite)
     *
     * @param timestamp time
     * @return running profile switch or null if none is running
     */
    suspend fun getPermanentProfileSwitchActiveAt(timestamp: Long): PS?

    /**
     * Get all profile switches from db
     *
     * @return List of profile switches
     */
    suspend fun getProfileSwitches(): List<PS>

    /**
     * Get profile switches from time
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of profile switches
     */
    suspend fun getProfileSwitchesFromTime(startTime: Long, ascending: Boolean): List<PS>

    /**
     * Get profile switches from time including invalidated records
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of profile switches
     */
    suspend fun getProfileSwitchesIncludingInvalidFromTime(startTime: Long, ascending: Boolean): List<PS>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    suspend fun getNextSyncElementProfileSwitch(id: Long): Pair<PS, PS>?

    /**
     * Get record with highest id
     *
     * @return database record id
     */
    suspend fun getLastProfileSwitchId(): Long?

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
    suspend fun insertOrUpdateProfileSwitch(profileSwitch: PS, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>): TransactionResult<PS>

    /**
     * Update profile switch record without creating UserEntry. For data migrations only.
     */
    suspend fun updateProfileSwitchNoLogging(profileSwitch: PS)

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
    suspend fun invalidateProfileSwitch(id: Long, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>): TransactionResult<PS>

    /**
     * Store records coming from NS to database
     *
     * @param profileSwitches list of records
     * @param doLog create UserEntry if true
     * @return List of inserted/updated/invalidated records
     */
    suspend fun syncNsProfileSwitches(profileSwitches: List<PS>, doLog: Boolean): TransactionResult<PS>

    /**
     * Update NS id' in database
     *
     * @param profileSwitches records containing NS id'
     * @return List of modified records
     */
    suspend fun updateProfileSwitchesNsIds(profileSwitches: List<PS>): TransactionResult<PS>

    // RM
    /**
     * Get running running mode at time
     *
     * @param timestamp time
     * @return running running mode or default
     */
    suspend fun getRunningModeActiveAt(timestamp: Long): RM

    /**
     *  Get running mode by NS id
     *  @return running mode
     */
    suspend fun getRunningModeByNSId(nsId: String): RM?

    /**
     * Get running running mode at time with duration == 0 (infinite)
     *
     * @param timestamp time
     * @return running running mode or default
     */
    suspend fun getPermanentRunningModeActiveAt(timestamp: Long): RM

    /**
     * Get all running modes from db
     *
     * @return List of running modes
     */
    suspend fun getRunningModes(): List<RM>

    /**
     * Get running modes from time
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of running modes
     */
    suspend fun getRunningModesFromTime(startTime: Long, ascending: Boolean): List<RM>

    /**
     * Get running modes from time to time
     *
     * @param startTime from
     * @param endTime from
     * @param ascending sort order
     * @return List of running modes
     */
    suspend fun getRunningModesFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<RM>

    /**
     * Get running modes from time including invalidated records
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of running modes
     */
    suspend fun getRunningModesIncludingInvalidFromTime(startTime: Long, ascending: Boolean): List<RM>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    suspend fun getNextSyncElementRunningMode(id: Long): Pair<RM, RM>?

    /**
     * Get record with highest id
     *
     * @return database record id
     */
    suspend fun getLastRunningModeId(): Long?

    /**
     * Cancel temporary running mode if there is some running at provided timestamp
     *
     * @param timestamp time
     * @param action Action for UserEntry logging
     * @param source Source for UserEntry logging
     * @param listValues Values for UserEntry logging
     */
    suspend fun cancelCurrentRunningMode(timestamp: Long, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit> = listOf()): TransactionResult<RM>

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
    suspend fun insertOrUpdateRunningMode(runningMode: RM, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>): TransactionResult<RM>

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
    suspend fun invalidateRunningMode(id: Long, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>): TransactionResult<RM>

    /**
     * Store records coming from NS to database
     *
     * @param runningModes list of records
     * @param doLog create UserEntry if true
     * @return List of inserted/updated/invalidated records
     */
    suspend fun syncNsRunningModes(runningModes: List<RM>, doLog: Boolean): TransactionResult<RM>

    /**
     * Update NS id' in database
     *
     * @param runningModes records containing NS id'
     * @return List of modified records
     */
    suspend fun updateRunningModesNsIds(runningModes: List<RM>): TransactionResult<RM>

    // TB
    /**
     * Get running temporary basal at time
     *
     * @param timestamp time
     * @return running temporary basal or null if none is running
     */
    suspend fun getTemporaryBasalActiveAt(timestamp: Long): TB?

    /**
     * Get latest temporary basal
     *
     * @return temporary basal or null if none in db
     */
    suspend fun getOldestTemporaryBasalRecord(): TB?

    /**
     *  Get highest id in database
     *  @return id
     */
    suspend fun getLastTemporaryBasalId(): Long?

    /**
     *  Get temporary basal by NS id
     *  @return temporary basal
     */
    suspend fun getTemporaryBasalByNSId(nsId: String): TB?

    /**
     * Get running temporary basal in time interval
     *
     * @param startTime from
     * @param endTime to
     * @return List of temporary basals
     */
    suspend fun getTemporaryBasalsActiveBetweenTimeAndTime(startTime: Long, endTime: Long): List<TB>

    /**
     * Get running temporary basal starting in time interval
     *
     * @param startTime from
     * @param endTime to
     * @param ascending sort order
     * @return List of temporary basals
     */
    suspend fun getTemporaryBasalsStartingFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<TB>

    /**
     * Get running temporary basal starting from time (suspend variant)
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of temporary basals
     */
    suspend fun getTemporaryBasalsStartingFromTime(startTime: Long, ascending: Boolean): List<TB>

    /**
     * Get running temporary basal starting from time including invalided records (suspend variant)
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of temporary basals including invalidated ones
     */
    suspend fun getTemporaryBasalsStartingFromTimeIncludingInvalid(startTime: Long, ascending: Boolean): List<TB>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    suspend fun getNextSyncElementTemporaryBasal(id: Long): Pair<TB, TB>?

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
    suspend fun invalidateTemporaryBasal(id: Long, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>): TransactionResult<TB>

    /**
     * Store records coming from NS to database
     *
     * @param temporaryBasals list of records
     * @param doLog create UserEntry if true
     * @return List of inserted/updated/invalidated records
     */
    suspend fun syncNsTemporaryBasals(temporaryBasals: List<TB>, doLog: Boolean): TransactionResult<TB>

    /**
     * Update NS id' in database
     *
     * @param temporaryBasals records containing NS id'
     * @return List of modified records
     */
    suspend fun updateTemporaryBasalsNsIds(temporaryBasals: List<TB>): TransactionResult<TB>

    /**
     * Sync record coming from pump to database
     *
     * @param temporaryBasal record to sync
     * @param type record type because filed is not nullable in class
     * @return List of inserted/updated records
     */
    suspend fun syncPumpTemporaryBasal(temporaryBasal: TB, type: TB.Type?): TransactionResult<TB>

    /**
     * Sync end of temporary basal coming from pump to database
     *
     * @param timestamp end temporary basal to this timme
     * @param endPumpId id from pump
     * @param pumpType PumpType
     * @param pumpSerial pump serial number
     * @return List of updated records
     */
    suspend fun syncPumpCancelTemporaryBasalIfAny(timestamp: Long, endPumpId: Long, pumpType: PumpType, pumpSerial: String): TransactionResult<TB>

    /**
     * Invalidate temporary basal coming from pump in database
     *
     * @param temporaryId temporary id of record
     * @return List of invalidated records
     */
    suspend fun syncPumpInvalidateTemporaryBasalWithTempId(temporaryId: Long): TransactionResult<TB>

    /**
     * Invalidate temporary basal coming from pump in database
     *
     * @param pumpId id from pump
     * @param pumpType PumpType
     * @param pumpSerial pump serial number
     * @return List of invalidated records
     */
    suspend fun syncPumpInvalidateTemporaryBasalWithPumpId(pumpId: Long, pumpType: PumpType, pumpSerial: String): TransactionResult<TB>

    /**
     * Sync record coming from pump to database using pump temp id
     *
     * @param temporaryBasal record to sync
     * @param type record type because filed is not nullable in class
     * @return List of updated records
     */
    suspend fun syncPumpTemporaryBasalWithTempId(temporaryBasal: TB, type: TB.Type?): TransactionResult<TB>

    /**
     * Store record to database using temporary pump id
     *
     * @param temporaryBasal record to sync
     * @return List of inserted records
     */
    suspend fun insertTemporaryBasalWithTempId(temporaryBasal: TB): TransactionResult<TB>

    // EB
    /**
     * Get running extended bolus at time
     *
     * @param timestamp time
     * @return running extended bolus or null if none is running
     */
    suspend fun getExtendedBolusActiveAt(timestamp: Long): EB?

    /**
     * Get latest extended bolus
     *
     * @return extended bolus or null if none in db
     */
    suspend fun getOldestExtendedBolusRecord(): EB?

    /**
     *  Get highest id in database
     *  @return id
     */
    suspend fun getLastExtendedBolusId(): Long?

    /**
     *  Get extended bolus by NS id
     *  @return extended bolus
     */
    suspend fun getExtendedBolusByNSId(nsId: String): EB?

    /**
     * Get running extended bolus starting in time interval
     *
     * @param startTime from
     * @param endTime to
     * @param ascending sort order
     * @return List of extended boluses
     */
    suspend fun getExtendedBolusesStartingFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): List<EB>

    /**
     * Get running extended boluses starting from time (suspend variant)
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of extended boluses
     */
    suspend fun getExtendedBolusesStartingFromTime(startTime: Long, ascending: Boolean): List<EB>

    /**
     * Get running extended boluses starting from time including invalided records (suspend variant)
     *
     * @param startTime from
     * @param ascending sort order
     * @return List of extended boluses including invalidated ones
     */
    suspend fun getExtendedBolusStartingFromTimeIncludingInvalid(startTime: Long, ascending: Boolean): List<EB>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    suspend fun getNextSyncElementExtendedBolus(id: Long): Pair<EB, EB>?

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
    suspend fun invalidateExtendedBolus(id: Long, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>): TransactionResult<EB>

    /**
     * Store records coming from NS to database
     *
     * @param extendedBoluses list of records
     * @param doLog create UserEntry if true
     * @return List of inserted/updated/invalidated records
     */
    suspend fun syncNsExtendedBoluses(extendedBoluses: List<EB>, doLog: Boolean): TransactionResult<EB>

    /**
     * Update NS id' in database
     *
     * @param extendedBoluses records containing NS id'
     * @return List of modified records
     */
    suspend fun updateExtendedBolusesNsIds(extendedBoluses: List<EB>): TransactionResult<EB>

    /**
     * Sync record coming from pump to database
     *
     * @param extendedBolus record to sync
     * @return List of inserted/updated records
     */
    suspend fun syncPumpExtendedBolus(extendedBolus: EB): TransactionResult<EB>

    /**
     * Sync end of extended bolus coming from pump to database
     *
     * @param timestamp time
     * @param endPumpId pump id of end
     * @param pumpType PumpType
     * @param pumpSerial pump serial number
     * @return List of updated records
     */
    suspend fun syncPumpStopExtendedBolusWithPumpId(timestamp: Long, endPumpId: Long, pumpType: PumpType, pumpSerial: String): TransactionResult<EB>

    // TT
    /**
     * Get running temporary target at time
     *
     * @param timestamp time
     * @return running temporary target or null if none is running
     */
    suspend fun getTemporaryTargetActiveAt(timestamp: Long): TT?

    /**
     *  Get highest id in database
     *  @return id
     */
    suspend fun getLastTemporaryTargetId(): Long?

    /**
     *  Get temporary target by NS id
     *  @return temporary target
     */
    suspend fun getTemporaryTargetByNSId(nsId: String): TT?

    /**
     * Get temporary targets from time (suspend variant)
     *
     * @param timestamp from
     * @param ascending sort order
     * @return List of temporary targets
     */
    suspend fun getTemporaryTargetDataFromTime(timestamp: Long, ascending: Boolean): List<TT>

    /**
     * Get temporary targets from time including invalidated (suspend variant)
     *
     * @param timestamp from
     * @param ascending sort order
     * @return List of temporary targets including invalidated ones
     */
    suspend fun getTemporaryTargetDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): List<TT>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    suspend fun getNextSyncElementTemporaryTarget(id: Long): Pair<TT, TT>?

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
    suspend fun invalidateTemporaryTarget(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): TransactionResult<TT>
    suspend fun insertAndCancelCurrentTemporaryTarget(temporaryTarget: TT, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): TransactionResult<TT>
    suspend fun cancelCurrentTemporaryTargetIfAny(timestamp: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): TransactionResult<TT>

    /**
     * Store records coming from NS to database
     *
     * @param temporaryTargets list of records
     * @param doLog create UserEntry if true
     * @return List of inserted/updated/invalidated records
     */
    suspend fun syncNsTemporaryTargets(temporaryTargets: List<TT>, doLog: Boolean): TransactionResult<TT>

    /**
     * Update NS id' in database
     *
     * @param temporaryTargets records containing NS id'
     * @return List of modified records
     */
    suspend fun updateTemporaryTargetsNsIds(temporaryTargets: List<TT>): TransactionResult<TT>

    // TE
    /**
     *  Get highest id in database
     *  @return id
     */
    suspend fun getLastTherapyEventId(): Long?

    /**
     *  Get therapy event by NS id
     *  @return therapy event
     */
    suspend fun getTherapyEventByNSId(nsId: String): TE?

    suspend fun getLastTherapyRecordUpToNow(type: TE.Type): TE?
    suspend fun getTherapyEventDataFromToTime(from: Long, to: Long): List<TE>

    /**
     * Get therapy events from time including invalidated (suspend variant)
     *
     * @param timestamp from
     * @param ascending sort order
     * @return List of therapy events including invalidated ones
     */
    suspend fun getTherapyEventDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): List<TE>

    /**
     * Get therapy events from time (suspend variant)
     *
     * @param timestamp from
     * @param ascending sort order
     * @return List of therapy events
     */
    suspend fun getTherapyEventDataFromTime(timestamp: Long, ascending: Boolean): List<TE>
    suspend fun getTherapyEventDataFromTime(timestamp: Long, type: TE.Type, ascending: Boolean): List<TE>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    suspend fun getNextSyncElementTherapyEvent(id: Long): Pair<TE, TE>?

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
    suspend fun insertPumpTherapyEventIfNewByTimestamp(
        therapyEvent: TE,
        timestamp: Long = System.currentTimeMillis(),
        action: Action,
        source: Sources,
        note: String?,
        listValues: List<ValueWithUnit>
    ): TransactionResult<TE>

    /**
     * Insert or update if exists record
     *
     * Create new scratch file from selection
     * @return List of inserted/updated records
     */
    suspend fun insertOrUpdateTherapyEvent(therapyEvent: TE): TransactionResult<TE>

    /**
     * Invalidate record with id
     *
     * @param id record id
     * @param action Action for UserEntry logging
     * @param source Source for UserEntry logging
     * @param listValues Values for UserEntry logging
     * @return List of changed records
     */
    suspend fun invalidateTherapyEvent(id: Long, action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>): TransactionResult<TE>

    /**
     * Invalidate records with notes containing string
     *
     * @param note string to search
     * @param action Action for UserEntry logging
     * @param source Source for UserEntry logging
     * @return List of changed records
     */
    suspend fun invalidateTherapyEventsWithNote(note: String, action: Action, source: Sources): TransactionResult<TE>

    /**
     * Store records coming from NS to database
     *
     * @param therapyEvents list of records
     * @param doLog create UserEntry if true
     * @return List of inserted/updated/invalidated records
     */
    suspend fun syncNsTherapyEvents(therapyEvents: List<TE>, doLog: Boolean): TransactionResult<TE>

    /**
     * Update NS id' in database
     *
     * @param therapyEvents records containing NS id'
     * @return List of modified records
     */
    suspend fun updateTherapyEventsNsIds(therapyEvents: List<TE>): TransactionResult<TE>

    // DS
    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    suspend fun getNextSyncElementDeviceStatus(id: Long): DS?

    /**
     * Get record with highest id
     *
     * @return database record id
     */
    suspend fun getLastDeviceStatusId(): Long?

    fun insertDeviceStatus(deviceStatus: DS)

    /**
     * Update NS id' in database
     *
     * @param deviceStatuses records containing NS id'
     * @return List of modified records
     */
    suspend fun updateDeviceStatusesNsIds(deviceStatuses: List<DS>): TransactionResult<DS>

    // HR

    /**
     * Get heart rates from specified time
     *
     * @param startTime from
     * @return List of heart rates
     */
    suspend fun getHeartRatesFromTime(startTime: Long): List<HR>

    /**
     * Get heart rates in time interval
     *
     * @param startTime from
     * @param endTime to
     * @return List of heart rates
     */
    suspend fun getHeartRatesFromTimeToTime(startTime: Long, endTime: Long): List<HR>

    /**
     * Insert or update if exists record
     *
     * @param heartRate record
     * @return List of inserted/updated records
     */
    suspend fun insertOrUpdateHeartRate(heartRate: HR): TransactionResult<HR>

    // FD
    /**
     * Get all food records
     *
     * @return List of records
     */
    suspend fun getFoods(): List<FD>

    /**
     * Get next changed record after id
     *
     * @param id record id
     * @return database record
     */
    suspend fun getNextSyncElementFood(id: Long): Pair<FD, FD>?

    /**
     * Get record with highest id
     *
     * @return database record id
     */
    suspend fun getLastFoodId(): Long?

    /**
     * Invalidate record with id
     *
     * @param id record id
     * @param action Action for UserEntry logging
     * @param source Source for UserEntry logging
     * @return List of changed records
     */
    suspend fun invalidateFood(id: Long, action: Action, source: Sources): TransactionResult<FD>

    /**
     * Store records coming from NS to database
     *
     * @param foods list of records
     * @return List of inserted/updated/invalidated records
     */
    suspend fun syncNsFood(foods: List<FD>): TransactionResult<FD>

    /**
     * Update NS id' in database
     *
     * @param foods records containing NS id'
     * @return List of modified records
     */
    suspend fun updateFoodsNsIds(foods: List<FD>): TransactionResult<FD>

    // UE
    suspend fun insertUserEntries(entries: List<UE>): TransactionResult<UE>

    suspend fun getUserEntryDataFromTime(timestamp: Long): List<UE>

    suspend fun getUserEntryFilteredDataFromTime(timestamp: Long): List<UE>

    // TDD

    /**
     * Remove data older than timestamp
     *
     * @param timestamp from
     */
    suspend fun clearCachedTddData(timestamp: Long)

    /**
     * Get newest 'count' records from database
     *
     * @param count amount
     * @param ascending sorted ascending if true
     * @return List of tdds
     */
    suspend fun getLastTotalDailyDoses(count: Int, ascending: Boolean): List<TDD>

    /**
     * Get cached TDD for specified time
     *
     * @param timestamp time
     * @return tdd or null
     */
    suspend fun getCalculatedTotalDailyDose(timestamp: Long): TDD?

    /**
     * Insert or update record
     */
    suspend fun insertOrUpdateCachedTotalDailyDose(totalDailyDose: TDD): TransactionResult<TDD>

    /**
     * Insert or update if exists record
     *
     * @param totalDailyDose record
     * @return List of inserted/updated records
     */
    suspend fun insertOrUpdateTotalDailyDose(totalDailyDose: TDD): TransactionResult<TDD>

    // SC

    /**
     * Get step counts records from time
     *
     * @param from time
     * @return list of step count records
     */
    suspend fun getStepsCountFromTime(from: Long): List<SC>

    /**
     * Get step counts records from interval
     *
     * @param startTime from
     * @param endTime to
     * @return list of step count records
     */
    suspend fun getStepsCountFromTimeToTime(startTime: Long, endTime: Long): List<SC>

    /**
     * Get latest step counts record from interval
     *
     * @param startTime from
     * @param endTime to
     * @return step count record
     */
    suspend fun getLastStepsCountFromTimeToTime(startTime: Long, endTime: Long): SC?

    /**
     * Insert or update if exists record
     *
     * @param stepsCount record
     * @return List of inserted/updated records
     */
    suspend fun insertOrUpdateStepsCount(stepsCount: SC): TransactionResult<SC>

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
    suspend fun collectNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): NE
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
    suspend fun getApsResultCloseTo(timestamp: Long): APSResult?

    /**
     * Get list of APSResults for interval
     *
     * @param start from
     * @param end to
     * @return List of APSResult
     */
    suspend fun getApsResults(start: Long, end: Long): List<APSResult>

    /**
     * Insert or update ApsResult record
     *
     * @param apsResult record
     * @return List of inserted records
     */
    suspend fun insertOrUpdateApsResult(apsResult: APSResult): TransactionResult<APSResult>

}

/**
 * Observe changes for a specific domain type using reified type parameter
 * @param T The domain type to observe (BS, CA, EB, TB, TT, TE, PS, EPS, BCR, etc.)
 * @return Flow that emits Unit when entities of type T change
 *
 * Example usage:
 * ```
 * persistenceLayer.observeChanges<TB>()
 *     .debounce(1000L)
 *     .onEach { loadData() }
 *     .launchIn(viewModelScope)
 * ```
 */
inline fun <reified T : Any> PersistenceLayer.observeChanges(): Flow<List<T>> =
    observeChanges(T::class.java)