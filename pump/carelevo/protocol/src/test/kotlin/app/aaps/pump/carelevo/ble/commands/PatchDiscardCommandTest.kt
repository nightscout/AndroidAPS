package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class PatchDiscardCommandTest {

    @Test
    fun `encode is just 0x36`() {
        assertThat(PatchDiscardCommand().encode().toList())
            .containsExactly(0x36.toByte()).inOrder()
    }

    @Test
    fun `decode returns result code`() {
        assertThat(PatchDiscardCommand().decode(byteArrayOf(0x96.toByte(), 0x00)).resultCode).isEqualTo(0)
    }

    @Test
    fun `decode non-zero result code`() {
        assertThat(PatchDiscardCommand().decode(byteArrayOf(0x96.toByte(), 0x05)).resultCode).isEqualTo(5)
    }

    @Test
    fun `decode wrong opcode throws`() {
        assertFailsWith<IllegalArgumentException> { PatchDiscardCommand().decode(byteArrayOf(0x95.toByte(), 0x00)) }
    }

    @Test
    fun `decode too short throws`() {
        assertFailsWith<IllegalArgumentException> { PatchDiscardCommand().decode(byteArrayOf(0x96.toByte())) }
    }
}
