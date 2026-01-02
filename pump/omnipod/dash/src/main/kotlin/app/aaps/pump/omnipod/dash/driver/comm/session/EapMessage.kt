package app.aaps.pump.omnipod.dash.driver.comm.session

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.dash.driver.comm.exceptions.MessageIOException
import java.nio.ByteBuffer

@Suppress("unused")
enum class EapCode(val code: Byte) {

    REQUEST(1),
    RESPONSE(2),
    SUCCESS(3),
    FAILURE(4);

    companion object {

        fun byValue(value: Byte): EapCode =
            EapCode.entries.firstOrNull { it.code == value }
                ?: throw IllegalArgumentException("Unknown EAP-AKA attribute type: $value")
    }
}

class EapMessage(
    val code: EapCode,
    val identifier: Byte,
    val subType: Byte = 0,
    val attributes: Array<EapAkaAttribute>
) {

    fun toByteArray(): ByteArray {

        val serializedAttributes = attributes.flatMap { it.toByteArray().asIterable() }
        val joinedAttributes = serializedAttributes.toTypedArray().toByteArray()

        val attrSize = joinedAttributes.size
        if (attrSize == 0) {
            return byteArrayOf(code.code, identifier, 0, 4)
        }
        val totalSize = HEADER_SIZE + attrSize

        val bb = ByteBuffer
            .allocate(totalSize)
            .put(code.code)
            .put(identifier)
            .put(((totalSize ushr 8) and 0XFF).toByte())
            .put((totalSize and 0XFF).toByte())
            .put(AKA_PACKET_TYPE)
            .put(SUBTYPE_AKA_CHALLENGE)
            .put(byteArrayOf(0, 0))
            .put(joinedAttributes)

        val ret = bb.array()
        return ret.copyOfRange(0, ret.size)
    }

    companion object {

        private const val HEADER_SIZE = 8
        private const val SUBTYPE_AKA_CHALLENGE = 1.toByte()
        const val SUBTYPE_SYNCHRONIZATION_FAILURE = 4.toByte()

        private const val AKA_PACKET_TYPE = 0x17.toByte()

        fun parse(aapsLogger: AAPSLogger, payload: ByteArray): EapMessage {
            payload.assertSizeAtLeast(4)

            val totalSize = (payload[2].toInt() shl 8) or payload[3].toInt()
            payload.assertSizeAtLeast(totalSize)

            if (payload.size == 4) { // SUCCESS/FAILURE
                return EapMessage(
                    code = EapCode.byValue(payload[0]),
                    identifier = payload[1],
                    attributes = arrayOf()
                )
            }
            if (totalSize > 0 && payload[4] != AKA_PACKET_TYPE) {
                throw MessageIOException("Invalid eap payload. Expected AKA packet type: ${payload.toHex()}")
            }
            val attributesPayload = payload.copyOfRange(8, totalSize)
            aapsLogger.debug(LTag.PUMPBTCOMM, "parsing EAP payload: ${payload.toHex()}")
            return EapMessage(
                code = EapCode.byValue(payload[0]),
                identifier = payload[1],
                attributes = EapAkaAttribute.parseAttributes(attributesPayload).toTypedArray(),
                subType = payload[5],
            )
        }
    }
}

private fun ByteArray.assertSizeAtLeast(size: Int) {
    if (this.size < size) {
        throw MessageIOException("Payload too short: ${this.toHex()}")
    }
}
