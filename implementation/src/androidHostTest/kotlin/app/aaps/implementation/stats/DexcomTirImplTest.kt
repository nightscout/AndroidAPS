package app.aaps.implementation.stats

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.sqrt

internal class DexcomTirImplTest {

    /** Builds a TIR with one reading in each band + one error, using values that fall in the same band
     *  whether the timestamp is treated as day or night (so the assertions are timezone-independent). */
    private fun populated(): DexcomTirImpl = DexcomTirImpl().apply {
        val t = 1_600_000_000_000L
        add(t, 30.0)  // < 39  -> error (excluded from count)
        add(t, 50.0)  // < ~54 -> very low
        add(t, 60.0)  // < ~70 -> low
        add(t, 100.0) // in range (below both day and night high thresholds)
        add(t, 200.0) // > both high thresholds, < ~250 -> high
        add(t, 300.0) // > ~250 -> very high
    }

    @Test
    fun `add categorises readings into the five ranges and excludes errors`() {
        val tir = populated()

        assertThat(tir.count()).isEqualTo(5) // the 30 mg/dl reading is an error, not counted
        assertThat(tir.veryLowPct()).isWithin(1e-6).of(20.0)
        assertThat(tir.lowPct()).isWithin(1e-6).of(20.0)
        assertThat(tir.inRangePct()).isWithin(1e-6).of(20.0)
        assertThat(tir.highPct()).isWithin(1e-6).of(20.0)
        assertThat(tir.veryHighPct()).isWithin(1e-6).of(20.0)
    }

    @Test
    fun `mean and standard deviation are computed over valid readings`() {
        val tir = populated()

        assertThat(tir.mean()).isWithin(1e-6).of((50.0 + 60.0 + 100.0 + 200.0 + 300.0) / 5)
        assertThat(tir.calculateSD()).isGreaterThan(0.0)
    }

    @Test
    fun `an empty accumulator yields zero percentages and SD`() {
        val tir = DexcomTirImpl()

        assertThat(tir.count()).isEqualTo(0)
        assertThat(tir.veryLowPct()).isEqualTo(0.0)
        assertThat(tir.inRangePct()).isEqualTo(0.0)
        assertThat(tir.calculateSD()).isEqualTo(0.0)
    }

    @Test
    fun `hyper intensity uses the area above 180 and the time`() {
        val t = 1_600_000_000_000L
        val tir = DexcomTirImpl().apply {
            add(t, 200.0)
            add(t + 300_000, 220.0)
            add(t + 600_000, 100.0)
        }

        // (20 x 5 + 40 x 5) mg/dL x min and 10 min
        assertThat(tir.cgpHyperIntensity()).isWithin(1e-6).of(sqrt(300.0 * 300.0 + 10.0 * 10.0))
        assertThat(tir.cgpHypoIntensity()).isEqualTo(0.0)
    }

    @Test
    fun `hypo intensity uses the area below 70 and the time`() {
        val tir = DexcomTirImpl().apply { add(1_600_000_000_000L, 60.0) }

        assertThat(tir.cgpHypoIntensity()).isWithin(1e-6).of(sqrt(50.0 * 50.0 + 5.0 * 5.0))
        assertThat(tir.cgpHyperIntensity()).isEqualTo(0.0)
    }

    @Test
    fun `intensity is the mean of the days`() {
        val t = 1_600_000_000_000L
        val tir = DexcomTirImpl().apply {
            add(t, 280.0)
            add(t + 86_400_000L, 100.0)
        }

        assertThat(tir.cgpHyperIntensity()).isWithin(1e-6).of(sqrt(500.0 * 500.0 + 5.0 * 5.0) / 2)
    }

    @Test
    fun `a reading counts for the time since the last one and at most five minutes`() {
        val t = 1_600_000_000_000L
        val everyMinute = DexcomTirImpl().apply { for (i in 0L until 5L) add(t + i * 60_000, 200.0) }
        val afterAGap = DexcomTirImpl().apply {
            add(t, 200.0)
            add(t + 1_800_000, 200.0)
        }

        // 5 + 4 x 1 minutes
        assertThat(everyMinute.cgpHyperIntensity()).isWithin(1e-6).of(sqrt(180.0 * 180.0 + 9.0 * 9.0))
        // 5 + 5 minutes, the gap is not glucose above the range
        assertThat(afterAGap.cgpHyperIntensity()).isWithin(1e-6).of(sqrt(200.0 * 200.0 + 10.0 * 10.0))
    }

    @Test
    fun `the pentagon range is the same at any hour and errors are not in it`() {
        val tir = populated()

        assertThat(tir.cgpBelowRangePct()).isWithin(1e-6).of(40.0)
        assertThat(tir.cgpAboveRangePct()).isWithin(1e-6).of(40.0)
    }

    @Test
    fun `a value at the limits is in range and has no intensity area`() {
        val tir = DexcomTirImpl().apply { add(1_600_000_000_000L, 180.0) }

        assertThat(tir.cgpAboveRangePct()).isEqualTo(0.0)
        assertThat(tir.cgpHyperIntensity()).isWithin(1e-6).of(5.0)
    }

    @Test
    fun `an empty accumulator has no intensity`() {
        val tir = DexcomTirImpl()

        assertThat(tir.cgpHypoIntensity()).isEqualTo(0.0)
        assertThat(tir.cgpHyperIntensity()).isEqualTo(0.0)
        assertThat(tir.cgpBelowRangePct()).isEqualTo(0.0)
    }

    @Test
    fun `threshold accessors are ordered low to high`() {
        val tir = DexcomTirImpl()
        assertThat(tir.veryLowTirMgdl()).isLessThan(tir.lowTirMgdl())
        assertThat(tir.lowTirMgdl()).isLessThan(tir.highTirMgdl())
        assertThat(tir.highTirMgdl()).isLessThan(tir.veryHighTirMgdl())
        assertThat(tir.highNightTirMgdl()).isLessThan(tir.highTirMgdl())
    }
}
