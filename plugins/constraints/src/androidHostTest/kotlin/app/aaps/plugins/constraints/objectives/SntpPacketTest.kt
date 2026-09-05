package app.aaps.plugins.constraints.objectives

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Golden vectors for the SNTP wire format.
 *
 * These pin the bytes and the arithmetic, not the transport, so they must keep passing unchanged if
 * the socket underneath is ever replaced. That is the point of them: this maths decides whether an
 * objective's minimum duration has really elapsed, and a rewrite that is a few seconds out would not
 * look wrong anywhere.
 *
 * The chosen fractions are exact in both directions - 0 and 0x80000000 (half a second) - so every
 * expected value below is computed by hand rather than by running the code being tested.
 */
class SntpPacketTest {

    /** 2024-01-01T00:00:00Z. NTP counts from 1900, so this is unix + 2 208 988 800. */
    private val unixMillis = 1_704_067_200_000L
    private val ntpSeconds = 3_913_056_000L

    private fun ByteArray.putTimeStamp(offset: Int, seconds: Long, fraction: Long) {
        this[offset] = (seconds shr 24).toByte()
        this[offset + 1] = (seconds shr 16).toByte()
        this[offset + 2] = (seconds shr 8).toByte()
        this[offset + 3] = seconds.toByte()
        this[offset + 4] = (fraction shr 24).toByte()
        this[offset + 5] = (fraction shr 16).toByte()
        this[offset + 6] = (fraction shr 8).toByte()
        this[offset + 7] = fraction.toByte()
    }

    @Test
    fun read32_treatsTheBytesAsUnsigned() {
        val buffer = ByteArray(8)
        buffer[0] = 0xFF.toByte(); buffer[1] = 0xFF.toByte(); buffer[2] = 0xFF.toByte(); buffer[3] = 0xFF.toByte()
        // The whole point of the hand written sign conversion: a naive Byte.toInt() would give -1.
        assertThat(SntpPacket.read32(buffer, 0)).isEqualTo(4_294_967_295L)
    }

    @Test
    fun read32_readsBigEndian() {
        val buffer = byteArrayOf(0x00, 0x00, 0x01, 0x00)
        assertThat(SntpPacket.read32(buffer, 0)).isEqualTo(256L)
    }

    @Test
    fun readTimeStamp_convertsThe1900EpochToUnixMillis() {
        val buffer = ByteArray(48)
        buffer.putTimeStamp(40, ntpSeconds, 0L)
        assertThat(SntpPacket.readTimeStamp(buffer, 40)).isEqualTo(unixMillis)
    }

    @Test
    fun readTimeStamp_addsHalfASecondForTheTopFractionBit() {
        val buffer = ByteArray(48)
        buffer.putTimeStamp(40, ntpSeconds, 0x8000_0000L)
        assertThat(SntpPacket.readTimeStamp(buffer, 40)).isEqualTo(unixMillis + 500)
    }

    @Test
    fun buildRequest_setsClientModeAndVersionThree() {
        val request = SntpPacket.buildRequest(unixMillis, lowOrderByte = 0)
        assertThat(request).hasLength(48)
        // mode 3 in the low three bits, version 3 in bits 3-5
        assertThat(request[0]).isEqualTo(0x1B.toByte())
    }

    @Test
    fun buildRequest_writesTheTransmitTimestampByteForByte() {
        val request = SntpPacket.buildRequest(unixMillis + 500, lowOrderByte = 0x2A)
        // 3 913 056 000 == 0xE93C7F00, and 500 ms == 0x80000000 of a second
        assertThat(request.copyOfRange(40, 48)).isEqualTo(
            byteArrayOf(
                0xE9.toByte(), 0x3C, 0x7F, 0x00,
                0x80.toByte(), 0x00, 0x00, 0x2A
            )
        )
    }

    @Test
    fun buildRequest_putsTheCallersRandomByteLast() {
        // The protocol wants random low order bits; only the last byte may differ.
        val a = SntpPacket.buildRequest(unixMillis, lowOrderByte = 0x00)
        val b = SntpPacket.buildRequest(unixMillis, lowOrderByte = 0xFF)
        assertThat(a.copyOfRange(0, 47)).isEqualTo(b.copyOfRange(0, 47))
        assertThat(a[47]).isEqualTo(0x00.toByte())
        assertThat(b[47]).isEqualTo(0xFF.toByte())
    }

    @Test
    fun writeThenRead_roundTripsAnExactHalfSecond() {
        val buffer = ByteArray(48)
        SntpPacket.writeTimeStamp(buffer, 40, unixMillis + 500, lowOrderByte = 0)
        assertThat(SntpPacket.readTimeStamp(buffer, 40)).isEqualTo(unixMillis + 500)
    }

    @Test
    fun writeThenRead_losesAMillisecondOnFractionsThatAreNotExact() {
        // Documents real precision loss rather than hiding it: 40 ms becomes 39 ms, because the
        // fraction is truncated on the way in and again on the way out. Anything relying on
        // millisecond exactness through this path would be wrong.
        val buffer = ByteArray(48)
        SntpPacket.writeTimeStamp(buffer, 40, unixMillis + 40, lowOrderByte = 0)
        assertThat(SntpPacket.readTimeStamp(buffer, 40)).isEqualTo(unixMillis + 39)
    }

    @Test
    fun parseResponse_correctsTheClockUsingBothHalvesOfTheRoundTrip() {
        // originate = what we sent, receive = server got it, transmit = server replied.
        val buffer = ByteArray(48)
        buffer.putTimeStamp(24, ntpSeconds, 0L)               // originate  T0 = 0 ms
        buffer.putTimeStamp(32, ntpSeconds, 0x8000_0000L)     // receive    T1 = +500 ms
        buffer.putTimeStamp(40, ntpSeconds + 1, 0L)           // transmit   T2 = +1000 ms

        val timing = SntpPacket.parseResponse(
            buffer = buffer,
            requestTime = unixMillis,
            requestTicks = 5_000L,
            responseTicks = 5_800L
        )

        // responseTime = 1 704 067 200 800
        // roundTrip    = 800 - (1000 - 500)                 = 300
        // clockOffset  = (500 - 0 + (1000 - 800)) / 2       = 350
        assertThat(timing.roundTripTime).isEqualTo(300L)
        assertThat(timing.ntpTime).isEqualTo(unixMillis + 1150)
        assertThat(timing.ntpTimeReference).isEqualTo(5_800L)
    }

    @Test
    fun parseResponse_reportsAnEarlyServerClockAsANegativeCorrection() {
        val buffer = ByteArray(48)
        buffer.putTimeStamp(24, ntpSeconds, 0L)               // originate T0 = 0 ms
        buffer.putTimeStamp(32, ntpSeconds - 10, 0L)          // receive   10 s behind us
        buffer.putTimeStamp(40, ntpSeconds - 10, 0L)          // transmit  same

        val timing = SntpPacket.parseResponse(
            buffer = buffer,
            requestTime = unixMillis,
            requestTicks = 0L,
            responseTicks = 0L
        )

        // clockOffset = (-10 000 - 0 + (-10 000 - 0)) / 2 = -10 000
        assertThat(timing.ntpTime).isEqualTo(unixMillis - 10_000)
    }
}
