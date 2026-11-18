package app.aaps.pump.carelevo.ble.data

enum class BondingState {
    BOND_NONE,
    BOND_BONDING,
    BOND_BONDED,
    BOND_ERROR
}

enum class PeripheralConnectionState {
    CONN_STATE_NONE,
    CONN_STATE_CONNECTING,
    CONN_STATE_CONNECTED,
    CONN_STATE_DISCONNECTING,
    CONN_STATE_DISCONNECTED
}

enum class ServiceDiscoverState {
    DISCOVER_STATE_NONE,
    DISCOVER_STATE_DISCOVERED,
    DISCOVER_STATE_FAILED,
    DISCOVER_STATE_CLEARED
}

enum class DeviceModuleState {
    DEVICE_NONE,
    DEVICE_STATE_OFF,
    DEVICE_STATE_TUNING_OFF,
    DEVICE_STATE_ON,
    DEVICE_STATE_TURNING_ON;

    companion object {

        /** The `EXTRA_STATE` of Android's adapter state broadcast. */
        fun Int.codeToDeviceResult() = when (this) {
            -1   -> DEVICE_NONE
            10   -> DEVICE_STATE_OFF
            11   -> DEVICE_STATE_TURNING_ON
            12   -> DEVICE_STATE_ON
            13   -> DEVICE_STATE_TUNING_OFF
            else -> throw IllegalArgumentException("Invalid code")
        }
    }
}

enum class NotificationState {
    NOTIFICATION_NONE,
    NOTIFICATION_ENABLED,
    NOTIFICATION_DISABLED
}
