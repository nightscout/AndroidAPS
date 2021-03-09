package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.status

enum class ConnectionStatus {
    IDLE,
    BUSY,
    CONNECTING,
    ESTABLISHING_SESSION,
    PAIRING,
    RUNNING_COMMAND;
}
