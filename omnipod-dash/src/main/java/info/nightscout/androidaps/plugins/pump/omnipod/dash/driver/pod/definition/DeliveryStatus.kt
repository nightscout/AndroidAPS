package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition

enum class DeliveryStatus(private val value: Byte) {
    SUSPENDED(0x00.toByte()),
    BASAL_ACTIVE(0x01.toByte()),
    TEMP_BASAL_ACTIVE(0x02.toByte()),
    PRIMING(0x04.toByte()),
    BOLUS_AND_BASAL_ACTIVE(0x05.toByte()),
    BOLUS_AND_TEMP_BASAL_ACTIVE(0x06.toByte()),
    UNKNOWN(0xff.toByte());

    companion object {

        fun byValue(value: Byte): DeliveryStatus {
            for (status in values()) {
                if (status.value == value) {
                    return status
                }
            }
            return UNKNOWN
        }
    }
}