package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class NoticeThresholdCommandTest {

    @Test
    fun `encode low-insulin type is 0x15 type value`() {
        assertThat(NoticeThresholdCommand(NoticeThresholdCommand.TYPE_LOW_INSULIN, 30).encode().toList())
            .containsExactly(0x15.toByte(), 0x00.toByte(), 30.toByte()).inOrder()
    }

    @Test
    fun `encode expiry type is 0x15 type value`() {
        assertThat(NoticeThresholdCommand(NoticeThresholdCommand.TYPE_EXPIRY, 100).encode().toList())
            .containsExactly(0x15.toByte(), 0x01.toByte(), 100.toByte()).inOrder()
    }

    @Test
    fun `decode returns echoed type and fabricated result 0`() {
        val r = NoticeThresholdCommand(NoticeThresholdCommand.TYPE_EXPIRY, 100).decode(byteArrayOf(0x75, 0x01))
        assertThat(r.thresholdType).isEqualTo(1)
        assertThat(r.resultCode).isEqualTo(0)
    }

    @Test
    fun `low-insulin value out of range throws`() {
        assertFailsWith<IllegalArgumentException> { NoticeThresholdCommand(NoticeThresholdCommand.TYPE_LOW_INSULIN, 60) }
    }

    @Test
    fun `expiry value out of range throws`() {
        assertFailsWith<IllegalArgumentException> { NoticeThresholdCommand(NoticeThresholdCommand.TYPE_EXPIRY, 200) }
    }

    @Test
    fun `bad type throws`() {
        assertFailsWith<IllegalArgumentException> { NoticeThresholdCommand(2, 30) }
    }

    @Test
    fun `decode wrong opcode throws`() {
        assertFailsWith<IllegalArgumentException> {
            NoticeThresholdCommand(NoticeThresholdCommand.TYPE_EXPIRY, 100).decode(byteArrayOf(0x76, 0x01))
        }
    }
}
