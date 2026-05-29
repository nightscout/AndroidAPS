package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e3.util.EversenseE3Writer

@EversensePacket(
    requestId = EversenseE3Packets.SetCurrentTransmitterDateAndTimeCommandId,
    responseId = EversenseE3Packets.SetCurrentTransmitterDateAndTimeResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class SetCurrentDatetimePacket : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        val now = System.currentTimeMillis()
        return EversenseE3Writer.writeDate(now) +
            EversenseE3Writer.writeTime(now) +
            EversenseE3Writer.writeTimezone(now)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }

        return Response()
    }

    class Response : EversenseBasePacket.Response() {}
}