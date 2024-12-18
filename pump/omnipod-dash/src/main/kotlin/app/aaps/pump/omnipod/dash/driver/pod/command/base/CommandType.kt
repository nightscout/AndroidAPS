package app.aaps.pump.omnipod.dash.driver.pod.command.base

enum class CommandType(val value: Byte) {

    SET_UNIQUE_ID(0x03.toByte()),
    GET_VERSION(0x07.toByte()),
    GET_STATUS(0x0e.toByte()),
    SILENCE_ALERTS(0x11.toByte()),
    PROGRAM_BASAL(0x13.toByte()), // Always preceded by 0x1a
    PROGRAM_TEMP_BASAL(0x16.toByte()), // Always preceded by 0x1a
    PROGRAM_BOLUS(0x17.toByte()), // Always preceded by 0x1a
    PROGRAM_ALERTS(0x19.toByte()),
    PROGRAM_INSULIN(0x1a.toByte()), // Always followed by one of: 0x13, 0x16, 0x17
    DEACTIVATE(0x1c.toByte()),
    PROGRAM_BEEPS(0x1e.toByte()),
    STOP_DELIVERY(0x1f.toByte());
}
