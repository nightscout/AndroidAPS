package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e3.util.EversenseE3Parser

@EversensePacket(
    requestId = EversenseE3Packets.ReadAllSensorGlucoseDataInSpecifiedRangeCommandId,
    responseId = EversenseE3Packets.ReadAllSensorGlucoseDataInSpecifiedRangeResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class GetGlucoseLogPacket(private val index: Int) : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        // 24-bit (3-byte) index sent as both from and to
        return byteArrayOf(
            index.toByte(),
            (index shr 8).toByte(),
            (index shr 16).toByte(),
            index.toByte(),
            (index shr 8).toByte(),
            (index shr 16).toByte()
        )
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null

        val s = getStartIndex()
        val recordIndex = (receivedData[s].toInt() and 0xFF) or
            ((receivedData[s + 1].toInt() and 0xFF) shl 8) or
            ((receivedData[s + 2].toInt() and 0xFF) shl 16)
        val datetime = EversenseE3Parser.readDate(receivedData, s + 3) + EversenseE3Parser.readTime(receivedData, s + 5)
        val glucoseInMgDl = (receivedData[s + 7].toInt() and 0xFF) or ((receivedData[s + 8].toInt() and 0xFF) shl 8)

        return Response(
            index = recordIndex,
            datetime = datetime,
            glucoseInMgDl = glucoseInMgDl
        )
    }

    data class Response(
        val index: Int,
        val datetime: Long,
        val glucoseInMgDl: Int
    ) : EversenseBasePacket.Response()
}
