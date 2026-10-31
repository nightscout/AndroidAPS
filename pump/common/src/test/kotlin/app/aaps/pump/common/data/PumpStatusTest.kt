package app.aaps.pump.common.data

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.pump.common.defs.TempBasalPair
import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTime
import org.junit.jupiter.api.Test

internal class PumpStatusTest {

    /** [PumpStatus] is abstract only for [errorInfo], so a bare subclass is enough to exercise it. */
    private class TestPumpStatus : PumpStatus(PumpType.MEDTRONIC_522_722) {

        override val errorInfo: String? = null
        var fragmentUpdates = 0
        override fun updateLastConnectionInFragment() {
            fragmentUpdates++
        }
    }

    private val sut = TestPumpStatus()

    private fun tbr(durationMinutes: Int, start: Long? = null) =
        TempBasalPair(insulinRate = 1.0, isPercent = false, durationMinutes = durationMinutes, start = start)

    // The estimated end is what tells the driver a temp basal has run out. Derived from the pump's own
    // start when it gave one, so a TBR read back late is not treated as if it began at the read.
    @Test fun `a temp basal with a start ends a duration after that start`() {
        sut.currentTempBasal = tbr(durationMinutes = 30, start = 1_000_000L)

        assertThat(sut.currentTempBasalEstimatedEnd).isEqualTo(1_000_000L + 30 * 60 * 1000)
    }

    // No start from the pump means the clock starts now - the only assumption available.
    @Test fun `a temp basal without a start ends a duration from now`() {
        val before = System.currentTimeMillis()

        sut.currentTempBasal = tbr(durationMinutes = 60)

        val end = sut.currentTempBasalEstimatedEnd!!
        assertThat(end).isAtLeast(before + 60 * 60 * 1000)
        assertThat(end).isAtMost(System.currentTimeMillis() + 60 * 60 * 1000)
    }

    @Test fun `clearing the temp basal clears the estimated end with it`() {
        sut.currentTempBasal = tbr(durationMinutes = 30, start = 1_000L)
        assertThat(sut.currentTempBasalEstimatedEnd).isNotNull()

        sut.currentTempBasal = null

        assertThat(sut.currentTempBasalEstimatedEnd).isNull()
    }

    // clearTbr() must leave nothing behind: an estimated end outliving its temp basal would report a
    // TBR that is not running.
    @Test fun `clearTbr leaves neither the temp basal nor its end`() {
        sut.currentTempBasal = tbr(durationMinutes = 45, start = 2_000L)

        sut.clearTbr()

        assertThat(sut.currentTempBasal).isNull()
        assertThat(sut.currentTempBasalEstimatedEnd).isNull()
    }

    @Test fun `recording a communication stamps both times and tells the fragment`() {
        val before = System.currentTimeMillis()

        sut.setLastCommunicationToNow()

        assertThat(sut.lastDataTime).isAtLeast(before)
        assertThat(sut.lastConnection).isAtLeast(before)
        assertThat(sut.fragmentUpdates).isEqualTo(1)
    }

    // The properties are views onto StateFlows, so a driver writing the property and a Composable
    // collecting the flow see the same value. Writing one and reading the other is the contract.
    @Test fun `the property writes are visible through the backing flows`() {
        sut.lastConnection = 123L
        sut.reservoirRemainingUnits = 42.5
        sut.batteryRemaining = 77

        assertThat(sut.lastConnectionFlow.value).isEqualTo(123L)
        assertThat(sut.reservoirRemainingUnitsFlow.value).isEqualTo(42.5)
        assertThat(sut.batteryRemainingFlow.value).isEqualTo(77)
    }

    @Test fun `the flows start from a defined value rather than undefined state`() {
        val fresh = TestPumpStatus()

        assertThat(fresh.lastConnection).isEqualTo(0L)
        assertThat(fresh.reservoirRemainingUnits).isEqualTo(0.0)
        assertThat(fresh.batteryRemaining).isNull()
        assertThat(fresh.lastBolusTime).isNull()
        assertThat(fresh.lastBolusAmount).isNull()
    }

    @Test fun `the pump type is carried on the status`() {
        assertThat(sut.pumpType).isEqualTo(PumpType.MEDTRONIC_522_722)
    }

    // ---- PumpTimeDifferenceDto ---------------------------------------------------------------

    // The sign says which way the pump is off. secondsBetween(local, pump) is positive when the pump
    // runs AHEAD of the phone - getting this backwards would move a correction the wrong way.
    @Test fun `a pump running ahead gives a positive difference`() {
        val local = DateTime(2026, 9, 14, 12, 0, 0)

        val dto = PumpTimeDifferenceDto(localDeviceTime = local, pumpTime = local.plusSeconds(90))

        assertThat(dto.timeDifference).isEqualTo(90)
    }

    @Test fun `a pump running behind gives a negative difference`() {
        val local = DateTime(2026, 9, 14, 12, 0, 0)

        val dto = PumpTimeDifferenceDto(localDeviceTime = local, pumpTime = local.minusSeconds(45))

        assertThat(dto.timeDifference).isEqualTo(-45)
    }

    @Test fun `clocks in step give no difference`() {
        val local = DateTime(2026, 9, 14, 12, 0, 0)

        assertThat(PumpTimeDifferenceDto(local, local).timeDifference).isEqualTo(0)
    }

    // The difference is computed in init, so it is right before anyone calls calculateDifference().
    @Test fun `the difference is available without calling calculate first`() {
        val local = DateTime(2026, 9, 14, 12, 0, 0)
        val dto = PumpTimeDifferenceDto(local, local.plusMinutes(2))

        assertThat(dto.timeDifference).isEqualTo(120)

        dto.calculateDifference()

        assertThat(dto.timeDifference).isEqualTo(120)
    }
}
