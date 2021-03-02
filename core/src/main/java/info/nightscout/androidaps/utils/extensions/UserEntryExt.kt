package info.nightscout.androidaps.utils.extensions

import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.UserEntry

fun UserEntry.Action.stringId(): Int {
    return when {
        this == UserEntry.Action.BOLUS                      -> R.string.uel_bolus
        this == UserEntry.Action.BOLUS_WIZARD               -> R.string.uel_bolus_wizard
        this == UserEntry.Action.BOLUS_ADVISOR              -> R.string.uel_bolus_advisor
        this == UserEntry.Action.BOLUS_RECORD               -> R.string.uel_bolus_record
        this == UserEntry.Action.EXTENDED_BOLUS             -> R.string.uel_extended_bolus
        this == UserEntry.Action.SUPERBOLUS_TBR             -> R.string.uel_superbolus_tbr
        this == UserEntry.Action.CARBS                      -> R.string.uel_carbs
        this == UserEntry.Action.EXTENDED_CARBS             -> R.string.uel_extended_carbs
        this == UserEntry.Action.TEMP_BASAL                 -> R.string.uel_temp_basal
        this == UserEntry.Action.TT                         -> R.string.uel_tt
        this == UserEntry.Action.TT_ACTIVITY                -> R.string.uel_tt_activity
        this == UserEntry.Action.TT_EATING_SOON             -> R.string.uel_tt_eating_soon
        this == UserEntry.Action.TT_HYPO                    -> R.string.uel_tt_hypo
        this == UserEntry.Action.NEW_PROFILE                -> R.string.uel_new_profile
        this == UserEntry.Action.CLONE_PROFILE              -> R.string.uel_clone_profile
        this == UserEntry.Action.STORE_PROFILE              -> R.string.uel_store_profile
        this == UserEntry.Action.PROFILE_SWITCH             -> R.string.uel_profile_switch
        this == UserEntry.Action.PROFILE_SWITCH_CLONED      -> R.string.uel_profile_switch_cloned
        this == UserEntry.Action.CLOSED_LOOP_MODE           -> R.string.uel_closed_loop_mode
        this == UserEntry.Action.LGS_LOOP_MODE              -> R.string.uel_lgs_loop_mode
        this == UserEntry.Action.OPEN_LOOP_MODE             -> R.string.uel_open_loop_mode
        this == UserEntry.Action.LOOP_DISABLED              -> R.string.uel_loop_disabled
        this == UserEntry.Action.LOOP_ENABLED               -> R.string.uel_loop_enabled
        this == UserEntry.Action.RECONNECT                  -> R.string.uel_reconnect
        this == UserEntry.Action.DISCONNECT_15M             -> R.string.uel_disconnect_15m
        this == UserEntry.Action.DISCONNECT_30M             -> R.string.uel_disconnect_30m
        this == UserEntry.Action.DISCONNECT_1H              -> R.string.uel_disconnect_1h
        this == UserEntry.Action.DISCONNECT_2H              -> R.string.uel_disconnect_2h
        this == UserEntry.Action.DISCONNECT_3H              -> R.string.uel_disconnect_3h
        this == UserEntry.Action.RESUME                     -> R.string.uel_resume
        this == UserEntry.Action.SUSPEND_1H                 -> R.string.uel_suspend_1h
        this == UserEntry.Action.SUSPEND_2H                 -> R.string.uel_suspend_2h
        this == UserEntry.Action.SUSPEND_3H                 -> R.string.uel_suspend_3h
        this == UserEntry.Action.SUSPEND_10H                -> R.string.uel_suspend_10h
        this == UserEntry.Action.HW_PUMP_ALLOWED            -> R.string.uel_hw_pump_allowed
        this == UserEntry.Action.CLEAR_PAIRING_KEYS         -> R.string.uel_clear_pairing_keys
        this == UserEntry.Action.ACCEPTS_TEMP_BASAL         -> R.string.uel_accepts_temp_basal
        this == UserEntry.Action.CANCEL_TEMP_BASAL          -> R.string.uel_cancel_temp_basal
        this == UserEntry.Action.CANCEL_EXTENDED_BOLUS      -> R.string.uel_cancel_extended_bolus
        this == UserEntry.Action.CANCEL_TT                  -> R.string.uel_cancel_tt
        this == UserEntry.Action.CAREPORTAL                 -> R.string.uel_careportal
        this == UserEntry.Action.CALIBRATION                -> R.string.uel_calibration
        this == UserEntry.Action.INSULIN_CHANGE             -> R.string.uel_insulin_change
        this == UserEntry.Action.PRIME_BOLUS                -> R.string.uel_prime_bolus
        this == UserEntry.Action.SITE_CHANGE                -> R.string.uel_site_change
        this == UserEntry.Action.TREATMENT                  -> R.string.uel_treatment
        this == UserEntry.Action.CAREPORTAL_NS_REFRESH      -> R.string.uel_careportal_ns_refresh
        this == UserEntry.Action.PROFILE_SWITCH_NS_REFRESH  -> R.string.uel_profile_switch_ns_refresh
        this == UserEntry.Action.TREATMENTS_NS_REFRESH      -> R.string.uel_treatments_ns_refresh
        this == UserEntry.Action.TT_NS_REFRESH              -> R.string.uel_tt_ns_refresh
        this == UserEntry.Action.AUTOMATION_REMOVED         -> R.string.uel_automation_removed
        this == UserEntry.Action.BG_REMOVED                 -> R.string.uel_bg_removed
        this == UserEntry.Action.CAREPORTAL_REMOVED         -> R.string.uel_careportal_removed
        this == UserEntry.Action.EXTENDED_BOLUS_REMOVED     -> R.string.uel_extended_bolus_removed
        this == UserEntry.Action.FOOD_REMOVED               -> R.string.uel_food_removed
        this == UserEntry.Action.PROFILE_REMOVED            -> R.string.uel_profile_removed
        this == UserEntry.Action.PROFILE_SWITCH_REMOVED     -> R.string.uel_profile_switch_removed
        this == UserEntry.Action.RESTART_EVENTS_REMOVED     -> R.string.uel_restart_events_removed
        this == UserEntry.Action.TREATMENT_REMOVED          -> R.string.uel_treatment_removed
        this == UserEntry.Action.TT_REMOVED                 -> R.string.uel_tt_removed
        this == UserEntry.Action.NS_PAUSED                  -> R.string.uel_ns_paused
        this == UserEntry.Action.NS_QUEUE_CLEARED           -> R.string.uel_ns_queue_cleared
        this == UserEntry.Action.NS_SETTINGS_COPIED         -> R.string.uel_ns_settings_copied
        this == UserEntry.Action.ERROR_DIALOG_OK            -> R.string.uel_error_dialog_ok
        this == UserEntry.Action.ERROR_DIALOG_MUTE          -> R.string.uel_error_dialog_mute
        this == UserEntry.Action.ERROR_DIALOG_MUTE_5MIN     -> R.string.uel_error_dialog_mute_5min
        this == UserEntry.Action.OBJECTIVE_STARTED          -> R.string.uel_objective_started
        this == UserEntry.Action.OBJECTIVE_UNSTARTED        -> R.string.uel_objective_unstarted
        this == UserEntry.Action.OBJECTIVES_SKIPPED         -> R.string.uel_objectives_skipped
        this == UserEntry.Action.STAT_RESET                 -> R.string.uel_stat_reset
        this == UserEntry.Action.DELETE_LOGS                -> R.string.uel_delete_logs
        this == UserEntry.Action.DELETE_FUTURE_TREATMENTS   -> R.string.uel_delete_future_treatments
        this == UserEntry.Action.EXPORT_SETTINGS            -> R.string.uel_export_settings
        this == UserEntry.Action.IMPORT_SETTINGS            -> R.string.uel_import_settings
        this == UserEntry.Action.RESET_DATABASES            -> R.string.uel_reset_databases
        this == UserEntry.Action.EXPORT_DATABASES           -> R.string.uel_export_databases
        this == UserEntry.Action.IMPORT_DATABASES           -> R.string.uel_import_databases
        this == UserEntry.Action.OTP_EXPORT                 -> R.string.uel_otp_export
        this == UserEntry.Action.OTP_RESET                  -> R.string.uel_otp_reset
        this == UserEntry.Action.SMS_BASAL                  -> R.string.uel_sms_basal
        this == UserEntry.Action.SMS_BOLUS                  -> R.string.uel_sms_bolus
        this == UserEntry.Action.SMS_CAL                    -> R.string.uel_sms_cal
        this == UserEntry.Action.SMS_CARBS                  -> R.string.uel_sms_carbs
        this == UserEntry.Action.SMS_EXTENDED_BOLUS         -> R.string.uel_sms_extended_bolus
        this == UserEntry.Action.SMS_LOOP_DISABLED          -> R.string.uel_sms_loop_disabled
        this == UserEntry.Action.SMS_LOOP_ENABLED           -> R.string.uel_sms_loop_enabled
        this == UserEntry.Action.SMS_LOOP_RESUME            -> R.string.uel_sms_loop_resume
        this == UserEntry.Action.SMS_LOOP_SUSPEND           -> R.string.uel_sms_loop_suspend
        this == UserEntry.Action.SMS_PROFILE                -> R.string.uel_sms_profile
        this == UserEntry.Action.SMS_PUMP_CONNECT           -> R.string.uel_sms_pump_connect
        this == UserEntry.Action.SMS_PUMP_DISCONNECT        -> R.string.uel_sms_pump_disconnect
        this == UserEntry.Action.SMS_SMS                    -> R.string.uel_sms_sms
        this == UserEntry.Action.SMS_TT                     -> R.string.uel_sms_tt
        else                                                -> R.string.uel_unknown
    }
}


fun UserEntry.Units.stringId(): Int {
    return when {
        this == UserEntry.Units.Mg_Dl    -> R.string.mgdl
        this == UserEntry.Units.Mmol_L   -> R.string.mmol
        this == UserEntry.Units.U        -> R.string.insulin_unit_shortname
        this == UserEntry.Units.U_H      -> R.string.profile_ins_units_per_hour
        this == UserEntry.Units.G        -> R.string.shortgram
        this == UserEntry.Units.M        -> R.string.shortminute
        this == UserEntry.Units.H        -> R.string.shorthour
        this == UserEntry.Units.Percent  -> R.string.shortpercent
        this == UserEntry.Units.R_String -> R.string.formated_string
        else                             -> 0
    }
}

fun UserEntry.ValueWithUnit.toStringWithUnit(): String {
    /* need injection to maje convertion
    return when (this.unit) {
        UserEntry.Units.Timestamp -> dateUtil.dateAndTimeAndSecondsString(this.lValue)
        UserEntry.Units.CPEvent   -> translator.translate(this.sValue)
        UserEntry.Units.R_String  -> resourceHelper.gs(this.iValue)
        UserEntry.Units.Mg_Dl     -> DecimalFormatter.to0Decimal(this.dValue) + resourceHelper.gs(UserEntry.Units.Mg_Dl.stringId())
        UserEntry.Units.Mmol_L    -> DecimalFormatter.to1Decimal(this.dValue) + resourceHelper.gs(UserEntry.Units.Mmol_L.stringId())
        UserEntry.Units.G         -> DecimalFormatter.to0Decimal(this.dValue) + resourceHelper.gs(UserEntry.Units.G.stringId())
        else                      -> if (!this.value().equals(0) && !this.value().equals("")) { v.value().toString() + if (!this.unit.stringId().equals(0)) resourceHelper.gs(this.unit.stringId()) else "" } else ""
    } */
    return ""
}