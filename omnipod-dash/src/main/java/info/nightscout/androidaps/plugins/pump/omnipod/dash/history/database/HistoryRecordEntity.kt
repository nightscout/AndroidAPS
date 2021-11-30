package info.nightscout.androidaps.plugins.pump.omnipod.dash.history.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.BolusRecord
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.InitialResult
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.ResolvedResult
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.TempBasalRecord

@Entity(tableName = "historyrecords")
data class HistoryRecordEntity(
    @PrimaryKey val id: String, // ULID
    val createdAt: Long, // creation date of the record
    val date: Long, // when event actually happened
    val commandType: OmnipodCommandType,
    val initialResult: InitialResult,
    @Embedded(prefix = "tempBasalRecord_") val tempBasalRecord: TempBasalRecord?,
    @Embedded(prefix = "bolusRecord_") val bolusRecord: BolusRecord?,
    val resolvedResult: ResolvedResult?,
    val resolvedAt: Long?
)
