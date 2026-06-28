package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket

/**
 * Reads the first and last record numbers for the blood glucose (calibration) log (16-bit indices).
 * Use [GetGlucoseLogRangePacket] for sensor glucose log range.
 */
@EversensePacket(
    requestId = EversenseE3Packets.ReadFirstAndLastBloodGlucoseDataRecordNumbersCommandId,
    responseId = EversenseE3Packets.ReadFirstAndLastBloodGlucoseDataRecordNumbersResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class GetCalibrationLogRangePacket : EversenseBasePacket() {

    override fun getRequestData(): ByteArray = ByteArray(0)

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null

        val s = getStartIndex()
        val from = (receivedData[s].toInt() and 0xFF) or ((receivedData[s + 1].toInt() and 0xFF) shl 8)
        val to = (receivedData[s + 2].toInt() and 0xFF) or ((receivedData[s + 3].toInt() and 0xFF) shl 8)

        return Response(rangeFrom = from, rangeTo = to)
    }

    data class Response(val rangeFrom: Int, val rangeTo: Int) : EversenseBasePacket.Response()
}
