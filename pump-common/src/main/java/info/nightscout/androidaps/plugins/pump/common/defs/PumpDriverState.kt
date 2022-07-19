package info.nightscout.androidaps.plugins.pump.common.defs

import info.nightscout.androidaps.plugins.pump.common.R

// TODO there are 3 classes now, that do similar things, sort of, need to define exact rules: PumpDeviceState, PumpDriverState, PumpStatusState

// TODO split this enum into 2
enum class PumpDriverState(var resourceId: Int) {

    NotInitialized(R.string.pump_status_not_initialized), // this state should be set only when driver is created
    Connecting(R.string.connecting), //
    Connected(R.string.connected), //
    Initialized(R.string.pump_status_initialized), // this is weird state that probably won't be used, since its more driver centric that communication centric
    EncryptCommunication(R.string.pump_status_encrypt), //
    Ready(R.string.pump_status_ready),
    Busy(R.string.pump_status_busy), //
    Suspended(R.string.pump_status_suspended), //
    Sleeping(R.string.pump_status_sleeping),
    ExecutingCommand(R.string.pump_status_executing_command),
    Disconnecting(R.string.disconnecting),
    Disconnected(R.string.disconnected),
    ErrorCommunicatingWithPump(R.string.pump_status_error_comm);

    fun isConnected(): Boolean = this == Connected || this == Initialized || this == Busy || this == Suspended
    fun isInitialized(): Boolean = this == Initialized || this == Busy || this == Suspended
}
