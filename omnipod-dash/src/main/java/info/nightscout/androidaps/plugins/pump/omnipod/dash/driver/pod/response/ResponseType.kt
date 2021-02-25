package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

enum class ResponseType(private val value: Byte) {
    ACTIVATION_RESPONSE(0x01.toByte()), DEFAULT_STATUS_RESPONSE(0x1d.toByte()), ADDITIONAL_STATUS_RESPONSE(0x02.toByte()), NAK_RESPONSE(0x06.toByte()), UNKNOWN(0xff.toByte());

    fun getValue(): Byte {
        return value
    }

    enum class AdditionalStatusResponseType(private val value: Byte) {
        STATUS_RESPONSE_PAGE_1(0x01.toByte()), ALARM_STATUS(0x02.toByte()), STATUS_RESPONSE_PAGE_3(0x03.toByte()), STATUS_RESPONSE_PAGE_5(0x05.toByte()), STATUS_RESPONSE_PAGE_6(0x06.toByte()), STATUS_RESPONSE_PAGE_70(0x46.toByte()), STATUS_RESPONSE_PAGE_80(0x50.toByte()), STATUS_RESPONSE_PAGE_81(0x51.toByte()), UNKNOWN(0xff.toByte());

        fun getValue(): Byte {
            return value
        }

        companion object {

            fun byValue(value: Byte): AdditionalStatusResponseType {
                for (type in values()) {
                    if (type.value == value) {
                        return type
                    }
                }
                return UNKNOWN
            }
        }
    }

    enum class ActivationResponseType(private val length: Byte) {
        GET_VERSION_RESPONSE(0x15.toByte()), SET_UNIQUE_ID_RESPONSE(0x1b.toByte()), UNKNOWN(0xff.toByte());

        companion object {

            fun byLength(length: Byte): ActivationResponseType {
                for (type in values()) {
                    if (type.length == length) {
                        return type
                    }
                }
                return UNKNOWN
            }
        }
    }

    companion object {

        fun byValue(value: Byte): ResponseType {
            for (type in values()) {
                if (type.value == value) {
                    return type
                }
            }
            return UNKNOWN
        }
    }
}