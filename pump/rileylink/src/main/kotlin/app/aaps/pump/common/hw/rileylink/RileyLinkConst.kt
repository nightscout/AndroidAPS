package app.aaps.pump.common.hw.rileylink

/**
 * Created by andy on 16/05/2018.
 */
object RileyLinkConst {

    const val PREFIX: String = "AAPS.RileyLink."

    object Intents {

        const val RileyLinkReady: String = PREFIX + "RileyLink_Ready"
        const val RileyLinkGattFailed: String = PREFIX + "RileyLink_Gatt_Failed"

        const val BluetoothConnected: String = PREFIX + "Bluetooth_Connected"
        const val BluetoothReconnected: String = PREFIX + "Bluetooth_Reconnected"

        //const val BluetoothDisconnected: String = PREFIX + "Bluetooth_Disconnected"
        const val RileyLinkDisconnected: String = PREFIX + "RileyLink_Disconnected"

        const val RileyLinkNewAddressSet: String = PREFIX + "NewAddressSet"

        //const val INTENT_NEW_rileylinkAddressKey: String = PREFIX + "INTENT_NEW_rileylinkAddressKey"
        //const val INTENT_NEW_pumpIDKey: String = PREFIX + "INTENT_NEW_pumpIDKey"
        const val RileyLinkDisconnect: String = PREFIX + "RileyLink_Disconnect"
    }

    object Prefs {

        //public static final String PrefPrefix = "pref_rileylink_";
        //public static final String RileyLinkAddress = PrefPrefix + "mac_address"; // pref_rileylink_mac_address
        val RileyLinkAddress: Int = R.string.key_rileylink_mac_address
        val RileyLinkName: Int = R.string.key_rileylink_name
        val OrangeUseScanning: Int = R.string.key_orange_use_scanning
        const val LastGoodDeviceCommunicationTime: String = PREFIX + "lastGoodDeviceCommunicationTime"
        const val LastGoodDeviceFrequency: String = PREFIX + "LastGoodDeviceFrequency"
        val Encoding: Int = R.string.key_medtronic_encoding
        val ShowBatteryLevel: Int = R.string.key_riley_link_show_battery_level
    }

    object IPC {

        // needs to br renamed (and maybe removed)
        const val MSG_PUMP_quickTune: String = PREFIX + "MSG_PUMP_quickTune"
        const val MSG_PUMP_tunePump: String = PREFIX + "MSG_PUMP_tunePump"

        //const val MSG_ServiceCommand: String = PREFIX + "MSG_ServiceCommand"
    }
}
