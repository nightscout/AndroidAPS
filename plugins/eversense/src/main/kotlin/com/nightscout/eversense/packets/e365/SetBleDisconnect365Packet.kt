package com.nightscout.eversense.packets.e365

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket

@EversensePacket(
    requestId = Eversense365Packets.WriteCommandId,
    responseId = Eversense365Packets.WriteResponseId,
    typeId = Eversense365Packets.WriteBleDisconnect,
    securityType = EversenseSecurityType.SecureV2
)
class SetBleDisconnect365Packet(private val intervalSeconds: Int = 300) : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        // UInt16 little-endian interval in seconds
        return byteArrayOf(
            (intervalSeconds and 0xFF).toByte(),
            ((intervalSeconds shr 8) and 0xFF).toByte()
        )
    }

    override fun parseResponse(): Response = Response()
    class Response : EversenseBasePacket.Response()
}