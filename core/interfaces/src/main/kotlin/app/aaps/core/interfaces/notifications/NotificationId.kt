package app.aaps.core.interfaces.notifications

import app.aaps.core.interfaces.notifications.NotificationCategory.AUTOMATION
import app.aaps.core.interfaces.notifications.NotificationCategory.CGM
import app.aaps.core.interfaces.notifications.NotificationCategory.LOOP
import app.aaps.core.interfaces.notifications.NotificationCategory.PROFILE
import app.aaps.core.interfaces.notifications.NotificationCategory.PUMP
import app.aaps.core.interfaces.notifications.NotificationCategory.SYNC
import app.aaps.core.interfaces.notifications.NotificationCategory.SYSTEM
import app.aaps.core.interfaces.notifications.NotificationId.Companion.fromOrdinal
import app.aaps.core.interfaces.notifications.NotificationLevel.ANNOUNCEMENT
import app.aaps.core.interfaces.notifications.NotificationLevel.IMPORTANT
import app.aaps.core.interfaces.notifications.NotificationLevel.INFO
import app.aaps.core.interfaces.notifications.NotificationLevel.LOW
import app.aaps.core.interfaces.notifications.NotificationLevel.NORMAL
import app.aaps.core.interfaces.notifications.NotificationLevel.URGENT

/**
 * Identity + intrinsic severity of every AAPS notification.
 *
 * [defaultLevel] is the single source of truth for severity — a caller should not normally
 * override it at the post site. [NotificationLevel.URGENT] is the alarm tier (sound + ramp +
 * full-screen) and is reserved for acute insulin-delivery failures, critical BG, and
 * user-configured alarms.
 *
 * The system-notification id is derived from [Enum.ordinal] (see [fromOrdinal]); there is no
 * hand-assigned integer id anymore (the old pre-enum `legacyId` was a fossil with no external
 * consumer).
 */
@Suppress("unused")
enum class NotificationId(
    val defaultLevel: NotificationLevel,
    val category: NotificationCategory,
    val allowMultiple: Boolean = false
) {

    // Profile
    PROFILE_SET_OK(INFO, PROFILE),
    PROFILE_NOT_SET_NOT_INITIALIZED(NORMAL, PROFILE),

    // Basal profile failed to write to the pump (wrong basal until fixed). Also covers the old
    // DanaR-only PROFILE_SET_FAILED, which was merged here.
    FAILED_UPDATE_PROFILE(URGENT, PROFILE),
    INVALID_PROFILE_NOT_ACCEPTED(NORMAL, PROFILE),

    // Pump — general
    EXTENDED_BOLUS_DISABLED(IMPORTANT, PUMP),
    PUMP_ERROR(URGENT, PUMP),
    // A user/remote (non-SMB) bolus failed to deliver — surfaced once, here, from the executor (the entry
    // dialog is gone by the time the async result arrives). SMB failures stay silent (the loop self-corrects).
    BOLUS_DELIVERY_FAILED(URGENT, PUMP),
    WRONG_SERIAL_NUMBER(NORMAL, PUMP),
    WRONG_BASAL_STEP(NORMAL, PUMP),
    WRONG_DRIVER(NORMAL, PUMP),
    PUMP_UNREACHABLE(URGENT, PUMP),
    UNSUPPORTED_FIRMWARE(IMPORTANT, PUMP),
    MINIMAL_BASAL_VALUE_REPLACED(NORMAL, PUMP),
    BASAL_PROFILE_NOT_ALIGNED_TO_HOURS(NORMAL, PUMP),
    WRONG_PUMP_PASSWORD(IMPORTANT, PUMP),
    MAXIMUM_BASAL_VALUE_REPLACED(NORMAL, PUMP),
    DEVICE_NOT_PAIRED(NORMAL, PUMP),
    UNSUPPORTED_ACTION_IN_PUMP(NORMAL, PUMP),
    WRONG_PUMP_DATA(NORMAL, PUMP),
    PUMP_SUSPENDED(NORMAL, PUMP),
    BLUETOOTH_NOT_ENABLED(INFO, PUMP),
    PATCH_NOT_ACTIVE(NORMAL, PUMP),
    PUMP_SETTINGS_FAILED(NORMAL, PUMP),
    PUMP_TIMEZONE_UPDATE_FAILED(NORMAL, PUMP),
    BLUETOOTH_NOT_SUPPORTED(IMPORTANT, PUMP),
    PUMP_WARNING(NORMAL, PUMP),
    PUMP_SYNC_ERROR(NORMAL, PUMP),
    BASAL_VALUE_BELOW_MINIMUM(NORMAL, PUMP),

    // Pump — Combo
    COMBO_PUMP_ALARM(URGENT, PUMP),
    COMBO_UNKNOWN_TBR(LOW, PUMP),

    // Pump — Medtronic
    MEDTRONIC_PUMP_ALARM(URGENT, PUMP),
    RILEYLINK_CONNECTION(NORMAL, PUMP),
    MDT_INVALID_HISTORY_DATA(NORMAL, PUMP),

    // Pump — Insight
    INSIGHT_DATE_TIME_UPDATED(INFO, PUMP),
    INSIGHT_TIMEOUT_DURING_HANDSHAKE(NORMAL, PUMP),

    // Pump — Omnipod
    OMNIPOD_POD_NOT_ATTACHED(NORMAL, PUMP),
    OMNIPOD_POD_SUSPENDED(NORMAL, PUMP),
    OMNIPOD_POD_ALERTS_UPDATED(INFO, PUMP),
    OMNIPOD_POD_ALERTS(URGENT, PUMP),
    OMNIPOD_TBR_ALERTS(LOW, PUMP),
    OMNIPOD_POD_FAULT(URGENT, PUMP),
    OMNIPOD_UNCERTAIN_SMB(NORMAL, PUMP),
    OMNIPOD_UNKNOWN_TBR(LOW, PUMP),
    OMNIPOD_STARTUP_STATUS_REFRESH_FAILED(NORMAL, PUMP),
    OMNIPOD_TIME_OUT_OF_SYNC(LOW, PUMP),

    // Pump — EOPatch
    EOFLOW_PATCH_ALERT(URGENT, PUMP, allowMultiple = true),

    // Pump — Equil
    EQUIL_ALARM(URGENT, PUMP),
    EQUIL_ALARM_INSULIN(URGENT, PUMP),

    // Pump — Dana
    DANA_PUMP_ALARM(URGENT, PUMP),

    // Pump — Dana emulator
    PUMP_EMULATOR_DISPLAY(INFO, PUMP),

    // CGM
    BG_READINGS_MISSED(URGENT, CGM),
    SENSOR_CHANGE_DETECTED(NORMAL, CGM),

    // CGM — Aidex
    AIDEX_SENSOR_EXPIRED(IMPORTANT, CGM),
    AIDEX_SENSOR_ERROR(IMPORTANT, CGM),
    AIDEX_SENSOR_STABILIZING(NORMAL, CGM),
    AIDEX_REPLACE_SENSOR(NORMAL, CGM),
    AIDEX_SIGNAL_LOST(NORMAL, CGM),

    // Loop / APS
    EASY_MODE_ENABLED(IMPORTANT, LOOP),
    UD_MODE_ENABLED(IMPORTANT, LOOP),
    SHORT_DIA(IMPORTANT, LOOP),
    CARBS_REQUIRED(NORMAL, LOOP),
    SMB_FALLBACK(NORMAL, LOOP),
    DYN_ISF_FALLBACK(NORMAL, LOOP),

    // Sync — Nightscout
    OLD_NS(IMPORTANT, SYNC),
    NSCLIENT_NO_WRITE_PERMISSION(NORMAL, SYNC),
    NS_ANNOUNCEMENT(ANNOUNCEMENT, SYNC),
    NS_ALARM(URGENT, SYNC),
    NS_URGENT_ALARM(URGENT, SYNC),
    NS_MALFUNCTION(IMPORTANT, SYNC),
    NSCLIENT_VERSION_DOES_NOT_MATCH(NORMAL, SYNC),
    NSCLIENT_PAIRING_ORPHAN(NORMAL, SYNC),
    OPEN_HUMANS_SIGNED_OUT(NORMAL, SYNC),

    // Sync — SMS
    INVALID_PHONE_NUMBER(IMPORTANT, SYNC),
    INVALID_MESSAGE_BODY(NORMAL, SYNC),
    APPROACHING_DAILY_LIMIT(IMPORTANT, SYNC),

    // System
    TOAST_ALARM(URGENT, SYSTEM),
    DST_LOOP_DISABLED(IMPORTANT, SYSTEM),
    DST_IN_24H(LOW, SYSTEM),
    DISK_FULL(IMPORTANT, SYSTEM),
    OVER_24H_TIME_CHANGE_REQUESTED(LOW, SYSTEM),
    INVALID_VERSION(IMPORTANT, SYSTEM),
    TIME_OR_TIMEZONE_CHANGE(NORMAL, SYSTEM),
    NEW_VERSION_DETECTED(NORMAL, SYSTEM),
    VERSION_EXPIRE(IMPORTANT, SYSTEM),
    IDENTIFICATION_NOT_SET(NORMAL, SYSTEM),
    MASTER_PASSWORD_NOT_SET(IMPORTANT, SYSTEM),
    AAPS_DIR_NOT_SELECTED(NORMAL, SYSTEM),
    GOOGLE_DRIVE_ERROR(IMPORTANT, SYSTEM),
    SETTINGS_EXPORT_RESULT(INFO, SYSTEM),
    SNACKBAR_FALLBACK(NORMAL, SYSTEM, allowMultiple = true),

    // Automation — general notification action (NOT the "Alarm" action, which uses the system
    // alarm clock via TimerUtil.scheduleReminder, not this notification path).
    AUTOMATION_MESSAGE(IMPORTANT, AUTOMATION, allowMultiple = true),

    // Scenes
    SCENE_ENDED(INFO, AUTOMATION, allowMultiple = true),
    SCENE_CHAINED(INFO, AUTOMATION, allowMultiple = true),
    SCENE_CHAIN_SKIPPED(NORMAL, AUTOMATION, allowMultiple = true),
    SCENE_CHAIN_ERROR(IMPORTANT, AUTOMATION, allowMultiple = true);

    companion object {

        fun fromOrdinal(ordinal: Int): NotificationId? = entries.getOrNull(ordinal)
    }
}
