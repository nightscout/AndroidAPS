package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class NeedleAckCommandTest {

    @Test
    fun `encode success is 0x19 0x00`() {
        assertThat(NeedleAckCommand(isSuccess = true).encode().toList()).containsExactly(0x19.toByte(), 0x00.toByte()).inOrder()
    }

    @Test
    fun `encode failure is 0x19 0x01`() {
        assertThat(NeedleAckCommand(isSuccess = false).encode().toList()).containsExactly(0x19.toByte(), 0x01.toByte()).inOrder()
    }

    @Test
    fun `decode reads result from 0x7A response`() {
        assertThat(NeedleAckCommand(isSuccess = true).decode(byteArrayOf(0x7A, 0x00)).resultCode).isEqualTo(0)
    }

    @Test
    fun `decode wrong opcode throws`() {
        assertFailsWith<IllegalArgumentException> { NeedleAckCommand(isSuccess = true).decode(byteArrayOf(0x79, 0x00)) }
    }
}
