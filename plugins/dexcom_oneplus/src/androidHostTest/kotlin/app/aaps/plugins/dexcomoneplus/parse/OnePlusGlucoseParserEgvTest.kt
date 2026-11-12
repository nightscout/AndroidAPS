package app.aaps.plugins.dexcomoneplus.parse

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OnePlusGlucoseParserEgvTest {

    @Test
    fun `parseEgv1 accepts CRC packet with Ok state`() {
        // opcode, status, sequence(4), timestamp(4), glucose(2), state, trend, predicted(2), crc(2)
        val buf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x4f.toByte())
        buf.put(0x00)
        buf.putInt(1)
        buf.putInt(100)
        buf.putShort(120) // glucose 120, not display-only
        buf.put(0x06) // Ok
        buf.put(0) // trend
        buf.putShort(0)
        val raw = buf.array()
        val crc = OnePlusFastCrc16.calculate(raw, raw.size - 2)
        raw[raw.size - 2] = crc[0]
        raw[raw.size - 1] = crc[1]

        val parsed = OnePlusGlucoseParser.parseControlPacket(raw)
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.calibration).isEqualTo(OnePlusCalibrationState.Ok)
        assertThat(parsed.usable).isTrue()
        assertThat(parsed.sample!!.mgdl).isEqualTo(120.0)
    }

    @Test
    fun `parseEgv2 warming up yields no sample but WarmingUp`() {
        val buf = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x4e.toByte())
        buf.put(0x00) // status
        buf.putInt(600) // session age seconds (~10 min)
        buf.putShort(1) // sequence
        buf.putShort(0) // bogus
        buf.putShort(60) // age
        buf.putShort(100) // glucose while warming — not usable
        buf.put(0x02) // WarmingUp
        buf.put(0) // trend
        buf.putShort(0) // predicted
        buf.put(0) // info — need 19 bytes: 1+1+4+2+2+2+2+1+1+2+1 = 19

        val parsed = OnePlusGlucoseParser.parseControlPacket(buf.array())
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.calibration).isEqualTo(OnePlusCalibrationState.WarmingUp)
        assertThat(parsed.usable).isFalse()
        assertThat(parsed.sample).isNull()
    }

    @Test
    fun `egv tx short and crc lengths`() {
        assertThat(OnePlusEGlucoseTx.requestShort()).isEqualTo(byteArrayOf(0x4e))
        val withCrc = OnePlusEGlucoseTx.requestWithCrc()
        assertThat(withCrc).hasLength(3)
        assertThat(withCrc[0]).isEqualTo(0x4e.toByte())
        assertThat(OnePlusFastCrc16.check(withCrc)).isTrue()
    }
}
