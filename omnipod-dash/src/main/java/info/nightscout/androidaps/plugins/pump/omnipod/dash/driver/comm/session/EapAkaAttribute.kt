package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.MessageIOException
import info.nightscout.androidaps.utils.extensions.toHex
import java.util.*

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

sealed class EapAkaAttribute {

    abstract fun toByteArray(): ByteArray

    companion object {

        const val SIZE_MULTIPLIER = 4 // The length for EAP-AKA attributes is a multiple of 4

        fun parseAttributes(aapsLogger: AAPSLogger, payload: ByteArray): List<EapAkaAttribute> {
            var tail = payload
            val ret = LinkedList<EapAkaAttribute>()
            while (tail.size > 0) {
                if (tail.size < 2) {
                    throw MessageIOException("Could not parse EAP attributes: ${payload.toHex()}")
                }
                val size = SIZE_MULTIPLIER * tail[1].toInt()
                if (tail.size < size) {
                    throw MessageIOException("Could not parse EAP attributes: ${payload.toHex()}")
                }
                val type = EapAkaAttributeType.byValue(tail[0])
                when (type) {
                    EapAkaAttributeType.AT_RES ->
                        ret.add(EapAkaAttributeRes.parse(tail.copyOfRange(2, size)))
                    EapAkaAttributeType.AT_CUSTOM_IV ->
                        ret.add(EapAkaAttributeCustomIV.parse(tail.copyOfRange(2, size)))
                    else                             ->
                        throw MessageIOException("Could not parse EAP attributes: ${payload.toHex()}. Expecting only AT_RES or CUSTOM_IV attribute types from the POD")
                }
                tail = tail.copyOfRange(size, tail.size)
            }
            return ret
        }
    }
}

data class EapAkaAttributeRand(val payload: ByteArray) : EapAkaAttribute() {

    init {
        require(payload.size == 16) { "AT_RAND payload size has to be 16 bytes. Payload: ${payload.toHex()}" }
    }

    override fun toByteArray(): ByteArray {
        return byteArrayOf(EapAkaAttributeType.AT_RAND.type, SIZE, 0, 0) + payload
    }

    companion object {

        private const val SIZE = (20 / SIZE_MULTIPLIER).toByte() // type, size, 2 reserved bytes, payload=16
    }
}

data class EapAkaAttributeAutn(val payload: ByteArray) : EapAkaAttribute() {

    init {
        require(payload.size == 16) { "AT_AUTN payload size has to be 16 bytes. Payload: ${payload.toHex()}" }
    }

    override fun toByteArray(): ByteArray {
        return byteArrayOf(EapAkaAttributeType.AT_AUTN.type, SIZE, 0, 0) + payload
    }

    companion object {

        private const val SIZE = (20 / SIZE_MULTIPLIER).toByte() // type, size, 2 reserved bytes, payload=16
    }
}

data class EapAkaAttributeRes(val payload: ByteArray) : EapAkaAttribute() {

    init {
        require(payload.size == 8) { "AT_RES payload size has to be 8 bytes. Payload: ${payload.toHex()}" }
    }

    override fun toByteArray(): ByteArray {
        return byteArrayOf(EapAkaAttributeType.AT_RES.type, SIZE, 0, PAYLOAD_SIZE_BITS) + payload

    }

    companion object {

        fun parse(payload: ByteArray): EapAkaAttributeRes {
            if (payload.size < 2 + 8) {
                throw MessageIOException("Could not parse RES attribute: ${payload.toHex()}")
            }
            return EapAkaAttributeRes(payload.copyOfRange(2, 2 + 8))
        }

        private const val SIZE = (12 / SIZE_MULTIPLIER).toByte() // type, size, len in bits=2, payload=8
        private const val PAYLOAD_SIZE_BITS = 64.toByte() // type, size, 2 reserved bytes, payload
    }
}

data class EapAkaAttributeCustomIV(val payload: ByteArray) : EapAkaAttribute() {

    init {
        require(payload.size == 4) { "CUSTOM_IV payload size has to be 4 bytes. Payload: ${payload.toHex()}" }
    }

    override fun toByteArray(): ByteArray {
        return byteArrayOf(EapAkaAttributeType.AT_CUSTOM_IV.type, SIZE, 0, 0) + payload
    }

    companion object {

        fun parse(payload: ByteArray): EapAkaAttributeCustomIV {
            if (payload.size < 2 + 4) {
                throw MessageIOException("Could not parse CUSTOM_IV attribute: ${payload.toHex()}")
            }
            return EapAkaAttributeCustomIV(payload.copyOfRange(2, 2 + 4))
        }

        private const val SIZE = (8 / SIZE_MULTIPLIER).toByte() // type, size, 2 reserved bytes, payload=4
    }
}
