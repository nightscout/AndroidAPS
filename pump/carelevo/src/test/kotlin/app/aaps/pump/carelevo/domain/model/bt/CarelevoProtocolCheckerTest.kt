package app.aaps.pump.carelevo.domain.model.bt

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Exhaustive coverage of the three opcode classifiers in CarelevoProtocolChecker.kt.
 *
 * Each classifier is a `when` over a fixed set of command opcodes. The tests below hit every
 * labeled branch (both the `true` and the `false` arms) plus the `else` fallback, and assert the
 * three classifiers are mutually exclusive.
 */
internal class CarelevoProtocolCheckerTest {

    // ---- Expected TRUE opcode sets, transcribed from the source `when` bodies -------------------

    private val patchTrue = setOf(
        0x11, 0x71, 0x12, 0x72,
        0x15, 0x75, 0x16, 0x76, 0x17, 0x77, 0x18, 0x78, 0x19, 0x79, 0x1A, 0x7A, 0x1B, 0x7B, 0x1C, 0x7C,
        0x26, 0x86, 0x27, 0x87,
        0x2A,
        0x31, 0x91, 0x33, 0x93, 0x94, 0x35, 0x95, 0x36, 0x96, 0x37, 0x97, 0x38, 0x98, 0x39, 0x99, 0x3A, 0x9A,
        0x3D, 0x9D, 0x9E, 0x3B, 0x9B, 0x3F, 0x9F,
        0x4D, 0xA1, 0xA2, 0xA3, 0x47, 0xA7,
        0x4A, 0xBA, 0x4B,
        0x1D, 0x7D,
        0x48, 0xA8,
        0xBB
    )

    private val basalTrue = setOf(
        0x13, 0x73, 0x14, 0x74,
        0x21, 0x81, 0x22, 0x82, 0x23, 0x83,
        0x88,
        0x2B, 0x8B,
        0x2D, 0x8D
    )

    private val bolusTrue = setOf(
        0x24, 0x84, 0x25, 0x85,
        0x29, 0x89,
        0x2C, 0x8C
    )

    /**
     * Every opcode that appears as a `when` label in any of the three classifiers. 0x9C is the only
     * labeled opcode that is `false` in all three, so it is added on top of the three TRUE unions.
     */
    private val universe: Set<Int> = patchTrue + basalTrue + bolusTrue + setOf(0x9C)

    /** Opcodes that are not labeled anywhere → must fall through to the `else -> false` arm. */
    private val unknownOpcodes = listOf(
        0x00, 0x01, 0x10, 0x1E, 0x1F, 0x20, 0x28, 0x2E, 0x2F, 0x30, 0x32, 0x34, 0x3C, 0x3E,
        0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x49, 0x4C, 0x4E, 0x4F,
        0x70, 0x80, 0x90, 0xA0, 0xB0, 0xBC, 0xC0, 0xFF, 0x100, 0x1000, -1, -0x11
    )

    // ---- Full-universe set checks (covers every labeled branch in every classifier) -------------

    @Test
    fun `isPatchProtocol returns true for exactly the patch opcode set`() {
        val actualTrue = universe.filter { isPatchProtocol(it) }.toSet()
        assertThat(actualTrue).isEqualTo(patchTrue)
    }

    @Test
    fun `isBasalProtocol returns true for exactly the basal opcode set`() {
        val actualTrue = universe.filter { isBasalProtocol(it) }.toSet()
        assertThat(actualTrue).isEqualTo(basalTrue)
    }

    @Test
    fun `isBolusProtocol returns true for exactly the bolus opcode set`() {
        val actualTrue = universe.filter { isBolusProtocol(it) }.toSet()
        assertThat(actualTrue).isEqualTo(bolusTrue)
    }

    @Test
    fun `every labeled opcode is classified false by the two classifiers that do not own it`() {
        universe.forEach { op ->
            val hits = listOf(isPatchProtocol(op), isBasalProtocol(op), isBolusProtocol(op)).count { it }
            // 0x9C is false in all three; every other labeled opcode is true in exactly one.
            val expectedHits = if (op == 0x9C) 0 else 1
            assertThat(hits).isEqualTo(expectedHits)
        }
    }

    @Test
    fun `the three true sets are mutually exclusive`() {
        assertThat(patchTrue.intersect(basalTrue)).isEmpty()
        assertThat(patchTrue.intersect(bolusTrue)).isEmpty()
        assertThat(basalTrue.intersect(bolusTrue)).isEmpty()
    }

    // ---- else-branch / unknown-opcode coverage --------------------------------------------------

    @Test
    fun `unknown opcodes fall through to false in all three classifiers`() {
        unknownOpcodes.forEach { op ->
            assertThat(isPatchProtocol(op)).isFalse()
            assertThat(isBasalProtocol(op)).isFalse()
            assertThat(isBolusProtocol(op)).isFalse()
        }
    }

    @Test
    fun `negative and out-of-byte-range opcodes are not patch, basal or bolus`() {
        listOf(-1, -0x100, 0x100, 0x1FF, Int.MAX_VALUE, Int.MIN_VALUE).forEach { op ->
            assertThat(isPatchProtocol(op)).isFalse()
            assertThat(isBasalProtocol(op)).isFalse()
            assertThat(isBolusProtocol(op)).isFalse()
        }
    }

    // ---- Expected-count guards (catch accidental additions / deletions of arms) ------------------

    @Test
    fun `patch true set has the expected cardinality`() {
        assertThat(patchTrue).hasSize(63)
    }

    @Test
    fun `basal true set has the expected cardinality`() {
        assertThat(basalTrue).hasSize(15)
    }

    @Test
    fun `bolus true set has the expected cardinality`() {
        assertThat(bolusTrue).hasSize(8)
    }

    // ---- Representative single-opcode checks (documented / notable branches) --------------------

    @Test
    fun `patch write opcodes 0x11 and 0x71 are patch protocol`() {
        assertThat(isPatchProtocol(0x11)).isTrue()
        assertThat(isPatchProtocol(0x71)).isTrue()
        assertThat(isBasalProtocol(0x11)).isFalse()
        assertThat(isBolusProtocol(0x11)).isFalse()
    }

    @Test
    fun `0x2A is patch only`() {
        assertThat(isPatchProtocol(0x2A)).isTrue()
        assertThat(isBasalProtocol(0x2A)).isFalse()
        assertThat(isBolusProtocol(0x2A)).isFalse()
    }

    @Test
    fun `0xBB is a patch-only opcode present in no other classifier`() {
        assertThat(isPatchProtocol(0xBB)).isTrue()
        assertThat(isBasalProtocol(0xBB)).isFalse()
        assertThat(isBolusProtocol(0xBB)).isFalse()
    }

    @Test
    fun `0x9C is explicitly false in all three classifiers`() {
        assertThat(isPatchProtocol(0x9C)).isFalse()
        assertThat(isBasalProtocol(0x9C)).isFalse()
        assertThat(isBolusProtocol(0x9C)).isFalse()
    }

    @Test
    fun `0x4D is patch protocol despite being a false-duplicate label in basal and bolus`() {
        assertThat(isPatchProtocol(0x4D)).isTrue()
        assertThat(isBasalProtocol(0x4D)).isFalse()
        assertThat(isBolusProtocol(0x4D)).isFalse()
    }

    @Test
    fun `basal-rate opcodes 0x13 0x14 and 0x21 are basal protocol`() {
        assertThat(isBasalProtocol(0x13)).isTrue()
        assertThat(isBasalProtocol(0x14)).isTrue()
        assertThat(isBasalProtocol(0x21)).isTrue()
        assertThat(isPatchProtocol(0x13)).isFalse()
        assertThat(isBolusProtocol(0x13)).isFalse()
    }

    @Test
    fun `0x88 is basal only`() {
        assertThat(isBasalProtocol(0x88)).isTrue()
        assertThat(isPatchProtocol(0x88)).isFalse()
        assertThat(isBolusProtocol(0x88)).isFalse()
    }

    @Test
    fun `bolus opcodes 0x24 0x25 and 0x29 are bolus protocol`() {
        assertThat(isBolusProtocol(0x24)).isTrue()
        assertThat(isBolusProtocol(0x25)).isTrue()
        assertThat(isBolusProtocol(0x29)).isTrue()
        assertThat(isPatchProtocol(0x24)).isFalse()
        assertThat(isBasalProtocol(0x24)).isFalse()
    }

    @Test
    fun `bolus opcodes 0x2C and 0x8C are bolus protocol`() {
        assertThat(isBolusProtocol(0x2C)).isTrue()
        assertThat(isBolusProtocol(0x8C)).isTrue()
        assertThat(isPatchProtocol(0x2C)).isFalse()
        assertThat(isBasalProtocol(0x2C)).isFalse()
    }

    @Test
    fun `0x28 is a gap between labeled opcodes and matches nothing`() {
        assertThat(isPatchProtocol(0x28)).isFalse()
        assertThat(isBasalProtocol(0x28)).isFalse()
        assertThat(isBolusProtocol(0x28)).isFalse()
    }

    @Test
    fun `zero opcode matches nothing`() {
        assertThat(isPatchProtocol(0x00)).isFalse()
        assertThat(isBasalProtocol(0x00)).isFalse()
        assertThat(isBolusProtocol(0x00)).isFalse()
    }

    @Test
    fun `high-nibble mirror opcodes 0x94 0x9D and 0x9E are patch protocol`() {
        assertThat(isPatchProtocol(0x94)).isTrue()
        assertThat(isPatchProtocol(0x9D)).isTrue()
        assertThat(isPatchProtocol(0x9E)).isTrue()
    }
}
