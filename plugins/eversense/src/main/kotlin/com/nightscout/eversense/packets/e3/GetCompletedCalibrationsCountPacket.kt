package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.EversenseE3Memory
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket

@EversensePacket(
    requestId = EversenseE3Packets.ReadTwoByteSerialFlashRegisterCommandId,
    responseId = EversenseE3Packets.ReadTwoByteSerialFlashRegisterResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class GetCompletedCalibrationsCountPacket : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.CalibrationsMadeInThisPhase.getRequestData()
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null

        val s = getStartIndex()
        val count = (receivedData[s].toInt() and 0xFF) or ((receivedData[s + 1].toInt() and 0xFF) shl 8)
        return Response(count = count)
    }

    data class Response(val count: Int) : EversenseBasePacket.Response()
}
