package app.aaps.plugins.eversense.packets.e365

import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket

@EversensePacket(
    requestId = Eversense365Packets.AuthenticateCommandId,
    responseId = Eversense365Packets.AuthenticateResponseId,
    typeId = Eversense365Packets.AuthenticateWhoAmI,
    securityType = EversenseSecurityType.SecureV2
)
class AuthWhoAmIPacket(private val clientId: ByteArray) : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return clientId
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }

        return Response(
            receivedData.copyOfRange(2, 34).toByteArray(),
            receivedData.copyOfRange(34, 42).toByteArray(),
            ((receivedData[42].toInt() shl 8) or receivedData[43].toInt()) == 0,
        )
    }

    data class Response(
        val serialNumber: ByteArray,
        val nonce: ByteArray,
        val flags: Boolean
    ) : EversenseBasePacket.Response()
}