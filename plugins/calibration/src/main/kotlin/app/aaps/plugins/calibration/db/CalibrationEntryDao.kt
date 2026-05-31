package app.aaps.plugins.calibration.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CalibrationEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CalibrationEntry): Long

    @Query("SELECT * FROM $TABLE_CALIBRATION_ENTRIES WHERE isValid = 1 ORDER BY timestamp DESC")
    suspend fun getAll(): List<CalibrationEntry>

    @Query("SELECT * FROM $TABLE_CALIBRATION_ENTRIES WHERE isValid = 1 AND timestamp >= :from ORDER BY timestamp DESC")
    suspend fun getSince(from: Long): List<CalibrationEntry>

    /** Emits the full valid-entries list on every table change. */
    @Query("SELECT * FROM $TABLE_CALIBRATION_ENTRIES WHERE isValid = 1 ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<CalibrationEntry>>

    @Query("UPDATE $TABLE_CALIBRATION_ENTRIES SET isValid = 0 WHERE id = :id")
    suspend fun invalidate(id: Long)

    @Query("DELETE FROM $TABLE_CALIBRATION_ENTRIES WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
