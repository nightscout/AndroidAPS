package app.aaps.pump.equil.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.aaps.pump.equil.R

@Entity(
    tableName = TABLE_EQUIL_HISTORY_RECORD,
    indices = [Index("type", "timestamp")]
)
data class EquilHistoryRecord(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    @Embedded(prefix = "tempBasalRecord_") var tempBasalRecord: EquilTempBasalRecord? = null,
    @Embedded(prefix = "bolusRecord_") var bolusRecord: EquilBolusRecord? = null,
    @Embedded(prefix = "basalprofile_") var basalValuesRecord: EquilBasalValuesRecord? = null,
    var type: EventType? = null,
    var timestamp: Long = 0,
    var serialNumber: String,
    var resolvedStatus: ResolvedResult? = null,
    var resolvedAt: Long? = null,
    var note: String? = null

) {

    constructor(
        type: EventType, eventTimestamp: Long, serialNumber: String
    ) : this(0, null, null, null, type, eventTimestamp, serialNumber, null, null, null)

    constructor(
        eventTimestamp: Long, serialNumber: String
    ) : this(0, null, null, null, null, eventTimestamp, serialNumber, null, null, null)

    fun isSuccess(): Boolean = resolvedStatus == ResolvedResult.SUCCESS

    enum class EventType(val resourceId: Int) {
        INITIALIZE_EQUIL(R.string.equil_common_cmd_pair),  // First step of Pod activation
        INSERT_CANNULA(R.string.equil_common_cmd_insert_cannula),  // Second step of Pod activation
        FILL(R.string.equil_common_cmd_fill),
        SET_BASAL_PROFILE(R.string.equil_common_cmd_set_basal_schedule),  //
        SET_BOLUS(R.string.equil_common_cmd_set_bolus),  //
        CANCEL_BOLUS(R.string.equil_common_cmd_cancel_bolus),  //
        SET_EXTENDED_BOLUS(R.string.equil_common_cmd_set_extended_bolus),  //
        CANCEL_EXTENDED_BOLUS(R.string.equil_common_cmd_cancel_extended_bolus),  //
        SET_TEMPORARY_BASAL(R.string.equil_common_cmd_set_tbr),  //
        CANCEL_TEMPORARY_BASAL(R.string.equil_common_cmd_cancel_tbr),  //
        SET_TIME(R.string.equil_common_cmd_set_time),  //
        SUSPEND_DELIVERY(R.string.equil_common_cmd_suspend_delivery),
        RESUME_DELIVERY(R.string.equil_common_cmd_resume_delivery),
        UNPAIR_EQUIL(R.string.equil_common_cmd_unpair),
        CHANGE_INSULIN(R.string.equil_common_change_insulin),
        SET_ALARM_MUTE(R.string.equil_common_set_alarm_mute),
        SET_ALARM_SHAKE(R.string.equil_common_set_alarm_shake),
        SET_ALARM_TONE(R.string.equil_common_set_alarm_tone),
        SET_ALARM_TONE_AND_SHAK(R.string.equil_common_set_alarm_tone_and_shake),
        READ_DEVICES(R.string.equil_common_read_devices),
        EQUIL_ALARM(R.string.equil_common_cmd_alarm)
    }
}



