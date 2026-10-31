package app.aaps.pump.eopatch.core.scan

enum class BleConnectionState {
    CONNECTING,
    CONNECTED_PREPARING,
    CONNECTED,
    DISCONNECTED,
    DISCONNECTING;

    val isConnecting: Boolean get() = this == CONNECTING
    val isConnected: Boolean get() = this == CONNECTED
}
