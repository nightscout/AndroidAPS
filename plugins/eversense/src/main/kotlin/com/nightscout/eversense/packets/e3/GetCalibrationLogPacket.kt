package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.CalibrationFlag
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e3.util.EversenseE3Parser
import com.nightscout.eversense.packets.e3.util.EversenseE3Writer

@EversensePacket(
    requestId = EversenseE3Packets.ReadLogOfBloodGlucoseDataInSpecifiedRangeCommandId,
    responseId = EversenseE3Packets.ReadLogOfBloodGlucoseDataInSpecifiedRangeResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class GetCalibrationLogPacket(private val index: Int) : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return EversenseE3Writer.writeInt16(index) + EversenseE3Writer.writeInt16(index)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null

        val s = getStartIndex()
        val recordIndex = (receivedData[s].toInt() and 0xFF) or ((receivedData[s + 1].toInt() and 0xFF) shl 8)
        val datetime = EversenseE3Parser.readDate(receivedData, s + 2) + EversenseE3Parser.readTime(receivedData, s + 4)
        val glucoseInMgDl = (receivedData[s + 6].toInt() and 0xFF) or ((receivedData[s + 8].toInt() and 0xFF) shl 7)
        val flagCode = receivedData[s + 9].toInt() and 0xFF

        return Response(
            index = recordIndex,
            datetime = datetime,
            glucoseInMgDl = glucoseInMgDl,
            flag = CalibrationFlag.from(flagCode)
        )
    }

    data class Response(
        val index: Int,
        val datetime: Long,
        val glucoseInMgDl: Int,
        val flag: CalibrationFlag
    ) : EversenseBasePacket.Response()
}
