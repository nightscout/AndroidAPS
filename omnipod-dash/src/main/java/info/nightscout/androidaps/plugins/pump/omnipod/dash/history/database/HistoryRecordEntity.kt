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
    tableName = DASH_TABLE_NAME,
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
    @Embedded(prefix = DASH_TBS_COLUMN_PREFIX) val tempBasalRecord: TempBasalRecord?,
    @Embedded(prefix = DASH_BOLUS_COLUMN_PREFIX) val bolusRecord: BolusRecord?,
    @Embedded(prefix = DASH_BASAL_COLUMN_PREFIX) val basalProfileRecord: BasalValuesRecord?,
    val resolvedResult: ResolvedResult?,
    val resolvedAt: Long?,
)