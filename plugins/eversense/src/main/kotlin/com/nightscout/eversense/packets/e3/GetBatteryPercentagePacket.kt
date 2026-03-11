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

        return Response(percentage = parseEnum(receivedData[getStartIndex()].toInt()))
    }

    private fun parseEnum(value: Int): Int {
        return when(value) {
            0 -> 0 //%
            1 -> 5 //%
            2 -> 10 //%
            3 -> 25 //%
            4 -> 35 //%
            5 -> 45 //%
            6 -> 55 //%
            7 -> 65 //%
            8 -> 75 //%
            9 -> 85 //%
            10 -> 95 //%
            11 -> 100 //%
            else -> -1
        }
    }

    data class Response(val percentage: Int) : EversenseBasePacket.Response()
}