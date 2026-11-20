package app.aaps.plugins.eversense.packets.e365

import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.protocol.PacketSpec
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SetHighGlucoseAlarm365Packet(private val value: Int) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.WriteCommandId, Eversense365Packets.WriteResponseId, Eversense365Packets.WriteHighGlucoseAlarm)) {

    override fun getRequestData(): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte()
    )

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class SetHighGlucoseAlarmEnabled365Packet(private val enabled: Boolean) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.WriteCommandId, Eversense365Packets.WriteResponseId, Eversense365Packets.WriteHighGlucoseAlarmEnable)) {

    override fun getRequestData(): ByteArray = byteArrayOf(if (enabled) 1 else 0)

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class SetLowGlucoseAlarm365Packet(private val value: Int) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.WriteCommandId, Eversense365Packets.WriteResponseId, Eversense365Packets.WriteLowGlucoseAlarm)) {

    override fun getRequestData(): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte()
    )

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class SetPredictionHighEnabled365Packet(private val enabled: Boolean) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.WriteCommandId, Eversense365Packets.WriteResponseId, Eversense365Packets.WritePredictionHighEnabled)) {

    override fun getRequestData(): ByteArray = byteArrayOf(if (enabled) 1 else 0)

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class SetPredictionHighThreshold365Packet(private val value: Int) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.WriteCommandId, Eversense365Packets.WriteResponseId, Eversense365Packets.WritePredictionHighThreshold)) {

    override fun getRequestData(): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte()
    )

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class SetPredictionHighTime365Packet(private val value: Int) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.WriteCommandId, Eversense365Packets.WriteResponseId, Eversense365Packets.WritePredictionHighTime)) {

    override fun getRequestData(): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte()
    )

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class SetPredictionLowEnabled365Packet(private val enabled: Boolean) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.WriteCommandId, Eversense365Packets.WriteResponseId, Eversense365Packets.WritePredictionLowEnabled)) {

    override fun getRequestData(): ByteArray = byteArrayOf(if (enabled) 1 else 0)

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class SetPredictionLowThreshold365Packet(private val value: Int) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.WriteCommandId, Eversense365Packets.WriteResponseId, Eversense365Packets.WritePredictionLowThreshold)) {

    override fun getRequestData(): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte()
    )

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class SetPredictionLowTime365Packet(private val value: Int) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.WriteCommandId, Eversense365Packets.WriteResponseId, Eversense365Packets.WritePredictionLowTime)) {

    override fun getRequestData(): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte()
    )

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class SetRateFallingEnabled365Packet(private val enabled: Boolean) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.WriteCommandId, Eversense365Packets.WriteResponseId, Eversense365Packets.WriteRateFallingEnabled)) {

    override fun getRequestData(): ByteArray = byteArrayOf(if (enabled) 1 else 0)

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class SetRateFallingThreshold365Packet(private val value: Double) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.WriteCommandId, Eversense365Packets.WriteResponseId, Eversense365Packets.WriteRateFallingThreshold)) {

    override fun getRequestData(): ByteArray {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putFloat(value.toFloat())
        return buf.array()
    }

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class SetRateRisingEnabled365Packet(private val enabled: Boolean) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.WriteCommandId, Eversense365Packets.WriteResponseId, Eversense365Packets.WriteRateRisingEnabled)) {

    override fun getRequestData(): ByteArray = byteArrayOf(if (enabled) 1 else 0)

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class SetRateRisingThreshold365Packet(private val value: Double) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.WriteCommandId, Eversense365Packets.WriteResponseId, Eversense365Packets.WriteRateRisingThreshold)) {

    override fun getRequestData(): ByteArray {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putFloat(value.toFloat())
        return buf.array()
    }

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class SetRepeatHighGlucose365Packet(private val value: Int) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.WriteCommandId, Eversense365Packets.WriteResponseId, Eversense365Packets.WriteHighGlucoseAlarmRepeat)) {

    override fun getRequestData(): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte()
    )

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class SetRepeatLowGlucose365Packet(private val value: Int) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.WriteCommandId, Eversense365Packets.WriteResponseId, Eversense365Packets.WriteLowGlucoseAlarmRepeat)) {

    override fun getRequestData(): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte()
    )

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class SetVibrateMode365Packet(private val enabled: Boolean) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.WriteCommandId, Eversense365Packets.WriteResponseId, Eversense365Packets.WriteVibrateMode)) {

    override fun getRequestData(): ByteArray = byteArrayOf(if (enabled) 1 else 0)

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}
