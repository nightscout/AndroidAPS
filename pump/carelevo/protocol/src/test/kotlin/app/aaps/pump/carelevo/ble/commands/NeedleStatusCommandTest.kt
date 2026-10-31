package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class NeedleStatusCommandTest {

    @Test
    fun `encode is 0x1A 0x00`() {
        assertThat(NeedleStatusCommand().encode().toList()).containsExactly(0x1A.toByte(), 0x00.toByte()).inOrder()
    }

    @Test
    fun `decode reads result from asymmetric 0x79 response`() {
        assertThat(NeedleStatusCommand().decode(byteArrayOf(0x79, 0x00)).resultCode).isEqualTo(0)
    }

    @Test
    fun `decode wrong opcode throws`() {
        assertFailsWith<IllegalArgumentException> { NeedleStatusCommand().decode(byteArrayOf(0x7A, 0x00)) }
    }

    @Test
    fun `decode too short throws`() {
        assertFailsWith<IllegalArgumentException> { NeedleStatusCommand().decode(byteArrayOf(0x79)) }
    }
}
