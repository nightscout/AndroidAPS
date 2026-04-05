package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.TABLE_GLUCOSE_VALUES

@Dao
internal interface GlucoseValueDao : TraceableDao<GlucoseValue> {

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE id = :id")
    override fun findById(id: Long): GlucoseValue?

    @Query("DELETE FROM $TABLE_GLUCOSE_VALUES")
    override fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_GLUCOSE_VALUES WHERE timestamp < :than")
    override fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_GLUCOSE_VALUES WHERE referenceId IS NOT NULL")
    override fun deleteTrackedChanges(): Int

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE isValid = 1 AND referenceId IS NULL ORDER BY timestamp DESC limit 1")
    suspend fun getLast(): GlucoseValue?

    @Query("SELECT id FROM $TABLE_GLUCOSE_VALUES ORDER BY id DESC limit 1")
    suspend fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE (nightscoutId = :nsId) AND (referenceId IS NULL)")
    suspend fun findByNSId(nsId: String): GlucoseValue?

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE (timestamp = :timestamp) AND (sourceSensor = :sourceSensor) AND (referenceId IS NULL)")
    suspend fun findByTimestampAndSensor(timestamp: Long, sourceSensor: GlucoseValue.SourceSensor): GlucoseValue?

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE (timestamp >= :timestamp) AND (isValid = 1) AND (referenceId IS NULL) AND (value >= 39) ORDER BY timestamp ASC")
    suspend fun compatGetBgReadingsDataFromTime(timestamp: Long): List<GlucoseValue>

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE (timestamp BETWEEN :start AND :end) AND (isValid = 1) AND (referenceId IS NULL) AND (value >= 39) ORDER BY timestamp ASC")
    suspend fun compatGetBgReadingsDataFromTime(start: Long, end: Long): List<GlucoseValue>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE id > :id ORDER BY id ASC limit 1")
    suspend fun getNextModifiedOrNewAfter(id: Long): GlucoseValue?

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE id = :referenceId")
    suspend fun getCurrentFromHistoric(referenceId: Long): GlucoseValue?

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<GlucoseValue>

    // Instara helpers
    // 1) Oldest Instara pumpId (sgvId) for a devicePrefix
    @Query("SELECT pumpId FROM glucoseValues WHERE sourceSensor='INSTARA' AND pumpId IS NOT NULL AND (pumpId / 100000)=:devicePrefix ORDER BY pumpId ASC LIMIT 1")
    suspend fun getOldestInstaraPumpIdForDevice(devicePrefix: Long): Long?

    // 2) Latest Instara pumpId (sgvId) for a devicePrefix
    @Query("SELECT pumpId FROM glucoseValues WHERE sourceSensor='INSTARA' AND pumpId IS NOT NULL AND (pumpId / 100000)=:devicePrefix ORDER BY pumpId DESC LIMIT 1")
    suspend fun getLatestInstaraPumpIdForDevice(devicePrefix: Long): Long?

    // 3) Latest Instara timestamp for a devicePrefix (freshness check)
    @Query("SELECT timestamp FROM glucoseValues WHERE sourceSensor='INSTARA' AND pumpId IS NOT NULL AND (pumpId / 100000)=:devicePrefix ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestInstaraTimestampForDevice(devicePrefix: Long): Long?

    // 4) FIRST missing Instara pumpId inside [startId ~ endId] (single-query gap detection)
    @Query("WITH ordered AS (SELECT pumpId, LEAD(pumpId) OVER (ORDER BY pumpId) AS nextId FROM glucoseValues WHERE sourceSensor='INSTARA' AND pumpId IS NOT NULL AND pumpId BETWEEN :startId AND :endId) SELECT (pumpId + 1) FROM ordered WHERE nextId IS NOT NULL AND nextId > pumpId + 1 ORDER BY pumpId LIMIT 1")
    suspend fun getFirstMissingInstaraPumpIdInRange(startId: Long, endId: Long): Long?

    // 5) Current Instara device prefix = latest pumpId / 100000.
    @Query("SELECT (pumpId / 100000) FROM glucoseValues WHERE sourceSensor='INSTARA' AND pumpId IS NOT NULL ORDER BY pumpId DESC LIMIT 1")
    suspend fun getLatestInstaraDevicePrefix(): Long?

    // 6) Instara sgvId is stored in DB column glucoseValues.pumpId
    @Query("SELECT EXISTS(SELECT 1 FROM glucoseValues WHERE sourceSensor='INSTARA' AND pumpId=:pumpId LIMIT 1)")
    suspend fun instaraPumpIdExists(pumpId: Long): Boolean

    // 7) InstaraTrendArrowResolver support (previous sgvId value lookup)
    // Returns mg/dL value for the given Instara pumpId (sgvId), or null if not found.
    @Query("SELECT value FROM glucoseValues WHERE sourceSensor='INSTARA' AND pumpId=:pumpId LIMIT 1")
    suspend fun getInstaraValueMgdlByPumpId(pumpId: Long): Double?
}