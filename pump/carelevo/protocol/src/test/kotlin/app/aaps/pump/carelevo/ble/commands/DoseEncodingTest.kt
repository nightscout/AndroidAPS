package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import kotlin.math.nextDown
import kotlin.test.assertFailsWith

/**
 * How a dose in units becomes the two bytes `[whole units, hundredths]` the patch gets. The rule is HALF_UP to two
 * decimals of the number as written (its shortest decimal form, as `BigDecimal.valueOf` reads it), not of its binary
 * value: 1.005 is stored as 1.00499999... and must still become 1.01.
 *
 * The values here are the ones a plain `round(value * 100)` gets wrong, or that come from floating point sums. They
 * pin what the patch gets before the rounding moves off `BigDecimal` for Kotlin Multiplatform.
 */
internal class DoseEncodingTest {

    private val expected = listOf(
        0.0 to (0 to 0),
        0.004 to (0 to 0),
        0.005 to (0 to 1),
        0.01 to (0 to 1),
        0.125 to (0 to 13),
        0.594 to (0 to 59),
        0.595 to (0 to 60),
        0.6 to (0 to 60),
        0.785 to (0 to 79),
        (0.1 + 0.2) to (0 to 30),
        (0.3 * 3) to (0 to 90),
        // The double just below the one 1.005 is stored as: written "1.0049999999999997", so down.
        1.005.nextDown() to (1 to 0),
        1.005 to (1 to 1),
        1.115 to (1 to 12),
        1.3335 to (1 to 33),
        2.675 to (2 to 68),
        4.35 to (4 to 35),
        15.0 to (15 to 0),
        25.0 to (25 to 0),
        99.995 to (100 to 0),
        255.99 to (255 to 99)
    )

    @Test
    fun `a dose becomes whole units and hundredths rounded half up as written`() {
        for ((dose, bytes) in expected) {
            val encoded = encodeUnitCenti(dose)
            assertWithMessage("$dose").that(encoded[0].toUByte().toInt() to encoded[1].toUByte().toInt()).isEqualTo(bytes)
        }
    }

    @Test
    fun `an immediate bolus rounds the same way`() {
        for ((dose, bytes) in expected.filter { it.first > 0.0 }) {
            val encoded = ImmediateBolusCommand(actionId = 1, volume = dose).encode()
            assertWithMessage("$dose").that(encoded[2].toUByte().toInt() to encoded[3].toUByte().toInt()).isEqualTo(bytes)
        }
    }

    /** The rate the patch really runs, shown to the user and sent to Nightscout (#2454). */
    @Test
    fun `the sent basal rate is the rounded rate`() {
        assertThat(BasalProgramCommand.sentSpeed(1.005)).isEqualTo(1.01)
        assertThat(BasalProgramCommand.sentSpeed(2.675)).isEqualTo(2.68)
        assertThat(BasalProgramCommand.sentSpeed(0.1 + 0.2)).isEqualTo(0.3)
        assertThat(BasalProgramCommand.sentSpeed(0.6)).isEqualTo(0.6)
        assertThat(BasalProgramCommand.sentSpeed(99.995)).isEqualTo(100.0)
    }

    @Test
    fun `a wrong answer names the opcodes in hex`() {
        val wrong = assertFailsWith<IllegalArgumentException> { InfusionInfoCommand().decode(byteArrayOf(0x05)) }
        assertThat(wrong.message).isEqualTo("expected opcode 0x91, got 0x05")

        val empty = assertFailsWith<IllegalArgumentException> { InfusionInfoCommand().decode(byteArrayOf()) }
        assertThat(empty.message).isEqualTo("expected opcode 0x91, got empty")
    }

    @Test
    fun `a wrong or short alarm snapshot is refused with its reason`() {
        val command = ActiveAlarmSnapshotCommand(ActiveAlarmSnapshotTier.CRITICAL)

        val wrong = assertFailsWith<IllegalArgumentException> { command.decode(byteArrayOf(0xA5.toByte())) }
        assertThat(wrong.message).isEqualTo("expected opcode 0xA4, got 0xA5")
        val empty = assertFailsWith<IllegalArgumentException> { command.decode(byteArrayOf()) }
        assertThat(empty.message).isEqualTo("expected opcode 0xA4, got empty")
        val short = assertFailsWith<IllegalArgumentException> { command.decode(byteArrayOf(0xA4.toByte(), 0x01)) }
        assertThat(short.message).isEqualTo("response too short: 2 < 15")
    }
}
