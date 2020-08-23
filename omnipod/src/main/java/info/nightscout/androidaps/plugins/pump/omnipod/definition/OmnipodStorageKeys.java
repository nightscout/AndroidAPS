package info.nightscout.androidaps.plugins.pump.omnipod.definition;

import info.nightscout.androidaps.plugins.pump.omnipod.R;

public class OmnipodStorageKeys {
    static final String Prefix = "AAPS.Omnipod.";

    public static class Prefs {
        public static final String PodState = Prefix + "pod_state";
        public static final String ActiveBolus = Prefix + "current_bolus";
        public static final int BeepBasalEnabled = R.string.key_omnipod_beep_basal_enabled;
        public static final int BeepBolusEnabled = R.string.key_omnipod_beep_bolus_enabled;
        public static final int BeepSMBEnabled = R.string.key_omnipod_beep_smb_enabled;
        public static final int BeepTBREnabled = R.string.key_omnipod_beep_tbr_enabled;
        public static final int PodDebuggingOptionsEnabled = R.string.key_omnipod_pod_debugging_options_enabled;
        public static final int TimeChangeEventEnabled = R.string.key_omnipod_timechange_enabled;
    }

    public static class Statistics {
        public static final String StatsPrefix = "omnipod_";
        public static final String TBRsSet = StatsPrefix + "tbrs_set";
        public static final String StandardBoluses = StatsPrefix + "std_boluses_delivered";
        public static final String SMBBoluses = StatsPrefix + "smb_boluses_delivered";
    }
}
