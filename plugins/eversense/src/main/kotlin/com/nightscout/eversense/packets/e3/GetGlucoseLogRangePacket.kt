package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket

/**
 * Reads the first and last record numbers for the sensor glucose log (24-bit indices).
 * Use [GetCalibrationLogRangePacket] for blood glucose (calibration) log range.
 */
@EversensePacket(
    requestId = EversenseE3Packets.ReadFirstAndLastSensorGlucoseRecordNumbersCommandId,
    responseId = EversenseE3Packets.ReadFirstAndLastSensorGlucoseRecordNumbersResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class GetGlucoseLogRangePacket : EversenseBasePacket() {

    override fun getRequestData(): ByteArray = ByteArray(0)

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null

        val s = getStartIndex()
        val from = (receivedData[s].toInt() and 0xFF) or
            ((receivedData[s + 1].toInt() and 0xFF) shl 8) or
            ((receivedData[s + 2].toInt() and 0xFF) shl 16)
        val to = (receivedData[s + 3].toInt() and 0xFF) or
            ((receivedData[s + 4].toInt() and 0xFF) shl 8) or
            ((receivedData[s + 5].toInt() and 0xFF) shl 16)

        return Response(rangeFrom = from, rangeTo = to)
    }

    data class Response(val rangeFrom: Int, val rangeTo: Int) : EversenseBasePacket.Response()
}
