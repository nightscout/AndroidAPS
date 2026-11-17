package app.aaps.pump.carelevo.ble.data

/**
 * The Bluetooth state the driver tracks. Only [isEnabled] (the adapter) still changes: the link is a Bluetooth v2
 * `BleLink` per session now, so the other fields stay at their NONE values.
 */
data class BleState(
    val isEnabled: DeviceModuleState,
    val isBonded: BondingState,
    val isServiceDiscovered: ServiceDiscoverState,
    val isConnected: PeripheralConnectionState,
    val isNotificationEnabled: NotificationState
)

fun BleState.isAvailable(): Boolean {
    return isEnabled != DeviceModuleState.DEVICE_STATE_OFF
}
