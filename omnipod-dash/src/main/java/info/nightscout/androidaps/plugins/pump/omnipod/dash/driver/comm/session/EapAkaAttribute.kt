package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

import info.nightscout.androidaps.utils.extensions.toHex

enum class EapAkaAttributeType(val type: Byte) {
    AT_RAND(1),
    AT_AUTN(2),
    AT_RES(3),
    AT_CUSTOM_IV(126);

    companion object {

        fun byValue(value: Byte): EapAkaAttributeType =
            EapAkaAttributeType.values().firstOrNull { it.type == value }
                ?: throw IllegalArgumentException("Unknown EAP-AKA attribute type: $value")
    }
}

abstract class EapAkaAttribute(val type: EapAkaAttributeType) {

    abstract fun toByteArray(): ByteArray

    companion object {

        const val SIZE_MULTIPLIER = 4 // The length for EAP-AKA attributes is a multiple of 4
    }
}

class EapAkaAttributeRand(val payload: ByteArray) : EapAkaAttribute(
    type = EapAkaAttributeType.AT_RAND
) {

    init {
        require(payload.size == 16) { "AT_RAND payload size has to be 16 bytes. Payload: ${payload.toHex()}" }
    }

    override fun toByteArray(): ByteArray {
        return byteArrayOf(type.type, SIZE, 0, 0) + payload
    }

    companion object {

        private const val SIZE = (20 / SIZE_MULTIPLIER).toByte() // type, size, 2 reserved bytes, payload=16
    }
}

class EapAkaAttributeAutn(val payload: ByteArray) : EapAkaAttribute(
    type = EapAkaAttributeType.AT_AUTN
) {

    init {
        require(payload.size == 16) { "AT_AUTN payload size has to be 16 bytes. Payload: ${payload.toHex()}" }
    }

    override fun toByteArray(): ByteArray {
        return byteArrayOf(type.type, SIZE, 0, 0) + payload
    }

    companion object {

        private const val SIZE = (20 / SIZE_MULTIPLIER).toByte() // type, size, 2 reserved bytes, payload=16
    }
}

class EapAkaAttributeRes(val payload: ByteArray) : EapAkaAttribute(
    type = EapAkaAttributeType.AT_AUTN
) {

    init {
        require(payload.size == 8) { "AT_RES payload size has to be 8 bytes. Payload: ${payload.toHex()}" }
    }

    override fun toByteArray(): ByteArray {
        return byteArrayOf(type.type, SIZE, 0, PAYLOAD_SIZE_BITS) + payload
    }

    companion object {

        private const val SIZE = (12 / SIZE_MULTIPLIER).toByte() // type, size, len in bits=2, payload=8
        private const val PAYLOAD_SIZE_BITS = 64.toByte() // type, size, 2 reserved bytes, payload
    }
}

class EapAkaAttributeCustomIV(val payload: ByteArray) : EapAkaAttribute(
    type = EapAkaAttributeType.AT_CUSTOM_IV
) {

    init {
        require(payload.size == 4) { "AT_RES payload size has to be 8 bytes. Payload: ${payload.toHex()}" }
    }

    override fun toByteArray(): ByteArray {
        return byteArrayOf(type.type, SIZE, 0, 0) + payload
    }

    companion object {

        private const val SIZE = (8 / SIZE_MULTIPLIER).toByte() // type, size, 2 reserved bytes, payload=4
    }
}