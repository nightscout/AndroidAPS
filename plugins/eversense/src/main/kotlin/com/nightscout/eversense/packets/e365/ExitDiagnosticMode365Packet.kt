package com.nightscout.eversense.packets.e365

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket

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
