package com.nightscout.eversense.packets.e365

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e365.utils.toUnix

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