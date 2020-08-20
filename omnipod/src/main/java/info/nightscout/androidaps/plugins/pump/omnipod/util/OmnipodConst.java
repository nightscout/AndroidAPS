package info.nightscout.androidaps.plugins.pump.omnipod.util;

import org.joda.time.Duration;

import info.nightscout.androidaps.plugins.pump.omnipod.R;

/**
 * Created by andy on 4.8.2019
 */

public class OmnipodConst {

    static final String Prefix = "AAPS.Omnipod.";

    public static class Prefs {
        public static final String PodState = Prefix + "pod_state";
        public static final String CurrentBolus = Prefix + "current_bolus";
        public static final int BeepBasalEnabled = R.string.key_omnipod_beep_basal_enabled;
        public static final int BeepBolusEnabled = R.string.key_omnipod_beep_bolus_enabled;
        public static final int BeepSMBEnabled = R.string.key_omnipod_beep_smb_enabled;
        public static final int BeepTBREnabled = R.string.key_omnipod_beep_tbr_enabled;
        public static final int PodDebuggingOptionsEnabled = R.string.key_omnipod_pod_debugging_options_enabled;
        public static final int TimeChangeEventEnabled = R.string.key_omnipod_timechange_enabled;
    }

    public static class Statistics {
        public static final String StatsPrefix = "omnipod_";
        public static final String FirstPumpStart = Prefix + "first_pump_use";
        public static final String LastGoodPumpCommunicationTime = Prefix + "lastGoodPumpCommunicationTime";
        public static final String TBRsSet = StatsPrefix + "tbrs_set";
        public static final String StandardBoluses = StatsPrefix + "std_boluses_delivered";
        public static final String SMBBoluses = StatsPrefix + "smb_boluses_delivered";
    }

    public static final double POD_PULSE_SIZE = 0.05;
    public static final double POD_BOLUS_DELIVERY_RATE = 0.025; // units per second
    public static final double POD_PRIMING_DELIVERY_RATE = 0.05; // units per second
    public static final double POD_CANNULA_INSERTION_DELIVERY_RATE = 0.05; // units per second
    public static final double MAX_RESERVOIR_READING = 50.0;
    public static final double MAX_BOLUS = 30.0;
    public static final double MAX_BASAL_RATE = 30.0;
    public static final Duration MAX_TEMP_BASAL_DURATION = Duration.standardHours(12);
    public static final int DEFAULT_ADDRESS = 0xffffffff;

    public static final Duration AVERAGE_BOLUS_COMMAND_COMMUNICATION_DURATION = Duration.millis(1500);
    public static final Duration AVERAGE_TEMP_BASAL_COMMAND_COMMUNICATION_DURATION = Duration.millis(1500);

    public static final Duration SERVICE_DURATION = Duration.standardHours(80);
    public static final Duration EXPIRATION_ADVISORY_WINDOW = Duration.standardHours(9);
    public static final Duration END_OF_SERVICE_IMMINENT_WINDOW = Duration.standardHours(1);
    public static final Duration NOMINAL_POD_LIFE = Duration.standardHours(72);
    public static final double LOW_RESERVOIR_ALERT = 20.0;

    public static final double POD_PRIME_BOLUS_UNITS = 2.6;
    public static final double POD_CANNULA_INSERTION_BOLUS_UNITS = 0.5;
}
