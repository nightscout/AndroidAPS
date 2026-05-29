package com.nightscout.eversense.packets.e365

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket

@EversensePacket(
    requestId = Eversense365Packets.WriteCommandId,
    responseId = Eversense365Packets.WriteResponseId,
    typeId = Eversense365Packets.WriteLowGlucoseAlarm,
    securityType = EversenseSecurityType.SecureV2
)
class SetLowGlucoseAlarm365Packet(private val value: Int) : EversenseBasePacket() {
    override fun getRequestData(): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte()
    )
    override fun parseResponse(): Response = Response()
    class Response : EversenseBasePacket.Response()
}
