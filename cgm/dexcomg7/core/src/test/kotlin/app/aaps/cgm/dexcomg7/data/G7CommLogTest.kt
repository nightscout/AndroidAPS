package app.aaps.cgm.dexcomg7.data

import android.content.Context
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

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

    @Test
    fun keepsLinesInTimeOrder() {
        val entries = listOf(G7CommLog.Entry(now, "phone"), G7CommLog.Entry(now - 1_000, "watch, sent late"))
        assertThat(G7CommLog.keep(entries, now).map { it.text }).containsExactly("watch, sent late", "phone").inOrder()
    }

    @Test
    fun linesFromTheWatchKeepTheirTimeAndAreAddedOnce(@TempDir dir: File) {
        val context = mock<Context>()
        whenever(context.filesDir).thenReturn(dir)
        val log = G7CommLog(context, AAPSLoggerTest())
        val time = System.currentTimeMillis() - 60_000
        val lines = listOf(G7CommLog.Entry(time, "Bonded (0801)"), G7CommLog.Entry(time + 1_000, "send control 4e"))

        log.addFrom("Watch", lines)
        log.addFrom("Watch", lines)

        assertThat(log.entries.value.map { it.time to it.text })
            .containsExactly(time to "Watch: Bonded (0801)", time + 1_000 to "Watch: send control 4e").inOrder()
    }
}
