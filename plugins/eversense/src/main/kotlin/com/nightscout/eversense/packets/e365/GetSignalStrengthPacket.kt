package com.nightscout.eversense.packets.e365

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import java.nio.ByteBuffer
import java.nio.ByteOrder

@EversensePacket(
    requestId = Eversense365Packets.ReadCommandId,
    responseId = Eversense365Packets.ReadResponseId,
    typeId = Eversense365Packets.ReadSignalStrength,
    securityType = EversenseSecurityType.SecureV2
)
class GetSignalStrengthPacket : EversenseBasePacket() {

    override fun getRequestData(): ByteArray = byteArrayOf()

    // Parsed message:
    // 42 1B -> CmdType & CmdId
    // 01 -> SensorType
    // 0A -> Sensor ID length
    // 00 00 00 00 00 00 00 00 00 00 -> Sensor ID (length = byte[3])
    // XX XX XX XX XX XX XX XX -> Timestamp
    // XX XX -> Signal Strength (raw)
    // XX XX XX XX -> Signal Coupling (little-endian float, multiply by 100 for percentage)
    // XX -> Placement
    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null

        val hex = receivedData.joinToString(" ") { it.toString(16).padStart(2, '0') }
        com.nightscout.eversense.util.EversenseLogger.info("GetSignalStrengthPacket", "Raw response: $hex")

        // Byte [3] = sensor ID length
        val sensorIdLength = receivedData[3].toInt() and 0xFF

        // Signal coupling float starts at byte 14 + sensorIdLength
        val couplingStart = 14 + sensorIdLength
        if (receivedData.size < couplingStart + 4) {
            com.nightscout.eversense.util.EversenseLogger.warning("GetSignalStrengthPacket", "Response too short: ${receivedData.size} bytes, need ${couplingStart + 4}")
            return Response(signalStrength = 0)
        }

        // Read little-endian float and multiply by 100 for percentage
        val floatBytes = byteArrayOf(
            receivedData[couplingStart].toByte(),
            receivedData[couplingStart + 1].toByte(),
            receivedData[couplingStart + 2].toByte(),
            receivedData[couplingStart + 3].toByte()
        )
        val signalFloat = ByteBuffer.wrap(floatBytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .float
        val signalPercent = (signalFloat * 100).toInt().coerceIn(0, 100)

        com.nightscout.eversense.util.EversenseLogger.info("GetSignalStrengthPacket", "sensorIdLength: $sensorIdLength, signalFloat: $signalFloat -> $signalPercent%")
        return Response(signalStrength = signalPercent)
    }

    data class Response(
        val signalStrength: Int  // 0-100, transmitter-to-sensor placement signal
    ) : EversenseBasePacket.Response()
}
