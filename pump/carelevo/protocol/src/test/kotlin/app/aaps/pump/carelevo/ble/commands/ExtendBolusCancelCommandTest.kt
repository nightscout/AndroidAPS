package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class ExtendBolusCancelCommandTest {

    @Test
    fun `encode is just opcode 0x29`() {
        assertThat(ExtendBolusCancelCommand().encode().toList())
            .containsExactly(0x29.toByte()).inOrder()
    }

    @Test
    fun `decode returns result and infused amount`() {
        val r = ExtendBolusCancelCommand().decode(byteArrayOf(0x89.toByte(), 0x00, 2.toByte(), 50.toByte()))
        assertThat(r.resultCode).isEqualTo(0)
        assertThat(r.infusedAmount).isEqualTo(2.5)
    }

    @Test
    fun `decode reads centi hundredths byte`() {
        val r = ExtendBolusCancelCommand().decode(byteArrayOf(0x89.toByte(), 0x01, 0.toByte(), 5.toByte()))
        assertThat(r.resultCode).isEqualTo(1)
        assertThat(r.infusedAmount).isEqualTo(0.05)
    }

    @Test
    fun `decode reads high unsigned bytes`() {
        val r = ExtendBolusCancelCommand().decode(byteArrayOf(0x89.toByte(), 0x00, 200.toByte(), 99.toByte()))
        assertThat(r.infusedAmount).isEqualTo(200.99)
    }

    @Test
    fun `decode wrong opcode throws`() {
        assertFailsWith<IllegalArgumentException> {
            ExtendBolusCancelCommand().decode(byteArrayOf(0x88.toByte(), 0x00, 0x00, 0x00))
        }
    }

    @Test
    fun `decode too short throws`() {
        assertFailsWith<IllegalArgumentException> {
            ExtendBolusCancelCommand().decode(byteArrayOf(0x89.toByte(), 0x00, 0x00))
        }
    }
}
