package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.EversenseE3Memory
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket

@EversensePacket(
    requestId = EversenseE3Packets.WriteTwoByteSerialFlashRegisterCommandId,
    responseId = EversenseE3Packets.WriteTwoByteSerialFlashRegisterResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class SetBleDisconnectPacket(private val intervalSeconds: Int = 300) : EversenseBasePacket() {
    override fun getRequestData(): ByteArray {
        val addr = EversenseE3Memory.BleDisconnect.getRequestData()
        return byteArrayOf(addr[0], addr[1], addr[2],
            (intervalSeconds and 0xFF).toByte(),
            ((intervalSeconds shr 8) and 0xFF).toByte()
        )
    }
    override fun parseResponse(): Response = Response()
    class Response : EversenseBasePacket.Response()
}