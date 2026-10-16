package app.aaps.cgm.dexcomg7.data

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class G7CommLogTest {

    private val now = 1_700_000_000_000L

    @Test
    fun keepsTwentyFourHours() {
        val entries = listOf(
            G7CommLog.Entry(now - G7CommLog.KEEP_MS - 1, "too old"),
            G7CommLog.Entry(now - G7CommLog.KEEP_MS, "just in"),
            G7CommLog.Entry(now, "new")
        )
        assertThat(G7CommLog.keep(entries, now).map { it.text }).containsExactly("just in", "new").inOrder()
    }

    @Test
    fun dropsTheOldestAboveTheLimit() {
        val entries = (0 until G7CommLog.MAX_ENTRIES + 10).map { G7CommLog.Entry(now, "line $it") }
        val kept = G7CommLog.keep(entries, now)
        assertThat(kept).hasSize(G7CommLog.MAX_ENTRIES)
        assertThat(kept.first().text).isEqualTo("line 10")
    }

    @Test
    fun fileLineRoundTrip() {
        val entry = G7CommLog.Entry(now, "control 4e00 with\ttab")
        val parsed = G7CommLog.parse(G7CommLog.format(entry).trimEnd('\n'))!!
        assertThat(parsed.time).isEqualTo(now)
        assertThat(parsed.text).isEqualTo("control 4e00 with\ttab")
        assertThat(G7CommLog.parse("garbage")).isNull()
        assertThat(G7CommLog.parse("")).isNull()
    }
}
