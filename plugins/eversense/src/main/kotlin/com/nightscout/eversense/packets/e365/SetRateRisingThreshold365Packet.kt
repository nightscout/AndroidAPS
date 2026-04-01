package com.nightscout.eversense.packets.e365

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import java.nio.ByteBuffer
import java.nio.ByteOrder

@EversensePacket(
    requestId = Eversense365Packets.WriteCommandId,
    responseId = Eversense365Packets.WriteResponseId,
    typeId = Eversense365Packets.WriteRateRisingThreshold,
    securityType = EversenseSecurityType.SecureV2
)
class SetRateRisingThreshold365Packet(private val value: Double) : EversenseBasePacket() {
    override fun getRequestData(): ByteArray {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putFloat(value.toFloat())
        return buf.array()
    }
    override fun parseResponse(): Response = Response()
    class Response : EversenseBasePacket.Response()
}
