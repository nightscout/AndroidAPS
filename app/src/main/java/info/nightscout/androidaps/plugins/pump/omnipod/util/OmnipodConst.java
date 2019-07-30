package info.nightscout.androidaps.plugins.pump.omnipod.util;

/**
 * Created by andy on 4.8.2019
 */

public class OmnipodConst {

    static final String Prefix = "AAPS.Omnipod.";

    public class Prefs {

        //public static final int BatteryType = R.string.pref_key_medtronic_battery_type;
    }

    public class Statistics {

        public static final String StatsPrefix = "omnipod_";
        public static final String FirstPumpStart = Prefix + "first_pump_use";
        public static final String LastGoodPumpCommunicationTime = Prefix + "lastGoodPumpCommunicationTime";
        public static final String LastGoodPumpFrequency = Prefix + "LastGoodPumpFrequency";
        public static final String TBRsSet = StatsPrefix + "tbrs_set";
        public static final String StandardBoluses = StatsPrefix + "std_boluses_delivered";
        public static final String SMBBoluses = StatsPrefix + "smb_boluses_delivered";
        public static final String LastPumpHistoryEntry = StatsPrefix + "pump_history_entry";
    }

}
