package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.EversenseE3Memory
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket

@EversensePacket(
    requestId = EversenseE3Packets.WriteFourByteSerialFlashRegisterCommandId,
    responseId = EversenseE3Packets.WriteFourByteSerialFlashRegisterResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class SetAppVersionE3Packet(private val appVersion: String = "8.0.4") : EversenseBasePacket() {
    override fun getRequestData(): ByteArray {
        val parts = appVersion.split(".")
        val i0 = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val i1 = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val i2 = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val addr = EversenseE3Memory.AppVersion.getRequestData()
        return byteArrayOf(addr[0], addr[1], addr[2],
            (i2 and 0xFF).toByte(),
            ((i2 and 0xFF00) shr 8).toByte(),
            (i1 and 0xFF).toByte(),
            (i0 and 0xFF).toByte()
        )
    }
    override fun parseResponse(): Response = Response()
    class Response : EversenseBasePacket.Response()
}