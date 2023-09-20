package com.microtechmd.equil.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = TABLE_DANA_HISTORY_PUMP,
    indices = [Index("code", "timestamp", "eventTimestamp","eventIndex")]
)
class EquilHistoryPump {

    @PrimaryKey var timestamp: Long = 0
    var code: Int = 0
    var battery: Int = 0
    var insulin: Int = 0
    var rate: Int = 0
    var largeRate: Int = 0
    var type: Int = 0
    var eventIndex: Int = 0
    var level: Int = 0
    var parm: Int = 0
    var port: Int = 0
    var eventTimestamp: Long = 0


}
