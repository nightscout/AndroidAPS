package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class PumpResumeCommandTest {

    @Test
    fun `encode mode and subId 4`() {
        assertThat(PumpResumeCommand(mode = 1, subId = 4).encode().toList())
            .containsExactly(0x27.toByte(), 1.toByte(), 0x04.toByte()).inOrder()
    }

    @Test
    fun `encode subId 1`() {
        assertThat(PumpResumeCommand(mode = 4, subId = 1).encode().toList())
            .containsExactly(0x27.toByte(), 4.toByte(), 0x01.toByte()).inOrder()
    }

    @Test
    fun `encode subId 0`() {
        assertThat(PumpResumeCommand(mode = 2, subId = 0).encode().toList())
            .containsExactly(0x27.toByte(), 2.toByte(), 0x00.toByte()).inOrder()
    }

    @Test
    fun `decode full response with subId`() {
        val r = PumpResumeCommand(mode = 1, subId = 4).decode(byteArrayOf(0x87.toByte(), 0x00, 0x03, 0x04))
        assertThat(r.resultCode).isEqualTo(0)
        assertThat(r.mode).isEqualTo(3)
        assertThat(r.subId).isEqualTo(4)
    }

    @Test
    fun `decode short response defaults subId to 0`() {
        val r = PumpResumeCommand(mode = 1, subId = 4).decode(byteArrayOf(0x87.toByte(), 0x00, 0x02))
        assertThat(r.resultCode).isEqualTo(0)
        assertThat(r.mode).isEqualTo(2)
        assertThat(r.subId).isEqualTo(0)
    }

    @Test
    fun `mode below range throws`() {
        assertFailsWith<IllegalArgumentException> { PumpResumeCommand(mode = 0, subId = 4) }
    }

    @Test
    fun `mode above range throws`() {
        assertFailsWith<IllegalArgumentException> { PumpResumeCommand(mode = 5, subId = 4) }
    }

    @Test
    fun `invalid subId throws`() {
        assertFailsWith<IllegalArgumentException> { PumpResumeCommand(mode = 1, subId = 2) }
    }

    @Test
    fun `decode wrong opcode throws`() {
        assertFailsWith<IllegalArgumentException> {
            PumpResumeCommand(mode = 1, subId = 4).decode(byteArrayOf(0x88.toByte(), 0x00, 0x01))
        }
    }

    @Test
    fun `decode too short throws`() {
        assertFailsWith<IllegalArgumentException> {
            PumpResumeCommand(mode = 1, subId = 4).decode(byteArrayOf(0x87.toByte(), 0x00))
        }
    }
}
