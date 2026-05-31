package app.aaps.plugins.calibration.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val TABLE_CALIBRATION_ENTRIES = "calibration_entries"

@Entity(
    tableName = TABLE_CALIBRATION_ENTRIES,
    indices = [Index("timestamp")]
)
data class CalibrationEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val fingerstickMgdl: Double,
    val sensorMgdlAtPairing: Double,
    val isValid: Boolean = true
)
