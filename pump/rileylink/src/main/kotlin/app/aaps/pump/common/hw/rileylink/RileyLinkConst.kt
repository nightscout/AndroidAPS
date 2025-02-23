package app.aaps.pump.common.hw.rileylink

/**
 * Created by andy on 16/05/2018.
 */
@Suppress("ConstPropertyName")
object RileyLinkConst {

    const val PREFIX: String = "AAPS.RileyLink."

    object Intents {

        const val RileyLinkReady: String = PREFIX + "RileyLink_Ready"
        const val RileyLinkGattFailed: String = PREFIX + "RileyLink_Gatt_Failed"

        const val BluetoothConnected: String = PREFIX + "Bluetooth_Connected"
        const val BluetoothReconnected: String = PREFIX + "Bluetooth_Reconnected"

        const val RileyLinkDisconnected: String = PREFIX + "RileyLink_Disconnected"

        const val RileyLinkNewAddressSet: String = PREFIX + "NewAddressSet"

        const val RileyLinkDisconnect: String = PREFIX + "RileyLink_Disconnect"
    }

    object IPC {

        // needs to br renamed (and maybe removed)
        const val MSG_PUMP_quickTune: String = PREFIX + "MSG_PUMP_quickTune"
        const val MSG_PUMP_tunePump: String = PREFIX + "MSG_PUMP_tunePump"

        //const val MSG_ServiceCommand: String = PREFIX + "MSG_ServiceCommand"
    }
}
