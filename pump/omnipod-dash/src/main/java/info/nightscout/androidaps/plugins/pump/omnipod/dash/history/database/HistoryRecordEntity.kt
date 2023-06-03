package info.nightscout.androidaps.plugins.pump.omnipod.dash.history.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.BasalValuesRecord
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.BolusRecord
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.InitialResult
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.ResolvedResult
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.TempBasalRecord

@Entity(
    tableName = "historyrecords",
    indices = [
        Index("createdAt"),
    ]
)
data class HistoryRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val createdAt: Long, // creation date of the record
    val date: Long, // when event actually happened
    val commandType: OmnipodCommandType,
    val initialResult: InitialResult,
    @Embedded(prefix = "tempBasalRecord_") val tempBasalRecord: TempBasalRecord?,
    @Embedded(prefix = "bolusRecord_") val bolusRecord: BolusRecord?,
    @Embedded(prefix = "basalprofile_") val basalProfileRecord: BasalValuesRecord?,
    val totalAmountDelivered: Double?,
    val resolvedResult: ResolvedResult?,
    val resolvedAt: Long?
)
