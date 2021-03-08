package info.nightscout.androidaps.utils.extensions

import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.UserEntry.*

fun Action.stringId(): Int {
    return when (this) {
        Action.BOLUS                      -> R.string.uel_bolus
        Action.BOLUS_WIZARD               -> R.string.uel_bolus_wizard
        Action.BOLUS_ADVISOR              -> R.string.uel_bolus_advisor
        Action.BOLUS_RECORD               -> R.string.uel_bolus_record
        Action.EXTENDED_BOLUS             -> R.string.uel_extended_bolus
        Action.SUPERBOLUS_TBR             -> R.string.uel_superbolus_tbr
        Action.CARBS                      -> R.string.uel_carbs
        Action.EXTENDED_CARBS             -> R.string.uel_extended_carbs
        Action.TEMP_BASAL                 -> R.string.uel_temp_basal
        Action.TT                         -> R.string.uel_tt
        Action.TT_ACTIVITY                -> R.string.uel_tt_activity
        Action.TT_EATING_SOON             -> R.string.uel_tt_eating_soon
        Action.TT_HYPO                    -> R.string.uel_tt_hypo
        Action.NEW_PROFILE                -> R.string.uel_new_profile
        Action.CLONE_PROFILE              -> R.string.uel_clone_profile
        Action.STORE_PROFILE              -> R.string.uel_store_profile
        Action.PROFILE_SWITCH             -> R.string.uel_profile_switch
        Action.PROFILE_SWITCH_CLONED      -> R.string.uel_profile_switch_cloned
        Action.CLOSED_LOOP_MODE           -> R.string.uel_closed_loop_mode
        Action.LGS_LOOP_MODE              -> R.string.uel_lgs_loop_mode
        Action.OPEN_LOOP_MODE             -> R.string.uel_open_loop_mode
        Action.LOOP_DISABLED              -> R.string.uel_loop_disabled
        Action.LOOP_ENABLED               -> R.string.uel_loop_enabled
        Action.RECONNECT                  -> R.string.uel_reconnect
        Action.DISCONNECT_15M             -> R.string.uel_disconnect_15m
        Action.DISCONNECT_30M             -> R.string.uel_disconnect_30m
        Action.DISCONNECT_1H              -> R.string.uel_disconnect_1h
        Action.DISCONNECT_2H              -> R.string.uel_disconnect_2h
        Action.DISCONNECT_3H              -> R.string.uel_disconnect_3h
        Action.RESUME                     -> R.string.uel_resume
        Action.SUSPEND_1H                 -> R.string.uel_suspend_1h
        Action.SUSPEND_2H                 -> R.string.uel_suspend_2h
        Action.SUSPEND_3H                 -> R.string.uel_suspend_3h
        Action.SUSPEND_10H                -> R.string.uel_suspend_10h
        Action.HW_PUMP_ALLOWED            -> R.string.uel_hw_pump_allowed
        Action.CLEAR_PAIRING_KEYS         -> R.string.uel_clear_pairing_keys
        Action.ACCEPTS_TEMP_BASAL         -> R.string.uel_accepts_temp_basal
        Action.CANCEL_TEMP_BASAL          -> R.string.uel_cancel_temp_basal
        Action.CANCEL_EXTENDED_BOLUS      -> R.string.uel_cancel_extended_bolus
        Action.CANCEL_TT                  -> R.string.uel_cancel_tt
        Action.CAREPORTAL                 -> R.string.uel_careportal
        Action.CALIBRATION                -> R.string.uel_calibration
        Action.INSULIN_CHANGE             -> R.string.uel_insulin_change
        Action.PRIME_BOLUS                -> R.string.uel_prime_bolus
        Action.SITE_CHANGE                -> R.string.uel_site_change
        Action.TREATMENT                  -> R.string.uel_treatment
        Action.CAREPORTAL_NS_REFRESH      -> R.string.uel_careportal_ns_refresh
        Action.PROFILE_SWITCH_NS_REFRESH  -> R.string.uel_profile_switch_ns_refresh
        Action.TREATMENTS_NS_REFRESH      -> R.string.uel_treatments_ns_refresh
        Action.TT_NS_REFRESH              -> R.string.uel_tt_ns_refresh
        Action.AUTOMATION_REMOVED         -> R.string.uel_automation_removed
        Action.BG_REMOVED                 -> R.string.uel_bg_removed
        Action.CAREPORTAL_REMOVED         -> R.string.uel_careportal_removed
        Action.EXTENDED_BOLUS_REMOVED     -> R.string.uel_extended_bolus_removed
        Action.FOOD_REMOVED               -> R.string.uel_food_removed
        Action.PROFILE_REMOVED            -> R.string.uel_profile_removed
        Action.PROFILE_SWITCH_REMOVED     -> R.string.uel_profile_switch_removed
        Action.RESTART_EVENTS_REMOVED     -> R.string.uel_restart_events_removed
        Action.TREATMENT_REMOVED          -> R.string.uel_treatment_removed
        Action.TT_REMOVED                 -> R.string.uel_tt_removed
        Action.NS_PAUSED                  -> R.string.uel_ns_paused
        Action.NS_QUEUE_CLEARED           -> R.string.uel_ns_queue_cleared
        Action.NS_SETTINGS_COPIED         -> R.string.uel_ns_settings_copied
        Action.ERROR_DIALOG_OK            -> R.string.uel_error_dialog_ok
        Action.ERROR_DIALOG_MUTE          -> R.string.uel_error_dialog_mute
        Action.ERROR_DIALOG_MUTE_5MIN     -> R.string.uel_error_dialog_mute_5min
        Action.OBJECTIVE_STARTED          -> R.string.uel_objective_started
        Action.OBJECTIVE_UNSTARTED        -> R.string.uel_objective_unstarted
        Action.OBJECTIVES_SKIPPED         -> R.string.uel_objectives_skipped
        Action.STAT_RESET                 -> R.string.uel_stat_reset
        Action.DELETE_LOGS                -> R.string.uel_delete_logs
        Action.DELETE_FUTURE_TREATMENTS   -> R.string.uel_delete_future_treatments
        Action.EXPORT_SETTINGS            -> R.string.uel_export_settings
        Action.IMPORT_SETTINGS            -> R.string.uel_import_settings
        Action.RESET_DATABASES            -> R.string.uel_reset_databases
        Action.EXPORT_DATABASES           -> R.string.uel_export_databases
        Action.IMPORT_DATABASES           -> R.string.uel_import_databases
        Action.OTP_EXPORT                 -> R.string.uel_otp_export
        Action.OTP_RESET                  -> R.string.uel_otp_reset
        Action.SMS_BASAL                  -> R.string.uel_sms_basal
        Action.SMS_BOLUS                  -> R.string.uel_sms_bolus
        Action.SMS_CAL                    -> R.string.uel_sms_cal
        Action.SMS_CARBS                  -> R.string.uel_sms_carbs
        Action.SMS_EXTENDED_BOLUS         -> R.string.uel_sms_extended_bolus
        Action.SMS_LOOP_DISABLED          -> R.string.uel_sms_loop_disabled
        Action.SMS_LOOP_ENABLED           -> R.string.uel_sms_loop_enabled
        Action.SMS_LOOP_RESUME            -> R.string.uel_sms_loop_resume
        Action.SMS_LOOP_SUSPEND           -> R.string.uel_sms_loop_suspend
        Action.SMS_PROFILE                -> R.string.uel_sms_profile
        Action.SMS_PUMP_CONNECT           -> R.string.uel_sms_pump_connect
        Action.SMS_PUMP_DISCONNECT        -> R.string.uel_sms_pump_disconnect
        Action.SMS_SMS                    -> R.string.uel_sms_sms
        Action.SMS_TT                     -> R.string.uel_sms_tt
        Action.TT_DELETED_FROM_NS         -> R.string.uel_tt_deleted_from_ns
        Action.TT_FROM_NS                 -> R.string.uel_tt_from_ns
        Action.TT_CANCELED_FROM_NS        -> R.string.uel_tt_canceleted_from_ns
        Action.EXPORT_CSV                 -> R.string.uel_export_csv
        else                              -> R.string.uel_unknown
    }
}

fun ColorGroup.colorId(): Int {
    return when (this) {
        ColorGroup.InsulinTreatment -> R.color.basal
        ColorGroup.CarbTreatment    -> R.color.carbs
        ColorGroup.TT               -> R.color.tempTargetConfirmation
        ColorGroup.Profile          -> R.color.white
        ColorGroup.Loop             -> R.color.loopClosed
        ColorGroup.Careportal       -> R.color.high
        ColorGroup.Pump             -> R.color.iob
        ColorGroup.Aaps             -> R.color.defaulttext
        else                        -> R.color.defaulttext
    }
}

fun Units.stringId(): Int {
    return when (this) {
        Units.Mg_Dl    -> R.string.mgdl
        Units.Mmol_L   -> R.string.mmol
        Units.U        -> R.string.insulin_unit_shortname
        Units.U_H      -> R.string.profile_ins_units_per_hour
        Units.G        -> R.string.shortgram
        Units.M        -> R.string.shortminute
        Units.H        -> R.string.shorthour
        Units.Percent  -> R.string.shortpercent
        Units.R_String -> R.string.formated_string
        else                             -> 0
    }
}
