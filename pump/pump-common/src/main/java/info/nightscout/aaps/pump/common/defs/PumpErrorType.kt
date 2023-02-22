package info.nightscout.aaps.pump.common.defs

import androidx.annotation.StringRes
import info.nightscout.aaps.pump.common.R

/**
 * Created by andy on 14/05/2018.
 */
enum class PumpErrorType(@StringRes var resourceId: Int, var hasParameter: Boolean =false) {

    NoBluetoothAdapter(R.string.ble_error_no_bt_adapter),  //
    BluetoothDisabled(R.string.ble_error_bt_disabled),  //
    FailedToConnectToBleDevice(R.string.ble_error_failed_to_conn_to_ble_device),
    ConfiguredPumpNotFound(R.string.ble_error_configured_pump_not_found),  //
    PumpFoundUnbonded(R.string.ble_error_pump_found_unbonded),
    PumpUnreachable(R.string.ble_error_pump_unreachable), //
    PumpPairInvalidPairCode(R.string.ble_error_pump_pair_invalid_pair_code), //
    DeviceIsNotPump(R.string.ble_error_not_correct_pump, true),  //
    EncryptionFailed(R.string.ble_error_encryption_failed)
    ;

}