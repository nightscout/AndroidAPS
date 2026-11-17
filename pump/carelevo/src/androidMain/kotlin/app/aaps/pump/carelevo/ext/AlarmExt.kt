package app.aaps.pump.carelevo.ext

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.carelevo.CarelevoStrings
import app.aaps.pump.carelevo.domain.type.AlarmCause

/**
 * All display texts for one [AlarmCause]: title, notification text ([notificationDescRes]) and
 * confirm-button label. One table maps each cause to its strings.
 */
data class AlarmStringResources(
    val titleRes: TextRef,
    val notificationDescRes: TextRef?,
    val btnRes: TextRef
)

/** Notification texts: (title, description?, button). */
fun AlarmCause.transformNotificationStringResources(): Triple<TextRef, TextRef?, TextRef> =
    stringResources().let { Triple(it.titleRes, it.notificationDescRes, it.btnRes) }

fun AlarmCause.stringResources(): AlarmStringResources = when (this) {
    AlarmCause.ALARM_WARNING_LOW_INSULIN                           -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_warning_low_insulin,
        CarelevoStrings.alarm_notification_desc_warning_low_insulin,
        CarelevoStrings.alarm_feat_btn_patch_discard
    )

    AlarmCause.ALARM_WARNING_PATCH_EXPIRED_PHASE_1,
    AlarmCause.ALARM_WARNING_PATCH_EXPIRED                         -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_warning_expired_patch,
        CarelevoStrings.alarm_notification_desc_warning_expired_patch,
        CarelevoStrings.alarm_feat_btn_patch_discard
    )

    AlarmCause.ALARM_WARNING_LOW_BATTERY                           -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_warning_low_battery,
        CarelevoStrings.alarm_notification_desc_warning_low_battery,
        CarelevoStrings.alarm_feat_btn_patch_force_discard
    )

    AlarmCause.ALARM_WARNING_INVALID_TEMPERATURE                   -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_warning_invalid_temperature,
        CarelevoStrings.alarm_notification_desc_warning_invalid_temperature,
        CarelevoStrings.alarm_feat_btn_patch_discard
    )

    AlarmCause.ALARM_WARNING_NOT_USED_APP_AUTO_OFF                 -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_warning_not_used_app,
        CarelevoStrings.alarm_notification_desc_warning_not_used_app,
        CarelevoStrings.alarm_feat_btn_resume_infusion
    )

    AlarmCause.ALARM_WARNING_BLE_NOT_CONNECTED                     -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_warning_not_connected_ble,
        null,
        CarelevoStrings.alarm_feat_btn_patch_force_discard
    )

    AlarmCause.ALARM_WARNING_INCOMPLETE_PATCH_SETTING              -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_warning_incomplete_patch_setting,
        CarelevoStrings.alarm_notification_desc_warning_incomplete_patch_setting,
        CarelevoStrings.alarm_feat_btn_patch_discard
    )

    AlarmCause.ALARM_WARNING_SELF_DIAGNOSIS_FAILED                 -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_warning_failed_safety_check,
        CarelevoStrings.alarm_notification_desc_warning_failed_safety_check,
        CarelevoStrings.alarm_feat_btn_patch_discard
    )

    AlarmCause.ALARM_WARNING_PATCH_ERROR                           -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_warning_patch_error,
        CarelevoStrings.alarm_notification_desc_warning_patch_error,
        CarelevoStrings.alarm_feat_btn_patch_discard
    )

    AlarmCause.ALARM_WARNING_PUMP_CLOGGED                          -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_warning_infusion_clogged,
        CarelevoStrings.alarm_notification_desc_warning_infusion_clogged,
        CarelevoStrings.alarm_feat_btn_patch_discard
    )

    AlarmCause.ALARM_WARNING_NEEDLE_INSERTION_ERROR                -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_warning_needle_injection_error,
        CarelevoStrings.alarm_notification_desc_warning_needle_injection_error,
        CarelevoStrings.alarm_feat_btn_patch_discard
    )

    AlarmCause.ALARM_ALERT_OUT_OF_INSULIN                          -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_alert_low_insulin,
        CarelevoStrings.alarm_notification_desc_alert_low_insulin,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_1                   -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_alert_expired_patch_phase1,
        CarelevoStrings.alarm_notification_desc_alert_expired_patch_phase1,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_2                   -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_alert_expired_patch_phase2,
        CarelevoStrings.alarm_notification_desc_alert_expired_patch_phase2,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_ALERT_LOW_BATTERY                             -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_alert_low_battery,
        CarelevoStrings.alarm_notification_desc_alert_low_battery,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_ALERT_INVALID_TEMPERATURE                     -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_alert_invalid_temperature,
        CarelevoStrings.alarm_notification_desc_alert_invalid_temperature,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_ALERT_APP_NO_USE                              -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_alert_not_used_app,
        CarelevoStrings.alarm_notification_desc_alert_not_used_app,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_ALERT_BLE_NOT_CONNECTED                       -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_alert_not_connected_ble,
        null,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_ALERT_PATCH_APPLICATION_INCOMPLETE            -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_alert_incomplete_patch_setting,
        CarelevoStrings.alarm_notification_desc_alert_incomplete_patch_setting,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_ALERT_RESUME_INSULIN_DELIVERY_TIMEOUT         -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_alert_resume_infusion,
        CarelevoStrings.alarm_notification_desc_alert_resume_infusion,
        CarelevoStrings.alarm_feat_btn_resume_infusion
    )

    AlarmCause.ALARM_ALERT_BLUETOOTH_OFF                           -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_alert_off_bluetooth,
        CarelevoStrings.alarm_notification_desc_alert_off_bluetooth,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_LOW_INSULIN                            -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_notice_low_insulin,
        CarelevoStrings.alarm_notification_desc_notice_low_insulin,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_PATCH_EXPIRED                          -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_notice_expired_patch,
        CarelevoStrings.alarm_notification_desc_notice_expired_patch,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_ATTACH_PATCH_CHECK                     -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_notice_check_patch,
        null,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_BG_CHECK                               -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_notice_check_bg,
        CarelevoStrings.alarm_notification_desc_notice_check_bg,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_TIME_ZONE_CHANGED                      -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_notice_change_time_zone,
        CarelevoStrings.alarm_notification_desc_notice_change_time_zone,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_LGS_START                              -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_notice_lgs_started,
        CarelevoStrings.alarm_notification_desc_notice_lgs_started,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_LGS_FINISHED_DISCONNECTED_PATCH_OR_CGM -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_notice_lgs_ended,
        CarelevoStrings.alarm_notification_desc_notice_lgs_ended_disconnected_patch_or_cgm,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_LGS_FINISHED_PAUSE_LGS                 -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_notice_lgs_ended,
        CarelevoStrings.alarm_notification_desc_notice_lgs_ended_pause_lgs,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_LGS_FINISHED_TIME_OVER                 -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_notice_lgs_ended,
        CarelevoStrings.alarm_notification_desc_notice_lgs_ended_time_over,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_LGS_FINISHED_OFF_LGS                   -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_notice_lgs_ended,
        CarelevoStrings.alarm_notification_desc_notice_lgs_ended_off_lgs,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_LGS_FINISHED_HIGH_BG                   -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_notice_lgs_ended,
        CarelevoStrings.alarm_notification_desc_notice_lgs_ended_high_bg,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_LGS_FINISHED_UNKNOWN                   -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_notice_lgs_ended,
        CarelevoStrings.alarm_notification_desc_notice_lgs_ended_unknown,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_NOTICE_LGS_NOT_WORKING                        -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_notice_lgs_error,
        CarelevoStrings.alarm_notification_desc_notice_lgs_error,
        CarelevoStrings.common_btn_ok
    )

    AlarmCause.ALARM_UNKNOWN                                       -> AlarmStringResources(
        CarelevoStrings.alarm_feat_title_notice_unknown,
        CarelevoStrings.alarm_feat_desc_unknown,
        CarelevoStrings.common_btn_ok
    )
}
