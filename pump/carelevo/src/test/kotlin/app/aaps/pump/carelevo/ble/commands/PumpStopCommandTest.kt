package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class PumpStopCommandTest {

    @Test
    fun `encode splits duration into hours and minutes`() {
        // 125 min = 2 h 5 min
        assertThat(PumpStopCommand(durationMinutes = 125, subId = 1).encode().toList())
            .containsExactly(0x26.toByte(), 2.toByte(), 5.toByte(), 1.toByte()).inOrder()
    }

    @Test
    fun `encode zero duration subId zero`() {
        assertThat(PumpStopCommand(durationMinutes = 0, subId = 0).encode().toList())
            .containsExactly(0x26.toByte(), 0.toByte(), 0.toByte(), 0.toByte()).inOrder()
    }

    @Test
    fun `encode max duration 359 minutes is 5h 59m`() {
        assertThat(PumpStopCommand(durationMinutes = 359, subId = 1).encode().toList())
            .containsExactly(0x26.toByte(), 5.toByte(), 59.toByte(), 1.toByte()).inOrder()
    }

    @Test
    fun `decode returns result code`() {
        assertThat(PumpStopCommand(durationMinutes = 30, subId = 0).decode(byteArrayOf(0x86.toByte(), 0x00)).resultCode)
            .isEqualTo(0)
    }

    @Test
    fun `decode returns non-zero result code`() {
        assertThat(PumpStopCommand(durationMinutes = 30, subId = 0).decode(byteArrayOf(0x86.toByte(), 0x05)).resultCode)
            .isEqualTo(5)
    }

    @Test
    fun `duration above range throws`() {
        // 360 min = 6 h → hours out of 0..5
        assertFailsWith<IllegalArgumentException> { PumpStopCommand(durationMinutes = 360, subId = 0) }
    }

    @Test
    fun `negative duration throws`() {
        assertFailsWith<IllegalArgumentException> { PumpStopCommand(durationMinutes = -1, subId = 0) }
    }

    @Test
    fun `subId out of range throws`() {
        assertFailsWith<IllegalArgumentException> { PumpStopCommand(durationMinutes = 30, subId = 2) }
    }

    @Test
    fun `decode wrong opcode throws`() {
        assertFailsWith<IllegalArgumentException> {
            PumpStopCommand(durationMinutes = 30, subId = 0).decode(byteArrayOf(0x87.toByte(), 0x00))
        }
    }

    @Test
    fun `decode too short throws`() {
        assertFailsWith<IllegalArgumentException> {
            PumpStopCommand(durationMinutes = 30, subId = 0).decode(byteArrayOf(0x86.toByte()))
        }
    }
}
