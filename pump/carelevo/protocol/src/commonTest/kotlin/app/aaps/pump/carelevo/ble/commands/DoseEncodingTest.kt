package app.aaps.pump.carelevo.ble.commands

import kotlin.math.nextDown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * How a dose in units becomes the two bytes `[whole units, hundredths]` the patch gets. The rule is HALF_UP to two
 * decimals of the number as written, not of its binary value: 1.005 is stored as 1.00499999... and must still become
 * 1.01.
 *
 * In common code, so it runs on iOS too: a dose must become the same bytes on every platform. The values are the ones a
 * plain `round(value * 100)` gets wrong, or that come from floating point sums. `DoseEncodingEquivalenceTest` checks
 * millions more against the `BigDecimal` rounding the driver used before.
 */
class DoseEncodingTest {

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
    fun aDoseBecomesWholeUnitsAndHundredthsRoundedHalfUpAsWritten() {
        for ((dose, bytes) in expected) {
            val encoded = encodeUnitCenti(dose)
            assertEquals(bytes, encoded[0].toUByte().toInt() to encoded[1].toUByte().toInt(), "$dose")
        }
    }

    @Test
    fun anImmediateBolusRoundsTheSameWay() {
        for ((dose, bytes) in expected.filter { it.first > 0.0 }) {
            val encoded = ImmediateBolusCommand(actionId = 1, volume = dose).encode()
            assertEquals(bytes, encoded[2].toUByte().toInt() to encoded[3].toUByte().toInt(), "$dose")
        }
    }

    /** The rate the patch really runs, shown to the user and sent to Nightscout (#2454). */
    @Test
    fun theSentBasalRateIsTheRoundedRate() {
        assertEquals(1.01, BasalProgramCommand.sentSpeed(1.005))
        assertEquals(2.68, BasalProgramCommand.sentSpeed(2.675))
        assertEquals(0.3, BasalProgramCommand.sentSpeed(0.1 + 0.2))
        assertEquals(0.6, BasalProgramCommand.sentSpeed(0.6))
        assertEquals(100.0, BasalProgramCommand.sentSpeed(99.995))
    }

    /** A negative or not finite amount is no dose; before, a negative one went to the patch as wrong bytes. */
    @Test
    fun aNegativeOrNotFiniteAmountIsRefused() {
        assertFailsWith<IllegalArgumentException> { encodeUnitCenti(-0.01) }
        assertFailsWith<IllegalArgumentException> { encodeUnitCenti(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { encodeUnitCenti(Double.POSITIVE_INFINITY) }
    }

    @Test
    fun aWrongAnswerNamesTheOpcodesInHex() {
        val wrong = assertFailsWith<IllegalArgumentException> { InfusionInfoCommand().decode(byteArrayOf(0x05)) }
        assertEquals("expected opcode 0x91, got 0x05", wrong.message)

        val empty = assertFailsWith<IllegalArgumentException> { InfusionInfoCommand().decode(byteArrayOf()) }
        assertEquals("expected opcode 0x91, got empty", empty.message)
    }

    @Test
    fun aWrongOrShortAlarmSnapshotIsRefusedWithItsReason() {
        val command = ActiveAlarmSnapshotCommand(ActiveAlarmSnapshotTier.CRITICAL)

        assertEquals("expected opcode 0xA4, got 0xA5", assertFailsWith<IllegalArgumentException> { command.decode(byteArrayOf(0xA5.toByte())) }.message)
        assertEquals("expected opcode 0xA4, got empty", assertFailsWith<IllegalArgumentException> { command.decode(byteArrayOf()) }.message)
        assertEquals("response too short: 2 < 15", assertFailsWith<IllegalArgumentException> { command.decode(byteArrayOf(0xA4.toByte(), 0x01)) }.message)
    }

    @Test
    fun aByteIsTwoUpperCaseHexDigits() {
        assertEquals("00", hex(0))
        assertEquals("05", hex(5))
        assertEquals("91", hex(0x91.toByte()))
        assertEquals("FF", hex(-1))
    }
}
