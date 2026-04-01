package com.nightscout.eversense.packets.e365

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.enums.EversenseTrendArrow
import com.nightscout.eversense.models.GlucoseHistoryItem
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e365.utils.toUnix
import com.nightscout.eversense.util.EversenseLogger

@EversensePacket(
    requestId = Eversense365Packets.ReadCommandId,
    responseId = 0x62.toByte(),
    typeId = Eversense365Packets.ReadLogValue,
    securityType = EversenseSecurityType.SecureV2
)
class GetGlucoseLogValuesPacket(
    private val from: Int,
    private val to: Int,
    private val sensorIdLength: Int = 10
) : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        val logType: Byte = 13 // LogTypes.Glucose
        return byteArrayOf(
            logType,
            (from and 0xFF).toByte(), ((from shr 8) and 0xFF).toByte(),
            ((from shr 16) and 0xFF).toByte(), ((from shr 24) and 0xFF).toByte(),
            (to and 0xFF).toByte(), ((to shr 8) and 0xFF).toByte(),
            ((to shr 16) and 0xFF).toByte(), ((to shr 24) and 0xFF).toByte()
        )
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null

        if (receivedData[6].toInt() != 13) {
            EversenseLogger.error("GetGlucoseLogValuesPacket", "Invalid log type: ${receivedData[6]}")
            return Response(count = 0, glucoseHistory = emptyList())
        }

        val actualData = receivedData.drop(7).toUByteArray()
        val recordLength = 193
        val offsetGlucose = sensorIdLength + 8 + 4
        val offsetTrend = offsetGlucose + 2 + 4
        val history = mutableListOf<GlucoseHistoryItem>()
        var i = 0

        while (i + recordLength <= actualData.size) {
            val chunk = actualData.copyOfRange(i, i + recordLength)
            val datetime = chunk.copyOfRange(4, 12).toUnix()
            val glucose = (chunk[offsetGlucose].toInt() and 0xFF) or
                ((chunk[offsetGlucose + 1].toInt() and 0xFF) shl 8)
            val trend = getTrend(chunk[offsetTrend].toInt() and 0xFF)
            i += recordLength

            if (glucose >= 0x03E8) {
                EversenseLogger.warning("GetGlucoseLogValuesPacket", "Glucose exceeds limits: $glucose — skipping")
                continue
            }
            history.add(GlucoseHistoryItem(valueInMgDl = glucose, datetime = datetime, trend = trend))
        }

        EversenseLogger.info("GetGlucoseLogValuesPacket", "History records: ${history.size}")
        return Response(count = history.size, glucoseHistory = history)
    }

    private fun getTrend(value: Int): EversenseTrendArrow = when (value) {
        1  -> EversenseTrendArrow.SINGLE_DOWN
        2  -> EversenseTrendArrow.FORTY_FIVE_DOWN
        4  -> EversenseTrendArrow.FLAT
        8  -> EversenseTrendArrow.FORTY_FIVE_UP
        16 -> EversenseTrendArrow.SINGLE_UP
        32 -> EversenseTrendArrow.SINGLE_DOWN
        64 -> EversenseTrendArrow.SINGLE_UP
        else -> EversenseTrendArrow.FLAT
    }

    data class Response(
        val count: Int,
        val glucoseHistory: List<GlucoseHistoryItem>
    ) : EversenseBasePacket.Response()
}
