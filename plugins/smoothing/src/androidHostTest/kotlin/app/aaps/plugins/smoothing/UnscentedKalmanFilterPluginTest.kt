package app.aaps.plugins.smoothing

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * Behavioural tests for the Unscented Kalman Filter smoother. `smooth()` is pure JVM math (no Android runtime),
 * so a plain JUnit5 test with mocked collaborators exercises the whole forward-UKF + backward-RTS pipeline,
 * segmentation, outlier rejection and the raw-copy fallbacks.
 *
 * Each plugin is freshly constructed so its learned state starts from defaults: `preferences.get(LastSaved…)`
 * returns the Mockito default 0, which makes `loadPersistedParameters()` take the "nothing persisted" path.
 */
internal class UnscentedKalmanFilterPluginTest {

    private val aapsLogger = mock<AAPSLogger>()
    private val rh = mock<ResourceHelper>()
    private val preferences = mock<Preferences>()
    private val persistenceLayer = mock<PersistenceLayer>()

    private fun plugin() = UnscentedKalmanFilterPlugin(aapsLogger, rh, preferences, persistenceLayer)

    /** Newest-first series (data[0] = most recent) at [stepMin]-minute spacing, timestamps descending. */
    private fun series(vararg values: Double, stepMin: Long = 5): MutableList<InMemoryGlucoseValue> {
        val base = 1_700_000_000_000L
        return values.mapIndexed { i, v ->
            InMemoryGlucoseValue(timestamp = base - i * stepMin * 60_000L, value = v)
        }.toMutableList()
    }

    @Test fun `empty input returns the same empty list`() {
        val data = mutableListOf<InMemoryGlucoseValue>()
        val out = plugin().smooth(data)
        assertThat(out).isSameInstanceAs(data)
        assertThat(out).isEmpty()
    }

    @Test fun `a single value is copied to smoothed, floored at 39`() {
        assertThat(plugin().smooth(series(100.0))[0].smoothed).isEqualTo(100.0)
        assertThat(plugin().smooth(series(20.0))[0].smoothed).isEqualTo(39.0)
    }

    @Test fun `error-code (38) values collapse to the 39 floor with no valid segment`() {
        val out = plugin().smooth(series(38.0, 38.0, 38.0))
        assertThat(out.map { it.smoothed }).containsExactly(39.0, 39.0, 39.0)
    }

    @Test fun `a clean series smooths every point to a sane value`() {
        val out = plugin().smooth(series(101.0, 99.0, 100.0, 102.0, 98.0, 100.0, 101.0, 99.0, 100.0, 100.0))
        assertThat(out).hasSize(10)
        out.forEach {
            assertThat(it.smoothed).isNotNull()
            assertThat(it.smoothed!!).isAtLeast(39.0)
            assertThat(it.smoothed!!).isWithin(30.0).of(100.0)
        }
    }

    @Test fun `a rising series produces a rising smoothed trend`() {
        // newest-first: data[0] is the most recent (highest) value, data.last() the oldest (lowest)
        val out = plugin().smooth(series(150.0, 140.0, 130.0, 120.0, 110.0, 100.0, 90.0, 80.0))
        assertThat(out.first().smoothed!!).isGreaterThan(out.last().smoothed!!)
    }

    @Test fun `an isolated spike is dampened toward the surrounding level`() {
        // stable 100s with one 300 spike — outlier handling must pull the smoothed spike far below the raw 300
        val out = plugin().smooth(series(100.0, 100.0, 100.0, 300.0, 100.0, 100.0, 100.0, 100.0))
        val spike = out[3]
        assertThat(spike.value).isEqualTo(300.0)
        assertThat(spike.smoothed!!).isLessThan(200.0)
    }

    @Test fun `data spanning a major gap is split into segments and both clusters are smoothed`() {
        val base = 1_700_000_000_000L
        val clusterA = listOf(100.0, 101.0, 99.0).mapIndexed { i, v -> InMemoryGlucoseValue(timestamp = base - i * 5 * 60_000L, value = v) }
        val gapBase = base - (3 * 5 + 120) * 60_000L // 120-minute (major) gap after cluster A
        val clusterB = listOf(120.0, 119.0, 121.0).mapIndexed { i, v -> InMemoryGlucoseValue(timestamp = gapBase - i * 5 * 60_000L, value = v) }
        val out = plugin().smooth((clusterA + clusterB).toMutableList())
        assertThat(out.count { it.smoothed != null }).isAtLeast(4)
    }

    @Test fun `smoothing is deterministic across fresh instances`() {
        val a = plugin().smooth(series(120.0, 118.0, 122.0, 119.0, 121.0, 120.0, 118.0))
        val b = plugin().smooth(series(120.0, 118.0, 122.0, 119.0, 121.0, 120.0, 118.0))
        a.indices.forEach { assertThat(b[it].smoothed!!).isWithin(1e-9).of(a[it].smoothed!!) }
    }
}
