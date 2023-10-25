package app.aaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.DBEntry
import app.aaps.database.entities.interfaces.DBEntryWithTime
import java.util.TimeZone

@Entity(
    tableName = TABLE_DEVICE_STATUS,
    foreignKeys = [],
    indices = [
        Index("id"),
        Index("nightscoutId"),
        Index("timestamp")
    ]
)
data class DeviceStatus(
    @PrimaryKey(autoGenerate = true)
    override var id: Long = 0,
    @Embedded
    var interfaceIDs_backing: InterfaceIDs? = null,
    override var timestamp: Long,
    override var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var device: String? = null,
    var pump: String? = null,
    var enacted: String? = null,
    var suggested: String? = null,
    var iob: String? = null,
    var uploaderBattery: Int,
    var isCharging: Boolean?,
    var configuration: String? = null

) : DBEntry, DBEntryWithTime {

    var interfaceIDs: InterfaceIDs
        get() {
            var value = this.interfaceIDs_backing
            if (value == null) {
                value = InterfaceIDs()
                interfaceIDs_backing = value
            }
            return value
        }
        set(value) {
            interfaceIDs_backing = value
        }
}