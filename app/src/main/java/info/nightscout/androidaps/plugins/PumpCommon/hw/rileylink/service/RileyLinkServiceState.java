package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service;

/**
 * Created by andy on 14/05/2018.
 */

public enum RileyLinkServiceState {

    NotStarted, //


    BluetoothNotAvailable, // BT not available, would happen only if device has no BT
    Initializing, // get all parameters required for connection (if not possible -> Disabled, if sucessful -> EnableBluetooth)

    BlueToothDisabled, // if BT gets disabled ( -> EnableBluetooth)
    EnableBlueTooth, // enable BT (if error no BT interface -> Disabled, BT not enabled -> BluetoothDisabled)
    BlueToothEnabled, // -> InitializeRileyLink
    RileyLinkInitializing, // start Gatt discovery (OK -> RileyLinkInitialized, Error -> BluetoothEnabled) ??
    RileyLinkInitialized, //
    RileyLinkReady, //
    RileyLinkConnected, // -> TuneUpPump (on 1st), else PumpConnectorReady
    TuneUpPump, // -> PumpConnectorReady
    PumpConnectorReady, //

    PumpConnected, //


    InitializingBluetooth;

}
