package app.aaps.pump.common.defs

import app.aaps.pump.common.R

// TODO there are 3 classes now, that do similar things, sort of, need to define exact rules: PumpDeviceState, PumpDriverState, PumpStatusState

// TODO split this enum into 2
enum class PumpDriverState(var resourceId: Int) {

    NotInitialized(R.string.pump_status_not_initialized), // this state should be set only when driver is created
    Connecting(app.aaps.core.ui.R.string.connecting), //
    Connected(app.aaps.core.interfaces.R.string.connected), //
    Initialized(R.string.pump_status_initialized), // this is weird state that probably won't be used, since its more driver centric that communication centric
    EncryptCommunication(R.string.pump_status_encrypt), //
    Ready(R.string.pump_status_ready),
    Busy(R.string.pump_status_busy), //
    Suspended(R.string.pump_status_suspended), //
    ExecutingCommand(R.string.pump_status_executing_command),
    Disconnecting(app.aaps.core.interfaces.R.string.disconnecting),
    Disconnected(app.aaps.core.ui.R.string.disconnected);

    fun isConnected(): Boolean = this == Connected || this == Initialized || this == Busy || this == Suspended
    fun isInitialized(): Boolean = this == Initialized || this == Busy || this == Suspended
}
