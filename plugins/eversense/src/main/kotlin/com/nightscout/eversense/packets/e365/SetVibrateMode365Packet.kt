package com.nightscout.eversense.packets.e365

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket

@EversensePacket(
    requestId = Eversense365Packets.WriteCommandId,
    responseId = Eversense365Packets.WriteResponseId,
    typeId = Eversense365Packets.WriteVibrateMode,
    securityType = EversenseSecurityType.SecureV2
)
class SetVibrateMode365Packet(private val enabled: Boolean) : EversenseBasePacket() {
    override fun getRequestData(): ByteArray = byteArrayOf(if (enabled) 1 else 0)
    override fun parseResponse(): Response = Response()
    class Response : EversenseBasePacket.Response()
}
