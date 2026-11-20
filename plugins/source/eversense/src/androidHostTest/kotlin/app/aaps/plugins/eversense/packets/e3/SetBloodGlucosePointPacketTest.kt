package app.aaps.plugins.eversense.packets.e3

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.Calendar
import java.util.TimeZone

/**
 * Byte-parity test for the E3 two-timestamp calibration packet against the iOS reference
 * (loopandlearn/EversenseKit commit b79d858).
 *
 * Lets an E3 user confirm the encoding matches iOS BEFORE connecting to a transmitter.
 * It does NOT prove the calibration works on hardware - only that the bytes match the
 * reference implementation. On-device validation is still required.
 */
class SetBloodGlucosePointPacketTest {

    private fun gmtMillis(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        cal.clear()
        cal.set(y, mo - 1, d, h, mi, s)
        return cal.timeInMillis
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    @Test
    fun `getRequestData encodes two-timestamp calibration body matching iOS reference`() {
        val sample = gmtMillis(2026, 6, 25, 14, 30, 0)
        val packet = SetBloodGlucosePointPacket(glucoseInMgDl = 120, sampleTimestamp = sample)
        val body = packet.getRequestData()

        assertEquals(14, body.size, "body must be 14 bytes")
        assertEquals("d934c073", body.copyOfRange(0, 4).hex(), "sample date+time bytes")
        assertEquals("7800", body.copyOfRange(8, 10).hex(), "glucose 120 little-endian")
        assertEquals("00000055", body.copyOfRange(10, 14).hex(), "padding + 0x55 trailer")
    }

    @Test
    fun `glucose is encoded little-endian for values over 255`() {
        val sample = gmtMillis(2026, 6, 25, 14, 30, 0)
        val packet = SetBloodGlucosePointPacket(glucoseInMgDl = 300, sampleTimestamp = sample)
        val body = packet.getRequestData()
        assertEquals("2c01", body.copyOfRange(8, 10).hex(), "glucose 300 little-endian")
    }
}