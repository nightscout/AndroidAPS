package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.event

enum class PodEventType {
    SCANNING,
    PAIRING,
    CONNECTING,
    CONNECTED,
    COMMAND_SENDING,
    COMMAND_SENT,
    RESPONSE_RECEIVED,
    // ...
}