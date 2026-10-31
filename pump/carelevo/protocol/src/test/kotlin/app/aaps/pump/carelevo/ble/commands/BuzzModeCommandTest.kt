package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class BuzzModeCommandTest {

    @Test
    fun `encode use=true is 0x18 0x01 (double inversion)`() {
        assertThat(BuzzModeCommand(use = true).encode().toList())
            .containsExactly(0x18.toByte(), 0x01.toByte()).inOrder()
    }

    @Test
    fun `encode use=false is 0x18 0x00`() {
        assertThat(BuzzModeCommand(use = false).encode().toList())
            .containsExactly(0x18.toByte(), 0x00.toByte()).inOrder()
    }

    @Test
    fun `decode returns result code`() {
        assertThat(BuzzModeCommand(use = true).decode(byteArrayOf(0x78, 0x00)).resultCode).isEqualTo(0)
    }

    @Test
    fun `decode wrong opcode throws`() {
        assertFailsWith<IllegalArgumentException> { BuzzModeCommand(use = true).decode(byteArrayOf(0x79, 0x00)) }
    }
}
