package app.aaps.pump.carelevo.ext

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.carelevo.CarelevoStrings
import app.aaps.pump.carelevo.domain.type.AlarmCause
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Covers the full [AlarmCause] -> string-resource table in `AlarmExt.kt`.
 *
 * Expected texts are asserted against the same `CarelevoStrings.*` names the production code uses, so the test
 * verifies the *mapping* without needing a Context.
 */
internal class AlarmExtTest {

    private fun expect(
        cause: AlarmCause,
        title: TextRef,
        notification: TextRef?,
        btn: TextRef
    ) {
        val actual = cause.stringResources()
        assertThat(actual).isEqualTo(AlarmStringResources(title, notification, btn))
        // the transform helper must be a pure projection of the same table row
        assertThat(cause.transformNotificationStringResources()).isEqualTo(Triple(title, notification, btn))
    }

    // ---------------------------------------------------------------------------------------------
    // WARNING tier
    // ---------------------------------------------------------------------------------------------

    @Test fun `warning low insulin`() = expect(
        AlarmCause.ALARM_WARNING_LOW_INSULIN,
        CarelevoStrings.alarm_feat_title_warning_low_insulin,
        CarelevoStrings.alarm_notification_desc_warning_low_insulin,
        CarelevoStrings.alarm_feat_btn_patch_discard
    )

    @Test fun `warning patch expired phase 1 shares the expired-patch row`() = expect(
        AlarmCause.ALARM_WARNING_PATCH_EXPIRED_PHASE_1,
        CarelevoStrings.alarm_feat_title_warning_expired_patch,
        CarelevoStrings.alarm_notification_desc_warning_expired_patch,
        CarelevoStrings.alarm_feat_btn_patch_discard
    )

    @Test fun `warning patch expired shares the expired-patch row`() = expect(
        AlarmCause.ALARM_WARNING_PATCH_EXPIRED,
        CarelevoStrings.alarm_feat_title_warning_expired_patch,
        CarelevoStrings.alarm_notification_desc_warning_expired_patch,
        CarelevoStrings.alarm_feat_btn_patch_discard
    )

    @Test fun `warning phase1 and warning expired map to identical resources`() {
        assertThat(AlarmCause.ALARM_WARNING_PATCH_EXPIRED_PHASE_1.stringResources())
            .isEqualTo(AlarmCause.ALARM_WARNING_PATCH_EXPIRED.stringResources())
    }

    @Test fun `warning low battery uses force-discard button`() = expect(
        AlarmCause.ALARM_WARNING_LOW_BATTERY,
        CarelevoStrings.alarm_feat_title_warning_low_battery,
        CarelevoStrings.alarm_notification_desc_warning_low_battery,
        CarelevoStrings.alarm_feat_btn_patch_force_discard
    )

    @Test fun `warning invalid temperature`() = expect(
        AlarmCause.ALARM_WARNING_INVALID_TEMPERATURE,
        CarelevoStrings.alarm_feat_title_warning_invalid_temperature,
        CarelevoStrings.alarm_notification_desc_warning_invalid_temperature,
        CarelevoStrings.alarm_feat_btn_patch_discard
    )

    @Test fun `warning not used app uses resume-infusion button`() = expect(
        AlarmCause.ALARM_WARNING_NOT_USED_APP_AUTO_OFF,
        CarelevoStrings.alarm_feat_title_warning_not_used_app,
        CarelevoStrings.alarm_notification_desc_warning_not_used_app,
        CarelevoStrings.alarm_feat_btn_resume_infusion
    )

    @Test fun `warning ble not connected has null descriptions`() = expect(
        AlarmCause.ALARM_WARNING_BLE_NOT_CONNECTED,
        CarelevoStrings.alarm_feat_title_warning_not_connected_ble,
        null,
        CarelevoStrings.alarm_feat_btn_patch_force_discard
    )

    @Test fun `warning incomplete patch setting`() = expect(
        AlarmCause.ALARM_WARNING_INCOMPLETE_PATCH_SETTING,
        CarelevoStrings.alarm_feat_title_warning_incomplete_patch_setting,
        CarelevoStrings.alarm_notification_desc_warning_incomplete_patch_setting,
        CarelevoStrings.alarm_feat_btn_patch_discard
    )

    @Test fun `warning self diagnosis failed`() = expect(
        AlarmCause.ALARM_WARNING_SELF_DIAGNOSIS_FAILED,
        CarelevoStrings.alarm_feat_title_warning_failed_safety_check,
        CarelevoStrings.alarm_notification_desc_warning_failed_safety_check,
        CarelevoStrings.alarm_feat_btn_patch_discard
    )

    @Test fun `warning patch error`() = expect(
        AlarmCause.ALARM_WARNING_PATCH_ERROR,
        CarelevoStrings.alarm_feat_title_warning_patch_error,
        CarelevoStrings.alarm_notification_desc_warning_patch_error,
        CarelevoStrings.alarm_feat_btn_patch_discard
    )

    @Test fun `warning pump clogged`() = expect(
        AlarmCause.ALARM_WARNING_PUMP_CLOGGED,
        CarelevoStrings.alarm_feat_title_warning_infusion_clogged,
        CarelevoStrings.alarm_notification_desc_warning_infusion_clogged,
        CarelevoStrings.alarm_feat_btn_patch_discard
    )

    @Test fun `warning needle insertion error`() = expect(
        AlarmCause.ALARM_WARNING_NEEDLE_INSERTION_ERROR,
        CarelevoStrings.alarm_feat_title_warning_needle_injection_error,
        CarelevoStrings.alarm_notification_desc_warning_needle_injection_error,
        CarelevoStrings.alarm_feat_btn_patch_discard
    )

    // ---------------------------------------------------------------------------------------------
    // ALERT tier
    // ---------------------------------------------------------------------------------------------

    @Test fun `alert out of insulin`() = expect(
        AlarmCause.ALARM_ALERT_OUT_OF_INSULIN,
        CarelevoStrings.alarm_feat_title_alert_low_insulin,
        CarelevoStrings.alarm_notification_desc_alert_low_insulin,
        CarelevoStrings.common_btn_ok
    )

    @Test fun `alert patch expired phase 1`() = expect(
        AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_1,
        CarelevoStrings.alarm_feat_title_alert_expired_patch_phase1,
        CarelevoStrings.alarm_notification_desc_alert_expired_patch_phase1,
        CarelevoStrings.common_btn_ok
    )

    @Test fun `alert patch expired phase 2`() = expect(
        AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_2,
        CarelevoStrings.alarm_feat_title_alert_expired_patch_phase2,
        CarelevoStrings.alarm_notification_desc_alert_expired_patch_phase2,
        CarelevoStrings.common_btn_ok
    )

    @Test fun `alert phase1 and phase2 map to distinct resources`() {
        assertThat(AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_1.stringResources())
            .isNotEqualTo(AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_2.stringResources())
    }

    @Test fun `alert low battery`() = expect(
        AlarmCause.ALARM_ALERT_LOW_BATTERY,
        CarelevoStrings.alarm_feat_title_alert_low_battery,
        CarelevoStrings.alarm_notification_desc_alert_low_battery,
        CarelevoStrings.common_btn_ok
    )

    @Test fun `alert invalid temperature`() = expect(
        AlarmCause.ALARM_ALERT_INVALID_TEMPERATURE,
        CarelevoStrings.alarm_feat_title_alert_invalid_temperature,
        CarelevoStrings.alarm_notification_desc_alert_invalid_temperature,
        CarelevoStrings.common_btn_ok
    )

    @Test fun `alert app no use`() = expect(
        AlarmCause.ALARM_ALERT_APP_NO_USE,
        CarelevoStrings.alarm_feat_title_alert_not_used_app,
        CarelevoStrings.alarm_notification_desc_alert_not_used_app,
        CarelevoStrings.common_btn_ok
    )

    @Test fun `alert ble not connected has null descriptions`() = expect(
        AlarmCause.ALARM_ALERT_BLE_NOT_CONNECTED,
        CarelevoStrings.alarm_feat_title_alert_not_connected_ble,
        null,
        CarelevoStrings.common_btn_ok
    )

    @Test fun `alert patch application incomplete`() = expect(
        AlarmCause.ALARM_ALERT_PATCH_APPLICATION_INCOMPLETE,
        CarelevoStrings.alarm_feat_title_alert_incomplete_patch_setting,
        CarelevoStrings.alarm_notification_desc_alert_incomplete_patch_setting,
        CarelevoStrings.common_btn_ok
    )

    @Test fun `alert resume insulin delivery timeout uses resume-infusion button`() = expect(
        AlarmCause.ALARM_ALERT_RESUME_INSULIN_DELIVERY_TIMEOUT,
        CarelevoStrings.alarm_feat_title_alert_resume_infusion,
        CarelevoStrings.alarm_notification_desc_alert_resume_infusion,
        CarelevoStrings.alarm_feat_btn_resume_infusion
    )

    @Test fun `alert bluetooth off`() = expect(
        AlarmCause.ALARM_ALERT_BLUETOOTH_OFF,
        CarelevoStrings.alarm_feat_title_alert_off_bluetooth,
        CarelevoStrings.alarm_notification_desc_alert_off_bluetooth,
        CarelevoStrings.common_btn_ok
    )

    // ---------------------------------------------------------------------------------------------
    // NOTICE tier
    // ---------------------------------------------------------------------------------------------

    @Test fun `notice low insulin`() = expect(
        AlarmCause.ALARM_NOTICE_LOW_INSULIN,
        CarelevoStrings.alarm_feat_title_notice_low_insulin,
        CarelevoStrings.alarm_notification_desc_notice_low_insulin,
        CarelevoStrings.common_btn_ok
    )

    @Test fun `notice patch expired`() = expect(
        AlarmCause.ALARM_NOTICE_PATCH_EXPIRED,
        CarelevoStrings.alarm_feat_title_notice_expired_patch,
        CarelevoStrings.alarm_notification_desc_notice_expired_patch,
        CarelevoStrings.common_btn_ok
    )

    @Test fun `notice attach patch check has null descriptions`() = expect(
        AlarmCause.ALARM_NOTICE_ATTACH_PATCH_CHECK,
        CarelevoStrings.alarm_feat_title_notice_check_patch,
        null,
        CarelevoStrings.common_btn_ok
    )

    @Test fun `notice bg check`() = expect(
        AlarmCause.ALARM_NOTICE_BG_CHECK,
        CarelevoStrings.alarm_feat_title_notice_check_bg,
        CarelevoStrings.alarm_notification_desc_notice_check_bg,
        CarelevoStrings.common_btn_ok
    )

    @Test fun `notice time zone changed`() = expect(
        AlarmCause.ALARM_NOTICE_TIME_ZONE_CHANGED,
        CarelevoStrings.alarm_feat_title_notice_change_time_zone,
        CarelevoStrings.alarm_notification_desc_notice_change_time_zone,
        CarelevoStrings.common_btn_ok
    )

    @Test fun `notice lgs start`() = expect(
        AlarmCause.ALARM_NOTICE_LGS_START,
        CarelevoStrings.alarm_feat_title_notice_lgs_started,
        CarelevoStrings.alarm_notification_desc_notice_lgs_started,
        CarelevoStrings.common_btn_ok
    )

    @Test fun `notice lgs finished disconnected patch or cgm`() = expect(
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_DISCONNECTED_PATCH_OR_CGM,
        CarelevoStrings.alarm_feat_title_notice_lgs_ended,
        CarelevoStrings.alarm_notification_desc_notice_lgs_ended_disconnected_patch_or_cgm,
        CarelevoStrings.common_btn_ok
    )

    @Test fun `notice lgs finished pause lgs`() = expect(
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_PAUSE_LGS,
        CarelevoStrings.alarm_feat_title_notice_lgs_ended,
        CarelevoStrings.alarm_notification_desc_notice_lgs_ended_pause_lgs,
        CarelevoStrings.common_btn_ok
    )

    @Test fun `notice lgs finished time over`() = expect(
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_TIME_OVER,
        CarelevoStrings.alarm_feat_title_notice_lgs_ended,
        CarelevoStrings.alarm_notification_desc_notice_lgs_ended_time_over,
        CarelevoStrings.common_btn_ok
    )

    @Test fun `notice lgs finished off lgs`() = expect(
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_OFF_LGS,
        CarelevoStrings.alarm_feat_title_notice_lgs_ended,
        CarelevoStrings.alarm_notification_desc_notice_lgs_ended_off_lgs,
        CarelevoStrings.common_btn_ok
    )

    @Test fun `notice lgs finished high bg`() = expect(
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_HIGH_BG,
        CarelevoStrings.alarm_feat_title_notice_lgs_ended,
        CarelevoStrings.alarm_notification_desc_notice_lgs_ended_high_bg,
        CarelevoStrings.common_btn_ok
    )

    @Test fun `notice lgs finished unknown`() = expect(
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_UNKNOWN,
        CarelevoStrings.alarm_feat_title_notice_lgs_ended,
        CarelevoStrings.alarm_notification_desc_notice_lgs_ended_unknown,
        CarelevoStrings.common_btn_ok
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
            .containsExactly(CarelevoStrings.alarm_feat_title_notice_lgs_ended)
        // but six distinct descriptions
        assertThat(lgsEnded.map { it.stringResources().notificationDescRes }.toSet()).hasSize(6)
    }

    @Test fun `notice lgs not working`() = expect(
        AlarmCause.ALARM_NOTICE_LGS_NOT_WORKING,
        CarelevoStrings.alarm_feat_title_notice_lgs_error,
        CarelevoStrings.alarm_notification_desc_notice_lgs_error,
        CarelevoStrings.common_btn_ok
    )

    // ---------------------------------------------------------------------------------------------
    // UNKNOWN
    // ---------------------------------------------------------------------------------------------

    @Test fun `unknown`() = expect(
        AlarmCause.ALARM_UNKNOWN,
        CarelevoStrings.alarm_feat_title_notice_unknown,
        CarelevoStrings.alarm_feat_desc_unknown,
        CarelevoStrings.common_btn_ok
    )

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

    @Test fun `transformNotificationStringResources projects title-notification-button for every cause`() {
        for (cause in AlarmCause.entries) {
            val sr = cause.stringResources()
            assertThat(cause.transformNotificationStringResources())
                .isEqualTo(Triple(sr.titleRes, sr.notificationDescRes, sr.btnRes))
        }
    }

    @Test fun `only ble-not-connected and attach-patch-check causes have null descriptions`() {
        val nullDescCauses = AlarmCause.entries.filter {
            it.stringResources().notificationDescRes == null
        }.toSet()
        assertThat(nullDescCauses).containsExactly(
            AlarmCause.ALARM_WARNING_BLE_NOT_CONNECTED,
            AlarmCause.ALARM_ALERT_BLE_NOT_CONNECTED,
            AlarmCause.ALARM_NOTICE_ATTACH_PATCH_CHECK
        )
    }

    // ---------------------------------------------------------------------------------------------
    // AlarmStringResources data class
    // ---------------------------------------------------------------------------------------------

    @Test fun `AlarmStringResources value equality and copy`() {
        val a = AlarmStringResources(TITLE, DESC, BUTTON)
        val b = AlarmStringResources(TITLE, DESC, BUTTON)
        assertThat(a).isEqualTo(b)
        assertThat(a.copy(notificationDescRes = null)).isEqualTo(AlarmStringResources(TITLE, null, BUTTON))
        assertThat(a.copy(notificationDescRes = null)).isNotEqualTo(b)
    }

    @Test fun `AlarmStringResources holds all three fields`() {
        val sr = AlarmStringResources(titleRes = TITLE, notificationDescRes = DESC, btnRes = BUTTON)
        assertThat(sr.titleRes).isEqualTo(TITLE)
        assertThat(sr.notificationDescRes).isEqualTo(DESC)
        assertThat(sr.btnRes).isEqualTo(BUTTON)
    }

    private companion object {

        val TITLE: TextRef = TextRef.Literal("title")
        val DESC: TextRef = TextRef.Literal("description")
        val BUTTON: TextRef = TextRef.Literal("button")
    }
}
