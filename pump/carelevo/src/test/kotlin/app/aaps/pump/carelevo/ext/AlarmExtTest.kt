package app.aaps.pump.carelevo.ext

import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.domain.type.AlarmCause
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Covers the full [AlarmCause] -> string-resource table in `AlarmExt.kt`.
 *
 * Expected resource IDs are asserted against the same `R.string.*` constants the production code
 * references (both resolve to the same runtime int), so the test verifies the *mapping* without
 * hardcoding opaque integers and without needing a Context.
 */
internal class AlarmExtTest {

    private fun expect(
        cause: AlarmCause,
        title: Int,
        screen: Int?,
        notification: Int?,
        btn: Int
    ) {
        val actual = cause.stringResources()
        assertThat(actual).isEqualTo(AlarmStringResources(title, screen, notification, btn))
        // transform helpers must be pure projections of the same table row
        assertThat(cause.transformStringResources()).isEqualTo(Triple(title, screen, btn))
        assertThat(cause.transformNotificationStringResources()).isEqualTo(Triple(title, notification, btn))
    }

    // ---------------------------------------------------------------------------------------------
    // WARNING tier
    // ---------------------------------------------------------------------------------------------

    @Test fun `warning low insulin`() = expect(
        AlarmCause.ALARM_WARNING_LOW_INSULIN,
        R.string.alarm_feat_title_warning_low_insulin,
        R.string.alarm_feat_desc_warning_low_insulin,
        R.string.alarm_notification_desc_warning_low_insulin,
        R.string.alarm_feat_btn_patch_discard
    )

    @Test fun `warning patch expired phase 1 shares the expired-patch row`() = expect(
        AlarmCause.ALARM_WARNING_PATCH_EXPIRED_PHASE_1,
        R.string.alarm_feat_title_warning_expired_patch,
        R.string.alarm_feat_desc_warning_expired_patch,
        R.string.alarm_notification_desc_warning_expired_patch,
        R.string.alarm_feat_btn_patch_discard
    )

    @Test fun `warning patch expired shares the expired-patch row`() = expect(
        AlarmCause.ALARM_WARNING_PATCH_EXPIRED,
        R.string.alarm_feat_title_warning_expired_patch,
        R.string.alarm_feat_desc_warning_expired_patch,
        R.string.alarm_notification_desc_warning_expired_patch,
        R.string.alarm_feat_btn_patch_discard
    )

    @Test fun `warning phase1 and warning expired map to identical resources`() {
        assertThat(AlarmCause.ALARM_WARNING_PATCH_EXPIRED_PHASE_1.stringResources())
            .isEqualTo(AlarmCause.ALARM_WARNING_PATCH_EXPIRED.stringResources())
    }

    @Test fun `warning low battery uses force-discard button`() = expect(
        AlarmCause.ALARM_WARNING_LOW_BATTERY,
        R.string.alarm_feat_title_warning_low_battery,
        R.string.alarm_feat_desc_warning_low_battery,
        R.string.alarm_notification_desc_warning_low_battery,
        R.string.alarm_feat_btn_patch_force_discard
    )

    @Test fun `warning invalid temperature`() = expect(
        AlarmCause.ALARM_WARNING_INVALID_TEMPERATURE,
        R.string.alarm_feat_title_warning_invalid_temperature,
        R.string.alarm_feat_desc_warning_invalid_temperature,
        R.string.alarm_notification_desc_warning_invalid_temperature,
        R.string.alarm_feat_btn_patch_discard
    )

    @Test fun `warning not used app uses resume-infusion button`() = expect(
        AlarmCause.ALARM_WARNING_NOT_USED_APP_AUTO_OFF,
        R.string.alarm_feat_title_warning_not_used_app,
        R.string.alarm_feat_desc_warning_not_used_app,
        R.string.alarm_notification_desc_warning_not_used_app,
        R.string.alarm_feat_btn_resume_infusion
    )

    @Test fun `warning ble not connected has null descriptions`() = expect(
        AlarmCause.ALARM_WARNING_BLE_NOT_CONNECTED,
        R.string.alarm_feat_title_warning_not_connected_ble,
        null,
        null,
        R.string.alarm_feat_btn_patch_force_discard
    )

    @Test fun `warning incomplete patch setting`() = expect(
        AlarmCause.ALARM_WARNING_INCOMPLETE_PATCH_SETTING,
        R.string.alarm_feat_title_warning_incomplete_patch_setting,
        R.string.alarm_feat_desc_warning_incomplete_patch_setting,
        R.string.alarm_notification_desc_warning_incomplete_patch_setting,
        R.string.alarm_feat_btn_patch_discard
    )

    @Test fun `warning self diagnosis failed`() = expect(
        AlarmCause.ALARM_WARNING_SELF_DIAGNOSIS_FAILED,
        R.string.alarm_feat_title_warning_failed_safety_check,
        R.string.alarm_feat_desc_warning_failed_safety_check,
        R.string.alarm_notification_desc_warning_failed_safety_check,
        R.string.alarm_feat_btn_patch_discard
    )

    @Test fun `warning patch error`() = expect(
        AlarmCause.ALARM_WARNING_PATCH_ERROR,
        R.string.alarm_feat_title_warning_patch_error,
        R.string.alarm_feat_desc_warning_patch_error,
        R.string.alarm_notification_desc_warning_patch_error,
        R.string.alarm_feat_btn_patch_discard
    )

    @Test fun `warning pump clogged`() = expect(
        AlarmCause.ALARM_WARNING_PUMP_CLOGGED,
        R.string.alarm_feat_title_warning_infusion_clogged,
        R.string.alarm_feat_desc_warning_infusion_clogged,
        R.string.alarm_notification_desc_warning_infusion_clogged,
        R.string.alarm_feat_btn_patch_discard
    )

    @Test fun `warning needle insertion error`() = expect(
        AlarmCause.ALARM_WARNING_NEEDLE_INSERTION_ERROR,
        R.string.alarm_feat_title_warning_needle_injection_error,
        R.string.alarm_feat_desc_warning_needle_injection_error,
        R.string.alarm_notification_desc_warning_needle_injection_error,
        R.string.alarm_feat_btn_patch_discard
    )

    // ---------------------------------------------------------------------------------------------
    // ALERT tier
    // ---------------------------------------------------------------------------------------------

    @Test fun `alert out of insulin`() = expect(
        AlarmCause.ALARM_ALERT_OUT_OF_INSULIN,
        R.string.alarm_feat_title_alert_low_insulin,
        R.string.alarm_feat_desc_alert_low_insulin,
        R.string.alarm_notification_desc_alert_low_insulin,
        R.string.common_btn_ok
    )

    @Test fun `alert patch expired phase 1`() = expect(
        AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_1,
        R.string.alarm_feat_title_alert_expired_patch_phase1,
        R.string.alarm_feat_desc_alert_expired_patch_phase1,
        R.string.alarm_notification_desc_alert_expired_patch_phase1,
        R.string.common_btn_ok
    )

    @Test fun `alert patch expired phase 2`() = expect(
        AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_2,
        R.string.alarm_feat_title_alert_expired_patch_phase2,
        R.string.alarm_feat_desc_alert_expired_patch_phase2,
        R.string.alarm_notification_desc_alert_expired_patch_phase2,
        R.string.common_btn_ok
    )

    @Test fun `alert phase1 and phase2 map to distinct resources`() {
        assertThat(AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_1.stringResources())
            .isNotEqualTo(AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_2.stringResources())
    }

    @Test fun `alert low battery`() = expect(
        AlarmCause.ALARM_ALERT_LOW_BATTERY,
        R.string.alarm_feat_title_alert_low_battery,
        R.string.alarm_feat_desc_alert_low_battery,
        R.string.alarm_notification_desc_alert_low_battery,
        R.string.common_btn_ok
    )

    @Test fun `alert invalid temperature`() = expect(
        AlarmCause.ALARM_ALERT_INVALID_TEMPERATURE,
        R.string.alarm_feat_title_alert_invalid_temperature,
        R.string.alarm_feat_desc_alert_invalid_temperature,
        R.string.alarm_notification_desc_alert_invalid_temperature,
        R.string.common_btn_ok
    )

    @Test fun `alert app no use`() = expect(
        AlarmCause.ALARM_ALERT_APP_NO_USE,
        R.string.alarm_feat_title_alert_not_used_app,
        R.string.alarm_feat_desc_alert_not_used_app,
        R.string.alarm_notification_desc_alert_not_used_app,
        R.string.common_btn_ok
    )

    @Test fun `alert ble not connected has null descriptions`() = expect(
        AlarmCause.ALARM_ALERT_BLE_NOT_CONNECTED,
        R.string.alarm_feat_title_alert_not_connected_ble,
        null,
        null,
        R.string.common_btn_ok
    )

    @Test fun `alert patch application incomplete`() = expect(
        AlarmCause.ALARM_ALERT_PATCH_APPLICATION_INCOMPLETE,
        R.string.alarm_feat_title_alert_incomplete_patch_setting,
        R.string.alarm_feat_desc_alert_incomplete_patch_setting,
        R.string.alarm_notification_desc_alert_incomplete_patch_setting,
        R.string.common_btn_ok
    )

    @Test fun `alert resume insulin delivery timeout uses resume-infusion button`() = expect(
        AlarmCause.ALARM_ALERT_RESUME_INSULIN_DELIVERY_TIMEOUT,
        R.string.alarm_feat_title_alert_resume_infusion,
        R.string.alarm_feat_desc_alert_resume_infusion,
        R.string.alarm_notification_desc_alert_resume_infusion,
        R.string.alarm_feat_btn_resume_infusion
    )

    @Test fun `alert bluetooth off`() = expect(
        AlarmCause.ALARM_ALERT_BLUETOOTH_OFF,
        R.string.alarm_feat_title_alert_off_bluetooth,
        R.string.alarm_feat_desc_alert_off_bluetooth,
        R.string.alarm_notification_desc_alert_off_bluetooth,
        R.string.common_btn_ok
    )

    // ---------------------------------------------------------------------------------------------
    // NOTICE tier
    // ---------------------------------------------------------------------------------------------

    @Test fun `notice low insulin`() = expect(
        AlarmCause.ALARM_NOTICE_LOW_INSULIN,
        R.string.alarm_feat_title_notice_low_insulin,
        R.string.alarm_feat_desc_notice_low_insulin,
        R.string.alarm_notification_desc_notice_low_insulin,
        R.string.common_btn_ok
    )

    @Test fun `notice patch expired`() = expect(
        AlarmCause.ALARM_NOTICE_PATCH_EXPIRED,
        R.string.alarm_feat_title_notice_expired_patch,
        R.string.alarm_feat_desc_notice_expired_patch,
        R.string.alarm_notification_desc_notice_expired_patch,
        R.string.common_btn_ok
    )

    @Test fun `notice attach patch check has null descriptions`() = expect(
        AlarmCause.ALARM_NOTICE_ATTACH_PATCH_CHECK,
        R.string.alarm_feat_title_notice_check_patch,
        null,
        null,
        R.string.common_btn_ok
    )

    @Test fun `notice bg check`() = expect(
        AlarmCause.ALARM_NOTICE_BG_CHECK,
        R.string.alarm_feat_title_notice_check_bg,
        R.string.alarm_feat_desc_notice_check_bg,
        R.string.alarm_notification_desc_notice_check_bg,
        R.string.common_btn_ok
    )

    @Test fun `notice time zone changed`() = expect(
        AlarmCause.ALARM_NOTICE_TIME_ZONE_CHANGED,
        R.string.alarm_feat_title_notice_change_time_zone,
        R.string.alarm_feat_desc_notice_change_time_zone,
        R.string.alarm_notification_desc_notice_change_time_zone,
        R.string.common_btn_ok
    )

    @Test fun `notice lgs start`() = expect(
        AlarmCause.ALARM_NOTICE_LGS_START,
        R.string.alarm_feat_title_notice_lgs_started,
        R.string.alarm_feat_desc_notice_lgs_started,
        R.string.alarm_notification_desc_notice_lgs_started,
        R.string.common_btn_ok
    )

    @Test fun `notice lgs finished disconnected patch or cgm`() = expect(
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_DISCONNECTED_PATCH_OR_CGM,
        R.string.alarm_feat_title_notice_lgs_ended,
        R.string.alarm_feat_desc_notice_lgs_ended_disconnected_patch_or_cgm,
        R.string.alarm_notification_desc_notice_lgs_ended_disconnected_patch_or_cgm,
        R.string.common_btn_ok
    )

    @Test fun `notice lgs finished pause lgs`() = expect(
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_PAUSE_LGS,
        R.string.alarm_feat_title_notice_lgs_ended,
        R.string.alarm_feat_desc_notice_lgs_ended_pause_lgs,
        R.string.alarm_notification_desc_notice_lgs_ended_pause_lgs,
        R.string.common_btn_ok
    )

    @Test fun `notice lgs finished time over`() = expect(
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_TIME_OVER,
        R.string.alarm_feat_title_notice_lgs_ended,
        R.string.alarm_feat_desc_notice_lgs_ended_time_over,
        R.string.alarm_notification_desc_notice_lgs_ended_time_over,
        R.string.common_btn_ok
    )

    @Test fun `notice lgs finished off lgs`() = expect(
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_OFF_LGS,
        R.string.alarm_feat_title_notice_lgs_ended,
        R.string.alarm_feat_desc_notice_lgs_ended_off_lgs,
        R.string.alarm_notification_desc_notice_lgs_ended_off_lgs,
        R.string.common_btn_ok
    )

    @Test fun `notice lgs finished high bg`() = expect(
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_HIGH_BG,
        R.string.alarm_feat_title_notice_lgs_ended,
        R.string.alarm_feat_desc_notice_lgs_ended_high_bg,
        R.string.alarm_notification_desc_notice_lgs_ended_high_bg,
        R.string.common_btn_ok
    )

    @Test fun `notice lgs finished unknown`() = expect(
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_UNKNOWN,
        R.string.alarm_feat_title_notice_lgs_ended,
        R.string.alarm_feat_desc_notice_lgs_ended_unknown,
        R.string.alarm_notification_desc_notice_lgs_ended_unknown,
        R.string.common_btn_ok
    )

    @Test fun `all lgs-finished causes share the ended title but differ in description`() {
        val lgsEnded = listOf(
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_DISCONNECTED_PATCH_OR_CGM,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_PAUSE_LGS,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_TIME_OVER,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_OFF_LGS,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_HIGH_BG,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_UNKNOWN
        )
        // one shared title
        assertThat(lgsEnded.map { it.stringResources().titleRes }.toSet())
            .containsExactly(R.string.alarm_feat_title_notice_lgs_ended)
        // but six distinct screen descriptions
        assertThat(lgsEnded.map { it.stringResources().screenDescRes }.toSet()).hasSize(6)
    }

    @Test fun `notice lgs not working`() = expect(
        AlarmCause.ALARM_NOTICE_LGS_NOT_WORKING,
        R.string.alarm_feat_title_notice_lgs_error,
        R.string.alarm_feat_desc_notice_lgs_error,
        R.string.alarm_notification_desc_notice_lgs_error,
        R.string.common_btn_ok
    )

    // ---------------------------------------------------------------------------------------------
    // UNKNOWN
    // ---------------------------------------------------------------------------------------------

    @Test fun `unknown reuses the same desc for screen and notification`() = expect(
        AlarmCause.ALARM_UNKNOWN,
        R.string.alarm_feat_title_notice_unknown,
        R.string.alarm_feat_desc_unknown,
        R.string.alarm_feat_desc_unknown,
        R.string.common_btn_ok
    )

    @Test fun `unknown screen and notification descriptions are identical`() {
        val sr = AlarmCause.ALARM_UNKNOWN.stringResources()
        assertThat(sr.screenDescRes).isEqualTo(sr.notificationDescRes)
    }

    // ---------------------------------------------------------------------------------------------
    // Table-wide invariants (drives every when-branch a second way)
    // ---------------------------------------------------------------------------------------------

    @Test fun `every cause maps to a non-empty resource table`() {
        for (cause in AlarmCause.entries) {
            val sr = cause.stringResources()
            assertThat(sr.titleRes).isNotEqualTo(0)
            assertThat(sr.btnRes).isNotEqualTo(0)
        }
    }

    @Test fun `transformStringResources projects title-screen-button for every cause`() {
        for (cause in AlarmCause.entries) {
            val sr = cause.stringResources()
            assertThat(cause.transformStringResources())
                .isEqualTo(Triple(sr.titleRes, sr.screenDescRes, sr.btnRes))
        }
    }

    @Test fun `transformNotificationStringResources projects title-notification-button for every cause`() {
        for (cause in AlarmCause.entries) {
            val sr = cause.stringResources()
            assertThat(cause.transformNotificationStringResources())
                .isEqualTo(Triple(sr.titleRes, sr.notificationDescRes, sr.btnRes))
        }
    }

    @Test fun `only ble-not-connected and attach-patch-check causes have null descriptions`() {
        val nullDescCauses = AlarmCause.entries.filter {
            it.stringResources().screenDescRes == null
        }.toSet()
        assertThat(nullDescCauses).containsExactly(
            AlarmCause.ALARM_WARNING_BLE_NOT_CONNECTED,
            AlarmCause.ALARM_ALERT_BLE_NOT_CONNECTED,
            AlarmCause.ALARM_NOTICE_ATTACH_PATCH_CHECK
        )
    }

    @Test fun `a null screen description implies a null notification description`() {
        for (cause in AlarmCause.entries) {
            val sr = cause.stringResources()
            if (sr.screenDescRes == null) assertThat(sr.notificationDescRes).isNull()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // AlarmStringResources data class
    // ---------------------------------------------------------------------------------------------

    @Test fun `AlarmStringResources value equality and copy`() {
        val a = AlarmStringResources(1, 2, 3, 4)
        val b = AlarmStringResources(1, 2, 3, 4)
        assertThat(a).isEqualTo(b)
        assertThat(a.copy(screenDescRes = null)).isEqualTo(AlarmStringResources(1, null, 3, 4))
        assertThat(a.copy(screenDescRes = null)).isNotEqualTo(b)
    }

    @Test fun `AlarmStringResources holds all four fields`() {
        val sr = AlarmStringResources(titleRes = 10, screenDescRes = 20, notificationDescRes = 30, btnRes = 40)
        assertThat(sr.titleRes).isEqualTo(10)
        assertThat(sr.screenDescRes).isEqualTo(20)
        assertThat(sr.notificationDescRes).isEqualTo(30)
        assertThat(sr.btnRes).isEqualTo(40)
    }
}
