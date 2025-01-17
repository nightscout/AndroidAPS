package app.aaps.pump.omnipod.dash.driver.pod.definition

import app.aaps.pump.omnipod.dash.driver.pod.util.HasValue

enum class DeliveryStatus(override val value: Byte) : HasValue {

    SUSPENDED(0x00.toByte()),
    BASAL_ACTIVE(0x01.toByte()),
    TEMP_BASAL_ACTIVE(0x02.toByte()),
    PRIMING(0x04.toByte()),
    BOLUS_AND_BASAL_ACTIVE(0x05.toByte()),
    BOLUS_AND_TEMP_BASAL_ACTIVE(0x06.toByte()),
    UNKNOWN(0xff.toByte());

    fun bolusDeliveringActive(): Boolean {
        return value in arrayOf(BOLUS_AND_BASAL_ACTIVE.value, BOLUS_AND_TEMP_BASAL_ACTIVE.value)
    }

    fun basalActive(): Boolean {
        return value in arrayOf(BOLUS_AND_BASAL_ACTIVE.value, BASAL_ACTIVE.value)
    }

    fun tempBasalActive(): Boolean {
        return value in arrayOf(BOLUS_AND_TEMP_BASAL_ACTIVE.value, TEMP_BASAL_ACTIVE.value)
    }

    fun suspended(): Boolean {
        return value == SUSPENDED.value
    }
}
