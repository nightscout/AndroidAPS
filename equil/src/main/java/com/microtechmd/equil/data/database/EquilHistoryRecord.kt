package com.microtechmd.equil.data.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.microtechmd.equil.R

@Entity(
    tableName = TABLE_DANA_HISTORY_RECORD,
    indices = [Index("type", "timestamp")]
)
data class EquilHistoryRecord(

    @PrimaryKey var timestamp: Long = 0,

    @Embedded(prefix = "tempBasalRecord_") val tempBasalRecord: EquilTempBasalRecord?,
    @Embedded(prefix = "bolusRecord_") val bolusRecord: EquilBolusRecord?,
    // @Embedded(prefix = "basalprofile_") val basalProfileRecord: EquilBasalValuesRecord?,
    var type: EventType = EventType.INITIALIZE_EQUIL,
    var eventTimestamp: Long = 0,
    val serialNumber: String

) {

    enum class EventType(val resourceId: Int) {
        INITIALIZE_EQUIL(R.string.equil_common_cmd_pair),  // First step of Pod activation
        INSERT_CANNULA(R.string.equil_common_cmd_insert_cannula),  // Second step of Pod activation
        SET_BASAL_PROFILE(R.string.equil_common_cmd_set_basal_schedule),  //
        SET_BOLUS(R.string.equil_common_cmd_set_bolus),  //
        CANCEL_BOLUS(R.string.equil_common_cmd_cancel_bolus),  //
        SET_TEMPORARY_BASAL(R.string.equil_common_cmd_set_tbr),  //
        CANCEL_TEMPORARY_BASAL(R.string.equil_common_cmd_cancel_tbr),  //
        SET_TIME(R.string.equil_common_cmd_set_time),  //
        SUSPEND_DELIVERY(R.string.equil_common_cmd_suspend_delivery),
        RESUME_DELIVERY(R.string.equil_common_cmd_resume_delivery),
        UNPAIR_EQUIL(R.string.equil_common_cmd_unpair),
        EQUIL_ALARM(R.string.equil_common_cmd_unpair)
    }
}



