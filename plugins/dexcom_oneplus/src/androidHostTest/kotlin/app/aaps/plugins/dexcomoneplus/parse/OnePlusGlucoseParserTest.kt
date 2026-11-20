package app.aaps.plugins.dexcomoneplus.parse

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.jupiter.api.Test

/**
 * Synthetic hex fixtures match upstream field layouts from
 * `EGlucoseRxMessage` / `EGlucoseRxMessage2` (pin 1e86d9a2a525…).
 * No captured device traffic claimed.
 */
class OnePlusGlucoseParserTest {

    @Test
    fun `inRange bounds 20 to 600`() {
        assertThat(OnePlusGlucoseParser.inRange(19.9)).isFalse()
        assertThat(OnePlusGlucoseParser.inRange(20.0)).isTrue()
        assertThat(OnePlusGlucoseParser.inRange(600.0)).isTrue()
        assertThat(OnePlusGlucoseParser.inRange(600.1)).isFalse()
    }

    @Test
    fun `toSample drops out of range`() {
        assertThat(OnePlusGlucoseParser.toSample(10.0, timestampMs = 1L)).isNull()
        val ok = OnePlusGlucoseParser.toSample(120.0, timestampMs = 2L, trendSlopeMgdlPerMin = 0.0, sequence = 7L)
        requireNotNull(ok)
        assertThat(ok.mgdl).isEqualTo(120.0)
        assertThat(ok.timestampMs).isEqualTo(2L)
        assertThat(ok.trendSlopeMgdlPerMin).isEqualTo(0.0)
        assertThat(ok.sequence).isEqualTo(7L)
    }

    @Test
    fun `parse EGV2 opcode 4e maps glucose age trend sequence`() {
        // Hex (LE): 4e | status | clock | seq | bogus | age | gluc | state | trend | pred
        // 4E 00 10 27 00 00 2A 00 00 00 1E 00 78 00 06 0C 00 00 00
        val packet = hex(
            "4E00" + // opcode + status
                "10270000" + // session clock 0x2710 = 10000s
                "2A00" + // sequence 42
                "0000" + // bogus
                "1E00" + // age 30s
                "7800" + // glucose 120
                "06" + // Ok
                "0C" + // trend +1.2 mg/dL/min
                "0000" + // predicted
                "00", // info (Message2 trailing; ignored)
        )
        val now = 1_700_000_000_000L
        val parsed = OnePlusGlucoseParser.parseControlPacket(packet, nowMs = now)
        requireNotNull(parsed)
        assertThat(parsed.opcode).isEqualTo(0x4e)
        assertThat(parsed.calibration).isEqualTo(OnePlusCalibrationState.Ok)
        assertThat(parsed.ageSeconds).isEqualTo(30)
        assertThat(parsed.sessionAgeSeconds).isEqualTo(10_000)
        val sample = requireNotNull(parsed.sample)
        assertThat(sample.mgdl).isEqualTo(120.0)
        assertThat(sample.timestampMs).isEqualTo(now - 30_000L)
        assertThat(sample.sequence).isEqualTo(42L)
        assertThat(sample.trendSlopeMgdlPerMin).isEqualTo(1.2)
        assertThat(OnePlusGlucoseParser.parse(packet, now)).isEqualTo(sample)
    }

    @Test
    fun `a display-only EGV2 value never becomes a sample`() {
        val packet = buildEgv2(flags = OnePlusGlucoseParser.DISPLAY_ONLY_FLAG)
        val parsed = OnePlusGlucoseParser.parseControlPacket(packet, nowMs = 1000L)
        requireNotNull(parsed)
        assertThat(parsed.glucoseIsDisplayOnly).isTrue()
        assertThat(parsed.flags).isEqualTo(0x10)
        assertThat(parsed.sample).isNull()
        assertThat(parsed.usable).isFalse()
        // The state is still read, so the session still counts as alive.
        assertThat(parsed.calibration).isEqualTo(OnePlusCalibrationState.Ok)
    }

    @Test
    fun `the top bits of the glucose field are not the display-only flag on a G7`() {
        // xDrip's G6 reading of the flag. On a G7 these bits are 0 and the flag is byte 18.
        val packet = buildEgv2(glucoseMasked = 0x1078)
        val parsed = requireNotNull(OnePlusGlucoseParser.parseControlPacket(packet, nowMs = 1000L))
        assertThat(parsed.glucoseIsDisplayOnly).isFalse()
        assertThat(parsed.sample?.mgdl).isEqualTo(120.0)
    }

    @Test
    fun `an invalid trend gives a sample without a slope`() {
        val packet = buildEgv2(trend = OnePlusGlucoseParser.TREND_INVALID)
        val parsed = requireNotNull(OnePlusGlucoseParser.parseControlPacket(packet, nowMs = 1000L))
        assertThat(parsed.sample?.trendSlopeMgdlPerMin).isNull()
    }

    @Test
    fun `parse EGV2 warming up yields no sample`() {
        val packet = buildEgv2(state = OnePlusCalibrationState.WarmingUp.value)
        val parsed = OnePlusGlucoseParser.parseControlPacket(packet, nowMs = 1000L)
        requireNotNull(parsed)
        assertThat(parsed.calibration).isEqualTo(OnePlusCalibrationState.WarmingUp)
        assertThat(parsed.sample).isNull()
        assertThat(parsed.usable).isFalse()
    }

    @Test
    fun `parse EGV2 rejects glucose outside 20-600`() {
        val low = buildEgv2(glucoseMasked = 15)
        assertThat(OnePlusGlucoseParser.parse(low, nowMs = 1000L)).isNull()
        val high = buildEgv2(glucoseMasked = 601)
        assertThat(OnePlusGlucoseParser.parse(high, nowMs = 1000L)).isNull()
    }

    @Test
    fun `parse EGV1 opcode 4f with CRC`() {
        // 14 payload bytes through trend + 2 CRC (predicted omitted; remaining unused).
        val body = hex(
            "4F" + // opcode
                "00" + // status
                "07000000" + // sequence 7
                "00000000" + // tx timestamp
                "6400" + // glucose 100
                "06" + // Ok
                "00", // trend flat
        )
        val packet = appendCrc(body)
        assertThat(packet.size).isEqualTo(16)
        assertThat(OnePlusFastCrc16.check(packet)).isTrue()

        val now = 5_000L
        val parsed = OnePlusGlucoseParser.parseControlPacket(packet, nowMs = now)
        requireNotNull(parsed)
        assertThat(parsed.opcode).isEqualTo(0x4f)
        val sample = requireNotNull(parsed.sample)
        assertThat(sample.mgdl).isEqualTo(100.0)
        assertThat(sample.timestampMs).isEqualTo(now)
        assertThat(sample.sequence).isEqualTo(7L)
        assertThat(sample.trendSlopeMgdlPerMin).isEqualTo(0.0)
    }

    @Test
    fun `parse EGV1 rejects bad CRC`() {
        val body = hex("4F00070000000000000064000600")
        val packet = appendCrc(body)
        packet[packet.lastIndex] = (packet[packet.lastIndex].toInt() xor 0xff).toByte()
        assertThat(OnePlusGlucoseParser.parseControlPacket(packet, nowMs = 1L)).isNull()
    }

    @Test
    fun `parse rejects unknown opcode and short packets`() {
        assertThat(OnePlusGlucoseParser.parseControlPacket(byteArrayOf(0x01), nowMs = 1L)).isNull()
        assertThat(OnePlusGlucoseParser.parseControlPacket(ByteArray(10) { 0x4e }, nowMs = 1L)).isNull()
    }

    @Test
    fun `negative trend rate`() {
        val packet = buildEgv2(trend = -5) // -0.5 mg/dL/min
        val sample = OnePlusGlucoseParser.parse(packet, nowMs = 10_000L)
        requireNotNull(sample)
        assertThat(sample.trendSlopeMgdlPerMin).isEqualTo(-0.5)
    }

    private fun buildEgv2(
        glucoseMasked: Int = 120,
        sequence: Int = 1,
        age: Int = 5,
        state: Int = OnePlusCalibrationState.Ok.value,
        trend: Int = 0,
        sessionAge: Int = 1000,
        status: Int = 0,
        flags: Int = 0,
    ): ByteArray {
        val buf = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x4e.toByte())
        buf.put(status.toByte())
        buf.putInt(sessionAge)
        buf.putShort(sequence.toShort())
        buf.putShort(0) // bogus
        buf.putShort(age.toShort())
        buf.putShort(glucoseMasked.toShort())
        buf.put(state.toByte())
        buf.put(trend.toByte())
        buf.putShort(0) // predicted
        buf.put(flags.toByte())
        return buf.array()
    }

    private fun appendCrc(body: ByteArray): ByteArray {
        val crc = OnePlusFastCrc16.calculate(body, body.size)
        return body + crc
    }

    private fun hex(s: String): ByteArray {
        val clean = s.replace(" ", "").replace("-", "")
        require(clean.length % 2 == 0)
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
