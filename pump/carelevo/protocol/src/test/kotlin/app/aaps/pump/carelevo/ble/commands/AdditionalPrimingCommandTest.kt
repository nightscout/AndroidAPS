package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class AdditionalPrimingCommandTest {

    @Test
    fun `encode is single opcode byte 0x1D`() {
        assertThat(AdditionalPrimingCommand().encode().toList())
            .containsExactly(0x1D.toByte()).inOrder()
    }

    @Test
    fun `decode returns result code`() {
        assertThat(AdditionalPrimingCommand().decode(byteArrayOf(0x7D, 0x00)).resultCode).isEqualTo(0)
    }

    @Test
    fun `decode non-zero result code`() {
        assertThat(AdditionalPrimingCommand().decode(byteArrayOf(0x7D, 0x05)).resultCode).isEqualTo(5)
    }

    @Test
    fun `decode wrong opcode throws`() {
        assertFailsWith<IllegalArgumentException> { AdditionalPrimingCommand().decode(byteArrayOf(0x7C, 0x00)) }
    }

    @Test
    fun `decode too short throws`() {
        assertFailsWith<IllegalArgumentException> { AdditionalPrimingCommand().decode(byteArrayOf(0x7D)) }
    }
}
