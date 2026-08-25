package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.aaps.database.entities.DeviceStatus
import app.aaps.database.entities.TABLE_DEVICE_STATUS

@Dao
internal interface DeviceStatusDao {

    @Insert
    fun insert(entry: DeviceStatus): Long

    @Update
    fun update(entry: DeviceStatus)

    @Query("SELECT * FROM $TABLE_DEVICE_STATUS WHERE id = :id")
    fun findById(id: Long): DeviceStatus?

    @Query("DELETE FROM $TABLE_DEVICE_STATUS")
    fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_DEVICE_STATUS WHERE timestamp < :than")
    fun deleteOlderThan(than: Long): Int

    @Query("SELECT id FROM $TABLE_DEVICE_STATUS ORDER BY id DESC limit 1")
    suspend fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_DEVICE_STATUS WHERE nightscoutId = :nsId")
    suspend fun findByNSId(nsId: String): DeviceStatus?

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_DEVICE_STATUS WHERE id > :id AND nightscoutId IS NULL ORDER BY id ASC limit 1")
    suspend fun getNextModifiedOrNewAfter(id: Long): DeviceStatus?
}