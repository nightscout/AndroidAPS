package app.aaps.pump.equil.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = TABLE_EQUIL_LOGS,
    indices = [Index("timestamp", "eventTimestamp")]
)
class Equillogs {

    @PrimaryKey var timestamp: Long = 0
    var text: String = ""
    var eventTimestamp: String = ""
}
