package app.aaps.plugins.eversense.packets.e365

import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket
import app.aaps.plugins.eversense.packets.e365.utils.toUnix

@EversensePacket(
    requestId = -1, // Can only be received
    responseId = Eversense365Packets.NotificationResponseId,
    typeId = Eversense365Packets.NotificationKeepAlive,
    securityType = EversenseSecurityType.SecureV2
)
class KeepAlivePacket : EversenseBasePacket() {
    override fun getRequestData(): ByteArray {
        return byteArrayOf()
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }

        return Response(
            glucoseDatetime = receivedData.copyOfRange(11, 19).toUnix(),
        )
    }

    data class Response(val glucoseDatetime: Long) : EversenseBasePacket.Response()
}