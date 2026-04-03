package app.aaps.core.interfaces.notifications

import app.aaps.core.interfaces.notifications.NotificationCategory.AUTOMATION
import app.aaps.core.interfaces.notifications.NotificationCategory.CGM
import app.aaps.core.interfaces.notifications.NotificationCategory.LOOP
import app.aaps.core.interfaces.notifications.NotificationCategory.PROFILE
import app.aaps.core.interfaces.notifications.NotificationCategory.PUMP
import app.aaps.core.interfaces.notifications.NotificationCategory.SYNC
import app.aaps.core.interfaces.notifications.NotificationCategory.SYSTEM
import app.aaps.core.interfaces.notifications.NotificationLevel.ANNOUNCEMENT
import app.aaps.core.interfaces.notifications.NotificationLevel.INFO
import app.aaps.core.interfaces.notifications.NotificationLevel.LOW
import app.aaps.core.interfaces.notifications.NotificationLevel.NORMAL
import app.aaps.core.interfaces.notifications.NotificationLevel.URGENT

@Suppress("unused")
enum class NotificationId(
    val legacyId: Int,
    val defaultLevel: NotificationLevel,
    val category: NotificationCategory,
    val allowMultiple: Boolean = false
) {

    // Profile
    PROFILE_SET_FAILED(0, URGENT, PROFILE),
    PROFILE_SET_OK(1, INFO, PROFILE),
    PROFILE_NOT_SET_NOT_INITIALIZED(5, NORMAL, PROFILE),
    FAILED_UPDATE_PROFILE(6, NORMAL, PROFILE),
    INVALID_PROFILE_NOT_ACCEPTED(75, NORMAL, PROFILE),

    // Pump — general
    EXTENDED_BOLUS_DISABLED(3, URGENT, PUMP),
    PUMP_ERROR(15, URGENT, PUMP),
    WRONG_SERIAL_NUMBER(16, NORMAL, PUMP),
    WRONG_BASAL_STEP(23, NORMAL, PUMP),
    WRONG_DRIVER(24, NORMAL, PUMP),
    PUMP_UNREACHABLE(26, URGENT, PUMP),
    UNSUPPORTED_FIRMWARE(28, URGENT, PUMP),
    MINIMAL_BASAL_VALUE_REPLACED(29, NORMAL, PUMP),
    BASAL_PROFILE_NOT_ALIGNED_TO_HOURS(30, NORMAL, PUMP),
    WRONG_PUMP_PASSWORD(34, URGENT, PUMP),
    MAXIMUM_BASAL_VALUE_REPLACED(39, NORMAL, PUMP),
    DEVICE_NOT_PAIRED(43, NORMAL, PUMP),
    UNSUPPORTED_ACTION_IN_PUMP(71, NORMAL, PUMP),
    WRONG_PUMP_DATA(72, NORMAL, PUMP),
    PUMP_SUSPENDED(80, NORMAL, PUMP),
    BLUETOOTH_NOT_ENABLED(82, INFO, PUMP),
    PATCH_NOT_ACTIVE(83, NORMAL, PUMP),
    PUMP_SETTINGS_FAILED(84, NORMAL, PUMP),
    PUMP_TIMEZONE_UPDATE_FAILED(85, NORMAL, PUMP),
    BLUETOOTH_NOT_SUPPORTED(86, URGENT, PUMP),
    PUMP_WARNING(87, NORMAL, PUMP),
    PUMP_SYNC_ERROR(88, NORMAL, PUMP),
    BASAL_VALUE_BELOW_MINIMUM(7, NORMAL, PUMP),

    // Pump — Combo
    COMBO_PUMP_ALARM(25, URGENT, PUMP),
    COMBO_UNKNOWN_TBR(81, LOW, PUMP),

    // Pump — Medtronic
    MEDTRONIC_PUMP_ALARM(44, URGENT, PUMP),
    RILEYLINK_CONNECTION(45, NORMAL, PUMP),
    MDT_INVALID_HISTORY_DATA(76, NORMAL, PUMP),

    // Pump — Insight
    INSIGHT_DATE_TIME_UPDATED(47, INFO, PUMP),
    INSIGHT_TIMEOUT_DURING_HANDSHAKE(48, NORMAL, PUMP),

    // Pump — Omnipod
    OMNIPOD_POD_NOT_ATTACHED(59, NORMAL, PUMP),
    OMNIPOD_POD_SUSPENDED(61, NORMAL, PUMP),
    OMNIPOD_POD_ALERTS_UPDATED(62, INFO, PUMP),
    OMNIPOD_POD_ALERTS(63, URGENT, PUMP),
    OMNIPOD_TBR_ALERTS(64, LOW, PUMP),
    OMNIPOD_POD_FAULT(66, URGENT, PUMP),
    OMNIPOD_UNCERTAIN_SMB(67, NORMAL, PUMP),
    OMNIPOD_UNKNOWN_TBR(68, LOW, PUMP),
    OMNIPOD_STARTUP_STATUS_REFRESH_FAILED(69, NORMAL, PUMP),
    OMNIPOD_TIME_OUT_OF_SYNC(70, LOW, PUMP),

    // Pump — EOPatch
    EOFLOW_PATCH_ALERT(79, URGENT, PUMP, allowMultiple = true),

    // Pump — Equil
    EQUIL_ALARM(93, URGENT, PUMP),
    EQUIL_ALARM_INSULIN(94, URGENT, PUMP),

    // Pump — Dana (USER_MESSAGE=1000)
    DANA_PUMP_ALARM(1000, URGENT, PUMP),

    // Pump — Dana emulator
    PUMP_EMULATOR_DISPLAY(1001, INFO, PUMP),

    // CGM
    BG_READINGS_MISSED(27, URGENT, CGM),

    // Loop / APS
    EASY_MODE_ENABLED(2, URGENT, LOOP),
    UD_MODE_ENABLED(4, URGENT, LOOP),
    SHORT_DIA(21, URGENT, LOOP),
    CARBS_REQUIRED(60, NORMAL, LOOP),
    SMB_FALLBACK(89, NORMAL, LOOP),
    DYN_ISF_FALLBACK(91, NORMAL, LOOP),

    // Sync — Nightscout
    OLD_NS(9, URGENT, SYNC),
    NSCLIENT_NO_WRITE_PERMISSION(13, NORMAL, SYNC),
    NS_ANNOUNCEMENT(18, ANNOUNCEMENT, SYNC),
    NS_ALARM(19, NORMAL, SYNC),
    NS_URGENT_ALARM(20, URGENT, SYNC),
    NS_MALFUNCTION(40, URGENT, SYNC),
    NSCLIENT_VERSION_DOES_NOT_MATCH(73, NORMAL, SYNC),

    // Sync — SMS
    INVALID_PHONE_NUMBER(10, URGENT, SYNC),
    INVALID_MESSAGE_BODY(11, NORMAL, SYNC),
    APPROACHING_DAILY_LIMIT(12, URGENT, SYNC),

    // System
    TOAST_ALARM(22, URGENT, SYSTEM),
    DST_LOOP_DISABLED(49, URGENT, SYSTEM),
    DST_IN_24H(50, LOW, SYSTEM),
    DISK_FULL(51, URGENT, SYSTEM),
    OVER_24H_TIME_CHANGE_REQUESTED(54, LOW, SYSTEM),
    INVALID_VERSION(55, URGENT, SYSTEM),
    TIME_OR_TIMEZONE_CHANGE(58, NORMAL, SYSTEM),
    NEW_VERSION_DETECTED(41, NORMAL, SYSTEM),
    VERSION_EXPIRE(74, URGENT, SYSTEM),
    IDENTIFICATION_NOT_SET(77, NORMAL, SYSTEM),
    MASTER_PASSWORD_NOT_SET(90, URGENT, SYSTEM),
    AAPS_DIR_NOT_SELECTED(92, NORMAL, SYSTEM),
    GOOGLE_DRIVE_ERROR(1100, URGENT, SYSTEM),
    SETTINGS_EXPORT_RESULT(-2, INFO, SYSTEM),

    // Automation
    AUTOMATION_MESSAGE(-1, URGENT, AUTOMATION, allowMultiple = true);

    companion object {

        private val legacyIdMap: Map<Int, NotificationId> by lazy {
            entries.associateBy { it.legacyId }
        }

        fun fromLegacyId(id: Int): NotificationId? = legacyIdMap[id]
    }
}
