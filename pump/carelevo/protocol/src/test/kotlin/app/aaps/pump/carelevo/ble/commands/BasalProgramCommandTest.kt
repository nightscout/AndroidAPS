package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class BasalProgramCommandTest {

    @Test
    fun `encode set is 0x13 seqNo then 2-byte segments`() {
        val bytes = BasalProgramCommand(isUpdate = false, seqNo = 0, segmentSpeeds = listOf(1.05, 1.3335)).encode().toList()
        // 1.05 -> [1,5]; 1.3335 HALF_UP -> 1.33 -> [1,33]
        assertThat(bytes).containsExactly(
            0x13.toByte(), 0x00.toByte(), 1.toByte(), 5.toByte(), 1.toByte(), 33.toByte()
        ).inOrder()
    }

    @Test
    fun `encode update uses 0x21 opcode`() {
        val bytes = BasalProgramCommand(isUpdate = true, seqNo = 1, segmentSpeeds = listOf(2.0)).encode().toList()
        assertThat(bytes).containsExactly(0x21.toByte(), 0x01.toByte(), 2.toByte(), 0.toByte()).inOrder()
    }

    @Test
    fun `decode set response 0x73`() {
        assertThat(BasalProgramCommand(false, 0, listOf(1.0)).decode(byteArrayOf(0x73, 0x00)).resultCode).isEqualTo(0)
    }

    @Test
    fun `decode update response 0x81`() {
        assertThat(BasalProgramCommand(true, 0, listOf(1.0)).decode(byteArrayOf(0x81.toByte(), 0x00)).resultCode).isEqualTo(0)
    }

    @Test
    fun `seqNo out of range throws`() {
        assertFailsWith<IllegalArgumentException> { BasalProgramCommand(false, 3, listOf(1.0)) }
    }

    @Test
    fun `empty segments throws`() {
        assertFailsWith<IllegalArgumentException> { BasalProgramCommand(false, 0, emptyList()) }
    }

    @Test
    fun `set command rejects update response opcode`() {
        assertFailsWith<IllegalArgumentException> { BasalProgramCommand(false, 0, listOf(1.0)).decode(byteArrayOf(0x81.toByte(), 0x00)) }
    }
}
