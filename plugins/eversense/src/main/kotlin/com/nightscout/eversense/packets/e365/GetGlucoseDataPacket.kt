package com.nightscout.eversense.packets.e365

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.enums.EversenseTrendArrow
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e365.utils.toInt
import com.nightscout.eversense.util.EversenseLogger
import com.nightscout.eversense.packets.e365.utils.toUnix

@EversensePacket(
    requestId = Eversense365Packets.ReadCommandId,
    responseId = Eversense365Packets.ReadResponseId,
    typeId = Eversense365Packets.ReadGlucoseData,
    securityType = EversenseSecurityType.SecureV2
)
class GetGlucoseDataPacket(private val sensorIdLen: Int) : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return byteArrayOf()
    }

    // 42 1F -> CmdType & CmdId
    // F6 95 86 CB C1 00 00 00 -> Current datetime
    // 00 -> Sensor type
    // 0A -> Sensor ID length
    // 00 00 00 00 00 00 00 00 00 00 -> Sensor ID
    // 00 18 82 cb c1 00 00 00 -> Most recent glucose datetime
    // bc 00 -> Most recent glucose value
    // 32 00 -> Signal strength (transmitter-to-sensor, little-endian UInt16)
    // 00 00 -> Glucose unavailable reason
    // ... measurement bytes ...
    // 04 -> Trend direction
    // 07 -> Battery percentage
    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null

        var sensorIdLen = receivedData[11].toInt()
        if (sensorIdLen == 0x00) {
            sensorIdLen = this.sensorIdLen
        }

        // Signal strength at bytes 22+sensorIdLen (little-endian UInt16)
        val signalRaw = (receivedData[22 + sensorIdLen].toInt() and 0xFF) or
            ((receivedData[23 + sensorIdLen].toInt() and 0xFF) shl 8)

        EversenseLogger.info("GetGlucoseDataPacket", "Sensor signal strength raw: $signalRaw")

        val sensorId = receivedData.copyOfRange(12, 12 + sensorIdLen)
            .toByteArray().joinToString("") { "%02x".format(it) }
        val rawHex = receivedData.toByteArray().joinToString("") { "%02x".format(it) }

        return Response(
            datetime = receivedData.copyOfRange(12 + sensorIdLen, 20 + sensorIdLen).toUnix(),
            glucoseInMgDl = receivedData.copyOfRange(20 + sensorIdLen, 22 + sensorIdLen).toInt(),
            trend = getTrend(receivedData[164 + sensorIdLen].toInt()),
            signalStrength = signalRaw,
            sensorId = sensorId,
            rawResponseHex = rawHex
        )
    }

    private fun getTrend(value: Int): EversenseTrendArrow {
        return when (value) {
            1  -> EversenseTrendArrow.SINGLE_DOWN
            2  -> EversenseTrendArrow.FORTY_FIVE_DOWN
            4  -> EversenseTrendArrow.FLAT
            8  -> EversenseTrendArrow.FORTY_FIVE_UP
            16 -> EversenseTrendArrow.SINGLE_UP
            32 -> EversenseTrendArrow.SINGLE_DOWN
            64 -> EversenseTrendArrow.SINGLE_UP
            else -> EversenseTrendArrow.NONE
        }
    }

    data class Response(
        val datetime: Long,
        val glucoseInMgDl: Int,
        val trend: EversenseTrendArrow,
        val signalStrength: Int,
        val sensorId: String = "",
        val rawResponseHex: String = ""
    ) : EversenseBasePacket.Response()
}
