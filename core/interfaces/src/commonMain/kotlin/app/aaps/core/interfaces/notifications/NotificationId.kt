package app.aaps.core.interfaces.notifications

import app.aaps.core.interfaces.notifications.NotificationCategory.AUTOMATION
import app.aaps.core.interfaces.notifications.NotificationCategory.CGM
import app.aaps.core.interfaces.notifications.NotificationCategory.LOOP
import app.aaps.core.interfaces.notifications.NotificationCategory.PROFILE
import app.aaps.core.interfaces.notifications.NotificationCategory.PUMP
import app.aaps.core.interfaces.notifications.NotificationCategory.SYNC
import app.aaps.core.interfaces.notifications.NotificationCategory.SYSTEM
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
 * The Android number of a single card is its [Enum.ordinal], only while the app runs: nothing stores it, so
 * an entry can go anywhere, and one that is no longer used is deleted. A reordering changes the numbers once,
 * after the update, for a card left in the tray from before; nothing rings because of it.
 */
@Suppress("unused")
enum class NotificationId(
    val defaultLevel: NotificationLevel,
    val category: NotificationCategory,
    val allowMultiple: Boolean = false
) {

    // Profile
    PROFILE_SET_OK(INFO, PROFILE),

    // Omnipod Dash only: its own card for a basal program the pod did not take. The queue's "profile not set in the
    // pump" is an alert (`CoreAlert.PROFILE_UPDATE_FAILED`); this goes when Dash moves to alerts.
    FAILED_UPDATE_PROFILE(URGENT, PROFILE),
    INVALID_PROFILE_NOT_ACCEPTED(NORMAL, PROFILE),

    // Pump — general
    EXTENDED_BOLUS_DISABLED(IMPORTANT, PUMP),
    PUMP_ERROR(URGENT, PUMP),

    // Equil low-battery alarm. Its own id: it once used FAILED_UPDATE_PROFILE, and a profile write that worked took
    // the battery alarm away with it.
    EQUIL_LOW_BATTERY(URGENT, PUMP),

    WRONG_SERIAL_NUMBER(NORMAL, PUMP),
    WRONG_BASAL_STEP(NORMAL, PUMP),
    WRONG_DRIVER(NORMAL, PUMP),
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

    // Pump clock / time-zone update failed (Medtrum, Omnipod Eros). Its own id: Eros once used FAILED_UPDATE_PROFILE,
    // and the two cards removed each other.
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

    // "Bolus block" enabled in pump settings - blocks all bolus delivery (wrong configuration for AAPS)
    DANA_BOLUS_BLOCK(URGENT, PUMP),

    // Pump — Dana emulator
    PUMP_EMULATOR_DISPLAY(INFO, PUMP),

    // CGM
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
    CARBS_REQUIRED(NORMAL, LOOP),
    SMB_FALLBACK(NORMAL, LOOP),
    DYN_ISF_FALLBACK(NORMAL, LOOP),

    // Sync — Nightscout
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
    INSULIN_MIGRATION_DEFAULT_USED(IMPORTANT, SYSTEM),
    /** Once after the update: AAPS has its own BG alarms now, urgent low is on (`_docs/ALARMS.md`, step 2). */
    BG_ALARMS_INTRO(IMPORTANT, SYSTEM),
    IMPORTED_INSULIN_ADDED(IMPORTANT, SYSTEM),
    IDENTIFICATION_NOT_SET(NORMAL, SYSTEM),
    MASTER_PASSWORD_NOT_SET(IMPORTANT, SYSTEM),
    AAPS_DIR_NOT_SELECTED(NORMAL, SYSTEM),

    // The AAPS directory IS selected, but the permission behind it is gone - Android drops a
    // persisted SAF grant on reinstall and on "clear storage". Separate from the id above because
    // the two need different words and different weight: that one is "you have not set this up
    // yet", this one is "something that was working has stopped and nothing said so". While it is
    // true, local settings export is silently off - the button just goes grey - so a user with no
    // cloud configured has no backup at all and no sign of it. Found on a real phone in September
    // 2026 where the last local backup was four months old.
    //
    // IMPORTANT, not URGENT: the backups have stopped, which has to be said plainly and has to
    // persist, but it is not the alarm tier - that is reserved for insulin delivery and BG.
    AAPS_DIR_ACCESS_LOST(IMPORTANT, SYSTEM),
    GOOGLE_DRIVE_ERROR(IMPORTANT, SYSTEM),
    SETTINGS_EXPORT_RESULT(INFO, SYSTEM),
    SNACKBAR_FALLBACK(NORMAL, SYSTEM, allowMultiple = true),

    // Automation — general notification action (NOT the "Alarm" action, which uses the system
    // alarm clock via TimerUtil.scheduleReminder, not this notification path).
    AUTOMATION_MESSAGE(IMPORTANT, AUTOMATION, allowMultiple = true),

    // Scenes
    SCENE_CHAINED(INFO, AUTOMATION, allowMultiple = true),
    SCENE_CHAIN_ERROR(IMPORTANT, AUTOMATION, allowMultiple = true),

    // A plugin's onStart threw. It stays ENABLED on purpose - disabling a failed pump driver makes
    // ActivePlugin.activePumpInternal throw "No pump selected" - so this alarm is how the user finds out
    // that a plugin is running only half built.
    //
    // allowMultiple, because one id is shared by every plugin. Without it a second failing plugin would
    // replace the first one's card, and one plugin starting cleanly would dismiss the card of another that
    // is still broken - leaving a blocked pump with no alarm to explain it. Same reason EQUIL_LOW_BATTERY
    // keeps its own id above. [app.aaps.core.interfaces.plugin.PluginBase] dismisses by handle, not by id.
    PLUGIN_START_FAILED(URGENT, SYSTEM, allowMultiple = true),

    // Work the plugin launched itself ended with an error. Separate from PLUGIN_START_FAILED because the
    // plugin did start - it is a polling loop or a queued command that died, so the text has to say
    // something else. Same reasons for allowMultiple.
    PLUGIN_WORK_FAILED(URGENT, SYSTEM, allowMultiple = true),

    // The Combo reported an error, so the driver does not connect until the user has handled it. Its own
    // id, so that this hint does not replace the card that names the error (COMBO_PUMP_ALARM) (#5263).
    COMBO_PUMP_ERROR_LOCKOUT(NORMAL, PUMP),

    // A scene ended, but its profile switch could not be undone, so the scene's percentage is still in force.
    // The End Scene dialog does not show the result, so without this nothing said so. IMPORTANT: it changes
    // insulin delivery until the user activates a profile, but nothing is delivered wrongly now.
    SCENE_PROFILE_NOT_RESTORED(IMPORTANT, AUTOMATION),

    // A Garmin watch connected to a client sent carbs or a pump disconnect. A client does not store them, because
    // they would never reach the master, and the watch cannot show why.
    GARMIN_NOT_ON_CLIENT(NORMAL, SYNC),

    // The Combo gave the bolus, but its history also had boluses that AAPS did not know about. Its own id,
    // so that it does not replace the low reservoir warning of the same bolus on COMBO_PUMP_ALARM (#5379).
    COMBO_UNKNOWN_BOLUS(URGENT, PUMP),

    // Desktop only: no OS key store was found (Linux without a keyring), so the keys that protect the settings
    // and the pairing secrets are plain files next to the data. IMPORTANT, not URGENT: nothing is wrong with
    // therapy, but a copy of the folder gives everything away, and the user can fix it.
    SECRETS_NOT_PROTECTED(IMPORTANT, SYSTEM),

    // A real pump is selected, but AAPS has no Bluetooth (Nearby devices) permission, so no command can reach the
    // pump and the queue fails them (#5440). It removes itself once the permission is back. IMPORTANT, not URGENT:
    // a bolus that fails because of it raises its own alarm.
    BLUETOOTH_PERMISSION_MISSING(IMPORTANT, PUMP),

    // Every alert raised through Alerts (a pump's, a CGM's, the app's own), one card each. The level is set per alert
    // from its severity.
    ALERT(URGENT, PUMP, allowMultiple = true),

    // The transmitter's firmware version, once per version. Information, not an event: the transmitter's alarms are
    // alerts (`EversenseAlertKind`).
    EVERSENSE_FIRMWARE(INFO, CGM)
}
