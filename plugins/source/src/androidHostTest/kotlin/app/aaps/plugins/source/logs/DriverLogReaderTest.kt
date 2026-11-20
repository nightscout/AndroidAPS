package app.aaps.plugins.source.logs

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** What the log screen is allowed to show, and what it must not lose on the way. */
class DriverLogReaderTest {

    /** The reader knows no driver, so the test names one, the way a caller does. */
    private val oneDriver = setOf("DEXCOM_ONEPLUS")

    @Test
    fun `keeps only the lines of the driver`() {
        val lines = listOf(
            "10:00:00.001 [main] I/BGSOURCE: something from another source",
            "10:00:00.002 [main] I/DEXCOM_ONEPLUS: DEXCOM_ONEPLUS_SESSION: link is up",
            "10:00:00.003 [main] D/LoopPlugin: something else entirely",
        )

        assertThat(DriverLogReader.keep(lines, oneDriver, 100))
            .containsExactly("10:00:00.002 [main] I/DEXCOM_ONEPLUS: DEXCOM_ONEPLUS_SESSION: link is up")
    }

    @Test
    fun `a stack trace stays with the line it belongs to`() {
        val lines = listOf(
            "10:00:00.001 [main] E/DEXCOM_ONEPLUS: DEXCOM_ONEPLUS_ERROR: the handshake stopped",
            "java.io.IOException: link closed",
            "\tat app.aaps.plugins.dexcomoneplus.session.OnePlusBleSession.open(OnePlusBleSession.kt:120)",
            "10:00:00.002 [main] E/OtherPlugin: not ours",
            "java.lang.IllegalStateException: not ours either",
        )

        val kept = DriverLogReader.keep(lines, oneDriver, 100)

        assertThat(kept).hasSize(3)
        assertThat(kept[1]).isEqualTo("java.io.IOException: link closed")
        assertThat(kept).doesNotContain("java.lang.IllegalStateException: not ours either")
    }

    @Test
    fun `when there are too many lines the newest are the ones kept`() {
        val lines = (1..10).map { "10:00:00.00$it [main] I/DEXCOM_ONEPLUS: line $it" }

        val kept = DriverLogReader.keep(lines, oneDriver, 3)

        assertThat(kept).hasSize(3)
        assertThat(kept.last()).endsWith("line 10")
        assertThat(kept.first()).endsWith("line 8")
    }

    @Test
    fun `a whole small file is read, and a big one only from its end`(@TempDir folder: File) {
        val file = File(folder, DriverLogReader.CURRENT_FILE)
        file.writeText("first line\nsecond line\nthird line\n")

        assertThat(DriverLogReader.tail(file, 1024L)).containsAtLeast("first line", "third line")

        // Reading from an offset lands in the middle of a line, so that half line is dropped.
        val fromTheEnd = DriverLogReader.tail(file, 15L)
        assertThat(fromTheEnd).doesNotContain("first line")
        assertThat(fromTheEnd).contains("third line")
    }

    @Test
    fun `a build that has not written anything yet answers with nothing`(@TempDir folder: File) {
        assertThat(DriverLogReader.read(folder.absolutePath, oneDriver)).isEmpty()
    }

    @Test
    fun `reads the driver lines back out of a real file`(@TempDir folder: File) {
        File(folder, DriverLogReader.CURRENT_FILE).writeText(
            "10:00:00.001 [main] I/DEXCOM_ONEPLUS: DEXCOM_ONEPLUS_SCAN: sensor heard\n" +
                "10:00:00.002 [main] I/SomethingElse: ignore me\n" +
                "10:00:00.003 [main] I/DEXCOM_ONEPLUS: DEXCOM_ONEPLUS_BG: value taken\n"
        )

        val all = DriverLogReader.read(folder.absolutePath, oneDriver)

        assertThat(all).hasSize(2)
        assertThat(all.first()).contains("DEXCOM_ONEPLUS_SCAN")
        assertThat(all.last()).contains("DEXCOM_ONEPLUS_BG")
    }

    @Test
    fun `only the lines of the logger names the caller gives are kept`() {
        val lines = listOf(
            "10:00:00.001 [main] I/DEXCOM_ONEPLUS: from the first driver",
            "10:00:00.002 [main] I/OTHER_DRIVER: from the second driver",
            "10:00:00.003 [main] I/OTHER_DRIVER_SCAN: a name that only starts the same",
        )

        assertThat(DriverLogReader.keep(lines, setOf("OTHER_DRIVER"), 100))
            .containsExactly("10:00:00.002 [main] I/OTHER_DRIVER: from the second driver")
        assertThat(DriverLogReader.keep(lines, setOf("DEXCOM_ONEPLUS", "OTHER_DRIVER"), 100)).hasSize(2)
    }

    @Test
    fun `no logger names means no lines even when the file has some`(@TempDir folder: File) {
        File(folder, DriverLogReader.CURRENT_FILE).writeText(
            "10:00:00.001 [main] I/DEXCOM_ONEPLUS: DEXCOM_ONEPLUS_SCAN: sensor heard\n"
        )

        assertThat(DriverLogReader.read(folder.absolutePath, emptySet())).isEmpty()
    }
}
