package app.aaps.plugins.dexcomoneplus.parse

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * What a G7 / Dexcom ONE+ answers to a calibration: the opcode 0x34 again, then a status where 1
 * means the value was taken. Read as in AAPS PR 5397 (`G7CalibrateRxMessage`).
 */
class OnePlusCalibrateEchoTest {

    private fun bytes(vararg values: Int): ByteArray = values.map { it.toByte() }.toByteArray()

    @Test
    fun `the ONE+ echo is decoded instead of being dropped`() {
        val parsed = OnePlusCalibrateRx.parse(bytes(0x34, 0x00, 0x01, 0x00))!!

        assertThat(parsed.valid).isTrue()
        assertThat(parsed.form).isEqualTo(OnePlusCalibrateRx.Form.Echo)
        assertThat(parsed.rawHex).isEqualTo("34000100")
    }

    @Test
    fun `echo status 1 is an acceptance`() {
        val parsed = OnePlusCalibrateRx.parse(bytes(0x34, 0x00, 0x01, 0x00))!!

        assertThat(parsed.accepted()).isTrue()
        assertThat(parsed.outcome()).isEqualTo(OnePlusCalibrateRx.Outcome.ACCEPTED)
    }

    @Test
    fun `any other echo status is a refusal`() {
        for (status in listOf(bytes(0x34, 0x00, 0x00, 0x00), bytes(0x34, 0x00, 0x02, 0x00), bytes(0x34, 0x00, 0x01, 0x01))) {
            val parsed = OnePlusCalibrateRx.parse(status)!!

            assertThat(parsed.accepted()).isFalse()
            assertThat(parsed.wantsSecondCalibration()).isFalse()
            assertThat(parsed.outcome()).isEqualTo(OnePlusCalibrateRx.Outcome.REFUSED)
        }
    }

    @Test
    fun `a longer echo is still read`() {
        assertThat(OnePlusCalibrateRx.parse(bytes(0x34, 0x00, 0x01, 0x00, 0x00))!!.accepted()).isTrue()
    }

    @Test
    fun `an echo carries its raw bytes into the message, so a field report has them`() {
        val parsed = OnePlusCalibrateRx.parse(bytes(0x34, 0x00, 0x02, 0x00))!!

        assertThat(parsed.message()).contains("34000200")
    }

    @Test
    fun `the G5 style answer still decodes and still says yes or no`() {
        val ok = ByteArray(5).also {
            it[0] = OnePlusCalibrateRx.OPCODE
            it[1] = 0x00
            it[2] = OnePlusCalibrateRx.OK.toByte()
            val crc = OnePlusFastCrc16.calculate(it, 3)
            it[3] = crc[0]
            it[4] = crc[1]
        }
        val parsed = OnePlusCalibrateRx.parse(ok)!!

        assertThat(parsed.form).isEqualTo(OnePlusCalibrateRx.Form.Status)
        assertThat(parsed.accepted()).isTrue()
        assertThat(parsed.outcome()).isEqualTo(OnePlusCalibrateRx.Outcome.ACCEPTED)
    }

    @Test
    fun `a refusal stays a refusal and not an unknown`() {
        val refused = ByteArray(5).also {
            it[0] = OnePlusCalibrateRx.OPCODE
            it[1] = 0x00
            it[2] = OnePlusCalibrateRx.NOT_READY.toByte()
            val crc = OnePlusFastCrc16.calculate(it, 3)
            it[3] = crc[0]
            it[4] = crc[1]
        }
        val parsed = OnePlusCalibrateRx.parse(refused)!!

        assertThat(parsed.outcome()).isEqualTo(OnePlusCalibrateRx.Outcome.REFUSED)
        assertThat(parsed.message()).isEqualTo("Not ready to calibrate")
    }

    @Test
    fun `a packet that is neither shape is still refused`() {
        assertThat(OnePlusCalibrateRx.parse(bytes(0x4e, 0x00, 0x01, 0x00))).isNull()
        assertThat(OnePlusCalibrateRx.parse(bytes(0x35, 0x00, 0x00, 0xff, 0xff))).isNull()
        assertThat(OnePlusCalibrateRx.parse(bytes(0x34, 0x00, 0x01))).isNull()
    }
}
