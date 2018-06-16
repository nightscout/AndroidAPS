package info.nightscout.androidaps.plugins.PumpMedtronic.util;

/**
 * Created by andy on 5/12/18.
 */

public class MedtronicConst {

    static final String Prefix = "AAPS.Medtronic.";

    public class Prefs {

        public static final String PrefPrefix = "pref_medtronic_";
        public static final String PumpSerial = PrefPrefix + "serial";
        public static final String PumpType = PrefPrefix + "pump_type";
        public static final String PumpFrequency = PrefPrefix + "frequency";
        public static final String MaxBolus = PrefPrefix + "max_bolus";
        public static final String MaxBasal = PrefPrefix + "max_basal";
        public static final String FirstPumpStart = Prefix + "first_pump_use";

        public static final String LastGoodPumpCommunicationTime = Prefix + "lastGoodPumpCommunicationTime";
        public static final String LastGoodPumpFrequency = Prefix + "LastGoodPumpFrequency";

    }


}
