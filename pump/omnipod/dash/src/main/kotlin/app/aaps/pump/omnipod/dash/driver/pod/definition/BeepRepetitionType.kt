package app.aaps.pump.omnipod.dash.driver.pod.definition

// FIXME names
enum class BeepRepetitionType(
    val value: Byte
) {

    XXX(0x01.toByte()), // Used in lump of coal alert, LOW_RESERVOIR
    EVERY_MINUTE_AND_EVERY_15_MIN(0x03.toByte()), // Used in USER_SET_EXPIRATION, suspend delivery
    XXX3(0x05.toByte()), // published system expiration alert
    XXX4(0x06.toByte()), // Used in imminent pod expiration alert, suspend in progress. No repeat?
    XXX5(0x08.toByte()); // Lump of coal alert
}
