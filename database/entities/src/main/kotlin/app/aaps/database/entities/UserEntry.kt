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
    var values: List<@JvmSuppressWildcards ValueWithUnit?>
) : DBEntry, DBEntryWithTime {

    enum class Action(val colorGroup: ColorGroup) {
        BOLUS(ColorGroup.InsulinTreatment),
        BOLUS_CALCULATOR_RESULT(ColorGroup.InsulinTreatment),
        BOLUS_CALCULATOR_RESULT_REMOVED(ColorGroup.Aaps),
        SMB(ColorGroup.InsulinTreatment),
        BOLUS_ADVISOR(ColorGroup.InsulinTreatment),
        EXTENDED_BOLUS(ColorGroup.InsulinTreatment),
        SUPERBOLUS_TBR(ColorGroup.InsulinTreatment),
        CARBS(ColorGroup.CarbTreatment),
        EXTENDED_CARBS(ColorGroup.CarbTreatment),
        TEMP_BASAL(ColorGroup.BasalTreatment),
        TT(ColorGroup.TT),
        NEW_PROFILE(ColorGroup.Profile),
        CLONE_PROFILE(ColorGroup.Profile),
        STORE_PROFILE(ColorGroup.Profile),
        PROFILE_SWITCH(ColorGroup.Profile),
        PROFILE_SWITCH_CLONED(ColorGroup.Profile),
        CLOSED_LOOP_MODE(ColorGroup.Loop),
        LGS_LOOP_MODE(ColorGroup.Loop),
        OPEN_LOOP_MODE(ColorGroup.Loop),
        LOOP_DISABLED(ColorGroup.Loop),
        LOOP_ENABLED(ColorGroup.Loop),
        LOOP_CHANGE(ColorGroup.Loop),
        LOOP_REMOVED(ColorGroup.Loop),
        RECONNECT(ColorGroup.Pump),
        DISCONNECT(ColorGroup.Pump),
        RESUME(ColorGroup.Loop),
        SUSPEND(ColorGroup.Loop),
        HW_PUMP_ALLOWED(ColorGroup.Pump),
        CLEAR_PAIRING_KEYS(ColorGroup.Pump),
        ACCEPTS_TEMP_BASAL(ColorGroup.BasalTreatment),
        CANCEL_TEMP_BASAL(ColorGroup.BasalTreatment),
        CANCEL_BOLUS(ColorGroup.InsulinTreatment),
        CANCEL_EXTENDED_BOLUS(ColorGroup.InsulinTreatment),
        CANCEL_TT(ColorGroup.TT),
        CAREPORTAL(ColorGroup.Careportal),
        SITE_CHANGE(ColorGroup.Pump),
        RESERVOIR_CHANGE(ColorGroup.Pump),
        CALIBRATION(ColorGroup.Careportal),
        PRIME_BOLUS(ColorGroup.Pump),
        TREATMENT(ColorGroup.InsulinTreatment),
        CAREPORTAL_NS_REFRESH(ColorGroup.Careportal),
        PROFILE_SWITCH_NS_REFRESH(ColorGroup.Profile),
        TREATMENTS_NS_REFRESH(ColorGroup.InsulinTreatment),
        TT_NS_REFRESH(ColorGroup.TT),
        AUTOMATION_REMOVED(ColorGroup.Aaps),
        BG_REMOVED(ColorGroup.Aaps),
        CAREPORTAL_REMOVED(ColorGroup.Careportal),
        EXTENDED_BOLUS_REMOVED(ColorGroup.InsulinTreatment),
        FOOD_REMOVED(ColorGroup.CarbTreatment),
        PROFILE_REMOVED(ColorGroup.Profile),
        PROFILE_SWITCH_REMOVED(ColorGroup.Profile),
        RESTART_EVENTS_REMOVED(ColorGroup.Aaps),
        TREATMENT_REMOVED(ColorGroup.InsulinTreatment),
        BOLUS_REMOVED(ColorGroup.InsulinTreatment),
        CARBS_REMOVED(ColorGroup.CarbTreatment),
        TEMP_BASAL_REMOVED(ColorGroup.BasalTreatment),
        TT_REMOVED(ColorGroup.TT),
        NS_PAUSED(ColorGroup.Aaps),
        NS_RESUME(ColorGroup.Aaps),
        NS_QUEUE_CLEARED(ColorGroup.Aaps),
        NS_SETTINGS_COPIED(ColorGroup.Aaps),
        ERROR_DIALOG_OK(ColorGroup.Aaps),
        ERROR_DIALOG_MUTE(ColorGroup.Aaps),
        ERROR_DIALOG_MUTE_5MIN(ColorGroup.Aaps),
        OBJECTIVE_STARTED(ColorGroup.Aaps),
        OBJECTIVE_UNSTARTED(ColorGroup.Aaps),
        OBJECTIVES_SKIPPED(ColorGroup.Aaps),
        STAT_RESET(ColorGroup.Aaps),
        DELETE_LOGS(ColorGroup.Aaps),
        DELETE_FUTURE_TREATMENTS(ColorGroup.Aaps),
        EXPORT_SETTINGS(ColorGroup.Aaps),
        IMPORT_SETTINGS(ColorGroup.Aaps),
        RESET_DATABASES(ColorGroup.Aaps),
        CLEANUP_DATABASES(ColorGroup.Aaps),
        EXPORT_DATABASES(ColorGroup.Aaps),
        IMPORT_DATABASES(ColorGroup.Aaps),
        OTP_EXPORT(ColorGroup.Aaps),
        OTP_RESET(ColorGroup.Aaps),
        STOP_SMS(ColorGroup.Aaps),
        FOOD(ColorGroup.CarbTreatment),
        EXPORT_CSV(ColorGroup.Aaps),
        START_AAPS(ColorGroup.Aaps),
        EXIT_AAPS(ColorGroup.Aaps),
        PLUGIN_ENABLED(ColorGroup.Aaps),
        PLUGIN_DISABLED(ColorGroup.Aaps),
        UNKNOWN(ColorGroup.Aaps)
        ;

        companion object {

            fun fromString(source: String?) = values().firstOrNull { it.name == source } ?: UNKNOWN
        }
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
        Medtrum,
        MDI,
        VirtualPump,
        SMS,                //From SMS plugin
        Treatments,         //From Treatments plugin
        Wear,               //From Wear plugin
        Food,               //From Food plugin
        ConfigBuilder,      //From ConfigBuilder Plugin
        Overview,           //From OverViewPlugin
        Stats,               //From Stat Activity
        Aaps,               // MainApp
        Unknown             //if necessary
        ;

        companion object {

            fun fromString(source: String?) = values().firstOrNull { it.name == source } ?: Unknown
        }
    }

    enum class ColorGroup {
        InsulinTreatment,
        BasalTreatment,
        CarbTreatment,
        TT,
        Profile,
        Loop,
        Careportal,
        Pump,
        Aaps
    }
}
