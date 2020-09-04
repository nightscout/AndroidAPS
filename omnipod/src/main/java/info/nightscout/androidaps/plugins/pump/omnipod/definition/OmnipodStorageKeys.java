package info.nightscout.androidaps.plugins.pump.omnipod.definition;

import info.nightscout.androidaps.plugins.pump.omnipod.R;

public class OmnipodStorageKeys {
    private static final String PREFIX = "AAPS.Omnipod.";

    public static class Preferences {
        public static final String POD_STATE = PREFIX + "pod_state";
        public static final String ACTIVE_BOLUS = PREFIX + "current_bolus";
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
    }

    public static class Statistics {
        public static final String TBRS_SET = PREFIX + "tbrs_set";
        public static final String STANDARD_BOLUSES_DELIVERED = PREFIX + "std_boluses_delivered";
        public static final String SMB_BOLUSES_DELIVERED = PREFIX + "smb_boluses_delivered";
    }
}
