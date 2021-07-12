package info.nightscout.androidaps.plugins.pump.common.defs

enum class PumpDriverState {

    NotInitialized,
    Connecting,
    Connected,
    Initialized,
    Ready, Busy,
    Suspended;

    fun isConnected(): Boolean = this == Connected || this == Initialized || this == Busy || this == Suspended
    fun isInitialized(): Boolean = this == Initialized || this == Busy || this == Suspended
}