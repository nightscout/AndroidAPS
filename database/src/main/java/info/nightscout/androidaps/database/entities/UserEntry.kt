package info.nightscout.androidaps.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import info.nightscout.androidaps.database.TABLE_USER_ENTRY
import info.nightscout.androidaps.database.interfaces.DBEntry
import info.nightscout.androidaps.database.interfaces.DBEntryWithTime
import java.util.*

@Entity(tableName = TABLE_USER_ENTRY)
data class UserEntry(
    @PrimaryKey(autoGenerate = true)
    override var id: Long = 0L,
    override var timestamp: Long,
    override var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var action: String,
    var s: String,
    var d1: Double,
    var d2: Double,
    var i1: Int,
    var i2: Int
) : DBEntry, DBEntryWithTime {
    enum class Type {
        BOLUS,
        BOLUS_WIZARD,
        BOLUS_ADVISOR,
        BOLUS_RECORD,
        EXTENDED_BOLUS,
        SUPERBOLUS_TBR,
        CARBS,
        EXTENDED_CARBS,
        TEMP_BASAL,

        TT,
        TT_ACTIVITY,
        TT_EATING_SOON,
        TT_HYPO,

        NEW_PROFILE,
        CLONE_PROFILE,
        STORE_PROFILE,
        PROFILE_SWITCH,
        PROFILE_SWITCH_CLONE,

        CLOSED_LOOP_MODE,
        LGS_LOOP_MODE,
        OPEN_LOOP_MODE,
        LOOP_DISABLED,
        LOOP_ENABLED,
        RECONNECT,
        DISCONNECT_15M,
        DISCONNECT_30M,
        DISCONNECT_1H,
        DISCONNECT_2H,
        DISCONNECT_3H,
        RESUME,
        SUSPEND_1H,
        SUSPEND_2H,
        SUSPEND_3H,
        SUSPEND_10H,

        HW_PUMP_ALLOWED,
        CLEAR_PAIRING_KEYS,
        ACCEPTS_TEMP_BASAL,
        CANCEL_TEMP_BASAL,
        CANCEL_EXTENDED_BOLUS,
        CANCEL_TT,

        CAREPORTAL,
        CALIBRATION,
        INSULIN_CHANGE,
        PRIME_BOLUS,
        SITE_CHANGE,

        TREATMENT,
        CAREPORTAL_NS_REFRESH,
        PROFILE_SWITCH_NS_REFRESH,
        TREATMENTS_NS_REFRESH,
        TT_NS_REFRESH,

        AUTOMATION_REMOVED,
        BG_REMOVED,
        CAREPORTAL_REMOVED,
        EXTENDED_BOLUS_REMOVED,
        FOOD_REMOVED,
        PROFILE_REMOVED,
        PROFILE_SWITCH_REMOVED,
        RESTART_EVENTS_REMOVED,
        TREATMENT_REMOVED,
        TT_REMOVED,

        NS_PAUSED,
        NS_QUEUE_CLEARED,
        NS_SETTINGS_COPIED,

        ERROR_DIALOG_OK,
        ERROR_DIALOG_MUTE ,
        ERROR_DIALOG_MUTE_5MIN,

        OBJECTIVE_STARTED,
        OBJECTIVE_UNSTARTED,
        OBJECTIVES_SKIPPED,

        STAT_RESET,

        DELETE_LOGS,
        DELETE_FUTURE_TREATMENTS,
        EXPORT_SETTINGS,
        IMPORT_SETTINGS,
        RESET_DATABASES,
        EXPORT_DATABASES,
        IMPORT_DATABASES,

        OTP_EXPORT,
        OTP_RESET,
        SMS_BASAL,
        SMS_BOLUS,
        SMS_CAL,
        SMS_CARBS,
        SMS_EXTENDED_BOLUS,
        SMS_LOOP_DISABLE,
        SMS_LOOP_ENABLE,
        SMS_LOOP_RESUME,
        SMS_LOOP_SUSPEND,
        SMS_PROFILE,
        SMS_PUMP_CONNECT,
        SMS_PUMP_DISCONNECT,
        SMS_SMS,
        SMS_TARGET
    }
}