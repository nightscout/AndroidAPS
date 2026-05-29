package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.EversenseE3Memory
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket

@EversensePacket(
    requestId = EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId,
    responseId = EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class GetBatteryPercentagePacket : EversenseBasePacket() {
    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.BatteryPercentage.getRequestData()
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        // The E3 battery register returns an enum index (0-11). Clamp to valid range.
        val raw = receivedData[getStartIndex()].toInt() and 0xFF
        val percentage = raw.coerceIn(0, 11)
        return Response(percentage = percentage)
    }

    data class Response(val percentage: Int) : EversenseBasePacket.Response()
}