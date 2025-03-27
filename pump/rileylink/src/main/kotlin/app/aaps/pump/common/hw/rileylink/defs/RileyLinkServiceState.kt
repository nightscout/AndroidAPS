package app.aaps.pump.common.hw.rileylink.defs

import app.aaps.pump.common.hw.rileylink.R

/**
 * Created by andy on 14/05/2018.
 */
enum class RileyLinkServiceState(var resourceId: Int) {

    NotStarted(R.string.rileylink_state_not_started),

    // Bluetooth
    BluetoothInitializing(R.string.rileylink_state_bt_init),  // (S) init BT (if error no BT interface -> Disabled, BT

    // not enabled -> BluetoothError)
    // BluetoothNotAvailable, // (E) BT not available, would happen only if device has no BT
    BluetoothError(R.string.rileylink_state_bt_error),  // (E) if BT gets disabled ( -> EnableBluetooth)
    BluetoothReady(R.string.rileylink_state_bt_ready),  // (OK)

    // RileyLink
    RileyLinkInitializing(R.string.rileylink_state_rl_init),  // (S) start Gatt discovery (OK -> RileyLinkReady, Error ->

    // BluetoothEnabled) ??
    RileyLinkError(R.string.rileylink_state_rl_error),  // (E)
    RileyLinkReady(R.string.rileylink_state_rl_ready),  // (OK) if tuning was already done we go to PumpConnectorReady

    // Tuning
    TuneUpDevice(R.string.rileylink_state_pc_tune_up),  // (S)
    PumpConnectorError(R.string.rileylink_state_pc_error),  // either TuneUp Error or pump couldn't not be contacted

    // error
    PumpConnectorReady(R.string.rileylink_state_connected),
    ; // (OK) RileyLink Ready for Pump Communication
    // Initializing, // get all parameters required for connection (if not possible -> Disabled, if successful ->
    // EnableBluetooth)
    // EnableBlueTooth, // enable BT (if error no BT interface -> Disabled, BT not enabled -> BluetoothError)
    // BlueToothEnabled, // -> InitializeRileyLink
    // RileyLinkInitialized, //
    // RileyLinkConnected, // -> TuneUpPump (on 1st), else PumpConnectorReady
    // PumpConnected, //

    fun isReady(): Boolean {
        return (this == PumpConnectorReady)
    }

    fun isConnecting(): Boolean =
        this == BluetoothInitializing ||
            // this == BluetoothError ||
            this == BluetoothReady ||
            this == RileyLinkInitializing ||
            this == RileyLinkReady
    // this == RileyLinkBLEError

    fun isError(): Boolean =
        this == BluetoothError ||
            // this == PumpConnectorError ||
            this == RileyLinkError
}
