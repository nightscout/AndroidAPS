package com.nightscout.eversense.packets.e365

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket

enum class LogType(val value: Byte) {
    ALERTS(0),
    CALIBRATIONS(6),
    GLUCOSE(13)
}

@EversensePacket(
    requestId = Eversense365Packets.ReadCommandId,
    responseId = Eversense365Packets.ReadResponseId,
    typeId = Eversense365Packets.ReadLogRange,
    securityType = EversenseSecurityType.SecureV2
)
class GetLogRangePacket365(private val logType: LogType) : EversenseBasePacket() {

    override fun getRequestData(): ByteArray = byteArrayOf(logType.value)

    // 42 38 -> CmdType & CmdId
    // XX -> log type
    // 00 00 00 00 -> rangeFrom (UInt32 little-endian)
    // 00 00 00 00 -> rangeTo (UInt32 little-endian)
    override fun parseResponse(): Response? {
        if (receivedData.size < 11) return null

        val rangeFrom = ((receivedData[3].toLong() and 0xFF) or
            ((receivedData[4].toLong() and 0xFF) shl 8) or
            ((receivedData[5].toLong() and 0xFF) shl 16) or
            ((receivedData[6].toLong() and 0xFF) shl 24)).toInt()

        val rangeTo = ((receivedData[7].toLong() and 0xFF) or
            ((receivedData[8].toLong() and 0xFF) shl 8) or
            ((receivedData[9].toLong() and 0xFF) shl 16) or
            ((receivedData[10].toLong() and 0xFF) shl 24)).toInt()

        com.nightscout.eversense.util.EversenseLogger.info(
            "GetLogRangePacket365", "Log range: $rangeFrom - $rangeTo"
        )
        return Response(rangeFrom = rangeFrom, rangeTo = rangeTo)
    }

    data class Response(val rangeFrom: Int, val rangeTo: Int) : EversenseBasePacket.Response()
}
