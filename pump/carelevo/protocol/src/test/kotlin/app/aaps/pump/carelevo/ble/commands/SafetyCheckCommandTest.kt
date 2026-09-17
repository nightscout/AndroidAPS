package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class SafetyCheckCommandTest {

    @Test
    fun `encode is a bare 0x12 request`() {
        assertThat(SafetyCheckCommand().encode().toList()).containsExactly(0x12.toByte())
    }

    @Test
    fun `decode progress frame uses fallback duration and is not terminal`() {
        val cmd = SafetyCheckCommand()
        val r = cmd.decode(byteArrayOf(0x72, SafetyCheckCommand.REP_REQUEST.toByte(), 2, 50)) // size 4 → fallback 210
        assertThat(r.resultCode).isEqualTo(SafetyCheckCommand.REP_REQUEST)
        assertThat(r.insulinVolume).isEqualTo(250) // 2*100 + 50
        assertThat(r.durationSeconds).isEqualTo(210)
        assertThat(cmd.isTerminal(r)).isFalse()
    }

    @Test
    fun `decode full success frame reads duration and is terminal`() {
        val cmd = SafetyCheckCommand()
        val r = cmd.decode(byteArrayOf(0x72, 0x00, 2, 50, 3, 30)) // SUCCESS, duration 3*60+30
        assertThat(r.resultCode).isEqualTo(SafetyCheckCommand.RESULT_SUCCESS)
        assertThat(r.insulinVolume).isEqualTo(250)
        assertThat(r.durationSeconds).isEqualTo(210)
        assertThat(cmd.isTerminal(r)).isTrue()
    }

    @Test
    fun `error result codes are terminal`() {
        val cmd = SafetyCheckCommand()
        val expired = cmd.decode(byteArrayOf(0x72, 2, 0, 0)) // EXPIRED
        assertThat(cmd.isTerminal(expired)).isTrue()
    }

    @Test
    fun `both progress codes are non-terminal`() {
        val cmd = SafetyCheckCommand()
        val p1 = cmd.decode(byteArrayOf(0x72, SafetyCheckCommand.REP_REQUEST.toByte(), 0, 0))
        val p2 = cmd.decode(byteArrayOf(0x72, SafetyCheckCommand.REP_REQUEST1.toByte(), 0, 0))
        assertThat(cmd.isTerminal(p1)).isFalse()
        assertThat(cmd.isTerminal(p2)).isFalse()
    }

    @Test
    fun `decode too short throws`() {
        assertFailsWith<IllegalArgumentException> { SafetyCheckCommand().decode(byteArrayOf(0x72, 0x00, 0x00)) }
    }

    @Test
    fun `decode wrong opcode throws`() {
        assertFailsWith<IllegalArgumentException> { SafetyCheckCommand().decode(byteArrayOf(0x73, 0x00, 0x00, 0x00)) }
    }
}
