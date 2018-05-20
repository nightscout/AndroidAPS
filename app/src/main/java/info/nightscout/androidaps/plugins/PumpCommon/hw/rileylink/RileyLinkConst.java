package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink;

/**
 * Created by andy on 16/05/2018.
 */

public class RileyLinkConst {

    static final String Prefix = "AAPS.RileyLink.";

    public class Intents {

        public static final String RileyLinkReady = Prefix + "RileyLink_Ready";
        public static final String RileyLinkGattFailed = Prefix + "RileyLink_Gatt_Failed";

        public static final String BluetoothConnected = Prefix + "Bluetooth_Connected";
        public static final String BluetoothDisconnected = Prefix + "Bluetooth_Disconnected";

    }

}
