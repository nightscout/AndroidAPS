package app.aaps.core.interfaces.pump.defs

enum class PumpDeviceState {

    NeverContacted,
    Sleeping,
    WakingUp,
    Active,
    ErrorWhenCommunicating,
    TimeoutWhenCommunicating,
    PumpUnreachable,
    InvalidConfiguration;
}