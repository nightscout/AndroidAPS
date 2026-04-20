package info.nightscout.androidaps.plugins.pump.carelevo.ble.data

import android.bluetooth.BluetoothDevice
import android.os.Build.VERSION_CODES.P

data class ScannedDevice(
    var device : BluetoothDevice,
    var rssi : Int
)

data class BleState(
    val isEnabled : DeviceModuleState,
    val isBonded : BondingState,
    val isServiceDiscovered : ServiceDiscoverState,
    val isConnected : PeripheralConnectionState,
    val isNotificationEnabled : NotificationState
)

fun BleState.isAvailable() : Boolean {
    return isEnabled != DeviceModuleState.DEVICE_STATE_OFF
}

fun BleState.isPeripheralConnected() : Boolean {
    return isConnected == PeripheralConnectionState.CONN_STATE_CONNECTED
}

fun BleState.isConnected() : Boolean {
    return isEnabled == DeviceModuleState.DEVICE_STATE_ON &&
        isBonded == BondingState.BOND_BONDED &&
        isConnected == PeripheralConnectionState.CONN_STATE_CONNECTED &&
        isServiceDiscovered == ServiceDiscoverState.DISCOVER_STATE_DISCOVERED &&
        isNotificationEnabled == NotificationState.NOTIFICATION_ENABLED
}

fun BleState.shouldBeConnected() : Boolean {
    return isEnabled == DeviceModuleState.DEVICE_STATE_ON &&
        isBonded == BondingState.BOND_BONDED &&
        isConnected == PeripheralConnectionState.CONN_STATE_CONNECTED &&
        isServiceDiscovered != ServiceDiscoverState.DISCOVER_STATE_DISCOVERED &&
        isNotificationEnabled != NotificationState.NOTIFICATION_ENABLED
}

fun BleState.shouldBeDiscovered() : Boolean {
    return isEnabled == DeviceModuleState.DEVICE_STATE_ON &&
        isBonded == BondingState.BOND_BONDED &&
        isServiceDiscovered == ServiceDiscoverState.DISCOVER_STATE_DISCOVERED &&
        isNotificationEnabled != NotificationState.NOTIFICATION_ENABLED
}

fun BleState.shouldBeNotificationEnabled() : Boolean {
    return isEnabled == DeviceModuleState.DEVICE_STATE_ON &&
        isBonded == BondingState.BOND_BONDED &&
        isConnected == PeripheralConnectionState.CONN_STATE_CONNECTED &&
        isServiceDiscovered == ServiceDiscoverState.DISCOVER_STATE_DISCOVERED &&
        isNotificationEnabled == NotificationState.NOTIFICATION_ENABLED
}

fun BleState.isDiscoverCleared() : Boolean {
    return isEnabled == DeviceModuleState.DEVICE_STATE_ON &&
        isBonded == BondingState.BOND_BONDED &&
        isConnected == PeripheralConnectionState.CONN_STATE_DISCONNECTED &&
        isServiceDiscovered == ServiceDiscoverState.DISCOVER_STATE_CLEARED &&
        isNotificationEnabled == NotificationState.NOTIFICATION_DISABLED
}

fun BleState.isReInitialized() : Boolean {
    return isEnabled == DeviceModuleState.DEVICE_STATE_ON &&
        isBonded == BondingState.BOND_NONE &&
        isConnected == PeripheralConnectionState.CONN_STATE_DISCONNECTED &&
        isServiceDiscovered == ServiceDiscoverState.DISCOVER_STATE_DISCOVERED &&
        isNotificationEnabled == NotificationState.NOTIFICATION_DISABLED
}

fun BleState.isAbnormalFailed() : Boolean {
    return isEnabled == DeviceModuleState.DEVICE_STATE_ON &&
        isBonded == BondingState.BOND_NONE &&
        isConnected == PeripheralConnectionState.CONN_STATE_DISCONNECTED &&
        isServiceDiscovered == ServiceDiscoverState.DISCOVER_STATE_DISCOVERED &&
        isNotificationEnabled == NotificationState.NOTIFICATION_DISABLED
}

fun BleState.isAbnormalBondingFailed() : Boolean {
    return isEnabled == DeviceModuleState.DEVICE_STATE_ON &&
        isBonded == BondingState.BOND_NONE &&
        isConnected == PeripheralConnectionState.CONN_STATE_CONNECTED &&
        isServiceDiscovered == ServiceDiscoverState.DISCOVER_STATE_DISCOVERED &&
        isNotificationEnabled == NotificationState.NOTIFICATION_DISABLED
}

fun BleState.isFailed() : Boolean {
    return isEnabled == DeviceModuleState.DEVICE_STATE_ON &&
        isBonded == BondingState.BOND_NONE &&
        isConnected == PeripheralConnectionState.CONN_STATE_CONNECTED &&
        isServiceDiscovered == ServiceDiscoverState.DISCOVER_STATE_NONE &&
        isNotificationEnabled == NotificationState.NOTIFICATION_DISABLED
}

fun BleState.isPairingFailed() : Boolean {
    return isEnabled == DeviceModuleState.DEVICE_STATE_ON &&
        isBonded == BondingState.BOND_NONE &&
        isConnected == PeripheralConnectionState.CONN_STATE_DISCONNECTED &&
        isServiceDiscovered == ServiceDiscoverState.DISCOVER_STATE_NONE &&
        isNotificationEnabled == NotificationState.NOTIFICATION_NONE
}