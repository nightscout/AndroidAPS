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
    var action: Action,
    var source: Sources,
    var note: String,
    var values: List<XXXValueWithUnit?>
) : DBEntry, DBEntryWithTime {
    enum class Action (val colorGroup: ColorGroup) {
        BOLUS (ColorGroup.InsulinTreatment),
        SMB (ColorGroup.InsulinTreatment),
        BOLUS_ADVISOR (ColorGroup.InsulinTreatment),
        EXTENDED_BOLUS (ColorGroup.InsulinTreatment),
        SUPERBOLUS_TBR (ColorGroup.InsulinTreatment),
        CARBS (ColorGroup.CarbTreatment),
        EXTENDED_CARBS (ColorGroup.CarbTreatment),
        TEMP_BASAL (ColorGroup.InsulinTreatment),
        TT (ColorGroup.TT),
        NEW_PROFILE (ColorGroup.Profile),
        CLONE_PROFILE (ColorGroup.Profile),
        STORE_PROFILE (ColorGroup.Profile),
        PROFILE_SWITCH (ColorGroup.Profile),
        PROFILE_SWITCH_CLONED (ColorGroup.Profile),
        CLOSED_LOOP_MODE (ColorGroup.Loop),
        LGS_LOOP_MODE (ColorGroup.Loop),
        OPEN_LOOP_MODE (ColorGroup.Loop),
        LOOP_DISABLED (ColorGroup.Loop),
        LOOP_ENABLED (ColorGroup.Loop),
        RECONNECT (ColorGroup.Pump),
        DISCONNECT (ColorGroup.Pump),
        RESUME (ColorGroup.Loop),
        SUSPEND (ColorGroup.Loop),
        HW_PUMP_ALLOWED (ColorGroup.Pump),
        CLEAR_PAIRING_KEYS (ColorGroup.Pump),
        ACCEPTS_TEMP_BASAL (ColorGroup.InsulinTreatment),
        CANCEL_TEMP_BASAL (ColorGroup.InsulinTreatment),
        CANCEL_EXTENDED_BOLUS (ColorGroup.InsulinTreatment),
        CANCEL_TT (ColorGroup.TT),
        CAREPORTAL (ColorGroup.Careportal),
        CALIBRATION (ColorGroup.Careportal),
        PRIME_BOLUS (ColorGroup.Careportal),
        TREATMENT (ColorGroup.InsulinTreatment),
        CAREPORTAL_NS_REFRESH (ColorGroup.Aaps),
        PROFILE_SWITCH_NS_REFRESH (ColorGroup.Aaps),
        TREATMENTS_NS_REFRESH (ColorGroup.Aaps),
        TT_NS_REFRESH (ColorGroup.Aaps),
        AUTOMATION_REMOVED (ColorGroup.Aaps),
        BG_REMOVED (ColorGroup.Careportal),
        CAREPORTAL_REMOVED (ColorGroup.Careportal),
        EXTENDED_BOLUS_REMOVED (ColorGroup.InsulinTreatment),
        FOOD_REMOVED (ColorGroup.Careportal),
        PROFILE_REMOVED (ColorGroup.Profile),
        PROFILE_SWITCH_REMOVED (ColorGroup.Profile),
        RESTART_EVENTS_REMOVED (ColorGroup.Aaps),
        TREATMENT_REMOVED (ColorGroup.InsulinTreatment),
        TT_REMOVED (ColorGroup.TT),
        NS_PAUSED (ColorGroup.Aaps),
        NS_RESUME (ColorGroup.Aaps),
        NS_QUEUE_CLEARED (ColorGroup.Aaps),
        NS_SETTINGS_COPIED (ColorGroup.Aaps),
        ERROR_DIALOG_OK (ColorGroup.Aaps),
        ERROR_DIALOG_MUTE (ColorGroup.Aaps),
        ERROR_DIALOG_MUTE_5MIN (ColorGroup.Aaps),
        OBJECTIVE_STARTED (ColorGroup.Aaps),
        OBJECTIVE_UNSTARTED (ColorGroup.Aaps),
        OBJECTIVES_SKIPPED (ColorGroup.Aaps),
        STAT_RESET (ColorGroup.Aaps),
        DELETE_LOGS (ColorGroup.Aaps),
        DELETE_FUTURE_TREATMENTS (ColorGroup.Aaps),
        EXPORT_SETTINGS (ColorGroup.Aaps),
        IMPORT_SETTINGS (ColorGroup.Aaps),
        RESET_DATABASES (ColorGroup.Aaps),
        EXPORT_DATABASES (ColorGroup.Aaps),
        IMPORT_DATABASES (ColorGroup.Aaps),
        OTP_EXPORT (ColorGroup.Aaps),
        OTP_RESET (ColorGroup.Aaps),
        STOP_SMS (ColorGroup.Aaps),
        FOOD (ColorGroup.Careportal),
        EXPORT_CSV (ColorGroup.Aaps),
        UNKNOWN (ColorGroup.Aaps)
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
        constructor(source: Sources) : this(0.0,0, 0, source.name, Units.Source, true)
        constructor(dvalue: Double, unit:String, condition:Boolean = true) : this(dvalue,0, 0, "", Units.fromText(unit), condition)
        constructor(rStringRef: Int, nbParam: Long) : this(0.0, rStringRef, nbParam, "", Units.R_String, !rStringRef.equals(0))             // additionnal constructors for formated strings with additional values as parameters (define number of parameters as long

        fun value() : Any {
            if (sValue != "") return sValue
            if (!dValue.equals(0.0)) return dValue
            if (!iValue.equals(0)) return iValue
            return lValue
        }
    }
    enum class Units(val text: String) {
        None (""),                              //Int or String
        Mg_Dl ("mg/dl"),                        //Double
        Mmol_L ("mmol"),                        //Double
        Timestamp("Timestamp"),                 //long
        U ("U"),                                //Double
        U_H ("U/h"),                            //Double
        G ("g"),                                //Int
        M ("m"),                                //Int
        H ("h"),                                //Int
        Percent ("%"),                          //Int
        TherapyEvent ("TherapyEvent"),          //String (All enum key translated by Translator function, mainly The
        R_String ("R.string"),                  //Int
        Source ("Source")                       //String

        ;

        companion object {
            fun fromString(unit: String?) = values().firstOrNull { it.name == unit } ?: None
            fun fromText(unit: String?) = values().firstOrNull { it.text == unit } ?: None
        }
    }
    enum class Sources() {
        TreatmentDialog,
        InsulinDialog,
        CarbDialog,
        WizardDialog,
        QuickWizard,
        ExtendedBolusDialog,
        TTDialog,
        ProfileSwitchDialog,
        LoopDialog,
        TempBasalDialog,
        CalibrationDialog,
        FillDialog,
        BgCheck,
        SensorInsert,
        BatteryChange,
        Note,
        Exercise,
        Question,
        Announcement,
        Actions,            //From Actions plugin
        Automation,         //From Automation plugin
        BG,                 // From BG plugin
        LocalProfile,       //From LocalProfile plugin
        Loop,               //From Loop plugin
        Maintenance,        //From Maintenance plugin
        NSClient,           //From NSClient plugin
        NSProfile,          //From NSProfile plugin
        Objectives,         //From Objectives plugin
        Pump,               //From Pump plugin
        SMS,                //From SMS plugin
        Treatments,         //From Treatments plugin
        Wear,               //From Wear plugin
        Food,               //From Food plugin
        Unknown             //if necessary
        ;

        companion object {
            fun fromString(source: String?) = values().firstOrNull { it.name == source } ?: Unknown
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