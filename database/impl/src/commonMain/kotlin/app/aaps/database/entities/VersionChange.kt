package app.aaps.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.aaps.database.entities.interfaces.DBEntry
import app.aaps.database.entities.interfaces.DBEntryWithTime

@Entity(tableName = TABLE_VERSION_CHANGES)
data class VersionChange(
    @PrimaryKey(autoGenerate = true)
    override var id: Long = 0L,
    override var timestamp: Long,
    override var utcOffset: Long = defaultUtcOffset(timestamp),
    var versionCode: Int,
    var versionName: String,
    var gitRemote: String?,
    var commitHash: String?
) : DBEntry, DBEntryWithTime