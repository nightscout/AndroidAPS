package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition

enum class PodStatus(private val value: Byte) {
    UNINITIALIZED(0x00.toByte()), MFG_TEST(0x01.toByte()), FILLED(0x02.toByte()), UID_SET(0x03.toByte()), ENGAGING_CLUTCH_DRIVE(0x04.toByte()), CLUTCH_DRIVE_ENGAGED(0x05.toByte()), BASAL_PROGRAM_RUNNING(0x06.toByte()), PRIMING(0x07.toByte()), RUNNING_ABOVE_MIN_VOLUME(0x08.toByte()), RUNNING_BELOW_MIN_VOLUME(0x09.toByte()), UNUSED_10(0x0a.toByte()), UNUSED_11(0x0b.toByte()), UNUSED_12(0x0c.toByte()), ALARM(0x0d.toByte()), LUMP_OF_COAL(0x0e.toByte()), DEACTIVATED(0x0f.toByte()), UNKNOWN(0xff.toByte());

    companion object {

        fun byValue(value: Byte): PodStatus {
            for (status in values()) {
                if (status.value == value) {
                    return status
                }
            }
            return UNKNOWN
        }
    }
}