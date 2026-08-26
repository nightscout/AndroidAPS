package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class BolusCancelCommandTest {

    @Test
    fun `encode is just the opcode 0x2C`() {
        assertThat(BolusCancelCommand().encode().toList())
            .containsExactly(0x2C.toByte()).inOrder()
    }

    @Test
    fun `decode returns result and infused amount`() {
        val r = BolusCancelCommand().decode(byteArrayOf(0x8C.toByte(), 0x00, 3.toByte(), 25.toByte()))
        assertThat(r.resultCode).isEqualTo(0)
        assertThat(r.infusedAmount).isEqualTo(3.25)
    }

    @Test
    fun `decode reads unsigned bytes`() {
        val r = BolusCancelCommand().decode(byteArrayOf(0x8C.toByte(), 0xFF.toByte(), 200.toByte(), 99.toByte()))
        assertThat(r.resultCode).isEqualTo(255)
        assertThat(r.infusedAmount).isEqualTo(200.99)
    }

    @Test
    fun `decode wrong opcode throws`() {
        assertFailsWith<IllegalArgumentException> {
            BolusCancelCommand().decode(byteArrayOf(0x8B.toByte(), 0x00, 0x00, 0x00))
        }
    }

    @Test
    fun `decode too short throws`() {
        assertFailsWith<IllegalArgumentException> {
            BolusCancelCommand().decode(byteArrayOf(0x8C.toByte(), 0x00, 0x00))
        }
    }
}
