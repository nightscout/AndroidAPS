package app.aaps.plugins.source.notificationreader

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class GlucoseDeduplicatorTest {

    private val min1 = 60_000L
    private val min3 = 3 * 60_000L
    private val min5 = 5 * 60_000L
    private val min15 = 15 * 60_000L

    private class MemStore : GlucoseDeduplicator.StateStore {

        var data: String? = null
        override fun load(): String? = data
        override fun save(json: String) {
            data = json
        }
    }

    private fun configWith(vararg packages: Pair<String, Int?>): PackageConfig {
        val entries = packages.joinToString(",") { (pkg, interval) ->
            val intervalField = interval?.let { ", \"intervalMinutes\": $it" } ?: ""
            """{ "package": "$pkg", "sensor": "Unknown"$intervalField }"""
        }
        return PackageConfig.fromJson("""{ "version": 2, "packages": [$entries] }""")
    }

    // ----- basic -----

    @Test
    fun `first reading is always accepted`() {
        val d = GlucoseDeduplicator(configWith("p" to 5), MemStore())
        assertThat(d.process("p", 100, 0L)).isTrue()
    }

    @Test
    fun `seed from package metadata`() {
        val d = GlucoseDeduplicator(configWith("p" to 1), MemStore())
        d.process("p", 100, 0L)
        assertThat(d.currentIntervalMs("p")).isEqualTo(min1)
    }

    @Test
    fun `seed default when package has no metadata`() {
        val d = GlucoseDeduplicator(configWith("p" to null), MemStore())
        d.process("p", 100, 0L)
        assertThat(d.currentIntervalMs("p")).isEqualTo(min5)
    }

    @Test
    fun `unknown package uses default seed`() {
        val d = GlucoseDeduplicator(configWith(), MemStore())
        assertThat(d.process("unknown", 100, 0L)).isTrue()
        assertThat(d.currentIntervalMs("unknown")).isEqualTo(min5)
    }

    // ----- duplicate window (same value) -----

    @Test
    fun `same value within window is rejected`() {
        val d = GlucoseDeduplicator(configWith("p" to 5), MemStore())
        d.process("p", 100, 0L)
        // window = 5min - 1min margin = 4min. At 30s and 3min: rejected.
        assertThat(d.process("p", 100, 30_000L)).isFalse()
        assertThat(d.process("p", 100, 3 * min1)).isFalse()
    }

    @Test
    fun `same value at or after window is accepted`() {
        val d = GlucoseDeduplicator(configWith("p" to 5), MemStore())
        d.process("p", 100, 0L)
        // threshold = 4min; gap exactly 4min → accepted (gap < threshold is false)
        assertThat(d.process("p", 100, 4 * min1)).isTrue()
    }

    @Test
    fun `same value well after window accepted (stable BG)`() {
        val d = GlucoseDeduplicator(configWith("p" to 5), MemStore())
        d.process("p", 100, 0L)
        assertThat(d.process("p", 100, min5)).isTrue()
        assertThat(d.process("p", 100, 2 * min5)).isTrue()
    }

    // ----- value-aware snap-down (over-seeding recovery) -----

    @Test
    fun `different value within window snaps down and accepts`() {
        // Seeded 15min, real sensor 5min → first 5min reading would normally be rejected by
        // timestamp alone (gap=5 < 12 threshold), but value differs → snap down to 5.
        val d = GlucoseDeduplicator(configWith("p" to 15), MemStore())
        d.process("p", 100, 0L)
        assertThat(d.process("p", 110, min5)).isTrue()
        assertThat(d.currentIntervalMs("p")).isEqualTo(min5)
    }

    @Test
    fun `different value within window with very short gap snaps to 1min`() {
        val d = GlucoseDeduplicator(configWith("p" to 5), MemStore())
        d.process("p", 100, 0L)
        assertThat(d.process("p", 105, min1)).isTrue()
        assertThat(d.currentIntervalMs("p")).isEqualTo(min1)
    }

    // ----- snap down via gap (after window) -----

    @Test
    fun `gap-based snap down on accepted reading`() {
        // Wait long enough for current threshold to pass with same value, then snap down.
        // Seeded 5min. Same value at gap=4min accepted, gap snaps to 3min interval.
        val d = GlucoseDeduplicator(configWith("p" to 5), MemStore())
        d.process("p", 100, 0L)
        d.process("p", 100, 4 * min1)
        assertThat(d.currentIntervalMs("p")).isEqualTo(min3)
    }

    // ----- snap up requires N consecutive -----

    @Test
    fun `snap up requires three consecutive long gaps`() {
        val d = GlucoseDeduplicator(configWith("p" to 1), MemStore())
        d.process("p", 100, 0L)
        // Gaps of 5min each → all accepted (gap >> threshold). Snap up after 3.
        d.process("p", 110, min5); assertThat(d.currentIntervalMs("p")).isEqualTo(min1)
        d.process("p", 120, 2 * min5); assertThat(d.currentIntervalMs("p")).isEqualTo(min1)
        d.process("p", 130, 3 * min5); assertThat(d.currentIntervalMs("p")).isEqualTo(min5)
    }

    @Test
    fun `snap up to 15min after three consecutive 15min gaps`() {
        val d = GlucoseDeduplicator(configWith("p" to 1), MemStore())
        d.process("p", 100, 0L)
        d.process("p", 110, min15)
        d.process("p", 120, 2 * min15)
        d.process("p", 130, 3 * min15)
        assertThat(d.currentIntervalMs("p")).isEqualTo(min15)
    }

    @Test
    fun `single missed reading does not change interval`() {
        // 5min sensor with one 10min gap (missed reading). 10min snaps to 5min interval → no change.
        val d = GlucoseDeduplicator(configWith("p" to 5), MemStore())
        d.process("p", 100, 0L)
        d.process("p", 110, 10 * min1)
        assertThat(d.currentIntervalMs("p")).isEqualTo(min5)
    }

    @Test
    fun `snap up counter reset by short gap`() {
        // Counting up from 1min toward 5min; an interrupting short gap resets.
        val d = GlucoseDeduplicator(configWith("p" to 1), MemStore())
        d.process("p", 100, 0L)
        d.process("p", 110, min5)              // count=1 toward 5
        d.process("p", 120, 2 * min5)          // count=2 toward 5
        d.process("p", 130, 2 * min5 + min1)   // gap=1min snaps to 1 (same as current) → reset
        d.process("p", 140, 2 * min5 + 2 * min1) // gap=1min, same value as 130? different → snaps down to 1 anyway
        // Now restart count toward 5.
        d.process("p", 150, 2 * min5 + 2 * min1 + min5)   // count=1
        d.process("p", 160, 2 * min5 + 2 * min1 + 2 * min5) // count=2
        assertThat(d.currentIntervalMs("p")).isEqualTo(min1)
        d.process("p", 170, 2 * min5 + 2 * min1 + 3 * min5) // count=3 → snap
        assertThat(d.currentIntervalMs("p")).isEqualTo(min5)
    }

    @Test
    fun `snap up counter reset by different long interval`() {
        // Counting toward 5min, then a 15min gap → counter restarts for 15.
        val d = GlucoseDeduplicator(configWith("p" to 1), MemStore())
        var t = 0L
        d.process("p", 100, t)
        t += min5; d.process("p", 110, t)   // count=1 for 5
        t += min5; d.process("p", 120, t)   // count=2 for 5
        t += min15; d.process("p", 130, t)  // count restarts → count=1 for 15
        assertThat(d.currentIntervalMs("p")).isEqualTo(min1)
        t += min15; d.process("p", 140, t)  // count=2 for 15
        t += min15; d.process("p", 150, t)  // count=3 for 15 → snap
        assertThat(d.currentIntervalMs("p")).isEqualTo(min15)
    }

    // ----- multi-package independence -----

    @Test
    fun `two packages have independent state`() {
        val d = GlucoseDeduplicator(configWith("a" to 5, "b" to 1), MemStore())
        d.process("a", 100, 0L)
        d.process("b", 200, 0L)
        // a is on 5min: same value at 1min rejected.
        assertThat(d.process("a", 100, min1)).isFalse()
        // b is on 1min: same value at 1min accepted (1min gap, threshold ~48s).
        assertThat(d.process("b", 200, min1)).isTrue()
    }

    // ----- persistence -----

    @Test
    fun `state survives recreate via store`() {
        val store = MemStore()
        val cfg = configWith("p" to 5)
        val d1 = GlucoseDeduplicator(cfg, store)
        d1.process("p", 100, 0L)
        d1.process("p", 100, min5)
        val intervalBefore = d1.currentIntervalMs("p")

        val d2 = GlucoseDeduplicator(cfg, store)
        assertThat(d2.currentIntervalMs("p")).isEqualTo(intervalBefore)
        // Same value at 30s after the last accepted (which was at min5) → rejected as duplicate.
        assertThat(d2.process("p", 100, min5 + 30_000L)).isFalse()
    }

    // ----- adaptation to wrong seed (the user's explicit ask) -----

    @Test
    fun `wrong-high seed converges to true short interval via value-aware snap-down`() {
        // Seeded 15min, real 5min, distinct values each cycle.
        val d = GlucoseDeduplicator(configWith("p" to 15), MemStore())
        var t = 0L
        var v = 100
        d.process("p", v, t)
        repeat(3) {
            t += min5; v += 2
            d.process("p", v, t)
        }
        assertThat(d.currentIntervalMs("p")).isEqualTo(min5)
    }

    @Test
    fun `wrong-low seed converges upward after three consistent long gaps`() {
        // Seeded 1min, real 5min.
        val d = GlucoseDeduplicator(configWith("p" to 1), MemStore())
        var t = 0L
        var v = 100
        d.process("p", v, t)
        repeat(3) {
            t += min5; v += 2
            d.process("p", v, t)
        }
        assertThat(d.currentIntervalMs("p")).isEqualTo(min5)
    }

    @Test
    fun `notification spam at same value with wrong-high seed is still rejected`() {
        // Even with seed=15 wrong for a 5min sensor, identical-value reposts within window
        // must still be deduped (this is the original bug).
        val d = GlucoseDeduplicator(configWith("p" to 15), MemStore())
        d.process("p", 100, 0L)
        assertThat(d.process("p", 100, 30_000L)).isFalse()
        assertThat(d.process("p", 100, min1)).isFalse()
        assertThat(d.process("p", 100, min3)).isFalse()
    }

    @Test
    fun `snap function boundaries`() {
        assertThat(GlucoseDeduplicator.snapGapToKnownIntervalMs(0L)).isEqualTo(min1)
        assertThat(GlucoseDeduplicator.snapGapToKnownIntervalMs(2 * min1)).isEqualTo(min1)
        assertThat(GlucoseDeduplicator.snapGapToKnownIntervalMs(2 * min1 + 1)).isEqualTo(min3)
        assertThat(GlucoseDeduplicator.snapGapToKnownIntervalMs(4 * min1)).isEqualTo(min3)
        assertThat(GlucoseDeduplicator.snapGapToKnownIntervalMs(4 * min1 + 1)).isEqualTo(min5)
        assertThat(GlucoseDeduplicator.snapGapToKnownIntervalMs(10 * min1)).isEqualTo(min5)
        assertThat(GlucoseDeduplicator.snapGapToKnownIntervalMs(10 * min1 + 1)).isEqualTo(min15)
        assertThat(GlucoseDeduplicator.snapGapToKnownIntervalMs(60 * min1)).isEqualTo(min15)
    }
}
