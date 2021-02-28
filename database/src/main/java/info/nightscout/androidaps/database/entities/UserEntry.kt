package info.nightscout.androidaps.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
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
    var action: Action,
    var s: String,
    var values: MutableList<ValueWithUnit>
) : DBEntry, DBEntryWithTime {
    enum class Action () {
        @SerializedName("BOLUS") BOLUS,
        @SerializedName("BOLUS_WIZARD") BOLUS_WIZARD,
        @SerializedName("BOLUS_ADVISOR") BOLUS_ADVISOR,
        @SerializedName("BOLUS_RECORD") BOLUS_RECORD,
        @SerializedName("EXTENDED_BOLUS") EXTENDED_BOLUS,
        @SerializedName("SUPERBOLUS_TBR") SUPERBOLUS_TBR,
        @SerializedName("CARBS") CARBS,
        @SerializedName("EXTENDED_CARBS") EXTENDED_CARBS,
        @SerializedName("TEMP_BASAL") TEMP_BASAL,
        @SerializedName("TT") TT,
        @SerializedName("TT_ACTIVITY") TT_ACTIVITY,
        @SerializedName("TT_EATING_SOON") TT_EATING_SOON,
        @SerializedName("TT_HYPO") TT_HYPO,
        @SerializedName("NEW_PROFILE") NEW_PROFILE,
        @SerializedName("CLONE_PROFILE") CLONE_PROFILE,
        @SerializedName("STORE_PROFILE") STORE_PROFILE,
        @SerializedName("PROFILE_SWITCH") PROFILE_SWITCH,
        @SerializedName("PROFILE_SWITCH_CLONED") PROFILE_SWITCH_CLONED,
        @SerializedName("CLOSED_LOOP_MODE") CLOSED_LOOP_MODE,
        @SerializedName("LGS_LOOP_MODE") LGS_LOOP_MODE,
        @SerializedName("OPEN_LOOP_MODE") OPEN_LOOP_MODE,
        @SerializedName("LOOP_DISABLED") LOOP_DISABLED,
        @SerializedName("LOOP_ENABLED") LOOP_ENABLED,
        @SerializedName("RECONNECT") RECONNECT,
        @SerializedName("DISCONNECT_15M") DISCONNECT_15M,
        @SerializedName("DISCONNECT_30M") DISCONNECT_30M,
        @SerializedName("DISCONNECT_1H") DISCONNECT_1H,
        @SerializedName("DISCONNECT_2H") DISCONNECT_2H,
        @SerializedName("DISCONNECT_3H") DISCONNECT_3H,
        @SerializedName("RESUME") RESUME,
        @SerializedName("SUSPEND_1H") SUSPEND_1H,
        @SerializedName("SUSPEND_2H") SUSPEND_2H,
        @SerializedName("SUSPEND_3H") SUSPEND_3H,
        @SerializedName("SUSPEND_10H") SUSPEND_10H,
        @SerializedName("HW_PUMP_ALLOWED") HW_PUMP_ALLOWED,
        @SerializedName("CLEAR_PAIRING_KEYS") CLEAR_PAIRING_KEYS,
        @SerializedName("ACCEPTS_TEMP_BASAL") ACCEPTS_TEMP_BASAL,
        @SerializedName("CANCEL_TEMP_BASAL") CANCEL_TEMP_BASAL,
        @SerializedName("CANCEL_EXTENDED_BOLUS") CANCEL_EXTENDED_BOLUS,
        @SerializedName("CANCEL_TT") CANCEL_TT,
        @SerializedName("CAREPORTAL") CAREPORTAL,
        @SerializedName("CALIBRATION") CALIBRATION,
        @SerializedName("INSULIN_CHANGE") INSULIN_CHANGE,
        @SerializedName("PRIME_BOLUS") PRIME_BOLUS,
        @SerializedName("SITE_CHANGE") SITE_CHANGE,
        @SerializedName("TREATMENT") TREATMENT,
        @SerializedName("CAREPORTAL_NS_REFRESH") CAREPORTAL_NS_REFRESH,
        @SerializedName("PROFILE_SWITCH_NS_REFRESH") PROFILE_SWITCH_NS_REFRESH,
        @SerializedName("TREATMENTS_NS_REFRESH") TREATMENTS_NS_REFRESH,
        @SerializedName("TT_NS_REFRESH") TT_NS_REFRESH,
        @SerializedName("AUTOMATION_REMOVED") AUTOMATION_REMOVED,
        @SerializedName("BG_REMOVED") BG_REMOVED,
        @SerializedName("CAREPORTAL_REMOVED") CAREPORTAL_REMOVED,
        @SerializedName("EXTENDED_BOLUS_REMOVED") EXTENDED_BOLUS_REMOVED,
        @SerializedName("FOOD_REMOVED") FOOD_REMOVED,
        @SerializedName("PROFILE_REMOVED") PROFILE_REMOVED,
        @SerializedName("PROFILE_SWITCH_REMOVED") PROFILE_SWITCH_REMOVED,
        @SerializedName("RESTART_EVENTS_REMOVED") RESTART_EVENTS_REMOVED,
        @SerializedName("TREATMENT_REMOVED") TREATMENT_REMOVED,
        @SerializedName("TT_REMOVED") TT_REMOVED,
        @SerializedName("NS_PAUSED") NS_PAUSED,
        @SerializedName("NS_QUEUE_CLEARED") NS_QUEUE_CLEARED,
        @SerializedName("NS_SETTINGS_COPIED") NS_SETTINGS_COPIED,
        @SerializedName("ERROR_DIALOG_OK") ERROR_DIALOG_OK,
        @SerializedName("ERROR_DIALOG_MUTE") ERROR_DIALOG_MUTE,
        @SerializedName("ERROR_DIALOG_MUTE_5MIN") ERROR_DIALOG_MUTE_5MIN,
        @SerializedName("OBJECTIVE_STARTED") OBJECTIVE_STARTED,
        @SerializedName("OBJECTIVE_UNSTARTED") OBJECTIVE_UNSTARTED,
        @SerializedName("OBJECTIVES_SKIPPED") OBJECTIVES_SKIPPED,
        @SerializedName("STAT_RESET") STAT_RESET,
        @SerializedName("DELETE_LOGS") DELETE_LOGS,
        @SerializedName("DELETE_FUTURE_TREATMENTS") DELETE_FUTURE_TREATMENTS,
        @SerializedName("EXPORT_SETTINGS") EXPORT_SETTINGS,
        @SerializedName("IMPORT_SETTINGS") IMPORT_SETTINGS,
        @SerializedName("RESET_DATABASES") RESET_DATABASES,
        @SerializedName("EXPORT_DATABASES") EXPORT_DATABASES,
        @SerializedName("IMPORT_DATABASES") IMPORT_DATABASES,
        @SerializedName("OTP_EXPORT") OTP_EXPORT,
        @SerializedName("OTP_RESET") OTP_RESET,
        @SerializedName("SMS_BASAL") SMS_BASAL,
        @SerializedName("SMS_BOLUS") SMS_BOLUS,
        @SerializedName("SMS_CAL") SMS_CAL,
        @SerializedName("SMS_CARBS") SMS_CARBS,
        @SerializedName("SMS_EXTENDED_BOLUS") SMS_EXTENDED_BOLUS,
        @SerializedName("SMS_LOOP_DISABLED") SMS_LOOP_DISABLED,
        @SerializedName("SMS_LOOP_ENABLED") SMS_LOOP_ENABLED,
        @SerializedName("SMS_LOOP_RESUME") SMS_LOOP_RESUME,
        @SerializedName("SMS_LOOP_SUSPEND") SMS_LOOP_SUSPEND,
        @SerializedName("SMS_PROFILE") SMS_PROFILE,
        @SerializedName("SMS_PUMP_CONNECT") SMS_PUMP_CONNECT,
        @SerializedName("SMS_PUMP_DISCONNECT") SMS_PUMP_DISCONNECT,
        @SerializedName("SMS_SMS") SMS_SMS,
        @SerializedName("SMS_TT") SMS_TT,
        @SerializedName("UNKNOWN") UNKNOWN
        ;

        companion object {
            fun fromString(source: String?) = UserEntry.Action.values().firstOrNull { it.name == source } ?: UserEntry.Action.UNKNOWN
        }
    }
    data class ValueWithUnit (val dValue: Double, val iValue: Int, val lValue: Long, val sValue: String, val unit: Units) {
        constructor(dvalue:Double?, unit:Units) : this(dvalue ?:0.0,0, 0, "", unit)
        constructor(ivalue:Int?, unit:Units) : this(0.0, ivalue ?:0, 0, "", unit)
        constructor(lvalue:Long?, unit:Units) : this(0.0,0, lvalue ?:0, "", unit)
        constructor(svalue:String?, unit:Units) : this(0.0,0, 0, svalue ?:"", unit)
        constructor(dvalue:Double?, unit:String) : this(dvalue ?:0.0,0, 0, "", Units.fromString(unit))
    }
    enum class Units {
        @SerializedName("None") None,
        @SerializedName("mg/dl") Mg_Dl,
        @SerializedName("mmol") Mmol_L,
        @SerializedName("Timestamp") Timestamp,
        @SerializedName("U") U,
        @SerializedName("U/h") U_H,
        @SerializedName("g") G,
        @SerializedName("m") M,
        @SerializedName("h") H,
        @SerializedName("Percent") Percent,
        @SerializedName("CPEvent") CPEvent,
        @SerializedName("TT_Reason") TT_Reason,
        @SerializedName("R_String") R_String
        ;

        companion object {
            fun fromString(unit: String?) = values().firstOrNull { it.name == unit } ?: None
        }
    }
}