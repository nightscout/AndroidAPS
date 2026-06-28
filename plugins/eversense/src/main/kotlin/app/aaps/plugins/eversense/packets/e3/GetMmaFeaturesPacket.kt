package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.enums.EversenseE3Memory
import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket

@EversensePacket(
    requestId = EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId,
    responseId = EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class GetMmaFeaturesPacket : EversenseBasePacket() {
    override fun getRequestData(): ByteArray = EversenseE3Memory.MmaFeatures.getRequestData()
    override fun parseResponse(): Response? {
        if (receivedData.size < getStartIndex() + 1) return null
        return Response(value = receivedData[getStartIndex()].toInt() and 0xFF)
    }
    data class Response(val value: Int) : EversenseBasePacket.Response()
}