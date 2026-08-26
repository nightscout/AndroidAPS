package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class AlertAlarmSetCommandTest {

    @Test
    fun `encode mode=0 is 0x48 0x00`() {
        assertThat(AlertAlarmSetCommand(mode = 0).encode().toList())
            .containsExactly(0x48.toByte(), 0x00.toByte()).inOrder()
    }

    @Test
    fun `encode mode=1 is 0x48 0x01 (raw byte)`() {
        assertThat(AlertAlarmSetCommand(mode = 1).encode().toList())
            .containsExactly(0x48.toByte(), 0x01.toByte()).inOrder()
    }

    @Test
    fun `encode high mode wraps to one byte (no range check)`() {
        // No bounds check: the mode is emitted as a raw mode.toByte(), so values > 0x7F wrap.
        assertThat(AlertAlarmSetCommand(mode = 200).encode().toList())
            .containsExactly(0x48.toByte(), 200.toByte()).inOrder()
    }

    @Test
    fun `decode returns result code`() {
        assertThat(AlertAlarmSetCommand(mode = 0).decode(byteArrayOf(0xA8.toByte(), 0x00)).resultCode).isEqualTo(0)
    }

    @Test
    fun `decode wrong opcode throws`() {
        assertFailsWith<IllegalArgumentException> {
            AlertAlarmSetCommand(mode = 0).decode(byteArrayOf(0x78, 0x00))
        }
    }

    @Test
    fun `decode too short throws`() {
        assertFailsWith<IllegalArgumentException> {
            AlertAlarmSetCommand(mode = 0).decode(byteArrayOf(0xA8.toByte()))
        }
    }
}
