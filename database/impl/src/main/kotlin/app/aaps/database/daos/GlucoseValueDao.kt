package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.TABLE_GLUCOSE_VALUES
import app.aaps.database.entities.TeljaneScopeRow
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

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
    fun getLast(): Maybe<GlucoseValue>

    @Query("SELECT id FROM $TABLE_GLUCOSE_VALUES ORDER BY id DESC limit 1")
    fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE unlikely(nightscoutId = :nsId) AND likely(referenceId IS NULL)")
    fun findByNSId(nsId: String): GlucoseValue?

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE unlikely(timestamp = :timestamp) AND likely(sourceSensor = :sourceSensor) AND likely(referenceId IS NULL)")
    fun findByTimestampAndSensor(timestamp: Long, sourceSensor: GlucoseValue.SourceSensor): GlucoseValue?

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE unlikely(timestamp >= :timestamp) AND likely(isValid = 1) AND likely(referenceId IS NULL) AND likely(value >= 39) ORDER BY timestamp ASC")
    fun compatGetBgReadingsDataFromTime(timestamp: Long): Single<List<GlucoseValue>>

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE unlikely(timestamp BETWEEN :start AND :end) AND likely(isValid = 1) AND likely(referenceId IS NULL) AND likely(value >= 39) ORDER BY timestamp ASC")
    fun compatGetBgReadingsDataFromTime(start: Long, end: Long): Single<List<GlucoseValue>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE id > :id ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<GlucoseValue>

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<GlucoseValue>

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<GlucoseValue>

    /**
     * Teljane-only insert:
     * - If (sourceSensor, sgvId) unique key already exists -> SKIP (Room IGNORE returns -1)
     * - If new -> insert and returns new rowId
     *
     * NOTE:
     * - This relies on your UNIQUE index (sourceSensor, sgvId) being present.
     * - If sgvId is null, callers should NOT use this method.
     *
     * We use fully-qualified names here to avoid changing imports.
     */
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    fun insertTeljaneIfNew(value: GlucoseValue): Long

    /**
     * Teljane: latest device prefix derived from latest Teljane row.
     *
     * sgvId is 13 digits: devicePrefix(8) * 100000 + mark(<= 99999)
     * devicePrefix = sgvId / 100000
     */
    @Query("""
        SELECT (sgvId / 100000)
        FROM $TABLE_GLUCOSE_VALUES
        WHERE isValid = 1
          AND referenceId IS NULL
          AND sourceSensor = :sourceSensor
          AND sgvId IS NOT NULL
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    fun getLatestDeviceIdPrefix(sourceSensor: GlucoseValue.SourceSensor): Maybe<Long>

    /**
     * Teljane: stats for ONE device only.
     *
     * IMPORTANT:
     * - Return type is TeljaneScopeRow (DB-layer projection).
     * - This type is not visible outside :database module.
     * - Mapping to PersistenceLayer.TeljaneScope happens in AppRepository.
     *
     * Range is bounded by numeric [deviceStart ~ deviceEnd] so (sourceSensor, sgvId) index is usable.
     * Note: deviceEnd uses +99999 ONLY to bound the device prefix range,
     * NOT as a gap-search scope end.
     */
    @Query("""
        SELECT
            COALESCE(MIN(sgvId), -1)       AS minSgvId,
            COALESCE(MAX(sgvId), -1)       AS maxSgvId,
            COALESCE(MAX(sgvMark), -1)     AS sgvMark,
            COALESCE(MAX(timestamp), -1)   AS latestTimestamp
        FROM $TABLE_GLUCOSE_VALUES
        WHERE isValid = 1
          AND referenceId IS NULL
          AND sourceSensor = :sourceSensor
          AND sgvId IS NOT NULL
          AND sgvId BETWEEN :deviceStart AND :deviceEnd
    """)
    fun getTeljaneScope(
        sourceSensor: GlucoseValue.SourceSensor,
        deviceStart: Long,
        deviceEnd: Long
    ): TeljaneScopeRow

    /**
     * Teljane: find oldest missing mark in [minMark ~ endMark] for this device.
     *
     * deviceStart is devicePrefix * 100000.
     * A stored row matches mark x when gv.sgvId == deviceStart + x
     */
    @Query("""
        WITH RECURSIVE seq(x) AS (
            SELECT :minMark
            UNION ALL
            SELECT x + 1 FROM seq WHERE x + 1 <= :endMark
        )
        SELECT x
        FROM seq
        WHERE NOT EXISTS (
            SELECT 1
            FROM $TABLE_GLUCOSE_VALUES gv
            WHERE gv.isValid = 1
              AND gv.referenceId IS NULL
              AND gv.sourceSensor = :sourceSensor
              AND gv.sgvId = (:deviceStart + CAST(x AS INTEGER))
        )
        LIMIT 1
    """)
    fun getFirstMissingMark(
        sourceSensor: GlucoseValue.SourceSensor,
        deviceStart: Long,
        minMark: Int,
        endMark: Int
    ): Maybe<Int>
}