package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.defs;

/**
 * Created by andy on 14/05/2018.
 */

public enum RileyLinkServiceState {

    NotStarted, //

    // Bluetooth
    BluetoothInitializing, // (S) init BT (if error no BT interface -> Disabled, BT not enabled -> BluetoothError)
    BluetoothNotAvailable, // (E) BT not available, would happen only if device has no BT
    BluetoothError, // (E) if BT gets disabled ( -> EnableBluetooth)
    BluetoothReady, // (OK)

    // RileyLink
    RileyLinkInitializing, // (S) start Gatt discovery (OK -> RileyLinkReady, Error -> BluetoothEnabled) ??
    RileyLinkError, // (E)
    RileyLinkReady, // (OK) if tunning was already done we go to PumpConnectorReady

    // Tunning
    TuneUpPump, // (S)
    //TuneUpPumpError, //
    PumpConnectorError, // either TuneUp Error or pump couldn't not be contacted error
    PumpConnectorReady, // (OK) RileyLink Ready for Pump Communication

    //Initializing, // get all parameters required for connection (if not possible -> Disabled, if sucessful -> EnableBluetooth)


    //EnableBlueTooth, // enable BT (if error no BT interface -> Disabled, BT not enabled -> BluetoothError)
    //BlueToothEnabled, // -> InitializeRileyLink
    //RileyLinkInitialized, //

    //RileyLinkConnected, // -> TuneUpPump (on 1st), else PumpConnectorReady

    //PumpConnected, //


    ;

    public static boolean isReady(RileyLinkServiceState serviceState) {
        return (serviceState == RileyLinkReady || serviceState == PumpConnectorReady);
    }
}
