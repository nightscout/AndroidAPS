package app.aaps.plugins.eversense.packets.e365

import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket

@EversensePacket(
    requestId = Eversense365Packets.AuthenticateCommandId,
    responseId = Eversense365Packets.AuthenticateResponseId,
    typeId = Eversense365Packets.AuthenticateStart,
    securityType = EversenseSecurityType.SecureV2
)
class AuthStartPacket(val secret: ByteArray) : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return secret
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }

        return Response(
            receivedData.copyOfRange(2, 66).toByteArray()
        )
    }

    data class Response(
        val sessionPublicKey: ByteArray
    ) : EversenseBasePacket.Response()
}