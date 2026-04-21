package info.nightscout.androidaps.plugins.pump.carelevo.ble.data

enum class BondingState {
    BOND_NONE,
    BOND_BONDING,
    BOND_BONDED,
    BOND_ERROR;

    companion object {
        fun Int.codeToBondingResult() = when(this) {
            -1 -> BOND_NONE
            10 -> BOND_NONE
            11 -> BOND_BONDING
            12 -> BOND_BONDED
            else -> throw IllegalArgumentException("Invalid code")
        }
    }
}

enum class PeripheralConnectionState {
    CONN_STATE_NONE,
    CONN_STATE_CONNECTING,
    CONN_STATE_CONNECTED,
    CONN_STATE_DISCONNECTING,
    CONN_STATE_DISCONNECTED;

    companion object {
        fun Int.codeToConnectionResult() = when(this) {
            -1 -> CONN_STATE_NONE
            0 -> CONN_STATE_DISCONNECTED
            1 -> CONN_STATE_CONNECTING
            2 -> CONN_STATE_CONNECTED
            3 -> CONN_STATE_DISCONNECTING
            else -> throw IllegalArgumentException("Invalid code")
        }
    }
}

enum class ServiceDiscoverState {
    DISCOVER_STATE_NONE,
    DISCOVER_STATE_DISCOVERED,
    DISCOVER_STATE_FAILED,
    DISCOVER_STATE_CLEARED;

    companion object {
        fun Int.codeToDiscoverResult() = when(this) {
            -1 -> DISCOVER_STATE_CLEARED
            0 -> DISCOVER_STATE_DISCOVERED
            else -> DISCOVER_STATE_FAILED
        }
    }
}

enum class DeviceModuleState {
    DEVICE_NONE,
    DEVICE_STATE_OFF,
    DEVICE_STATE_TUNING_OFF,
    DEVICE_STATE_ON,
    DEVICE_STATE_TURNING_ON;

    companion object {
        fun Int.codeToDeviceResult() = when(this) {
            -1 -> DEVICE_NONE
            10 -> DEVICE_STATE_OFF
            11 -> DEVICE_STATE_TURNING_ON
            12 -> DEVICE_STATE_ON
            13 -> DEVICE_STATE_TUNING_OFF
            else -> throw IllegalArgumentException("Invalid code")
        }
    }
}

enum class NotificationState {
    NOTIFICATION_NONE,
    NOTIFICATION_ENABLED,
    NOTIFICATION_DISABLED;

    companion object {
        fun Int.codeToNotificationResult() = when(this) {
            -1 -> NOTIFICATION_NONE
            0 -> NOTIFICATION_DISABLED
            1 -> NOTIFICATION_ENABLED
            else -> throw IllegalArgumentException("Invalid code")
        }
    }
}

enum class FailureState {
    FAILURE_INVALID_PARAMS,
    FAILURE_RESOURCE_NOT_INITIALIZED,
    FAILURE_PERMISSION_NOT_GRANTED,
    FAILURE_BT_NOT_ENABLED,
    FAILURE_COMMAND_NOT_EXECUTABLE
}