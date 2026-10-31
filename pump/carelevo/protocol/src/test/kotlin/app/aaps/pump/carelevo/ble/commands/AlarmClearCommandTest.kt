package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class AlarmClearCommandTest {

    @Test
    fun `encode alarmType raw and cause`() {
        assertThat(AlarmClearCommand(alarmType = 3, cause = 50).encode().toList())
            .containsExactly(0x47.toByte(), 3.toByte(), 50.toByte()).inOrder()
    }

    @Test
    fun `encode passes alarmType through raw above 0x7F`() {
        assertThat(AlarmClearCommand(alarmType = 0x80, cause = 0).encode().toList())
            .containsExactly(0x47.toByte(), 0x80.toByte(), 0.toByte()).inOrder()
    }

    @Test
    fun `decode maps subId cause result`() {
        val r = AlarmClearCommand(alarmType = 3, cause = 50).decode(byteArrayOf(0xA7.toByte(), 0x01, 0x02, 0x00))
        assertThat(r.subId).isEqualTo(1)
        assertThat(r.cause).isEqualTo(2)
        assertThat(r.resultCode).isEqualTo(0)
    }

    @Test
    fun `cause above range throws`() {
        assertFailsWith<IllegalArgumentException> { AlarmClearCommand(alarmType = 3, cause = 101) }
    }

    @Test
    fun `cause below range throws`() {
        assertFailsWith<IllegalArgumentException> { AlarmClearCommand(alarmType = 3, cause = -1) }
    }

    @Test
    fun `decode wrong opcode throws`() {
        assertFailsWith<IllegalArgumentException> {
            AlarmClearCommand(alarmType = 3, cause = 50).decode(byteArrayOf(0x99.toByte(), 0x01, 0x02, 0x00))
        }
    }

    @Test
    fun `decode too short throws`() {
        assertFailsWith<IllegalArgumentException> {
            AlarmClearCommand(alarmType = 3, cause = 50).decode(byteArrayOf(0xA7.toByte(), 0x01, 0x02))
        }
    }
}
