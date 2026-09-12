package app.aaps.pump.carelevo.ext

import androidx.annotation.StringRes
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.domain.type.AlarmCause

/**
 * All display resources for one [AlarmCause]. Single source of truth for BOTH presentation
 * surfaces — the in-app alarm screen ([screenDescRes]) and the Android notification
 * ([notificationDescRes]); title and confirm-button label are shared. One table maps each cause
 * to its string resources.
 */
data class AlarmStringResources(
    @param:StringRes val titleRes: Int,
    @param:StringRes val screenDescRes: Int?,
    @param:StringRes val notificationDescRes: Int?,
    @param:StringRes val btnRes: Int
)

/** In-app alarm screen resources: (title, description?, button). */
fun AlarmCause.transformStringResources(): Triple<Int, Int?, Int> =
    stringResources().let { Triple(it.titleRes, it.screenDescRes, it.btnRes) }

/** Notification resources: (title, description?, button). */
fun AlarmCause.transformNotificationStringResources(): Triple<Int, Int?, Int> =
    stringResources().let { Triple(it.titleRes, it.notificationDescRes, it.btnRes) }

fun AlarmCause.stringResources(): AlarmStringResources = when (this) {
    AlarmCause.ALARM_WARNING_LOW_INSULIN                           -> AlarmStringResources(
        R.string.alarm_feat_title_warning_low_insulin,
        R.string.alarm_feat_desc_warning_low_insulin,
        R.string.alarm_notification_desc_warning_low_insulin,
        R.string.alarm_feat_btn_patch_discard
    )

    AlarmCause.ALARM_WARNING_PATCH_EXPIRED_PHASE_1,
    AlarmCause.ALARM_WARNING_PATCH_EXPIRED                         -> AlarmStringResources(
        R.string.alarm_feat_title_warning_expired_patch,
        R.string.alarm_feat_desc_warning_expired_patch,
        R.string.alarm_notification_desc_warning_expired_patch,
        R.string.alarm_feat_btn_patch_discard
    )

    AlarmCause.ALARM_WARNING_LOW_BATTERY                           -> AlarmStringResources(
        R.string.alarm_feat_title_warning_low_battery,
        R.string.alarm_feat_desc_warning_low_battery,
        R.string.alarm_notification_desc_warning_low_battery,
        R.string.alarm_feat_btn_patch_force_discard
    )

    AlarmCause.ALARM_WARNING_INVALID_TEMPERATURE                   -> AlarmStringResources(
        R.string.alarm_feat_title_warning_invalid_temperature,
        R.string.alarm_feat_desc_warning_invalid_temperature,
        R.string.alarm_notification_desc_warning_invalid_temperature,
        R.string.alarm_feat_btn_patch_discard
    )

    AlarmCause.ALARM_WARNING_NOT_USED_APP_AUTO_OFF                 -> AlarmStringResources(
        R.string.alarm_feat_title_warning_not_used_app,
        R.string.alarm_feat_desc_warning_not_used_app,
        R.string.alarm_notification_desc_warning_not_used_app,
        R.string.alarm_feat_btn_resume_infusion
    )

    AlarmCause.ALARM_WARNING_BLE_NOT_CONNECTED                     -> AlarmStringResources(
        R.string.alarm_feat_title_warning_not_connected_ble,
        null,
        null,
        R.string.alarm_feat_btn_patch_force_discard
    )

    AlarmCause.ALARM_WARNING_INCOMPLETE_PATCH_SETTING              -> AlarmStringResources(
        R.string.alarm_feat_title_warning_incomplete_patch_setting,
        R.string.alarm_feat_desc_warning_incomplete_patch_setting,
        R.string.alarm_notification_desc_warning_incomplete_patch_setting,
        R.string.alarm_feat_btn_patch_discard
    )

    AlarmCause.ALARM_WARNING_SELF_DIAGNOSIS_FAILED                 -> AlarmStringResources(
        R.string.alarm_feat_title_warning_failed_safety_check,
        R.string.alarm_feat_desc_warning_failed_safety_check,
        R.string.alarm_notification_desc_warning_failed_safety_check,
        R.string.alarm_feat_btn_patch_discard
    )

    AlarmCause.ALARM_WARNING_PATCH_ERROR                           -> AlarmStringResources(
        R.string.alarm_feat_title_warning_patch_error,
        R.string.alarm_feat_desc_warning_patch_error,
        R.string.alarm_notification_desc_warning_patch_error,
        R.string.alarm_feat_btn_patch_discard
    )

    AlarmCause.ALARM_WARNING_PUMP_CLOGGED                          -> AlarmStringResources(
        R.string.alarm_feat_title_warning_infusion_clogged,
        R.string.alarm_feat_desc_warning_infusion_clogged,
        R.string.alarm_notification_desc_warning_infusion_clogged,
        R.string.alarm_feat_btn_patch_discard
    )

    AlarmCause.ALARM_WARNING_NEEDLE_INSERTION_ERROR                -> AlarmStringResources(
        R.string.alarm_feat_title_warning_needle_injection_error,
        R.string.alarm_feat_desc_warning_needle_injection_error,
        R.string.alarm_notification_desc_warning_needle_injection_error,
        R.string.alarm_feat_btn_patch_discard
    )

    AlarmCause.ALARM_ALERT_OUT_OF_INSULIN                          -> AlarmStringResources(
        R.string.alarm_feat_title_alert_low_insulin,
        R.string.alarm_feat_desc_alert_low_insulin,
        R.string.alarm_notification_desc_alert_low_insulin,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_1                   -> AlarmStringResources(
        R.string.alarm_feat_title_alert_expired_patch_phase1,
        R.string.alarm_feat_desc_alert_expired_patch_phase1,
        R.string.alarm_notification_desc_alert_expired_patch_phase1,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_2                   -> AlarmStringResources(
        R.string.alarm_feat_title_alert_expired_patch_phase2,
        R.string.alarm_feat_desc_alert_expired_patch_phase2,
        R.string.alarm_notification_desc_alert_expired_patch_phase2,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_ALERT_LOW_BATTERY                             -> AlarmStringResources(
        R.string.alarm_feat_title_alert_low_battery,
        R.string.alarm_feat_desc_alert_low_battery,
        R.string.alarm_notification_desc_alert_low_battery,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_ALERT_INVALID_TEMPERATURE                     -> AlarmStringResources(
        R.string.alarm_feat_title_alert_invalid_temperature,
        R.string.alarm_feat_desc_alert_invalid_temperature,
        R.string.alarm_notification_desc_alert_invalid_temperature,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_ALERT_APP_NO_USE                              -> AlarmStringResources(
        R.string.alarm_feat_title_alert_not_used_app,
        R.string.alarm_feat_desc_alert_not_used_app,
        R.string.alarm_notification_desc_alert_not_used_app,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_ALERT_BLE_NOT_CONNECTED                       -> AlarmStringResources(
        R.string.alarm_feat_title_alert_not_connected_ble,
        null,
        null,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_ALERT_PATCH_APPLICATION_INCOMPLETE            -> AlarmStringResources(
        R.string.alarm_feat_title_alert_incomplete_patch_setting,
        R.string.alarm_feat_desc_alert_incomplete_patch_setting,
        R.string.alarm_notification_desc_alert_incomplete_patch_setting,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_ALERT_RESUME_INSULIN_DELIVERY_TIMEOUT         -> AlarmStringResources(
        R.string.alarm_feat_title_alert_resume_infusion,
        R.string.alarm_feat_desc_alert_resume_infusion,
        R.string.alarm_notification_desc_alert_resume_infusion,
        R.string.alarm_feat_btn_resume_infusion
    )

    AlarmCause.ALARM_ALERT_BLUETOOTH_OFF                           -> AlarmStringResources(
        R.string.alarm_feat_title_alert_off_bluetooth,
        R.string.alarm_feat_desc_alert_off_bluetooth,
        R.string.alarm_notification_desc_alert_off_bluetooth,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_LOW_INSULIN                            -> AlarmStringResources(
        R.string.alarm_feat_title_notice_low_insulin,
        R.string.alarm_feat_desc_notice_low_insulin,
        R.string.alarm_notification_desc_notice_low_insulin,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_PATCH_EXPIRED                          -> AlarmStringResources(
        R.string.alarm_feat_title_notice_expired_patch,
        R.string.alarm_feat_desc_notice_expired_patch,
        R.string.alarm_notification_desc_notice_expired_patch,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_ATTACH_PATCH_CHECK                     -> AlarmStringResources(
        R.string.alarm_feat_title_notice_check_patch,
        null,
        null,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_BG_CHECK                               -> AlarmStringResources(
        R.string.alarm_feat_title_notice_check_bg,
        R.string.alarm_feat_desc_notice_check_bg,
        R.string.alarm_notification_desc_notice_check_bg,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_TIME_ZONE_CHANGED                      -> AlarmStringResources(
        R.string.alarm_feat_title_notice_change_time_zone,
        R.string.alarm_feat_desc_notice_change_time_zone,
        R.string.alarm_notification_desc_notice_change_time_zone,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_LGS_START                              -> AlarmStringResources(
        R.string.alarm_feat_title_notice_lgs_started,
        R.string.alarm_feat_desc_notice_lgs_started,
        R.string.alarm_notification_desc_notice_lgs_started,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_LGS_FINISHED_DISCONNECTED_PATCH_OR_CGM -> AlarmStringResources(
        R.string.alarm_feat_title_notice_lgs_ended,
        R.string.alarm_feat_desc_notice_lgs_ended_disconnected_patch_or_cgm,
        R.string.alarm_notification_desc_notice_lgs_ended_disconnected_patch_or_cgm,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_LGS_FINISHED_PAUSE_LGS                 -> AlarmStringResources(
        R.string.alarm_feat_title_notice_lgs_ended,
        R.string.alarm_feat_desc_notice_lgs_ended_pause_lgs,
        R.string.alarm_notification_desc_notice_lgs_ended_pause_lgs,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_LGS_FINISHED_TIME_OVER                 -> AlarmStringResources(
        R.string.alarm_feat_title_notice_lgs_ended,
        R.string.alarm_feat_desc_notice_lgs_ended_time_over,
        R.string.alarm_notification_desc_notice_lgs_ended_time_over,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_LGS_FINISHED_OFF_LGS                   -> AlarmStringResources(
        R.string.alarm_feat_title_notice_lgs_ended,
        R.string.alarm_feat_desc_notice_lgs_ended_off_lgs,
        R.string.alarm_notification_desc_notice_lgs_ended_off_lgs,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_LGS_FINISHED_HIGH_BG                   -> AlarmStringResources(
        R.string.alarm_feat_title_notice_lgs_ended,
        R.string.alarm_feat_desc_notice_lgs_ended_high_bg,
        R.string.alarm_notification_desc_notice_lgs_ended_high_bg,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_LGS_FINISHED_UNKNOWN                   -> AlarmStringResources(
        R.string.alarm_feat_title_notice_lgs_ended,
        R.string.alarm_feat_desc_notice_lgs_ended_unknown,
        R.string.alarm_notification_desc_notice_lgs_ended_unknown,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_LGS_NOT_WORKING                        -> AlarmStringResources(
        R.string.alarm_feat_title_notice_lgs_error,
        R.string.alarm_feat_desc_notice_lgs_error,
        R.string.alarm_notification_desc_notice_lgs_error,
        R.string.common_btn_ok
    )

    AlarmCause.ALARM_UNKNOWN                                       -> AlarmStringResources(
        R.string.alarm_feat_title_notice_unknown,
        R.string.alarm_feat_desc_unknown,
        R.string.alarm_feat_desc_unknown,
        R.string.common_btn_ok
    )
}
