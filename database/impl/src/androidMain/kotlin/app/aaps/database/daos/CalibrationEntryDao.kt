package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.CalibrationEntry
import app.aaps.database.entities.TABLE_CALIBRATION_ENTRIES

@Dao
internal interface CalibrationEntryDao : TraceableDao<CalibrationEntry> {

    @Query("SELECT * FROM $TABLE_CALIBRATION_ENTRIES WHERE id = :id")
    override fun findById(id: Long): CalibrationEntry?

    @Query("DELETE FROM $TABLE_CALIBRATION_ENTRIES")
    override fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_CALIBRATION_ENTRIES WHERE timestamp < :than")
    override fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_CALIBRATION_ENTRIES WHERE referenceId IS NOT NULL")
    override fun deleteTrackedChanges(): Int

    @Query("SELECT id FROM $TABLE_CALIBRATION_ENTRIES ORDER BY id DESC limit 1")
    suspend fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_CALIBRATION_ENTRIES WHERE (nightscoutId = :nsId) AND (referenceId IS NULL)")
    suspend fun findByNSId(nsId: String): CalibrationEntry?

    @Query("SELECT * FROM $TABLE_CALIBRATION_ENTRIES WHERE (timestamp = :timestamp) AND (referenceId IS NULL)")
    suspend fun findByTimestamp(timestamp: Long): CalibrationEntry?

    @Query("SELECT * FROM $TABLE_CALIBRATION_ENTRIES WHERE isValid = 1 AND referenceId IS NULL ORDER BY timestamp DESC")
    suspend fun getAllValid(): List<CalibrationEntry>

    @Query("SELECT * FROM $TABLE_CALIBRATION_ENTRIES WHERE isValid = 1 AND referenceId IS NULL AND timestamp >= :from ORDER BY timestamp DESC")
    suspend fun getValidSince(from: Long): List<CalibrationEntry>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_CALIBRATION_ENTRIES WHERE id > :id ORDER BY id ASC limit 1")
    suspend fun getNextModifiedOrNewAfter(id: Long): CalibrationEntry?

    @Query("SELECT * FROM $TABLE_CALIBRATION_ENTRIES WHERE id = :referenceId")
    suspend fun getCurrentFromHistoric(referenceId: Long): CalibrationEntry?
}
