package info.nightscout.androidaps.plugins.pump.medtronic.util;

import info.nightscout.androidaps.R;

/**
 * Created by andy on 5/12/18.
 */

public class MedtronicConst {

    static final String Prefix = "AAPS.Medtronic.";

    public class Prefs {
        public static final int PumpSerial = R.string.key_medtronic_serial;
        public static final int PumpType = R.string.key_medtronic_pump_type;
        public static final int PumpFrequency = R.string.key_medtronic_frequency;
        public static final int MaxBolus = R.string.key_medtronic_max_bolus;
        public static final int MaxBasal = R.string.key_medtronic_max_basal;
        public static final int BolusDelay = R.string.key_medtronic_bolus_delay;
        public static final int Encoding = R.string.key_medtronic_encoding;
        public static final int BatteryType = R.string.key_medtronic_battery_type;
        public static final int BolusDebugEnabled = R.string.key_medtronic_bolus_debug;
    }

    public class Statistics {

        public static final String StatsPrefix = "medtronic_";
        public static final String FirstPumpStart = Prefix + "first_pump_use";
        public static final String LastGoodPumpCommunicationTime = Prefix + "lastGoodPumpCommunicationTime";
        public static final String LastGoodPumpFrequency = Prefix + "LastGoodPumpFrequency";
        public static final String TBRsSet = StatsPrefix + "tbrs_set";
        public static final String StandardBoluses = StatsPrefix + "std_boluses_delivered";
        public static final String SMBBoluses = StatsPrefix + "smb_boluses_delivered";
        public static final String LastPumpHistoryEntry = StatsPrefix + "pump_history_entry";
        public static final String LastPrime = StatsPrefix + "last_sent_prime";
    }

}
