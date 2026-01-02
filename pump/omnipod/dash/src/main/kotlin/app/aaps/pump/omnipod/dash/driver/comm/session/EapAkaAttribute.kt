package app.aaps.pump.omnipod.dash.driver.comm.session

import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.dash.driver.comm.exceptions.MessageIOException
import java.util.LinkedList

enum class EapAkaAttributeType(val type: Byte) {
    AT_RAND(1),
    AT_AUTN(2),
    AT_RES(3),
    AT_AUTS(4),
    AT_CLIENT_ERROR_CODE(22),
    AT_CUSTOM_IV(126);

    companion object {

        fun byValue(value: Byte): EapAkaAttributeType =
            EapAkaAttributeType.entries.firstOrNull { it.type == value }
                ?: throw IllegalArgumentException("Unknown EAP-AKA attribute type: $value")
    }
}

sealed class EapAkaAttribute {

    abstract fun toByteArray(): ByteArray

    companion object {

        const val SIZE_MULTIPLIER = 4 // The length for EAP-AKA attributes is a multiple of 4

        fun parseAttributes(payload: ByteArray): List<EapAkaAttribute> {
            var tail = payload
            val ret = LinkedList<EapAkaAttribute>()
            while (tail.isNotEmpty()) {
                if (tail.size < 2) {
                    throw MessageIOException("Could not parse EAP attributes: ${payload.toHex()}")
                }
                val size = SIZE_MULTIPLIER * tail[1].toInt()
                if (tail.size < size) {
                    throw MessageIOException("Could not parse EAP attributes: ${payload.toHex()}")
                }
                val type = EapAkaAttributeType.byValue(tail[0])
                when (type) {
                    EapAkaAttributeType.AT_RES               ->
                        ret.add(EapAkaAttributeRes.parse(tail.copyOfRange(2, EapAkaAttributeRes.SIZE)))

                    EapAkaAttributeType.AT_CUSTOM_IV         ->
                        ret.add(EapAkaAttributeCustomIV.parse(tail.copyOfRange(2, EapAkaAttributeCustomIV.SIZE)))

                    EapAkaAttributeType.AT_AUTN              ->
                        ret.add(EapAkaAttributeAutn.parse(tail.copyOfRange(2, EapAkaAttributeAutn.SIZE)))

                    EapAkaAttributeType.AT_AUTS              ->
                        ret.add(EapAkaAttributeAuts.parse(tail.copyOfRange(2, EapAkaAttributeAuts.SIZE)))

                    EapAkaAttributeType.AT_RAND              ->
                        ret.add(EapAkaAttributeRand.parse(tail.copyOfRange(2, EapAkaAttributeRand.SIZE)))

                    EapAkaAttributeType.AT_CLIENT_ERROR_CODE ->
                        ret.add(
                            EapAkaAttributeClientErrorCode.parse(
                                tail.copyOfRange(
                                    2,
                                    EapAkaAttributeClientErrorCode.SIZE
                                )
                            )
                        )
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
        return byteArrayOf(EapAkaAttributeType.AT_RAND.type, (SIZE / SIZE_MULTIPLIER).toByte(), 0, 0) + payload
    }

    companion object {

        fun parse(payload: ByteArray): EapAkaAttribute {
            if (payload.size < 2 + 16) {
                throw MessageIOException("Could not parse RAND attribute: ${payload.toHex()}")
            }
            return EapAkaAttributeRand(payload.copyOfRange(2, 2 + 16))
        }

        const val SIZE = 20 // type, size, 2 reserved bytes, payload=16
    }
}

data class EapAkaAttributeAutn(val payload: ByteArray) : EapAkaAttribute() {

    init {
        require(payload.size == 16) { "AT_AUTN payload size has to be 16 bytes. Payload: ${payload.toHex()}" }
    }

    override fun toByteArray(): ByteArray {
        return byteArrayOf(EapAkaAttributeType.AT_AUTN.type, (SIZE / SIZE_MULTIPLIER).toByte(), 0, 0) + payload
    }

    companion object {

        fun parse(payload: ByteArray): EapAkaAttribute {
            if (payload.size < 2 + 16) {
                throw MessageIOException("Could not parse AUTN attribute: ${payload.toHex()}")
            }
            return EapAkaAttributeAutn(payload.copyOfRange(2, 2 + 16))
        }

        const val SIZE = 20 // type, size, 2 reserved bytes, payload=16
    }
}

data class EapAkaAttributeAuts(val payload: ByteArray) : EapAkaAttribute() {

    init {
        require(payload.size == 14) { "AT_AUTS payload size has to be 14 bytes. Payload: ${payload.toHex()}" }
    }

    override fun toByteArray(): ByteArray {
        return byteArrayOf(EapAkaAttributeType.AT_AUTS.type, (SIZE / SIZE_MULTIPLIER).toByte(), 0, 0) + payload
    }

    companion object {

        fun parse(payload: ByteArray): EapAkaAttribute {
            if (payload.size < SIZE - 2) {
                throw MessageIOException("Could not parse AUTS attribute: ${payload.toHex()}")
            }
            return EapAkaAttributeAuts(payload)
        }

        const val SIZE = 16 // type, size, 2 reserved bytes, payload=16
    }
}

data class EapAkaAttributeRes(val payload: ByteArray) : EapAkaAttribute() {

    init {
        require(payload.size == 8) { "AT_RES payload size has to be 8 bytes. Payload: ${payload.toHex()}" }
    }

    override fun toByteArray(): ByteArray {
        return byteArrayOf(
            EapAkaAttributeType.AT_RES.type,
            (SIZE / SIZE_MULTIPLIER).toByte(),
            0,
            PAYLOAD_SIZE_BITS
        ) + payload
    }

    companion object {

        fun parse(payload: ByteArray): EapAkaAttributeRes {
            if (payload.size < 2 + 8) {
                throw MessageIOException("Could not parse RES attribute: ${payload.toHex()}")
            }
            return EapAkaAttributeRes(payload.copyOfRange(2, 2 + 8))
        }

        const val SIZE = 12 // type, size, len in bits=2, payload=8
        private const val PAYLOAD_SIZE_BITS = 64.toByte() // type, size, 2 reserved bytes, payload
    }
}

data class EapAkaAttributeCustomIV(val payload: ByteArray) : EapAkaAttribute() {

    init {
        require(payload.size == 4) { "CUSTOM_IV payload size has to be 4 bytes. Payload: ${payload.toHex()}" }
    }

    override fun toByteArray(): ByteArray {
        return byteArrayOf(EapAkaAttributeType.AT_CUSTOM_IV.type, (SIZE / SIZE_MULTIPLIER).toByte(), 0, 0) + payload
    }

    companion object {

        fun parse(payload: ByteArray): EapAkaAttributeCustomIV {
            if (payload.size < 2 + 4) {
                throw MessageIOException("Could not parse CUSTOM_IV attribute: ${payload.toHex()}")
            }
            return EapAkaAttributeCustomIV(payload.copyOfRange(2, 2 + 4))
        }

        const val SIZE = 8 // type, size, 2 reserved bytes, payload=4
    }
}

data class EapAkaAttributeClientErrorCode(val payload: ByteArray) : EapAkaAttribute() {

    init {
        require(payload.size == 2) { "Error code hast to be 2 bytes. Payload: ${payload.toHex()}" }
    }

    override fun toByteArray(): ByteArray {
        return byteArrayOf(
            EapAkaAttributeType.AT_CLIENT_ERROR_CODE.type,
            (SIZE / SIZE_MULTIPLIER).toByte(),
            0,
            0
        ) + payload
    }

    companion object {

        fun parse(payload: ByteArray): EapAkaAttributeClientErrorCode {
            if (payload.size < 2 + 2) {
                throw MessageIOException("Could not parse CLIENT_ERROR_CODE attribute: ${payload.toHex()}")
            }
            return EapAkaAttributeClientErrorCode(payload.copyOfRange(2, 4))
        }

        const val SIZE = 4 // type, size=1, payload:2
    }
}
