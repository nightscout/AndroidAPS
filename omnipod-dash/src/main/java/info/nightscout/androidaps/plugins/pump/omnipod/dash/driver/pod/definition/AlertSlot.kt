package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition

enum class AlertSlot(
    val value: Byte
) {

    AUTO_OFF(0x00.toByte()),
    MULTI_COMMAND(0x01.toByte()),
    EXPIRATION_IMMINENT(0x02.toByte()),
    USER_SET_EXPIRATION(0x03.toByte()),
    LOW_RESERVOIR(0x04.toByte()),
    SUSPEND_IN_PROGRESS(0x05.toByte()),
    SUSPEND_ENDED(0x06.toByte()),
    EXPIRATION(0x07.toByte()),
    UNKNOWN(0xff.toByte());

    companion object {

        fun byValue(value: Byte): AlertSlot {
            for (slot in values()) {
                if (slot.value == value) {
                    return slot
                }
            }
            return UNKNOWN
        }
    }
}