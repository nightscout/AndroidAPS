package app.aaps.plugins.eversense.packets.e365

import app.aaps.plugins.eversense.enums.EversenseAlarm
import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.e365.utils.toByteArray
import app.aaps.plugins.eversense.packets.e365.utils.toTimeZone
import app.aaps.plugins.eversense.packets.e365.utils.toUnix
import app.aaps.plugins.eversense.packets.e365.utils.toUnixArray
import app.aaps.plugins.eversense.protocol.CalibrationPayload
import app.aaps.plugins.eversense.protocol.PacketSpec
import app.aaps.plugins.eversense.protocol.PacketTime
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.math.abs

class AuthIdentityPacket(val secret: ByteArray) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.AuthenticateCommandId, Eversense365Packets.AuthenticateResponseId, Eversense365Packets.AuthenticateIdentity)) {

    override fun getRequestData(): ByteArray {
        return secret
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response()
    }

    class Response: EversenseBasePacket.Response()
}

class AuthStartPacket(val secret: ByteArray) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.AuthenticateCommandId, Eversense365Packets.AuthenticateResponseId, Eversense365Packets.AuthenticateStart)) {

    override fun getRequestData(): ByteArray {
        return secret
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(
            receivedData.copyOfRange(2, 66).toByteArray()
        )
    }

    data class Response(
        val sessionPublicKey: ByteArray
    ) : EversenseBasePacket.Response()
}

class AuthWhoAmIPacket(private val clientId: ByteArray) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.AuthenticateCommandId, Eversense365Packets.AuthenticateResponseId, Eversense365Packets.AuthenticateWhoAmI)) {

    override fun getRequestData(): ByteArray {
        return clientId
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(
            receivedData.copyOfRange(2, 34).toByteArray(),
            receivedData.copyOfRange(34, 42).toByteArray(),
            ((receivedData[42].toInt() shl 8) or receivedData[43].toInt()) == 0,
        )
    }

    data class Response(
        val serialNumber: ByteArray,
        val nonce: ByteArray,
        val flags: Boolean
    ) : EversenseBasePacket.Response()
}

class EnterDiagnosticMode365Packet : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.OperationCommandId, Eversense365Packets.OperationResponseId, Eversense365Packets.EnterDiagnosticModeOperationId)) {

    override fun getRequestData(): ByteArray = ByteArray(0)

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class ExitDiagnosticMode365Packet : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.OperationCommandId, Eversense365Packets.OperationResponseId, Eversense365Packets.ExitDiagnosticModeOperationId)) {

    override fun getRequestData(): ByteArray = ByteArray(0)

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class KeepAlivePacket : EversenseBasePacket(PacketSpec.v2(-1, Eversense365Packets.NotificationResponseId, Eversense365Packets.NotificationKeepAlive)) {

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }
        return Response(
            glucoseDatetime = receivedData.copyOfRange(11, 19).toUnix(),
        )
    }

    data class Response(val glucoseDatetime: Long) : EversenseBasePacket.Response()
}

class Ping365Packet : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.ReadCommandId, Eversense365Packets.ReadResponseId, Eversense365Packets.ReadPing)) {

    override fun getRequestData(): ByteArray = byteArrayOf()

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

/**
 * Push notification packet for alarms with data payload.
 *
 * Packet format (empirically confirmed against real device logs - alarm code at [2] correctly
 * decoded real alarms like HIGH_GLUCOSE across multiple live captures; [3] was observed to be
 * constant/always 0, i.e. it's the reserved byte, not the code):
 * [0] = 0x44 (NotificationResponseId)
 * [1] = 0x03 (AlarmWithData)
 * [2] = alarm code
 * [3] = reserved
 * [4..11] = alarm datetime (Unix2000)
 * [12..] = alarm data
 */

class PushAlarmWithDataPacket : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.NotificationResponseId, Eversense365Packets.NotificationAlarmWithData, 0)) {

    override fun getRequestData(): ByteArray = ByteArray(0)

    override fun parseResponse(): Response? {
        if (receivedData.size < 12) return null
        val alarmCode = receivedData[2].toInt() and 0xFF
        val alarm = EversenseAlarm.from(alarmCode)
        val datetime = receivedData.copyOfRange(4, 12).toUnix()
        return Response(
            alarm = ActiveAlarm(code = alarm, codeRaw = alarmCode, flag = 0, priority = 0),
            datetime = datetime
        )
    }

    data class Response(
        val alarm: ActiveAlarm,
        val datetime: Long
    ) : EversenseBasePacket.Response()
}

/**
 * Push keep-alive packet that includes battery level and most recent glucose datetime.
 *
 * Offsets:
 * [10] = battery level (raw enum value)
 * [11..18] = most recent glucose datetime (Unix2000)
 */

class PushKeepAlive365Packet : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.NotificationResponseId, Eversense365Packets.NotificationKeepAlive, 0)) {

    override fun getRequestData(): ByteArray = ByteArray(0)

    override fun parseResponse(): Response? {
        if (receivedData.size < 19) return null
        val batteryLevel = receivedData[OFFSET_BATTERY_LEVEL].toInt() and 0xFF
        val glucoseDatetime = receivedData.copyOfRange(
            OFFSET_GLUCOSE_DATETIME_START,
            OFFSET_GLUCOSE_DATETIME_END
        ).toUnix()
        return Response(
            batteryLevelRaw = batteryLevel,
            mostRecentGlucoseDatetime = glucoseDatetime
        )
    }

    data class Response(
        val batteryLevelRaw: Int,
        val mostRecentGlucoseDatetime: Long
    ) : EversenseBasePacket.Response()
    companion object {
        private const val OFFSET_BATTERY_LEVEL = 10
        private const val OFFSET_GLUCOSE_DATETIME_START = 11
        private const val OFFSET_GLUCOSE_DATETIME_END = 19
    }
}

class SetAppVersion365Packet(private val appVersion: String = "8.0.4") : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.WriteCommandId, Eversense365Packets.WriteResponseId, Eversense365Packets.WriteAppVersion)) {

    override fun getRequestData(): ByteArray {
        val data = ByteArray(18)
        val versionBytes = appVersion.toByteArray(Charsets.US_ASCII)
        versionBytes.copyInto(data, 0, 0, minOf(versionBytes.size, 18))
        return data
    }

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class SetBleDisconnect365Packet(private val intervalSeconds: Int = 300) : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.WriteCommandId, Eversense365Packets.WriteResponseId, Eversense365Packets.WriteBleDisconnect)) {

    override fun getRequestData(): ByteArray {
        // UInt16 little-endian interval in seconds
        return byteArrayOf(
            (intervalSeconds and 0xFF).toByte(),
            ((intervalSeconds shr 8) and 0xFF).toByte()
        )
    }

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class SetBloodGlucosePointPacket365(private val glucoseInMgDl: Int, private val timestampMs: Long = PacketTime.nowMillis()) :
    EversenseBasePacket(PacketSpec.v2(Eversense365Packets.WriteCommandId, Eversense365Packets.WriteResponseId, Eversense365Packets.WriteCalibration)) {

    override fun getRequestData(): ByteArray = CalibrationPayload.v2(glucoseInMgDl, timestampMs, PacketTime.nowMillis())

    override fun parseResponse(): Response = Response()

    class Response : EversenseBasePacket.Response()
}

class SetCurrentDateTimePacket : EversenseBasePacket(PacketSpec.v2(Eversense365Packets.WriteCommandId, Eversense365Packets.WriteResponseId, Eversense365Packets.WriteCurrentDateTime)) {

    override fun getRequestData(): ByteArray {
        val now = PacketTime.nowMillis()
        val timezoneOffset = TimeZone.getDefault().getOffset(now)
        val timezoneNegative = if (timezoneOffset < 0) 255.toByte() else 0.toByte()
        var request = now.toUnixArray()
        request += abs(timezoneOffset).toTimeZone()
        request += byteArrayOf(timezoneNegative)
        return request
    }

    override fun parseResponse(): Response {
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}
