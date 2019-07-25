package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs;

import info.nightscout.androidaps.R;

/**
 * Created by andy on 14/05/2018.
 */

public enum RileyLinkServiceState {

    NotStarted(R.string.rileylink_state_not_started), //

    // Bluetooth
    BluetoothInitializing(R.string.rileylink_state_bt_init), // (S) init BT (if error no BT interface -> Disabled, BT
                                                             // not enabled -> BluetoothError)
    // BluetoothNotAvailable, // (E) BT not available, would happen only if device has no BT
    BluetoothError(R.string.rileylink_state_bt_error), // (E) if BT gets disabled ( -> EnableBluetooth)
    BluetoothReady(R.string.rileylink_state_bt_ready), // (OK)

    // RileyLink
    RileyLinkInitializing(R.string.rileylink_state_rl_init), // (S) start Gatt discovery (OK -> RileyLinkReady, Error ->
                                                             // BluetoothEnabled) ??
    RileyLinkError(R.string.rileylink_state_rl_error), // (E)
    RileyLinkReady(R.string.rileylink_state_connected), // (OK) if tunning was already done we go to PumpConnectorReady

    // Tunning
    TuneUpDevice(R.string.rileylink_state_pc_tune_up), // (S)
    PumpConnectorError(R.string.rileylink_state_pc_error), // either TuneUp Error or pump couldn't not be contacted
                                                           // error
    PumpConnectorReady(R.string.rileylink_state_connected), // (OK) RileyLink Ready for Pump Communication

    // Initializing, // get all parameters required for connection (if not possible -> Disabled, if sucessful ->
    // EnableBluetooth)

    // EnableBlueTooth, // enable BT (if error no BT interface -> Disabled, BT not enabled -> BluetoothError)
    // BlueToothEnabled, // -> InitializeRileyLink
    // RileyLinkInitialized, //

    // RileyLinkConnected, // -> TuneUpPump (on 1st), else PumpConnectorReady

    // PumpConnected, //

    ;

    int resourceId;
    Integer resourceIdPod;


    RileyLinkServiceState(int resourceId) {
        this.resourceId = resourceId;
    }


    RileyLinkServiceState(int resourceId, int resourceIdPod) {
        this.resourceId = resourceId;
        this.resourceIdPod = resourceIdPod;
    }


    public static boolean isReady(RileyLinkServiceState serviceState) {
        return (/* serviceState == RileyLinkReady || */serviceState == PumpConnectorReady);
    }


    public int getResourceId(RileyLinkTargetDevice targetDevice) {
        if (this.resourceIdPod != null) {

            return (targetDevice == null || targetDevice == RileyLinkTargetDevice.MedtronicPump) ? //
            this.resourceId
                : this.resourceIdPod;
        } else {
            return this.resourceId;
        }
    }


    public boolean isConnecting() {

        return (this == RileyLinkServiceState.BluetoothInitializing || //
            // this == RileyLinkServiceState.BluetoothError || //
            this == RileyLinkServiceState.BluetoothReady || //
            this == RileyLinkServiceState.RileyLinkInitializing || //
        this == RileyLinkReady
        // this == RileyLinkServiceState.RileyLinkBLEError
        );
    }


    public boolean isError() {

        return (this == RileyLinkServiceState.BluetoothError || //
        // this == RileyLinkServiceState.PumpConnectorError || //
        this == RileyLinkServiceState.RileyLinkError);
    }
}
