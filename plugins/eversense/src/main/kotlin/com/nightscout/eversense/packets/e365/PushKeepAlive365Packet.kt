package com.nightscout.eversense.packets.e365

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e365.utils.toUnix

/**
 * Push keep-alive packet that includes battery level and most recent glucose datetime.
 *
 * Offsets:
 * [10] = battery level (raw enum value)
 * [11..18] = most recent glucose datetime (Unix2000)
 */
@EversensePacket(
    requestId = Eversense365Packets.NotificationResponseId,
    responseId = Eversense365Packets.NotificationKeepAlive,
    typeId = 0,
    securityType = EversenseSecurityType.SecureV2
)
class PushKeepAlive365Packet : EversenseBasePacket() {

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
