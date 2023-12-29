package app.aaps.pump.insight.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = DATABASE_INSIGHT_HISTORY_OFFSETS,
    indices = [Index("pumpSerial")]
)
data class InsightHistoryOffset(
    @PrimaryKey val pumpSerial: String,
    var offset: Long
)
