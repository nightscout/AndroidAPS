package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.EversenseE3Memory
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket

@EversensePacket(
    requestId = EversenseE3Packets.WriteSingleByteSerialFlashRegisterCommandId,
    responseId = EversenseE3Packets.WriteSingleByteSerialFlashRegisterResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class SetHighGlucoseRepeatIntervalDayPacket(private val intervalMinutes: Int) : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return EversenseE3Memory.HighGlucoseAlarmRepeatIntervalDay.getRequestData() +
            byteArrayOf(intervalMinutes.toByte())
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}
