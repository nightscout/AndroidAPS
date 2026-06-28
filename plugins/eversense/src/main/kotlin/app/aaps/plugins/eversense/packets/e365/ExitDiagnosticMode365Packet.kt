package app.aaps.plugins.eversense.packets.e365

import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket

@EversensePacket(
    requestId = Eversense365Packets.OperationCommandId,
    responseId = Eversense365Packets.OperationResponseId,
    typeId = Eversense365Packets.ExitDiagnosticModeOperationId,
    securityType = EversenseSecurityType.SecureV2
)
class ExitDiagnosticMode365Packet : EversenseBasePacket() {
    override fun getRequestData(): ByteArray = ByteArray(0)
    override fun parseResponse(): Response = Response()
    class Response : EversenseBasePacket.Response()
}
