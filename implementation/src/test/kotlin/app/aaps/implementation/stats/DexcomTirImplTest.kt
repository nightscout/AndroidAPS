package app.aaps.implementation.stats

import app.aaps.core.interfaces.profile.ProfileUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class DexcomTirImplTest {

    // Unstubbed: its results only feed getString/StringBuilder in the view builders, which tolerate null.
    private val profileUtil: ProfileUtil = mock()
    private val context get() = RuntimeEnvironment.getApplication()

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
    fun `threshold accessors are ordered low to high`() {
        val tir = DexcomTirImpl()
        assertThat(tir.veryLowTirMgdl()).isLessThan(tir.lowTirMgdl())
        assertThat(tir.lowTirMgdl()).isLessThan(tir.highTirMgdl())
        assertThat(tir.highTirMgdl()).isLessThan(tir.veryHighTirMgdl())
        assertThat(tir.highNightTirMgdl()).isLessThan(tir.highTirMgdl())
    }

    @Test
    fun `view builders produce the expected structure`() {
        val tir = populated()
        assertThat(tir.toTableRowHeader(context).childCount).isEqualTo(5)
        assertThat(tir.toTableRow(context).childCount).isEqualTo(5)
        // HbA1c/SD/range-header text views build without throwing.
        assertThat(tir.toHbA1cView(context)).isNotNull()
        assertThat(tir.toSDView(context, profileUtil)).isNotNull()
        assertThat(tir.toRangeHeaderView(context, profileUtil)).isNotNull()
    }

    @Test
    fun `HbA1c view is empty when there are no readings`() {
        assertThat(DexcomTirImpl().toHbA1cView(context).text.toString()).isEmpty()
    }
}
