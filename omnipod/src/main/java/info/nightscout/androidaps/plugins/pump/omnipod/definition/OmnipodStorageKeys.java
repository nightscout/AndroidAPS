package info.nightscout.androidaps.plugins.pump.omnipod.definition;

import info.nightscout.androidaps.plugins.pump.omnipod.R;

public class OmnipodStorageKeys {
    public static class Preferences {
        public static final int POD_STATE = R.string.key_omnipod_pod_state;
        public static final int ACTIVE_BOLUS = R.string.key_omnipod_current_bolus;
        public static final int BASAL_BEEPS_ENABLED = R.string.key_omnipod_basal_beeps_enabled;
        public static final int BOLUS_BEEPS_ENABLED = R.string.key_omnipod_bolus_beeps_enabled;
        public static final int SMB_BEEPS_ENABLED = R.string.key_omnipod_smb_beeps_enabled;
        public static final int TBR_BEEPS_ENABLED = R.string.key_omnipod_tbr_beeps_enabled;
        public static final int SUSPEND_DELIVERY_BUTTON_ENABLED = R.string.key_omnipod_suspend_delivery_button_enabled;
        public static final int PULSE_LOG_BUTTON_ENABLED = R.string.key_omnipod_pulse_log_button_enabled;
        public static final int TIME_CHANGE_EVENT_ENABLED = R.string.key_omnipod_time_change_event_enabled;
        public static final int EXPIRATION_REMINDER_ENABLED = R.string.key_omnipod_expiration_reminder_enabled;
        public static final int EXPIRATION_REMINDER_HOURS_BEFORE_SHUTDOWN = R.string.key_omnipod_expiration_reminder_hours_before_shutdown;
        public static final int LOW_RESERVOIR_ALERT_ENABLED = R.string.key_omnipod_low_reservoir_alert_enabled;
        public static final int LOW_RESERVOIR_ALERT_UNITS = R.string.key_omnipod_low_reservoir_alert_units;
        public static final int NOTIFICATION_UNCERTAIN_TBR_SOUND_ENABLED = R.string.key_omnipod_notification_uncertain_tbr_sound_enabled;
        public static final int NOTIFICATION_UNCERTAIN_SMB_SOUND_ENABLED = R.string.key_omnipod_notification_uncertain_smb_sound_enabled;
        public static final int NOTIFICATION_UNCERTAIN_BOLUS_SOUND_ENABLED = R.string.key_omnipod_notification_uncertain_bolus_sound_enabled;
        public static final int AUTOMATICALLY_ACKNOWLEDGE_ALERTS_ENABLED = R.string.key_omnipod_automatically_acknowledge_alerts_enabled;
        public static final int RILEY_LINK_STATS_BUTTON_ENABLED = R.string.key_omnipod_riley_link_stats_button_enabled;
        public static final int SHOW_RILEY_LINK_BATTERY_LEVEL = R.string.key_riley_link_show_battery_level;
        public static final int BATTERY_CHANGE_LOGGING_ENABLED = R.string.key_omnipod_battery_change_logging_enabled;
    }

    public static class Statistics {
        public static final int TBRS_SET = R.string.key_omnipod_tbrs_set;
        public static final int STANDARD_BOLUSES_DELIVERED = R.string.key_omnipod_std_boluses_delivered;
        public static final int SMB_BOLUSES_DELIVERED = R.string.key_omnipod_smb_boluses_delivered;
    }
}
