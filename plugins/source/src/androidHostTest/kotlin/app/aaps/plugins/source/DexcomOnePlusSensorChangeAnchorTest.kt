package app.aaps.plugins.source

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Rules of [DexcomOnePlusSensorChangeAnchor.resolve]. */
class DexcomOnePlusSensorChangeAnchorTest {

    private val now = 1_700_100_000_000L
    private val autoStartMs = now
    private val hour = 60L * 60L * 1000L

    @Test
    fun `resolve keeps the auto start when no sensor change exists yet`() {
        val resolved = DexcomOnePlusSensorChangeAnchor.resolve(
            autoStartMs = autoStartMs,
            lastSensorChangeMs = null,
            now = now,
        )

        assertThat(resolved).isEqualTo(autoStartMs)
    }

    @Test
    fun `resolve prefers a recent manual sensor change logged before the auto start`() {
        val manualInsertMs = autoStartMs - 3 * hour

        val resolved = DexcomOnePlusSensorChangeAnchor.resolve(
            autoStartMs = autoStartMs,
            lastSensorChangeMs = manualInsertMs,
            now = now,
        )

        assertThat(resolved).isEqualTo(manualInsertMs)
    }

    @Test
    fun `resolve ignores a manual sensor change older than the lookback window`() {
        val staleManualMs = autoStartMs - 25 * hour

        val resolved = DexcomOnePlusSensorChangeAnchor.resolve(
            autoStartMs = autoStartMs,
            lastSensorChangeMs = staleManualMs,
            now = now,
        )

        assertThat(resolved).isEqualTo(autoStartMs)
    }

    @Test
    fun `resolve ignores a sensor change that is not earlier than the auto start`() {
        val resolved = DexcomOnePlusSensorChangeAnchor.resolve(
            autoStartMs = autoStartMs,
            lastSensorChangeMs = autoStartMs + hour,
            now = now,
        )

        assertThat(resolved).isEqualTo(autoStartMs)
    }
}
