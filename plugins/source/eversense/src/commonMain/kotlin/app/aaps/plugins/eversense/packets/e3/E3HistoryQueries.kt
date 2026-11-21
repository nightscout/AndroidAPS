package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.protocol.PacketSpec

/**
 * Reads the first and last record numbers for the blood glucose (calibration) log (16-bit indices).
 * Use [GetGlucoseLogRangePacket] for sensor glucose log range.
 */

class GetCalibrationLogRangePacket : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.ReadFirstAndLastBloodGlucoseDataRecordNumbersCommandId, EversenseE3Packets.ReadFirstAndLastBloodGlucoseDataRecordNumbersResponseId)) {

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

/**
 * Reads the first and last record numbers for the sensor glucose log (24-bit indices).
 * Use [GetCalibrationLogRangePacket] for blood glucose (calibration) log range.
 */

class GetGlucoseLogRangePacket : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.ReadFirstAndLastSensorGlucoseRecordNumbersCommandId, EversenseE3Packets.ReadFirstAndLastSensorGlucoseRecordNumbersResponseId)) {

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
