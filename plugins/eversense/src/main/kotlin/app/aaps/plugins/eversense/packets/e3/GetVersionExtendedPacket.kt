package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.enums.EversenseE3Memory
import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket

@EversensePacket(
    requestId = EversenseE3Packets.ReadFourByteSerialFlashRegisterCommandId,
    responseId = EversenseE3Packets.ReadFourByteSerialFlashRegisterResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class GetVersionExtendedPacket : EversenseBasePacket() {
    override fun getRequestData(): ByteArray = EversenseE3Memory.TransmitterSoftwareVersionExt.getRequestData()
    override fun parseResponse(): Response? {
        val start = getStartIndex()
        if (receivedData.size < start + 4) return null
        val extVersion = (start until start + 4).map { receivedData[it].toInt().toChar() }.joinToString("")
        return Response(extVersion = extVersion.trim())
    }
    data class Response(val extVersion: String) : EversenseBasePacket.Response()
}