package app.aaps.pump.omnipod.omnipod5.history.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import app.aaps.pump.omnipod.common.history.data.BasalValuesRecord
import app.aaps.pump.omnipod.common.history.data.BolusRecord
import app.aaps.pump.omnipod.common.history.data.InitialResult
import app.aaps.pump.omnipod.common.history.data.ResolvedResult
import app.aaps.pump.omnipod.common.history.data.TempBasalRecord

@Entity(tableName = "historyrecords", indices = [Index("createdAt")])
data class HistoryRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val date: Long,
    val commandType: OmnipodCommandType,
    val initialResult: InitialResult,
    @Embedded(prefix = "tempBasal_") val tempBasalRecord: TempBasalRecord?,
    @Embedded(prefix = "bolus_") val bolusRecord: BolusRecord?,
    @Embedded(prefix = "basal_") val basalProfileRecord: BasalValuesRecord?,
    val totalAmountDelivered: Double?,
    val resolvedResult: ResolvedResult?,
    val resolvedAt: Long?
)
