package app.aaps.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.aaps.database.entities.interfaces.DBEntry
import app.aaps.database.entities.interfaces.DBEntryWithTime
import java.util.TimeZone

@Entity(
    tableName = TABLE_USER_ENTRY,
    indices = [
        Index("source"),
        Index("timestamp")
    ]
)
data class UserEntry(
    @PrimaryKey(autoGenerate = true)
    override var id: Long = 0L,
    override var timestamp: Long,
    override var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var action: Action,
    var source: Sources,
    var note: String,
    var values: List<@JvmSuppressWildcards ValueWithUnit>
) : DBEntry, DBEntryWithTime {

    enum class Action {
        BOLUS,
        BOLUS_CALCULATOR_RESULT,
        BOLUS_CALCULATOR_RESULT_REMOVED,
        SMB,
        BOLUS_ADVISOR,
        EXTENDED_BOLUS,
        SUPERBOLUS_TBR,
        CARBS,
        EXTENDED_CARBS,
        TEMP_BASAL,
        TT,
        NEW_INSULIN,
        STORE_INSULIN,
        CHANGE_PUMP_INSULIN,
        CHANGE_INSULIN_CONCENTRATION,
        NEW_PROFILE,
        CLONE_PROFILE,
        STORE_PROFILE,
        PROFILE_SWITCH,
        PROFILE_SWITCH_CLONED,
        CLOSED_LOOP_MODE,
        LGS_LOOP_MODE,
        OPEN_LOOP_MODE,
        LOOP_DISABLED,
        LOOP_RESUME,
        LOOP_CHANGE,
        PUMP_RUNNING,
        LOOP_REMOVED,
        RECONNECT,
        DISCONNECT,
        RESUME,
        SUSPEND,
        HW_PUMP_ALLOWED,
        CLEAR_PAIRING_KEYS,
        ACCEPTS_TEMP_BASAL,
        CANCEL_TEMP_BASAL,
        CANCEL_BOLUS,
        CANCEL_EXTENDED_BOLUS,
        CANCEL_TT,
        CAREPORTAL,
        SENSOR_LOCATION,
        SITE_CHANGE,
        SITE_LOCATION,
        RESERVOIR_CHANGE,
        CALIBRATION,
        PRIME_BOLUS,
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
        INSULIN_REMOVED,
        PROFILE_REMOVED,
        PROFILE_SWITCH_REMOVED,
        RESTART_EVENTS_REMOVED,
        TREATMENT_REMOVED,
        BOLUS_REMOVED,
        CARBS_REMOVED,
        TEMP_BASAL_REMOVED,
        TT_REMOVED,
        NS_PAUSED,
        NS_RESUME,
        NS_QUEUE_CLEARED,
        NS_SETTINGS_COPIED,
        ERROR_DIALOG_OK,
        ERROR_DIALOG_MUTE,
        ERROR_DIALOG_MUTE_5MIN,
        OBJECTIVE_STARTED,
        OBJECTIVE_UNSTARTED,
        OBJECTIVES_SKIPPED,
        STAT_RESET,
        DELETE_LOGS,
        DELETE_FUTURE_TREATMENTS,
        EXPORT_SETTINGS,
        IMPORT_SETTINGS,
        SELECT_DIRECTORY,
        RESET_DATABASES,
        RESET_APS_RESULTS,
        CLEANUP_DATABASES,
        EXPORT_DATABASES,
        IMPORT_DATABASES,
        OTP_EXPORT,
        OTP_RESET,
        STOP_SMS,
        FOOD,
        EXPORT_CSV,
        START_AAPS,
        EXIT_AAPS,
        PLUGIN_ENABLED,
        PLUGIN_DISABLED,
        RUNNING_MODE,
        RUNNING_MODE_REMOVED,
        RUNNING_MODE_UPDATED,
        UNKNOWN
        ;
    }

    enum class Sources {
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
        ConcentrationDialog,
        FillDialog,
        SiteRotationDialog,
        BgCheck,
        SensorInsert,
        BatteryChange,
        Note,
        Exercise,
        Question,
        Announcement,
        SettingsExport,
        Actions,            //From Actions plugin
        Automation,         //From Automation plugin
        Autotune,           //From Autotune plugin
        BG,                 //From BG plugin => Add One Source per BG Source for Calibration or Sensor Change
        Aidex,
        Dexcom,
        Eversense,
        Glimp,
        MM640g,
        NSClientSource,
        PocTech,
        Tomato,
        Glunovo,
        Intelligo,
        Xdrip,
        Insulin,            //From Insulin plugin
        LocalProfile,       //From LocalProfile plugin
        Loop,               //From Loop plugin
        Maintenance,        //From Maintenance plugin
        NSClient,           //From NSClient plugin
        NSProfile,          //From NSProfile plugin
        Objectives,         //From Objectives plugin
        Pump,               //To update with one Source per pump
        Dana,               //Only one UserEntry in Common module Dana
        DanaR,
        DanaRC,
        DanaRv2,
        DanaRS,
        DanaI,
        DiaconnG8,
        Insight,
        Combo,
        Medtronic,
        Omnipod,            //No entry currently
        OmnipodEros,
        OmnipodDash,        //No entry currently
        EOPatch2,
        Equil,
        Medtrum,
        MDI,
        VirtualPump,
        Random,
        SMS,                //From SMS plugin
        Treatments,         //From Treatments plugin
        Wear,               //From Wear plugin
        Food,               //From Food plugin
        ConfigBuilder,      //From ConfigBuilder Plugin
        Overview,           //From OverViewPlugin
        Ottai,              //From Ottai plugin
        SyaiTag,            //From Syai Tag plugin
        SiBionic,
        Sino,
        Stats,              //From Stat Activity
        Aaps,               // MainApp
        BgFragment,
        Garmin,
        Database,           //for PersistenceLayer
        Unknown,            //if necessary
        ;

        companion object {

            fun fromString(source: String?) = entries.firstOrNull { it.name == source } ?: Unknown
        }
    }
}
