package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.enums.CalibrationFlag
import app.aaps.plugins.eversense.enums.EversenseAlarm
import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.e3.util.EversenseE3Parser
import app.aaps.plugins.eversense.packets.e3.util.EversenseE3Writer
import app.aaps.plugins.eversense.protocol.PacketSpec

class GetAlertLogPacket(private val index: Int) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.ReadAllSensorGlucoseAlertsInSpecifiedRangeCommandId, EversenseE3Packets.ReadAllSensorGlucoseAlertsInSpecifiedRangeResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Writer.writeInt16(index) + EversenseE3Writer.writeInt16(index)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        val s = getStartIndex()
        val recordIndex = (receivedData[s].toInt() and 0xFF) or ((receivedData[s + 1].toInt() and 0xFF) shl 8)
        val datetime = EversenseE3Parser.readDate(receivedData, s + 2) + EversenseE3Parser.readTime(receivedData, s + 4)
        val alarmCode = receivedData[s + 7].toInt() and 0xFF
        return Response(
            index = recordIndex,
            datetime = datetime,
            alarm = EversenseAlarm.from(alarmCode)
        )
    }

    data class Response(
        val index: Int,
        val datetime: Long,
        val alarm: EversenseAlarm
    ) : EversenseBasePacket.Response()
}

class GetCalibrationLogPacket(private val index: Int) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.ReadLogOfBloodGlucoseDataInSpecifiedRangeCommandId, EversenseE3Packets.ReadLogOfBloodGlucoseDataInSpecifiedRangeResponseId)) {

    override fun getRequestData(): ByteArray {
        return EversenseE3Writer.writeInt16(index) + EversenseE3Writer.writeInt16(index)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        val s = getStartIndex()
        val recordIndex = (receivedData[s].toInt() and 0xFF) or ((receivedData[s + 1].toInt() and 0xFF) shl 8)
        val datetime = EversenseE3Parser.readDate(receivedData, s + 2) + EversenseE3Parser.readTime(receivedData, s + 4)
        val glucoseInMgDl = (receivedData[s + 6].toInt() and 0xFF) or ((receivedData[s + 8].toInt() and 0xFF) shl 7)
        val flagCode = receivedData[s + 9].toInt() and 0xFF
        return Response(
            index = recordIndex,
            datetime = datetime,
            glucoseInMgDl = glucoseInMgDl,
            flag = CalibrationFlag.from(flagCode)
        )
    }

    data class Response(
        val index: Int,
        val datetime: Long,
        val glucoseInMgDl: Int,
        val flag: CalibrationFlag
    ) : EversenseBasePacket.Response()
}

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

class GetGlucoseLogPacket(private val index: Int) : EversenseBasePacket(PacketSpec.e3(EversenseE3Packets.ReadAllSensorGlucoseDataInSpecifiedRangeCommandId, EversenseE3Packets.ReadAllSensorGlucoseDataInSpecifiedRangeResponseId)) {

    override fun getRequestData(): ByteArray {
        // 24-bit (3-byte) index sent as both from and to
        return byteArrayOf(
            index.toByte(),
            (index shr 8).toByte(),
            (index shr 16).toByte(),
            index.toByte(),
            (index shr 8).toByte(),
            (index shr 16).toByte()
        )
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        val s = getStartIndex()
        val recordIndex = (receivedData[s].toInt() and 0xFF) or
            ((receivedData[s + 1].toInt() and 0xFF) shl 8) or
            ((receivedData[s + 2].toInt() and 0xFF) shl 16)
        val datetime = EversenseE3Parser.readDate(receivedData, s + 3) + EversenseE3Parser.readTime(receivedData, s + 5)
        val glucoseInMgDl = (receivedData[s + 7].toInt() and 0xFF) or ((receivedData[s + 8].toInt() and 0xFF) shl 8)
        return Response(
            index = recordIndex,
            datetime = datetime,
            glucoseInMgDl = glucoseInMgDl
        )
    }

    data class Response(
        val index: Int,
        val datetime: Long,
        val glucoseInMgDl: Int
    ) : EversenseBasePacket.Response()
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
