package app.aaps.pump.omnipod.dash.driver.pod.definition

enum class BeepType(val value: Byte) {

    SILENT(0x00.toByte()),
    FOUR_TIMES_BIP_BEEP(0x02.toByte()), // Used in low reservoir alert, user expiration alert, expiration alert, imminent expiration alert, lump of coal alert
    XXX(0x04.toByte()), // Used during suspend
    LONG_SINGLE_BEEP(0x06.toByte()); // Used in stop delivery command
}
