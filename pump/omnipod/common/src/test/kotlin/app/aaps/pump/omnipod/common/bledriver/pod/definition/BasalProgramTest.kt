package app.aaps.pump.omnipod.common.bledriver.pod.definition

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.Calendar

/**
 * [BasalProgram] - dosing-schedule encode/query logic. [rateAt] reads the local default
 * timezone via `Calendar.getInstance()`, so test timestamps are built the same way (rather
 * than hardcoded epoch millis) to stay timezone-independent - see this codebase's own
 * O5PodStateManagerExtensionsTest doc comment for the same caution about a previous flaky
 * test caused by exactly this pattern.
 */
class BasalProgramTest {

    private fun millisAt(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        return calendar.timeInMillis
    }

    @Test
    fun `rateAt returns the rate of the segment covering that half-hour slot`() {
        val program = BasalProgram(
            listOf(
                BasalProgram.Segment(0, 24, 100),
                BasalProgram.Segment(24, 48, 150)
            )
        )

        assertThat(program.rateAt(millisAt(10, 15))).isEqualTo(1.0)
        assertThat(program.rateAt(millisAt(14, 45))).isEqualTo(1.5)
    }

    @Test
    fun `rateAt treats startSlotIndex as inclusive and endSlotIndex as exclusive`() {
        val program = BasalProgram(listOf(BasalProgram.Segment(20, 22, 200)))

        assertThat(program.rateAt(millisAt(10, 0))).isEqualTo(2.0)
        assertThat(program.rateAt(millisAt(10, 29))).isEqualTo(2.0)
        assertThat(program.rateAt(millisAt(11, 0))).isEqualTo(0.0)
    }

    @Test
    fun `rateAt returns zero when no segment covers the slot`() {
        val program = BasalProgram(listOf(BasalProgram.Segment(0, 10, 100)))

        assertThat(program.rateAt(millisAt(23, 0))).isEqualTo(0.0)
    }

    @Test
    fun `hasZeroUnitSegments is true only when some segment has a zero rate`() {
        val withZero = BasalProgram(listOf(BasalProgram.Segment(0, 24, 0), BasalProgram.Segment(24, 48, 100)))
        val withoutZero = BasalProgram(listOf(BasalProgram.Segment(0, 48, 50)))

        assertThat(withZero.hasZeroUnitSegments()).isTrue()
        assertThat(withoutZero.hasZeroUnitSegments()).isFalse()
    }

    @Test
    fun `addSegment appends to the segment list`() {
        val program = BasalProgram(listOf(BasalProgram.Segment(0, 24, 100)))

        program.addSegment(BasalProgram.Segment(24, 48, 200))

        assertThat(program.segments).hasSize(2)
        assertThat(program.segments[1].basalRateInHundredthUnitsPerHour).isEqualTo(200)
    }

    @Test
    fun `segments is unmodifiable from the outside`() {
        val program = BasalProgram(listOf(BasalProgram.Segment(0, 24, 100)))

        assertThrows(UnsupportedOperationException::class.java) {
            program.segments.add(BasalProgram.Segment(24, 48, 200))
        }
    }

    @Test
    fun `Segment getPulsesPerHour converts hundredth-units-per-hour to pulses using the 0-05U pulse size`() {
        val segment = BasalProgram.Segment(0, 48, 100)

        assertThat(segment.getPulsesPerHour()).isEqualTo(20.toShort())
    }

    @Test
    fun `Segment getNumberOfSlots is the half-open slot range width`() {
        val segment = BasalProgram.Segment(10, 34, 100)

        assertThat(segment.getNumberOfSlots()).isEqualTo(24.toShort())
    }

    @Test
    fun `Segment equals and hashCode are structural`() {
        val a = BasalProgram.Segment(0, 24, 100)
        val b = BasalProgram.Segment(0, 24, 100)
        val different = BasalProgram.Segment(0, 24, 150)

        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
        assertThat(a).isNotEqualTo(different)
    }

    @Test
    fun `segments self-heals instead of crashing when Gson deserialization bypasses the constructor`() {
        val deserialized = Gson().fromJson("{}", BasalProgram::class.java)

        assertThat(deserialized.segments).isEmpty()
        assertThat(deserialized.rateAt(millisAt(10, 0))).isEqualTo(0.0)
    }

    @Test
    fun `BasalProgram equals and hashCode compare by segment list`() {
        val a = BasalProgram(listOf(BasalProgram.Segment(0, 48, 100)))
        val b = BasalProgram(listOf(BasalProgram.Segment(0, 48, 100)))
        val different = BasalProgram(listOf(BasalProgram.Segment(0, 48, 200)))

        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
        assertThat(a).isNotEqualTo(different)
    }
}
