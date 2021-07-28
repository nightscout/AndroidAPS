package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition

// FIXME names
enum class BeepRepetitionType(
    val value: Byte
) {

    XXX(0x01.toByte()), // Used in lump of coal alert, LOW_RESERVOIR
    XXX2(0x03.toByte()), // Used in USER_SET_EXPIRATION
    XXX3(0x05.toByte()), // published system expiration alert
    XXX4(0x06.toByte()), // Used in imminent pod expiration alert
    XXX5(0x08.toByte()); // Lump of coal alert
}
