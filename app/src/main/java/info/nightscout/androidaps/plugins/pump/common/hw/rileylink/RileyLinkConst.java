package info.nightscout.androidaps.plugins.pump.common.hw.rileylink;

import info.nightscout.androidaps.R;

/**
 * Created by andy on 16/05/2018.
 */

public class RileyLinkConst {

    static final String Prefix = "AAPS.RileyLink.";

    public class Intents {

        public static final String RileyLinkReady = Prefix + "RileyLink_Ready";
        public static final String RileyLinkGattFailed = Prefix + "RileyLink_Gatt_Failed";

        public static final String BluetoothConnected = Prefix + "Bluetooth_Connected";
        public static final String BluetoothReconnected = Prefix + "Bluetooth_Reconnected";
        public static final String BluetoothDisconnected = Prefix + "Bluetooth_Disconnected";
        public static final String RileyLinkDisconnected = Prefix + "RileyLink_Disconnected";

        public static final String RileyLinkNewAddressSet = Prefix + "NewAddressSet";

        public static final String INTENT_NEW_rileylinkAddressKey = Prefix + "INTENT_NEW_rileylinkAddressKey";
        public static final String INTENT_NEW_pumpIDKey = Prefix + "INTENT_NEW_pumpIDKey";
        public static final String RileyLinkDisconnect = Prefix + "RileyLink_Disconnect";
    }

    public class Prefs {

        //public static final String PrefPrefix = "pref_rileylink_";
        //public static final String RileyLinkAddress = PrefPrefix + "mac_address"; // pref_rileylink_mac_address
        public static final int RileyLinkAddress = R.string.key_rileylink_mac_address;
        public static final String LastGoodDeviceCommunicationTime = Prefix + "lastGoodDeviceCommunicationTime";
        public static final String LastGoodDeviceFrequency = Prefix + "LastGoodDeviceFrequency";
    }

    public class IPC {

        // needs to br renamed (and maybe removed)
        public static final String MSG_PUMP_quickTune = Prefix + "MSG_PUMP_quickTune";
        public static final String MSG_PUMP_tunePump = Prefix + "MSG_PUMP_tunePump";
        public static final String MSG_PUMP_fetchHistory = Prefix + "MSG_PUMP_fetchHistory";
        public static final String MSG_PUMP_fetchSavedHistory = Prefix + "MSG_PUMP_fetchSavedHistory";

        public static final String MSG_ServiceCommand = Prefix + "MSG_ServiceCommand";
    }

}
