package app.aaps.pump.apex.connectivity.commands

enum class CommandId(val raw: Int) {
    SetValue(0xA1),
    GetValue(0xA3),
    Heartbeat(0xA5),
}