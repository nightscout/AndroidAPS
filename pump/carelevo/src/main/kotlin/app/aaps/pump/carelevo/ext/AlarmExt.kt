package app.aaps.pump.carelevo.ext

import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType

fun AlarmType.transformToContentsResources(
    callback: (labelRes: Int, labelColorAttrRes: Int, labelContainerColorAttrRes: Int, iconRes: Int) -> Unit
) {
    when (this) {
        AlarmType.WARNING      -> {
            callback(
                R.string.alarm_feat_label_warning,
                app.aaps.core.ui.R.color.colorLightGray,
                app.aaps.core.ui.R.color.toastError,
                app.aaps.core.ui.R.drawable.ic_toast_error
            )
        }

        AlarmType.ALERT        -> {
            callback(
                R.string.alarm_feat_label_alert,
                app.aaps.core.ui.R.color.colorLightGray,
                app.aaps.core.ui.R.color.toastWarn,
                app.aaps.core.ui.R.drawable.ic_toast_warn
            )
        }

        AlarmType.NOTICE       -> {
            callback(
                R.string.alarm_feat_label_notice,
                app.aaps.core.ui.R.color.colorLightGray,
                app.aaps.core.ui.R.color.cobAlert,
                app.aaps.core.ui.R.drawable.ic_toast_info
            )
        }

        AlarmType.UNKNOWN_TYPE -> {
            callback(
                R.string.alarm_feat_label_unknown,
                app.aaps.core.ui.R.color.colorLightGray,
                app.aaps.core.ui.R.color.toastError,
                app.aaps.core.ui.R.drawable.ic_toast_error
            )
        }
    }
}

fun AlarmCause.transformStringResources(): Triple<Int, Int?, Int> {
    return when (this) {
        AlarmCause.ALARM_WARNING_LOW_INSULIN                           -> {
            Triple(
                R.string.alarm_feat_title_warning_low_insulin,
                R.string.alarm_feat_desc_warning_low_insulin,
                R.string.alarm_feat_btn_patch_discard
            )
        }

        AlarmCause.ALARM_WARNING_PATCH_EXPIRED_PHASE_1                 -> {
            Triple(
                R.string.alarm_feat_title_warning_expired_patch,
                R.string.alarm_feat_desc_warning_expired_patch,
                R.string.alarm_feat_btn_patch_discard
            )
        }

        AlarmCause.ALARM_WARNING_LOW_BATTERY                           -> {
            Triple(
                R.string.alarm_feat_title_warning_low_battery,
                R.string.alarm_feat_desc_warning_low_battery,
                R.string.alarm_feat_btn_patch_force_discard
            )
        }

        AlarmCause.ALARM_WARNING_INVALID_TEMPERATURE                   -> {
            Triple(
                R.string.alarm_feat_title_warning_invalid_temperature,
                R.string.alarm_feat_desc_warning_invalid_temperature,
                R.string.alarm_feat_btn_patch_discard
            )
        }

        AlarmCause.ALARM_WARNING_NOT_USED_APP_AUTO_OFF                 -> {
            Triple(
                R.string.alarm_feat_title_warning_not_used_app,
                R.string.alarm_feat_desc_warning_not_used_app,
                R.string.alarm_feat_btn_resume_infusion
            )
        }

        AlarmCause.ALARM_WARNING_BLE_NOT_CONNECTED                     -> {
            Triple(
                R.string.alarm_feat_title_warning_not_connected_ble,
                null,
                R.string.alarm_feat_btn_patch_force_discard
            )
        }

        AlarmCause.ALARM_WARNING_INCOMPLETE_PATCH_SETTING              -> {
            Triple(
                R.string.alarm_feat_title_warning_incomplete_patch_setting,
                R.string.alarm_feat_desc_warning_incomplete_patch_setting,
                R.string.alarm_feat_btn_patch_discard
            )
        }

        AlarmCause.ALARM_WARNING_SELF_DIAGNOSIS_FAILED                 -> {
            Triple(
                R.string.alarm_feat_title_warning_failed_safety_check,
                R.string.alarm_feat_desc_warning_failed_safety_check,
                R.string.alarm_feat_btn_patch_discard
            )
        }

        AlarmCause.ALARM_WARNING_PATCH_EXPIRED                         -> {
            Triple(
                R.string.alarm_feat_title_warning_expired_patch,
                R.string.alarm_feat_desc_warning_expired_patch,
                R.string.alarm_feat_btn_patch_discard
            )
        }

        AlarmCause.ALARM_WARNING_PATCH_ERROR                           -> {
            Triple(
                R.string.alarm_feat_title_warning_patch_error,
                R.string.alarm_feat_desc_warning_patch_error,
                R.string.alarm_feat_btn_patch_discard
            )
        }

        AlarmCause.ALARM_WARNING_PUMP_CLOGGED                          -> {
            Triple(
                R.string.alarm_feat_title_warning_infusion_clogged,
                R.string.alarm_feat_desc_warning_infusion_clogged,
                R.string.alarm_feat_btn_patch_discard
            )
        }

        AlarmCause.ALARM_WARNING_NEEDLE_INSERTION_ERROR                -> {
            Triple(
                R.string.alarm_feat_title_warning_needle_injection_error,
                R.string.alarm_feat_desc_warning_needle_injection_error,
                R.string.alarm_feat_btn_patch_discard
            )
        }

        AlarmCause.ALARM_ALERT_OUT_OF_INSULIN                          -> {
            Triple(
                R.string.alarm_feat_title_alert_low_insulin,
                R.string.alarm_feat_desc_alert_low_insulin,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_1                   -> {
            Triple(
                R.string.alarm_feat_title_alert_expired_patch_second,
                R.string.alarm_feat_desc_alert_expired_patch_second,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_2                   -> {
            Triple(
                R.string.alarm_feat_title_alert_expired_patch_first,
                R.string.alarm_feat_desc_alert_expired_patch_first,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_ALERT_LOW_BATTERY                             -> {
            Triple(
                R.string.alarm_feat_title_alert_low_battery,
                R.string.alarm_feat_desc_alert_low_battery,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_ALERT_INVALID_TEMPERATURE                     -> {
            Triple(
                R.string.alarm_feat_title_alert_invalid_temperature,
                R.string.alarm_feat_desc_alert_invalid_temperature,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_ALERT_APP_NO_USE                              -> {
            Triple(
                R.string.alarm_feat_title_alert_not_used_app,
                R.string.alarm_feat_desc_alert_not_used_app,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_ALERT_BLE_NOT_CONNECTED                       -> {
            Triple(
                R.string.alarm_feat_title_alert_not_connected_ble,
                null,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_ALERT_PATCH_APPLICATION_INCOMPLETE            -> {
            Triple(
                R.string.alarm_feat_title_alert_incomplete_patch_setting,
                R.string.alarm_feat_desc_alert_incomplete_patch_setting,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_ALERT_RESUME_INSULIN_DELIVERY_TIMEOUT         -> {
            Triple(
                R.string.alarm_feat_title_alert_resume_infusion,
                R.string.alarm_feat_desc_alert_resume_infusion,
                R.string.alarm_feat_btn_resume_infusion
            )
        }

        AlarmCause.ALARM_ALERT_BLUETOOTH_OFF                           -> {
            Triple(
                R.string.alarm_feat_title_alert_off_bluetooth,
                R.string.alarm_feat_desc_alert_off_bluetooth,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_LOW_INSULIN                            -> {
            Triple(
                R.string.alarm_feat_title_notice_low_insulin,
                R.string.alarm_feat_desc_notice_low_insulin,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_PATCH_EXPIRED                          -> {
            Triple(
                R.string.alarm_feat_title_notice_expired_patch,
                R.string.alarm_feat_desc_notice_expired_patch,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_ATTACH_PATCH_CHECK                     -> {
            Triple(
                R.string.alarm_feat_title_notice_check_patch,
                null,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_BG_CHECK                               -> {
            Triple(
                R.string.alarm_feat_title_notice_check_bg,
                R.string.alarm_feat_desc_notice_check_bg,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_TIME_ZONE_CHANGED                      -> {
            Triple(
                R.string.alarm_feat_title_notice_change_time_zone,
                R.string.alarm_feat_desc_notice_change_time_zone,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_LGS_START                              -> {
            Triple(
                R.string.alarm_feat_title_notice_lgs_started,
                R.string.alarm_feat_desc_notice_lgs_started,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_LGS_FINISHED_DISCONNECTED_PATCH_OR_CGM -> {
            Triple(
                R.string.alarm_feat_title_notice_lgs_ended,
                R.string.alarm_feat_desc_notice_lgs_ended_disconnected_patch_or_cgm,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_LGS_FINISHED_PAUSE_LGS                 -> {
            Triple(
                R.string.alarm_feat_title_notice_lgs_ended,
                R.string.alarm_feat_desc_notice_lgs_ended_pause_lgs,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_LGS_FINISHED_TIME_OVER                 -> {
            Triple(
                R.string.alarm_feat_title_notice_lgs_ended,
                R.string.alarm_feat_desc_notice_lgs_ended_time_over,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_LGS_FINISHED_OFF_LGS                   -> {
            Triple(
                R.string.alarm_feat_title_notice_lgs_ended,
                R.string.alarm_feat_desc_notice_lgs_ended_off_lgs,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_LGS_FINISHED_HIGH_BG                   -> {
            Triple(
                R.string.alarm_feat_title_notice_lgs_ended,
                R.string.alarm_feat_desc_notice_lgs_ended_high_bg,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_LGS_FINISHED_UNKNOWN                   -> {
            Triple(
                R.string.alarm_feat_title_notice_lgs_ended,
                R.string.alarm_feat_desc_notice_lgs_ended_unknown,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_LGS_NOT_WORKING                        -> {
            Triple(
                R.string.alarm_feat_title_notice_lgs_error,
                R.string.alarm_feat_desc_notice_lgs_error,
                R.string.common_btn_ok
            )
        }

        else                                                           -> {
            Triple(
                R.string.alarm_feat_title_notice_unknown,
                R.string.alarm_feat_desc_unknown,
                R.string.common_btn_ok
            )
        }
    }
}

fun AlarmCause.transformNotificationStringResources(): Triple<Int, Int?, Int> {
    return when (this) {
        AlarmCause.ALARM_WARNING_LOW_INSULIN                           -> {
            Triple(
                R.string.alarm_feat_title_warning_low_insulin,
                R.string.alarm_notification_desc_warning_low_insulin,
                R.string.alarm_feat_btn_patch_discard
            )
        }

        AlarmCause.ALARM_WARNING_PATCH_EXPIRED_PHASE_1                 -> {
            Triple(
                R.string.alarm_feat_title_warning_expired_patch,
                R.string.alarm_notification_desc_warning_expired_patch,
                R.string.alarm_feat_btn_patch_discard
            )
        }

        AlarmCause.ALARM_WARNING_LOW_BATTERY                           -> {
            Triple(
                R.string.alarm_feat_title_warning_low_battery,
                R.string.alarm_notification_desc_warning_low_battery,
                R.string.alarm_feat_btn_patch_force_discard
            )
        }

        AlarmCause.ALARM_WARNING_INVALID_TEMPERATURE                   -> {
            Triple(
                R.string.alarm_feat_title_warning_invalid_temperature,
                R.string.alarm_notification_desc_warning_invalid_temperature,
                R.string.alarm_feat_btn_patch_discard
            )
        }

        AlarmCause.ALARM_WARNING_NOT_USED_APP_AUTO_OFF                 -> {
            Triple(
                R.string.alarm_feat_title_warning_not_used_app,
                R.string.alarm_notification_desc_warning_not_used_app,
                R.string.alarm_feat_btn_resume_infusion
            )
        }

        AlarmCause.ALARM_WARNING_BLE_NOT_CONNECTED                     -> {
            Triple(
                R.string.alarm_feat_title_warning_not_connected_ble,
                null,
                R.string.alarm_feat_btn_patch_force_discard
            )
        }

        AlarmCause.ALARM_WARNING_INCOMPLETE_PATCH_SETTING              -> {
            Triple(
                R.string.alarm_feat_title_warning_incomplete_patch_setting,
                R.string.alarm_notification_desc_warning_incomplete_patch_setting,
                R.string.alarm_feat_btn_patch_discard
            )
        }

        AlarmCause.ALARM_WARNING_SELF_DIAGNOSIS_FAILED                 -> {
            Triple(
                R.string.alarm_feat_title_warning_failed_safety_check,
                R.string.alarm_notification_desc_warning_failed_safety_check,
                R.string.alarm_feat_btn_patch_discard
            )
        }

        AlarmCause.ALARM_WARNING_PATCH_EXPIRED                         -> {
            Triple(
                R.string.alarm_feat_title_warning_expired_patch,
                R.string.alarm_notification_desc_warning_expired_patch,
                R.string.alarm_feat_btn_patch_discard
            )
        }

        AlarmCause.ALARM_WARNING_PATCH_ERROR                           -> {
            Triple(
                R.string.alarm_feat_title_warning_patch_error,
                R.string.alarm_notification_desc_warning_patch_error,
                R.string.alarm_feat_btn_patch_discard
            )
        }

        AlarmCause.ALARM_WARNING_PUMP_CLOGGED                          -> {
            Triple(
                R.string.alarm_feat_title_warning_infusion_clogged,
                R.string.alarm_notification_desc_warning_infusion_clogged,
                R.string.alarm_feat_btn_patch_discard
            )
        }

        AlarmCause.ALARM_WARNING_NEEDLE_INSERTION_ERROR                -> {
            Triple(
                R.string.alarm_feat_title_warning_needle_injection_error,
                R.string.alarm_notification_desc_warning_needle_injection_error,
                R.string.alarm_feat_btn_patch_discard
            )
        }

        AlarmCause.ALARM_ALERT_OUT_OF_INSULIN                          -> {
            Triple(
                R.string.alarm_feat_title_alert_low_insulin,
                R.string.alarm_notification_desc_alert_low_insulin,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_1                   -> {
            Triple(
                R.string.alarm_feat_title_alert_expired_patch_second,
                R.string.alarm_notification_desc_alert_expired_patch_second,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_2                   -> {
            Triple(
                R.string.alarm_feat_title_alert_expired_patch_first,
                R.string.alarm_notification_desc_alert_expired_patch_first,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_ALERT_LOW_BATTERY                             -> {
            Triple(
                R.string.alarm_feat_title_alert_low_battery,
                R.string.alarm_notification_desc_alert_low_battery,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_ALERT_INVALID_TEMPERATURE                     -> {
            Triple(
                R.string.alarm_feat_title_alert_invalid_temperature,
                R.string.alarm_notification_desc_alert_invalid_temperature,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_ALERT_APP_NO_USE                              -> {
            Triple(
                R.string.alarm_feat_title_alert_not_used_app,
                R.string.alarm_notification_desc_alert_not_used_app,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_ALERT_BLE_NOT_CONNECTED                       -> {
            Triple(
                R.string.alarm_feat_title_alert_not_connected_ble,
                null,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_ALERT_PATCH_APPLICATION_INCOMPLETE            -> {
            Triple(
                R.string.alarm_feat_title_alert_incomplete_patch_setting,
                R.string.alarm_notification_desc_alert_incomplete_patch_setting,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_ALERT_RESUME_INSULIN_DELIVERY_TIMEOUT         -> {
            Triple(
                R.string.alarm_feat_title_alert_resume_infusion,
                R.string.alarm_notification_desc_alert_resume_infusion,
                R.string.alarm_feat_btn_resume_infusion
            )
        }

        AlarmCause.ALARM_ALERT_BLUETOOTH_OFF                           -> {
            Triple(
                R.string.alarm_feat_title_alert_off_bluetooth,
                R.string.alarm_notification_desc_alert_off_bluetooth,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_LOW_INSULIN                            -> {
            Triple(
                R.string.alarm_feat_title_notice_low_insulin,
                R.string.alarm_notification_desc_notice_low_insulin,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_PATCH_EXPIRED                          -> {
            Triple(
                R.string.alarm_feat_title_notice_expired_patch,
                R.string.alarm_notification_desc_notice_expired_patch,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_ATTACH_PATCH_CHECK                     -> {
            Triple(
                R.string.alarm_feat_title_notice_check_patch,
                null,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_BG_CHECK                               -> {
            Triple(
                R.string.alarm_feat_title_notice_check_bg,
                R.string.alarm_notification_desc_notice_check_bg,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_TIME_ZONE_CHANGED                      -> {
            Triple(
                R.string.alarm_feat_title_notice_change_time_zone,
                R.string.alarm_notification_desc_notice_change_time_zone,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_LGS_START                              -> {
            Triple(
                R.string.alarm_feat_title_notice_lgs_started,
                R.string.alarm_notification_desc_notice_lgs_started,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_LGS_FINISHED_DISCONNECTED_PATCH_OR_CGM -> {
            Triple(
                R.string.alarm_feat_title_notice_lgs_ended,
                R.string.alarm_notification_desc_notice_lgs_ended_disconnected_patch_or_cgm,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_LGS_FINISHED_PAUSE_LGS                 -> {
            Triple(
                R.string.alarm_feat_title_notice_lgs_ended,
                R.string.alarm_notification_desc_notice_lgs_ended_pause_lgs,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_LGS_FINISHED_TIME_OVER                 -> {
            Triple(
                R.string.alarm_feat_title_notice_lgs_ended,
                R.string.alarm_notification_desc_notice_lgs_ended_time_over,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_LGS_FINISHED_OFF_LGS                   -> {
            Triple(
                R.string.alarm_feat_title_notice_lgs_ended,
                R.string.alarm_notification_desc_notice_lgs_ended_off_lgs,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_LGS_FINISHED_HIGH_BG                   -> {
            Triple(
                R.string.alarm_feat_title_notice_lgs_ended,
                R.string.alarm_notification_desc_notice_lgs_ended_high_bg,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_LGS_FINISHED_UNKNOWN                   -> {
            Triple(
                R.string.alarm_feat_title_notice_lgs_ended,
                R.string.alarm_notification_desc_notice_lgs_ended_unknown,
                R.string.common_btn_ok
            )
        }

        AlarmCause.ALARM_NOTICE_LGS_NOT_WORKING                        -> {
            Triple(
                R.string.alarm_feat_title_notice_lgs_error,
                R.string.alarm_notification_desc_notice_lgs_error,
                R.string.common_btn_ok
            )
        }

        else                                                           -> {
            Triple(
                R.string.alarm_feat_title_notice_unknown,
                R.string.alarm_feat_desc_unknown,
                R.string.common_btn_ok
            )
        }
    }
}
