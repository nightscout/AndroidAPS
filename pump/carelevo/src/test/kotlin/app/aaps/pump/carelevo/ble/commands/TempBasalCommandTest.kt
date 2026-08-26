package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class TempBasalCommandTest {

    @Test
    fun `byUnit encodes 6-byte frame with trailing 0x00`() {
        assertThat(TempBasalCommand.byUnit(infusionUnit = 1.5, infusionHour = 2, infusionMin = 30).encode().toList())
            .containsExactly(0x23.toByte(), 1.toByte(), 50.toByte(), 2.toByte(), 30.toByte(), 0x00.toByte()).inOrder()
    }

    @Test
    fun `byUnit rounds fractional unit to centi`() {
        assertThat(TempBasalCommand.byUnit(infusionUnit = 0.05, infusionHour = 0, infusionMin = 0).encode().toList())
            .containsExactly(0x23.toByte(), 0.toByte(), 5.toByte(), 0.toByte(), 0.toByte(), 0x00.toByte()).inOrder()
    }

    @Test
    fun `byPercent encodes 5-byte frame with no trailing byte and divides by 100`() {
        // 150 % → 1.50 → [1, 50]
        assertThat(TempBasalCommand.byPercent(infusionPercent = 150, infusionHour = 1, infusionMin = 0).encode().toList())
            .containsExactly(0x23.toByte(), 1.toByte(), 50.toByte(), 1.toByte(), 0.toByte()).inOrder()
    }

    @Test
    fun `byPercent below 100 encodes fractional whole-part zero`() {
        // 90 % → 0.90 → [0, 90]
        assertThat(TempBasalCommand.byPercent(infusionPercent = 90, infusionHour = 5, infusionMin = 45).encode().toList())
            .containsExactly(0x23.toByte(), 0.toByte(), 90.toByte(), 5.toByte(), 45.toByte()).inOrder()
    }

    @Test
    fun `byUnit accepts any unit value - no range check`() {
        // BY_UNIT has no value-range check on the wire, so this must NOT throw.
        assertThat(TempBasalCommand.byUnit(infusionUnit = 99.0, infusionHour = 0, infusionMin = 0).encode().toList())
            .containsExactly(0x23.toByte(), 99.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0x00.toByte()).inOrder()
    }

    @Test
    fun `decode returns result code`() {
        val r = TempBasalCommand.byUnit(infusionUnit = 1.0, infusionHour = 0, infusionMin = 0).decode(byteArrayOf(0x83.toByte(), 0x00))
        assertThat(r.resultCode).isEqualTo(0)
    }

    @Test
    fun `byUnit hour above range throws`() {
        assertFailsWith<IllegalArgumentException> { TempBasalCommand.byUnit(infusionUnit = 1.0, infusionHour = 25, infusionMin = 0) }
    }

    @Test
    fun `byUnit minute above range throws`() {
        assertFailsWith<IllegalArgumentException> { TempBasalCommand.byUnit(infusionUnit = 1.0, infusionHour = 0, infusionMin = 60) }
    }

    @Test
    fun `byPercent value above range throws`() {
        // 20001 / 100.0 = 200.01 > 200.0
        assertFailsWith<IllegalArgumentException> { TempBasalCommand.byPercent(infusionPercent = 20001, infusionHour = 0, infusionMin = 0) }
    }

    @Test
    fun `byPercent negative value below range throws`() {
        // -1 / 100.0 = -0.01 < 0.0
        assertFailsWith<IllegalArgumentException> { TempBasalCommand.byPercent(infusionPercent = -1, infusionHour = 0, infusionMin = 0) }
    }

    @Test
    fun `decode wrong opcode throws`() {
        assertFailsWith<IllegalArgumentException> {
            TempBasalCommand.byUnit(infusionUnit = 1.0, infusionHour = 0, infusionMin = 0).decode(byteArrayOf(0x84.toByte(), 0x00))
        }
    }

    @Test
    fun `decode too short throws`() {
        assertFailsWith<IllegalArgumentException> {
            TempBasalCommand.byUnit(infusionUnit = 1.0, infusionHour = 0, infusionMin = 0).decode(byteArrayOf(0x83.toByte()))
        }
    }
}
