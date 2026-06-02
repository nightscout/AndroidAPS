package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.enums.EversenseE3Memory
import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket
import app.aaps.plugins.eversense.packets.e3.util.EversenseE3Parser

@EversensePacket(
    requestId = EversenseE3Packets.ReadTwoByteSerialFlashRegisterCommandId,
    responseId = EversenseE3Packets.ReadTwoByteSerialFlashRegisterResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class GetLastCalibrationTimePacket : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.LastCalibrationTime.getRequestData()
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        return Response(time = EversenseE3Parser.readTime(receivedData, getStartIndex()))
    }

    data class Response(val time: Long) : EversenseBasePacket.Response()
}