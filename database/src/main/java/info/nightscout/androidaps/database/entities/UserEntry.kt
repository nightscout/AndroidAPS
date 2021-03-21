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
    enum class Action (val colorGroup: ColorGroup) {
        @SerializedName("BOLUS") BOLUS (ColorGroup.InsulinTreatment),
        @SerializedName("BOLUS_ADVISOR") BOLUS_ADVISOR (ColorGroup.InsulinTreatment),
        @SerializedName("BOLUS_RECORD") BOLUS_RECORD (ColorGroup.InsulinTreatment),
        @SerializedName("EXTENDED_BOLUS") EXTENDED_BOLUS (ColorGroup.InsulinTreatment),
        @SerializedName("SUPERBOLUS_TBR") SUPERBOLUS_TBR (ColorGroup.InsulinTreatment),
        @SerializedName("CARBS") CARBS (ColorGroup.CarbTreatment),
        @SerializedName("EXTENDED_CARBS") EXTENDED_CARBS (ColorGroup.CarbTreatment),
        @SerializedName("TEMP_BASAL") TEMP_BASAL (ColorGroup.InsulinTreatment),
        @SerializedName("TT") TT (ColorGroup.TT),
        @SerializedName("NEW_PROFILE") NEW_PROFILE (ColorGroup.Profile),
        @SerializedName("CLONE_PROFILE") CLONE_PROFILE (ColorGroup.Profile),
        @SerializedName("STORE_PROFILE") STORE_PROFILE (ColorGroup.Profile),
        @SerializedName("PROFILE_SWITCH") PROFILE_SWITCH (ColorGroup.Profile),
        @SerializedName("PROFILE_SWITCH_CLONED") PROFILE_SWITCH_CLONED (ColorGroup.Profile),
        @SerializedName("CLOSED_LOOP_MODE") CLOSED_LOOP_MODE (ColorGroup.Loop),
        @SerializedName("LGS_LOOP_MODE") LGS_LOOP_MODE (ColorGroup.Loop),
        @SerializedName("OPEN_LOOP_MODE") OPEN_LOOP_MODE (ColorGroup.Loop),
        @SerializedName("LOOP_DISABLED") LOOP_DISABLED (ColorGroup.Loop),
        @SerializedName("LOOP_ENABLED") LOOP_ENABLED (ColorGroup.Loop),
        @SerializedName("RECONNECT") RECONNECT (ColorGroup.Pump),
        @SerializedName("DISCONNECT") DISCONNECT (ColorGroup.Pump),
        @SerializedName("RESUME") RESUME (ColorGroup.Loop),
        @SerializedName("SUSPEND") SUSPEND (ColorGroup.Loop),
        @SerializedName("HW_PUMP_ALLOWED") HW_PUMP_ALLOWED (ColorGroup.Pump),
        @SerializedName("CLEAR_PAIRING_KEYS") CLEAR_PAIRING_KEYS (ColorGroup.Pump),
        @SerializedName("ACCEPTS_TEMP_BASAL") ACCEPTS_TEMP_BASAL (ColorGroup.InsulinTreatment),
        @SerializedName("CANCEL_TEMP_BASAL") CANCEL_TEMP_BASAL (ColorGroup.InsulinTreatment),
        @SerializedName("CANCEL_EXTENDED_BOLUS") CANCEL_EXTENDED_BOLUS (ColorGroup.InsulinTreatment),
        @SerializedName("CANCEL_TT") CANCEL_TT (ColorGroup.TT),
        @SerializedName("CAREPORTAL") CAREPORTAL (ColorGroup.Careportal),
        @SerializedName("CALIBRATION") CALIBRATION (ColorGroup.Careportal),
        @SerializedName("PRIME_BOLUS") PRIME_BOLUS (ColorGroup.Careportal),
        @SerializedName("TREATMENT") TREATMENT (ColorGroup.InsulinTreatment),
        @SerializedName("CAREPORTAL_NS_REFRESH") CAREPORTAL_NS_REFRESH (ColorGroup.Aaps),
        @SerializedName("PROFILE_SWITCH_NS_REFRESH") PROFILE_SWITCH_NS_REFRESH (ColorGroup.Aaps),
        @SerializedName("TREATMENTS_NS_REFRESH") TREATMENTS_NS_REFRESH (ColorGroup.Aaps),
        @SerializedName("TT_NS_REFRESH") TT_NS_REFRESH (ColorGroup.Aaps),
        @SerializedName("AUTOMATION_REMOVED") AUTOMATION_REMOVED (ColorGroup.Aaps),
        @SerializedName("BG_REMOVED") BG_REMOVED (ColorGroup.Careportal),
        @SerializedName("CAREPORTAL_REMOVED") CAREPORTAL_REMOVED (ColorGroup.Careportal),
        @SerializedName("EXTENDED_BOLUS_REMOVED") EXTENDED_BOLUS_REMOVED (ColorGroup.InsulinTreatment),
        @SerializedName("FOOD_REMOVED") FOOD_REMOVED (ColorGroup.Careportal),
        @SerializedName("PROFILE_REMOVED") PROFILE_REMOVED (ColorGroup.Profile),
        @SerializedName("PROFILE_SWITCH_REMOVED") PROFILE_SWITCH_REMOVED (ColorGroup.Profile),
        @SerializedName("RESTART_EVENTS_REMOVED") RESTART_EVENTS_REMOVED (ColorGroup.Aaps),
        @SerializedName("TREATMENT_REMOVED") TREATMENT_REMOVED (ColorGroup.InsulinTreatment),
        @SerializedName("TT_REMOVED") TT_REMOVED (ColorGroup.TT),
        @SerializedName("NS_PAUSED") NS_PAUSED (ColorGroup.Aaps),
        @SerializedName("NS_RESUME") NS_RESUME (ColorGroup.Aaps),
        @SerializedName("NS_QUEUE_CLEARED") NS_QUEUE_CLEARED (ColorGroup.Aaps),
        @SerializedName("NS_SETTINGS_COPIED") NS_SETTINGS_COPIED (ColorGroup.Aaps),
        @SerializedName("ERROR_DIALOG_OK") ERROR_DIALOG_OK (ColorGroup.Aaps),
        @SerializedName("ERROR_DIALOG_MUTE") ERROR_DIALOG_MUTE (ColorGroup.Aaps),
        @SerializedName("ERROR_DIALOG_MUTE_5MIN") ERROR_DIALOG_MUTE_5MIN (ColorGroup.Aaps),
        @SerializedName("OBJECTIVE_STARTED") OBJECTIVE_STARTED (ColorGroup.Aaps),
        @SerializedName("OBJECTIVE_UNSTARTED") OBJECTIVE_UNSTARTED (ColorGroup.Aaps),
        @SerializedName("OBJECTIVES_SKIPPED") OBJECTIVES_SKIPPED (ColorGroup.Aaps),
        @SerializedName("STAT_RESET") STAT_RESET (ColorGroup.Aaps),
        @SerializedName("DELETE_LOGS") DELETE_LOGS (ColorGroup.Aaps),
        @SerializedName("DELETE_FUTURE_TREATMENTS") DELETE_FUTURE_TREATMENTS (ColorGroup.Aaps),
        @SerializedName("EXPORT_SETTINGS") EXPORT_SETTINGS (ColorGroup.Aaps),
        @SerializedName("IMPORT_SETTINGS") IMPORT_SETTINGS (ColorGroup.Aaps),
        @SerializedName("RESET_DATABASES") RESET_DATABASES (ColorGroup.Aaps),
        @SerializedName("EXPORT_DATABASES") EXPORT_DATABASES (ColorGroup.Aaps),
        @SerializedName("IMPORT_DATABASES") IMPORT_DATABASES (ColorGroup.Aaps),
        @SerializedName("OTP_EXPORT") OTP_EXPORT (ColorGroup.Aaps),
        @SerializedName("OTP_RESET") OTP_RESET (ColorGroup.Aaps),
        @SerializedName("SMS_BASAL") SMS_BASAL (ColorGroup.InsulinTreatment),
        @SerializedName("SMS_BOLUS") SMS_BOLUS (ColorGroup.InsulinTreatment),
        @SerializedName("SMS_CAL") SMS_CAL (ColorGroup.Careportal),
        @SerializedName("SMS_CARBS") SMS_CARBS (ColorGroup.CarbTreatment),
        @SerializedName("SMS_EXTENDED_BOLUS") SMS_EXTENDED_BOLUS (ColorGroup.InsulinTreatment),
        @SerializedName("SMS_LOOP_DISABLED") SMS_LOOP_DISABLED (ColorGroup.Loop),
        @SerializedName("SMS_LOOP_ENABLED") SMS_LOOP_ENABLED (ColorGroup.Loop),
        @SerializedName("SMS_LOOP_RESUME") SMS_LOOP_RESUME (ColorGroup.Loop),
        @SerializedName("SMS_LOOP_SUSPEND") SMS_LOOP_SUSPEND (ColorGroup.Loop),
        @SerializedName("SMS_PROFILE") SMS_PROFILE (ColorGroup.Profile),
        @SerializedName("SMS_PUMP_CONNECT") SMS_PUMP_CONNECT (ColorGroup.Pump),
        @SerializedName("SMS_PUMP_DISCONNECT") SMS_PUMP_DISCONNECT (ColorGroup.Pump),
        @SerializedName("SMS_SMS") SMS_SMS (ColorGroup.Aaps),
        @SerializedName("SMS_TT") SMS_TT (ColorGroup.TT),
        @SerializedName("TT_DELETED_FROM_NS") TT_DELETED_FROM_NS (ColorGroup.TT),
        @SerializedName("CAREPORTAL_DELETED_FROM_NS") CAREPORTAL_DELETED_FROM_NS (ColorGroup.Careportal),
        @SerializedName("CAREPORTAL_FROM_NS") CAREPORTAL_FROM_NS (ColorGroup.Careportal),
        @SerializedName("FOOD_FROM_NS") FOOD_FROM_NS (ColorGroup.Careportal),
        @SerializedName("TT_FROM_NS") TT_FROM_NS (ColorGroup.TT),
        @SerializedName("TT_CANCELED_FROM_NS") TT_CANCELED_FROM_NS (ColorGroup.TT),
        @SerializedName("EXPORT_CSV") EXPORT_CSV (ColorGroup.Aaps),
        @SerializedName("UNKNOWN") UNKNOWN (ColorGroup.Aaps)
        ;

        companion object {
            fun fromString(source: String?) = values().firstOrNull { it.name == source } ?: UNKNOWN
        }
    }
    data class ValueWithUnit (val dValue: Double=0.0, val iValue: Int=0, val lValue: Long=0, val sValue: String="", val unit: Units=Units.None, val condition:Boolean=true){
        constructor(dvalue: Double, unit: Units, condition:Boolean = true) : this(dvalue, 0, 0, "", unit, condition)
        constructor(ivalue: Int, unit: Units, condition:Boolean = true) : this(0.0, ivalue, 0, "", unit, condition)
        constructor(lvalue: Long, unit: Units, condition:Boolean = true) : this(0.0,0, lvalue, "", unit, condition)
        constructor(svalue: String, unit:Units) : this(0.0,0, 0, svalue, unit, svalue != "")
        constructor(dvalue: Double, unit:String) : this(dvalue,0, 0, "", Units.fromText(unit))
        constructor(rStringRef: Int, nbParam: Long) : this(0.0, rStringRef, nbParam, "", Units.R_String, !rStringRef.equals(0))             // additionnal constructors for formated strings with additional values as parameters (define number of parameters as long

        fun value() : Any {
            if (sValue != "") return sValue
            if (!dValue.equals(0.0)) return dValue
            if (!iValue.equals(0)) return iValue
            return lValue
        }
    }
    enum class Units(val text: String) {
        @SerializedName("None") None (""),                              //Int or String
        @SerializedName("Mg_Dl") Mg_Dl ("mg/dl"),                       //Double
        @SerializedName("Mmol_L") Mmol_L ("mmol"),                      //Double
        @SerializedName("Timestamp") Timestamp("Timestamp"),            //long
        @SerializedName("U") U ("U"),                                   //Double
        @SerializedName("U_H") U_H ("U/h"),                             //Double
        @SerializedName("G") G ("g"),                                   //Int
        @SerializedName("M") M ("m"),                                   //Int
        @SerializedName("H") H ("h"),                                   //Int
        @SerializedName("Percent") Percent ("%"),                       //Int
        @SerializedName("TherapyEvent") TherapyEvent ("TherapyEvent"),  //String (All enum key translated by Translator function, mainly TherapyEvent)
        @SerializedName("R_String") R_String ("R.string")               //Int
        ;

        companion object {
            fun fromString(unit: String?) = values().firstOrNull { it.name == unit } ?: None
            fun fromText(unit: String?) = values().firstOrNull { it.text == unit } ?: None
        }
    }

    enum class ColorGroup() {
        InsulinTreatment,
        CarbTreatment,
        TT,
        Profile,
        Loop,
        Careportal,
        Pump,
        Aaps
    }
}