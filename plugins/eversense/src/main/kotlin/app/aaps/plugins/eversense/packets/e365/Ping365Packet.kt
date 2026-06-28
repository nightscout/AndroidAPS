package app.aaps.plugins.eversense.packets.e365

import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket

@EversensePacket(
    requestId = Eversense365Packets.ReadCommandId,
    responseId = Eversense365Packets.ReadResponseId,
    typeId = Eversense365Packets.ReadPing,
    securityType = EversenseSecurityType.SecureV2
)
class Ping365Packet : EversenseBasePacket() {
    override fun getRequestData(): ByteArray = byteArrayOf()
    override fun parseResponse(): Response = Response()
    class Response : EversenseBasePacket.Response()
}