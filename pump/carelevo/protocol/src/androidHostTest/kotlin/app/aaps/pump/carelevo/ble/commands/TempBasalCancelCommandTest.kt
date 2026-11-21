package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class TempBasalCancelCommandTest {

    @Test
    fun `encode is single opcode byte 0x2D`() {
        assertThat(TempBasalCancelCommand().encode().toList())
            .containsExactly(0x2D.toByte()).inOrder()
    }

    @Test
    fun `decode returns result code`() {
        assertThat(TempBasalCancelCommand().decode(byteArrayOf(0x8D.toByte(), 0x00)).resultCode).isEqualTo(0)
    }

    @Test
    fun `decode returns non-zero result code`() {
        assertThat(TempBasalCancelCommand().decode(byteArrayOf(0x8D.toByte(), 0x05)).resultCode).isEqualTo(5)
    }

    @Test
    fun `decode wrong opcode throws`() {
        assertFailsWith<IllegalArgumentException> { TempBasalCancelCommand().decode(byteArrayOf(0x8C.toByte(), 0x00)) }
    }

    @Test
    fun `decode too short throws`() {
        assertFailsWith<IllegalArgumentException> { TempBasalCancelCommand().decode(byteArrayOf(0x8D.toByte())) }
    }
}
