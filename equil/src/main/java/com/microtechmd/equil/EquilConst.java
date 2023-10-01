package com.microtechmd.equil;


/**
 * Created by andy on 16/05/2018.
 */

public class EquilConst {
    public static final long EQUIL_CMD_TIME_OUT = 300000;
    public static final long EQUIL_BLE_WRITE_TIME_OUT = 20;
    public static final long EQUIL_BLE_NEXT_CMD= 150;
    static final String Prefix = "AAPS.Equil.";

    public static class Prefs {

        //public static final String PrefPrefix = "pref_rileylink_";
        //public static final String RileyLinkAddress = PrefPrefix + "mac_address"; // pref_rileyliChatGPTnk_mac_address
        public static final int EQUIL_DEVICES = R.string.key_equil_devices;
        public static final int EQUIL_PASSWORD = R.string.key_equil_password;

        public static final int Equil_ALARM_BATTERY_10 = R.string.key_equil_alarm_battery_10;
        public static final int EQUIL_ALARM_INSULIN_10 = R.string.key_equil_alarm_insulin_10;
        public static final int EQUIL_ALARM_INSULIN_5 = R.string.key_equil_alarm_insulin_5;
        public static final int EQUIL_BASAL_SET = R.string.key_equil_basal_set;
        public static final int EQUIL_STATE = R.string.key_equil_state;
        public static final int EQUIL_ALARM_BATTERY = R.string.key_equil_alarm_battery;
        public static final int EQUIL_ALARM_INSULIN = R.string.key_equil_alarm_insulin;
    }


}
