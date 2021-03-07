package info.nightscout.androidaps.utils.extensions

import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.UserEntry
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
        else                                        -> R.string.uel_unknown
    }
}

fun Action.colorId(): Int {
    return when (this) {
        Action.EXTENDED_CARBS             -> R.color.carbs
        Action.TEMP_BASAL                 -> R.color.basal
        Action.TT                         -> R.color.tempTargetConfirmation
        Action.TT_ACTIVITY                -> R.color.tempTargetConfirmation
        Action.TT_EATING_SOON             -> R.color.tempTargetConfirmation
        Action.TT_HYPO                    -> R.color.tempTargetConfirmation
        Action.NEW_PROFILE                -> R.color.white
        Action.CLONE_PROFILE              -> R.color.white
        Action.STORE_PROFILE              -> R.color.white
        Action.PROFILE_SWITCH             -> R.color.white
        Action.PROFILE_SWITCH_CLONED      -> R.color.white
        Action.CLOSED_LOOP_MODE           -> R.color.loopClosed
        Action.LGS_LOOP_MODE              -> R.color.loopClosed
        Action.OPEN_LOOP_MODE             -> R.color.loopOpened
        Action.LOOP_DISABLED              -> R.color.loopDisabled
        Action.LOOP_ENABLED               -> R.color.loopClosed
        Action.RECONNECT                  -> R.color.loopDisconnected
        Action.DISCONNECT_15M             -> R.color.loopDisconnected
        Action.DISCONNECT_30M             -> R.color.loopDisconnected
        Action.DISCONNECT_1H              -> R.color.loopDisconnected
        Action.DISCONNECT_2H              -> R.color.loopDisconnected
        Action.DISCONNECT_3H              -> R.color.loopDisconnected
        Action.RESUME                     -> R.color.loopClosed
        Action.SUSPEND_1H                 -> R.color.loopSuspended
        Action.SUSPEND_2H                 -> R.color.loopSuspended
        Action.SUSPEND_3H                 -> R.color.loopSuspended
        Action.SUSPEND_10H                -> R.color.loopSuspended
        Action.HW_PUMP_ALLOWED            -> R.color.defaulttext
        Action.CLEAR_PAIRING_KEYS         -> R.color.defaulttext
        Action.ACCEPTS_TEMP_BASAL         -> R.color.basal
        Action.CANCEL_TEMP_BASAL          -> R.color.basal
        Action.CANCEL_EXTENDED_BOLUS      -> R.color.extendedBolus
        Action.CANCEL_TT                  -> R.color.tempTargetConfirmation
        Action.CAREPORTAL                 -> R.color.notificationAnnouncement
        Action.CALIBRATION                -> R.color.calibration
        Action.INSULIN_CHANGE             -> R.color.iob
        Action.PRIME_BOLUS                -> R.color.defaulttext
        Action.SITE_CHANGE                -> R.color.defaulttext
        Action.TREATMENT                  -> R.color.defaulttext
        Action.CAREPORTAL_NS_REFRESH      -> R.color.notificationAnnouncement
        Action.PROFILE_SWITCH_NS_REFRESH  -> R.color.white
        Action.TREATMENTS_NS_REFRESH      -> R.color.defaulttext
        Action.TT_NS_REFRESH              -> R.color.tempTargetConfirmation
        Action.AUTOMATION_REMOVED         -> R.color.defaulttext
        Action.BG_REMOVED                 -> R.color.calibration
        Action.CAREPORTAL_REMOVED         -> R.color.notificationAnnouncement
        Action.EXTENDED_BOLUS_REMOVED     -> R.color.extendedBolus
        Action.FOOD_REMOVED               -> R.color.carbs
        Action.PROFILE_REMOVED            -> R.color.white
        Action.PROFILE_SWITCH_REMOVED     -> R.color.white
        Action.RESTART_EVENTS_REMOVED     -> R.color.defaulttext
        Action.TREATMENT_REMOVED          -> R.color.defaulttext
        Action.TT_REMOVED                 -> R.color.tempTargetConfirmation
        Action.NS_PAUSED                  -> R.color.defaulttext
        Action.NS_QUEUE_CLEARED           -> R.color.defaulttext
        Action.NS_SETTINGS_COPIED         -> R.color.defaulttext
        Action.ERROR_DIALOG_OK            -> R.color.defaulttext
        Action.ERROR_DIALOG_MUTE          -> R.color.defaulttext
        Action.ERROR_DIALOG_MUTE_5MIN     -> R.color.defaulttext
        Action.OBJECTIVE_STARTED          -> R.color.defaulttext
        Action.OBJECTIVE_UNSTARTED        -> R.color.defaulttext
        Action.OBJECTIVES_SKIPPED         -> R.color.defaulttext
        Action.STAT_RESET                 -> R.color.defaulttext
        Action.DELETE_LOGS                -> R.color.defaulttext
        Action.DELETE_FUTURE_TREATMENTS   -> R.color.defaulttext
        Action.EXPORT_SETTINGS            -> R.color.defaulttext
        Action.IMPORT_SETTINGS            -> R.color.defaulttext
        Action.RESET_DATABASES            -> R.color.defaulttext
        Action.EXPORT_DATABASES           -> R.color.defaulttext
        Action.IMPORT_DATABASES           -> R.color.defaulttext
        Action.OTP_EXPORT                 -> R.color.defaulttext
        Action.OTP_RESET                  -> R.color.defaulttext
        Action.SMS_BASAL                  -> R.color.basal
        Action.SMS_BOLUS                  -> R.color.iob
        Action.SMS_CAL                    -> R.color.calibration
        Action.SMS_CARBS                  -> R.color.carbs
        Action.SMS_EXTENDED_BOLUS         -> R.color.extendedBolus
        Action.SMS_LOOP_DISABLED          -> R.color.loopDisabled
        Action.SMS_LOOP_ENABLED           -> R.color.loopClosed
        Action.SMS_LOOP_RESUME            -> R.color.loopClosed
        Action.SMS_LOOP_SUSPEND           -> R.color.loopSuspended
        Action.SMS_PROFILE                -> R.color.white
        Action.SMS_PUMP_CONNECT           -> R.color.loopDisconnected
        Action.SMS_PUMP_DISCONNECT        -> R.color.loopDisconnected
        Action.SMS_SMS                    -> R.color.defaulttext
        Action.SMS_TT                     -> R.color.tempTargetConfirmation
        Action.TT_DELETED_FROM_NS         -> R.color.tempTargetConfirmation
        Action.TT_FROM_NS                 -> R.color.tempTargetConfirmation
        Action.TT_CANCELED_FROM_NS        -> R.color.tempTargetConfirmation
        else                              -> R.color.defaulttext
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
