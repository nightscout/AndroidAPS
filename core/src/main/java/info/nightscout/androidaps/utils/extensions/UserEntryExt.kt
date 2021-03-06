package info.nightscout.androidaps.utils.extensions

import com.google.gson.annotations.SerializedName
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.UserEntry

fun UserEntry.Action.stringId(): Int {
    return when (this) {
        UserEntry.Action.BOLUS                      -> R.string.uel_bolus
        UserEntry.Action.BOLUS_WIZARD               -> R.string.uel_bolus_wizard
        UserEntry.Action.BOLUS_ADVISOR              -> R.string.uel_bolus_advisor
        UserEntry.Action.BOLUS_RECORD               -> R.string.uel_bolus_record
        UserEntry.Action.EXTENDED_BOLUS             -> R.string.uel_extended_bolus
        UserEntry.Action.SUPERBOLUS_TBR             -> R.string.uel_superbolus_tbr
        UserEntry.Action.CARBS                      -> R.string.uel_carbs
        UserEntry.Action.EXTENDED_CARBS             -> R.string.uel_extended_carbs
        UserEntry.Action.TEMP_BASAL                 -> R.string.uel_temp_basal
        UserEntry.Action.TT                         -> R.string.uel_tt
        UserEntry.Action.TT_ACTIVITY                -> R.string.uel_tt_activity
        UserEntry.Action.TT_EATING_SOON             -> R.string.uel_tt_eating_soon
        UserEntry.Action.TT_HYPO                    -> R.string.uel_tt_hypo
        UserEntry.Action.NEW_PROFILE                -> R.string.uel_new_profile
        UserEntry.Action.CLONE_PROFILE              -> R.string.uel_clone_profile
        UserEntry.Action.STORE_PROFILE              -> R.string.uel_store_profile
        UserEntry.Action.PROFILE_SWITCH             -> R.string.uel_profile_switch
        UserEntry.Action.PROFILE_SWITCH_CLONED      -> R.string.uel_profile_switch_cloned
        UserEntry.Action.CLOSED_LOOP_MODE           -> R.string.uel_closed_loop_mode
        UserEntry.Action.LGS_LOOP_MODE              -> R.string.uel_lgs_loop_mode
        UserEntry.Action.OPEN_LOOP_MODE             -> R.string.uel_open_loop_mode
        UserEntry.Action.LOOP_DISABLED              -> R.string.uel_loop_disabled
        UserEntry.Action.LOOP_ENABLED               -> R.string.uel_loop_enabled
        UserEntry.Action.RECONNECT                  -> R.string.uel_reconnect
        UserEntry.Action.DISCONNECT_15M             -> R.string.uel_disconnect_15m
        UserEntry.Action.DISCONNECT_30M             -> R.string.uel_disconnect_30m
        UserEntry.Action.DISCONNECT_1H              -> R.string.uel_disconnect_1h
        UserEntry.Action.DISCONNECT_2H              -> R.string.uel_disconnect_2h
        UserEntry.Action.DISCONNECT_3H              -> R.string.uel_disconnect_3h
        UserEntry.Action.RESUME                     -> R.string.uel_resume
        UserEntry.Action.SUSPEND_1H                 -> R.string.uel_suspend_1h
        UserEntry.Action.SUSPEND_2H                 -> R.string.uel_suspend_2h
        UserEntry.Action.SUSPEND_3H                 -> R.string.uel_suspend_3h
        UserEntry.Action.SUSPEND_10H                -> R.string.uel_suspend_10h
        UserEntry.Action.HW_PUMP_ALLOWED            -> R.string.uel_hw_pump_allowed
        UserEntry.Action.CLEAR_PAIRING_KEYS         -> R.string.uel_clear_pairing_keys
        UserEntry.Action.ACCEPTS_TEMP_BASAL         -> R.string.uel_accepts_temp_basal
        UserEntry.Action.CANCEL_TEMP_BASAL          -> R.string.uel_cancel_temp_basal
        UserEntry.Action.CANCEL_EXTENDED_BOLUS      -> R.string.uel_cancel_extended_bolus
        UserEntry.Action.CANCEL_TT                  -> R.string.uel_cancel_tt
        UserEntry.Action.CAREPORTAL                 -> R.string.uel_careportal
        UserEntry.Action.CALIBRATION                -> R.string.uel_calibration
        UserEntry.Action.INSULIN_CHANGE             -> R.string.uel_insulin_change
        UserEntry.Action.PRIME_BOLUS                -> R.string.uel_prime_bolus
        UserEntry.Action.SITE_CHANGE                -> R.string.uel_site_change
        UserEntry.Action.TREATMENT                  -> R.string.uel_treatment
        UserEntry.Action.CAREPORTAL_NS_REFRESH      -> R.string.uel_careportal_ns_refresh
        UserEntry.Action.PROFILE_SWITCH_NS_REFRESH  -> R.string.uel_profile_switch_ns_refresh
        UserEntry.Action.TREATMENTS_NS_REFRESH      -> R.string.uel_treatments_ns_refresh
        UserEntry.Action.TT_NS_REFRESH              -> R.string.uel_tt_ns_refresh
        UserEntry.Action.AUTOMATION_REMOVED         -> R.string.uel_automation_removed
        UserEntry.Action.BG_REMOVED                 -> R.string.uel_bg_removed
        UserEntry.Action.CAREPORTAL_REMOVED         -> R.string.uel_careportal_removed
        UserEntry.Action.EXTENDED_BOLUS_REMOVED     -> R.string.uel_extended_bolus_removed
        UserEntry.Action.FOOD_REMOVED               -> R.string.uel_food_removed
        UserEntry.Action.PROFILE_REMOVED            -> R.string.uel_profile_removed
        UserEntry.Action.PROFILE_SWITCH_REMOVED     -> R.string.uel_profile_switch_removed
        UserEntry.Action.RESTART_EVENTS_REMOVED     -> R.string.uel_restart_events_removed
        UserEntry.Action.TREATMENT_REMOVED          -> R.string.uel_treatment_removed
        UserEntry.Action.TT_REMOVED                 -> R.string.uel_tt_removed
        UserEntry.Action.NS_PAUSED                  -> R.string.uel_ns_paused
        UserEntry.Action.NS_QUEUE_CLEARED           -> R.string.uel_ns_queue_cleared
        UserEntry.Action.NS_SETTINGS_COPIED         -> R.string.uel_ns_settings_copied
        UserEntry.Action.ERROR_DIALOG_OK            -> R.string.uel_error_dialog_ok
        UserEntry.Action.ERROR_DIALOG_MUTE          -> R.string.uel_error_dialog_mute
        UserEntry.Action.ERROR_DIALOG_MUTE_5MIN     -> R.string.uel_error_dialog_mute_5min
        UserEntry.Action.OBJECTIVE_STARTED          -> R.string.uel_objective_started
        UserEntry.Action.OBJECTIVE_UNSTARTED        -> R.string.uel_objective_unstarted
        UserEntry.Action.OBJECTIVES_SKIPPED         -> R.string.uel_objectives_skipped
        UserEntry.Action.STAT_RESET                 -> R.string.uel_stat_reset
        UserEntry.Action.DELETE_LOGS                -> R.string.uel_delete_logs
        UserEntry.Action.DELETE_FUTURE_TREATMENTS   -> R.string.uel_delete_future_treatments
        UserEntry.Action.EXPORT_SETTINGS            -> R.string.uel_export_settings
        UserEntry.Action.IMPORT_SETTINGS            -> R.string.uel_import_settings
        UserEntry.Action.RESET_DATABASES            -> R.string.uel_reset_databases
        UserEntry.Action.EXPORT_DATABASES           -> R.string.uel_export_databases
        UserEntry.Action.IMPORT_DATABASES           -> R.string.uel_import_databases
        UserEntry.Action.OTP_EXPORT                 -> R.string.uel_otp_export
        UserEntry.Action.OTP_RESET                  -> R.string.uel_otp_reset
        UserEntry.Action.SMS_BASAL                  -> R.string.uel_sms_basal
        UserEntry.Action.SMS_BOLUS                  -> R.string.uel_sms_bolus
        UserEntry.Action.SMS_CAL                    -> R.string.uel_sms_cal
        UserEntry.Action.SMS_CARBS                  -> R.string.uel_sms_carbs
        UserEntry.Action.SMS_EXTENDED_BOLUS         -> R.string.uel_sms_extended_bolus
        UserEntry.Action.SMS_LOOP_DISABLED          -> R.string.uel_sms_loop_disabled
        UserEntry.Action.SMS_LOOP_ENABLED           -> R.string.uel_sms_loop_enabled
        UserEntry.Action.SMS_LOOP_RESUME            -> R.string.uel_sms_loop_resume
        UserEntry.Action.SMS_LOOP_SUSPEND           -> R.string.uel_sms_loop_suspend
        UserEntry.Action.SMS_PROFILE                -> R.string.uel_sms_profile
        UserEntry.Action.SMS_PUMP_CONNECT           -> R.string.uel_sms_pump_connect
        UserEntry.Action.SMS_PUMP_DISCONNECT        -> R.string.uel_sms_pump_disconnect
        UserEntry.Action.SMS_SMS                    -> R.string.uel_sms_sms
        UserEntry.Action.SMS_TT                     -> R.string.uel_sms_tt
        UserEntry.Action.TT_DELETED_FROM_NS         -> R.string.uel_tt_deleted_from_ns
        UserEntry.Action.TT_FROM_NS                 -> R.string.uel_tt_from_ns
        UserEntry.Action.TT_CANCELED_FROM_NS        -> R.string.uel_tt_canceleted_from_ns
        UserEntry.Action.UE_EXPORT_TO_XML           -> R.string.ue_export_to_xml
        else                                        -> R.string.uel_unknown
    }
}

fun UserEntry.Action.colorId(): Int {
    return when (this) {
        UserEntry.Action.BOLUS                      -> R.color.iob
        UserEntry.Action.BOLUS_WIZARD               -> R.color.iob
        UserEntry.Action.BOLUS_ADVISOR              -> R.color.iob
        UserEntry.Action.BOLUS_RECORD               -> R.color.iob
        UserEntry.Action.EXTENDED_BOLUS             -> R.color.extendedBolus
        UserEntry.Action.SUPERBOLUS_TBR             -> R.color.carbs
        UserEntry.Action.CARBS                      -> R.color.carbs
        UserEntry.Action.EXTENDED_CARBS             -> R.color.carbs
        UserEntry.Action.TEMP_BASAL                 -> R.color.basal
        UserEntry.Action.TT                         -> R.color.tempTargetConfirmation
        UserEntry.Action.TT_ACTIVITY                -> R.color.tempTargetConfirmation
        UserEntry.Action.TT_EATING_SOON             -> R.color.tempTargetConfirmation
        UserEntry.Action.TT_HYPO                    -> R.color.tempTargetConfirmation
        UserEntry.Action.NEW_PROFILE                -> R.color.white
        UserEntry.Action.CLONE_PROFILE              -> R.color.white
        UserEntry.Action.STORE_PROFILE              -> R.color.white
        UserEntry.Action.PROFILE_SWITCH             -> R.color.white
        UserEntry.Action.PROFILE_SWITCH_CLONED      -> R.color.white
        UserEntry.Action.CLOSED_LOOP_MODE           -> R.color.loopClosed
        UserEntry.Action.LGS_LOOP_MODE              -> R.color.loopClosed
        UserEntry.Action.OPEN_LOOP_MODE             -> R.color.loopOpened
        UserEntry.Action.LOOP_DISABLED              -> R.color.loopDisabled
        UserEntry.Action.LOOP_ENABLED               -> R.color.loopClosed
        UserEntry.Action.RECONNECT                  -> R.color.loopDisconnected
        UserEntry.Action.DISCONNECT_15M             -> R.color.loopDisconnected
        UserEntry.Action.DISCONNECT_30M             -> R.color.loopDisconnected
        UserEntry.Action.DISCONNECT_1H              -> R.color.loopDisconnected
        UserEntry.Action.DISCONNECT_2H              -> R.color.loopDisconnected
        UserEntry.Action.DISCONNECT_3H              -> R.color.loopDisconnected
        UserEntry.Action.RESUME                     -> R.color.loopClosed
        UserEntry.Action.SUSPEND_1H                 -> R.color.loopSuspended
        UserEntry.Action.SUSPEND_2H                 -> R.color.loopSuspended
        UserEntry.Action.SUSPEND_3H                 -> R.color.loopSuspended
        UserEntry.Action.SUSPEND_10H                -> R.color.loopSuspended
        UserEntry.Action.HW_PUMP_ALLOWED            -> R.color.defaulttext
        UserEntry.Action.CLEAR_PAIRING_KEYS         -> R.color.defaulttext
        UserEntry.Action.ACCEPTS_TEMP_BASAL         -> R.color.basal
        UserEntry.Action.CANCEL_TEMP_BASAL          -> R.color.basal
        UserEntry.Action.CANCEL_EXTENDED_BOLUS      -> R.color.extendedBolus
        UserEntry.Action.CANCEL_TT                  -> R.color.tempTargetConfirmation
        UserEntry.Action.CAREPORTAL                 -> R.color.notificationAnnouncement
        UserEntry.Action.CALIBRATION                -> R.color.calibration
        UserEntry.Action.INSULIN_CHANGE             -> R.color.iob
        UserEntry.Action.PRIME_BOLUS                -> R.color.defaulttext
        UserEntry.Action.SITE_CHANGE                -> R.color.defaulttext
        UserEntry.Action.TREATMENT                  -> R.color.defaulttext
        UserEntry.Action.CAREPORTAL_NS_REFRESH      -> R.color.notificationAnnouncement
        UserEntry.Action.PROFILE_SWITCH_NS_REFRESH  -> R.color.white
        UserEntry.Action.TREATMENTS_NS_REFRESH      -> R.color.defaulttext
        UserEntry.Action.TT_NS_REFRESH              -> R.color.tempTargetConfirmation
        UserEntry.Action.AUTOMATION_REMOVED         -> R.color.defaulttext
        UserEntry.Action.BG_REMOVED                 -> R.color.calibration
        UserEntry.Action.CAREPORTAL_REMOVED         -> R.color.notificationAnnouncement
        UserEntry.Action.EXTENDED_BOLUS_REMOVED     -> R.color.extendedBolus
        UserEntry.Action.FOOD_REMOVED               -> R.color.carbs
        UserEntry.Action.PROFILE_REMOVED            -> R.color.white
        UserEntry.Action.PROFILE_SWITCH_REMOVED     -> R.color.white
        UserEntry.Action.RESTART_EVENTS_REMOVED     -> R.color.defaulttext
        UserEntry.Action.TREATMENT_REMOVED          -> R.color.defaulttext
        UserEntry.Action.TT_REMOVED                 -> R.color.tempTargetConfirmation
        UserEntry.Action.NS_PAUSED                  -> R.color.defaulttext
        UserEntry.Action.NS_QUEUE_CLEARED           -> R.color.defaulttext
        UserEntry.Action.NS_SETTINGS_COPIED         -> R.color.defaulttext
        UserEntry.Action.ERROR_DIALOG_OK            -> R.color.defaulttext
        UserEntry.Action.ERROR_DIALOG_MUTE          -> R.color.defaulttext
        UserEntry.Action.ERROR_DIALOG_MUTE_5MIN     -> R.color.defaulttext
        UserEntry.Action.OBJECTIVE_STARTED          -> R.color.defaulttext
        UserEntry.Action.OBJECTIVE_UNSTARTED        -> R.color.defaulttext
        UserEntry.Action.OBJECTIVES_SKIPPED         -> R.color.defaulttext
        UserEntry.Action.STAT_RESET                 -> R.color.defaulttext
        UserEntry.Action.DELETE_LOGS                -> R.color.defaulttext
        UserEntry.Action.DELETE_FUTURE_TREATMENTS   -> R.color.defaulttext
        UserEntry.Action.EXPORT_SETTINGS            -> R.color.defaulttext
        UserEntry.Action.IMPORT_SETTINGS            -> R.color.defaulttext
        UserEntry.Action.RESET_DATABASES            -> R.color.defaulttext
        UserEntry.Action.EXPORT_DATABASES           -> R.color.defaulttext
        UserEntry.Action.IMPORT_DATABASES           -> R.color.defaulttext
        UserEntry.Action.OTP_EXPORT                 -> R.color.defaulttext
        UserEntry.Action.OTP_RESET                  -> R.color.defaulttext
        UserEntry.Action.SMS_BASAL                  -> R.color.basal
        UserEntry.Action.SMS_BOLUS                  -> R.color.iob
        UserEntry.Action.SMS_CAL                    -> R.color.calibration
        UserEntry.Action.SMS_CARBS                  -> R.color.carbs
        UserEntry.Action.SMS_EXTENDED_BOLUS         -> R.color.extendedBolus
        UserEntry.Action.SMS_LOOP_DISABLED          -> R.color.loopDisabled
        UserEntry.Action.SMS_LOOP_ENABLED           -> R.color.loopClosed
        UserEntry.Action.SMS_LOOP_RESUME            -> R.color.loopClosed
        UserEntry.Action.SMS_LOOP_SUSPEND           -> R.color.loopSuspended
        UserEntry.Action.SMS_PROFILE                -> R.color.white
        UserEntry.Action.SMS_PUMP_CONNECT           -> R.color.loopDisconnected
        UserEntry.Action.SMS_PUMP_DISCONNECT        -> R.color.loopDisconnected
        UserEntry.Action.SMS_SMS                    -> R.color.defaulttext
        UserEntry.Action.SMS_TT                     -> R.color.tempTargetConfirmation
        UserEntry.Action.TT_DELETED_FROM_NS         -> R.color.tempTargetConfirmation
        UserEntry.Action.TT_FROM_NS                 -> R.color.tempTargetConfirmation
        UserEntry.Action.TT_CANCELED_FROM_NS        -> R.color.tempTargetConfirmation
        else                                        -> R.color.defaulttext
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
