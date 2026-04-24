package com.nightscout.eversense.packets.e365

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket

@EversensePacket(
    requestId = Eversense365Packets.WriteCommandId,
    responseId = Eversense365Packets.WriteResponseId,
    typeId = Eversense365Packets.WriteAppVersion,
    securityType = EversenseSecurityType.SecureV2
)
class SetAppVersion365Packet(private val appVersion: String = "8.0.4") : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        val data = ByteArray(18)
        val versionBytes = appVersion.toByteArray(Charsets.US_ASCII)
        versionBytes.copyInto(data, 0, 0, minOf(versionBytes.size, 18))
        return data
    }

    override fun parseResponse(): Response = Response()
    class Response : EversenseBasePacket.Response()
}