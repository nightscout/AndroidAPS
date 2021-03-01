package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Id
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessagePacket
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageType
import retrofit2.http.HEAD
import java.nio.ByteBuffer

enum class EapCode(val code: Byte) {
    REQUEST(1),
    RESPONSE(2),
    SUCCESS(3),
    FAILURE(4);

    companion object {

        fun byValue(value: Byte): EapCode =
            EapCode.values().firstOrNull { it.code == value }
                ?: throw IllegalArgumentException("Unknown EAP-AKA attribute type: $value")
    }
}

class EapAkaMessage(
    val code: EapCode,
    val identifier: Byte,
    val sequenceNumber: Byte,
    val source: Id,
    val destination: Id,
    val payload: ByteArray,
    val attributes: Array<EapAkaAttribute>?,
    val messagePacket: MessagePacket = MessagePacket(
        type = MessageType.SESSION_ESTABLISHMENT,
        source = source,
        destination = destination,
        payload = payload,
        sequenceNumber = sequenceNumber,
        sas = true // TODO: understand why this is true for PairMessages
    )) {

    fun toByteArray(): ByteArray {

        val serializedAttributes = attributes?.flatMap{ it.toByteArray().asIterable() }
        val joinedAttributes = serializedAttributes?.toTypedArray()?.toByteArray()

        val attrSize = joinedAttributes?.size ?: 0
        val totalSize = HEADER_SIZE + attrSize
        
        var bb = ByteBuffer
            .allocate(totalSize)
            .put(code.code)
            .put(identifier)
            .put(((totalSize ushr 1) and 0XFF).toByte())
            .put((totalSize and 0XFF).toByte())
            .put(AKA_PACKET_TYPE)
            .put(SUBTYPE_AKA_CHALLENGE)
            .put(byteArrayOf(0,0))
            .put(joinedAttributes ?: ByteArray(0))

        val ret = bb.array()
        return ret.copyOfRange(0, ret.size)
    }

    companion object {
        private const val HEADER_SIZE = 8
        private const val SUBTYPE_AKA_CHALLENGE = 1.toByte()
        private const val AKA_PACKET_TYPE = 0x17.toByte()
    }
}