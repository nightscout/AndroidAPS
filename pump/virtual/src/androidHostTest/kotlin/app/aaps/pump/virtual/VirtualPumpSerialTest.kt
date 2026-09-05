package app.aaps.pump.virtual

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * The two things the generated serial has to get right: a user can type it, and it does not repeat.
 *
 * Neither is visible from a call site. A serial with an `O` in it still works everywhere in the app and
 * only fails when somebody reads it out and the other person types a zero.
 */
internal class VirtualPumpSerialTest {

    @Test
    fun `a serial is ten characters`() {
        assertThat(generateVirtualPumpSerial()).hasLength(10)
    }

    @Test
    fun `a serial contains nothing that can be misread`() {
        // The point of Crockford's alphabet. I and L look like 1, O looks like 0, U is dropped so that
        // no word forms by accident. Lower case is excluded too - the code is quoted in upper case.
        val forbidden = "ILOU".toSet()
        repeat(2_000) {
            val serial = generateVirtualPumpSerial()
            assertThat(serial.filter { it in forbidden }).isEmpty()
            assertThat(serial.all { it in '0'..'9' || it in 'A'..'Z' }).isTrue()
        }
    }

    @Test
    fun `two serials differ`() {
        // 32^10 codes, so a repeat here would mean the draw is broken rather than unlucky.
        assertThat(generateVirtualPumpSerial()).isNotEqualTo(generateVirtualPumpSerial())
    }

    @Test
    fun `the whole alphabet is reachable`() {
        // A generator that could never emit some symbols would still pass every test above while
        // quietly having a smaller keyspace than the uniqueness argument assumes.
        val seen = buildSet { repeat(5_000) { addAll(generateVirtualPumpSerial().toSet()) } }
        assertThat(seen).hasSize(32)
    }

    @Test
    fun `the draw is taken from the supplied random`() {
        // Same seed, same code: this is what makes the generator testable, and it is the only reason
        // the parameter exists.
        assertThat(generateVirtualPumpSerial(Random(42))).isEqualTo(generateVirtualPumpSerial(Random(42)))
    }
}
