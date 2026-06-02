package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket

@EversensePacket(
    requestId = EversenseE3Packets.PingCommandId,
    responseId = EversenseE3Packets.PingResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class PingPacket : EversenseBasePacket() {

    override fun getRequestData(): ByteArray = ByteArray(0)

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}
