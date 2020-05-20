package info.nightscout.androidaps.db

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable

@DatabaseTable(tableName = DatabaseHelper.DATABASE_OPEN_HUMANS_QUEUE)
data class OHQueueItem @JvmOverloads constructor(
    @DatabaseField(generatedId = true)
    val id: Long = 0,
    @DatabaseField
    val file: String = "",
    @DatabaseField
    val content: String = ""
)