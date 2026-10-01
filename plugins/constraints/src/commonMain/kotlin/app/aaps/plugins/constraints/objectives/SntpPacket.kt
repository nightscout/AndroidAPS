package app.aaps.plugins.constraints.objectives

/**
 * The SNTP wire format, with no socket and no platform types.
 *
 * This is the half of `SntpClient` that decides what the bytes mean: building the 48 byte request,
 * reading the three timestamps out of a response, and turning them into a corrected clock. It is
 * split out so it can be tested against captured packets, and so the transport underneath it can be
 * replaced without touching the arithmetic.
 *
 * Why that matters here: these numbers decide whether an objective's minimum duration has really
 * elapsed, which is what stops a user unlocking closed loop and SMB by moving the phone clock
 * forward. A rewrite that is a few seconds out would not look wrong on screen.
 *
 * The arithmetic is deliberately left exactly as it was - including the integer division in
 * [readTimeStamp] and the halving in [parseResponse] - so this move cannot change a result.
 */
internal object SntpPacket {

    const val NTP_PACKET_SIZE = 48
    const val NTP_PORT = 123

    private const val ORIGINATE_TIME_OFFSET = 24
    private const val RECEIVE_TIME_OFFSET = 32
    private const val TRANSMIT_TIME_OFFSET = 40
    private const val NTP_MODE_CLIENT = 3
    private const val NTP_VERSION = 3

    /** Seconds between Jan 1 1900 and Jan 1 1970: 70 years plus 17 leap days. */
    private const val OFFSET_1900_TO_1970 = (365L * 70L + 17L) * 24L * 60L * 60L

    /**
     * The 48 byte client request, with [requestTime] written as the transmit timestamp.
     *
     * [lowOrderByte] is the last byte of that timestamp. The protocol wants random data there, and
     * the caller supplies it rather than this function generating it, so a test can produce a byte
     * exact packet.
     */
    fun buildRequest(requestTime: Long, lowOrderByte: Int): ByteArray {
        val buffer = ByteArray(NTP_PACKET_SIZE)
        // mode = 3 (client) in the low 3 bits, version = 3 in bits 3-5
        buffer[0] = (NTP_MODE_CLIENT or (NTP_VERSION shl 3)).toByte()
        writeTimeStamp(buffer, TRANSMIT_TIME_OFFSET, requestTime, lowOrderByte)
        return buffer
    }

    /** What one completed transaction says about this device's clock. */
    data class Timing(
        val ntpTime: Long,
        val ntpTimeReference: Long,
        val roundTripTime: Long
    )

    /**
     * Turns a server response into a corrected time.
     *
     * [requestTicks] and [responseTicks] are monotonic values from the same clock, used for the
     * latency correction; [requestTime] is the wall clock reading taken next to [requestTicks].
     */
    fun parseResponse(buffer: ByteArray, requestTime: Long, requestTicks: Long, responseTicks: Long): Timing {
        val responseTime = requestTime + (responseTicks - requestTicks)
        val originateTime = readTimeStamp(buffer, ORIGINATE_TIME_OFFSET)
        val receiveTime = readTimeStamp(buffer, RECEIVE_TIME_OFFSET)
        val transmitTime = readTimeStamp(buffer, TRANSMIT_TIME_OFFSET)
        val roundTripTime = responseTicks - requestTicks - (transmitTime - receiveTime)
        val clockOffset = (receiveTime - originateTime + (transmitTime - responseTime)) / 2
        // Use the times on this side of the network latency - response rather than request.
        return Timing(
            ntpTime = responseTime + clockOffset,
            ntpTimeReference = responseTicks,
            roundTripTime = roundTripTime
        )
    }

    /** Reads an unsigned 32 bit big endian number from [offset]. */
    fun read32(buffer: ByteArray, offset: Int): Long {
        val b0 = buffer[offset]
        val b1 = buffer[offset + 1]
        val b2 = buffer[offset + 2]
        val b3 = buffer[offset + 3]

        // convert signed bytes to unsigned values
        val i0 = if (b0.toInt() and 0x80 == 0x80) (b0.toInt() and 0x7F) + 0x80 else b0.toInt()
        val i1 = if (b1.toInt() and 0x80 == 0x80) (b1.toInt() and 0x7F) + 0x80 else b1.toInt()
        val i2 = if (b2.toInt() and 0x80 == 0x80) (b2.toInt() and 0x7F) + 0x80 else b2.toInt()
        val i3 = if (b3.toInt() and 0x80 == 0x80) (b3.toInt() and 0x7F) + 0x80 else b3.toInt()
        return (i0.toLong() shl 24) + (i1.toLong() shl 16) + (i2.toLong() shl 8) + i3.toLong()
    }

    /** Reads the NTP timestamp at [offset] as milliseconds since January 1 1970. */
    fun readTimeStamp(buffer: ByteArray, offset: Int): Long {
        val seconds = read32(buffer, offset)
        val fraction = read32(buffer, offset + 4)
        return (seconds - OFFSET_1900_TO_1970) * 1000 + fraction * 1000L / 0x100000000L
    }

    /** Writes [time] (milliseconds since January 1 1970) as an NTP timestamp at [offsetParam]. */
    fun writeTimeStamp(buffer: ByteArray, offsetParam: Int, time: Long, lowOrderByte: Int) {
        var offset = offsetParam
        var seconds = time / 1000L
        val milliseconds = time - seconds * 1000L
        seconds += OFFSET_1900_TO_1970

        // write seconds in big endian format
        buffer[offset++] = (seconds shr 24).toByte()
        buffer[offset++] = (seconds shr 16).toByte()
        buffer[offset++] = (seconds shr 8).toByte()
        buffer[offset++] = (seconds shr 0).toByte()
        val fraction = milliseconds * 0x100000000L / 1000L
        // write fraction in big endian format
        buffer[offset++] = (fraction shr 24).toByte()
        buffer[offset++] = (fraction shr 16).toByte()
        buffer[offset++] = (fraction shr 8).toByte()
        // low order bits should be random data
        buffer[offset] = lowOrderByte.toByte()
    }
}
