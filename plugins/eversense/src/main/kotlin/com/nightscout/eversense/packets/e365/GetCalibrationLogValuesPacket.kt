package com.nightscout.eversense.packets.e365

import com.nightscout.eversense.enums.CalibrationFlag
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e365.utils.toUnix
import com.nightscout.eversense.util.EversenseLogger

data class CalibrationHistoryItem(
    val datetime: Long,
    val glucoseInMgDl: Int,
    val flag: CalibrationFlag
)

@EversensePacket(
    requestId = Eversense365Packets.ReadCommandId,
    responseId = Eversense365Packets.ReadLogsId,
    typeId = Eversense365Packets.ReadLogValue,
    securityType = EversenseSecurityType.SecureV2
)
class GetCalibrationLogValuesPacket(
    private val from: Int,
    private val to: Int
) : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return byteArrayOf(
            Eversense365Packets.LogTypeCalibrations,
            (from and 0xFF).toByte(), ((from shr 8) and 0xFF).toByte(),
            ((from shr 16) and 0xFF).toByte(), ((from shr 24) and 0xFF).toByte(),
            (to and 0xFF).toByte(), ((to shr 8) and 0xFF).toByte(),
            ((to shr 16) and 0xFF).toByte(), ((to shr 24) and 0xFF).toByte()
        )
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null

        if (receivedData[6].toInt() != Eversense365Packets.LogTypeCalibrations.toInt()) {
            EversenseLogger.error("GetCalibrationLogValuesPacket", "Invalid log type: ${receivedData[6]}")
            return Response(count = 0, calibrationHistory = emptyList())
        }

        val actualData = receivedData.drop(7).toUByteArray()
        val recordLength = 56

        // Offset: recordLength(4) + datetime(8) + FsStartEndFlag(2) + ProcessingDatetime(8)
        //         + SampleDatetime(8) + MmaFSDatetime(8) + DecisionDatetime(8) = 46
        val offsetGlucose = 4 + 8 + 2 + 8 + 8 + 8 + 8
        val offsetFlag = offsetGlucose + 2

        val history = mutableListOf<CalibrationHistoryItem>()
        var i = 0

        while (i + recordLength <= actualData.size) {
            val chunk = actualData.copyOfRange(i, i + recordLength)
            val datetime = chunk.copyOfRange(4, 12).toUnix()
            val glucose = (chunk[offsetGlucose].toInt() and 0xFF) or ((chunk[offsetGlucose + 1].toInt() and 0xFF) shl 8)
            val flag = CalibrationFlag.from(chunk[offsetFlag].toInt() and 0xFF)
            history.add(CalibrationHistoryItem(datetime = datetime, glucoseInMgDl = glucose, flag = flag))
            i += recordLength
        }

        return Response(count = history.size, calibrationHistory = history)
    }

    data class Response(
        val count: Int,
        val calibrationHistory: List<CalibrationHistoryItem>
    ) : EversenseBasePacket.Response()
}
