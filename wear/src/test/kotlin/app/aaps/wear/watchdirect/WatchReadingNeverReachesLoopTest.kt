package app.aaps.wear.watchdirect

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The hard rule of direct-to-watch: a reading the watch takes from the sensor itself never reaches
 * the insulin loop.
 *
 * The loop only gets glucose through `insertCgmSourceData` on the phone. So the rule holds when
 * (1) the watch app cannot store glucose at all, and (2) the direct-to-watch code sends nothing to
 * the phone but its link status. The phone side has its own guard on top
 * (`DexcomOnePlusIngest.mayFeedLoop`, tested in `:plugins:source`).
 */
class WatchReadingNeverReachesLoopTest {

    private val mainSources = File("src/main/kotlin").absoluteFile
    private val watchDirectSources = File(mainSources, "app/aaps/wear/watchdirect")

    private fun kotlinFiles(dir: File) = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `the guard has something to check`() {
        assertThat(kotlinFiles(watchDirectSources)).isNotEmpty()
    }

    @Test
    fun `the watch app has no way to store glucose for the loop`() {
        val offenders = kotlinFiles(mainSources).filter { file ->
            val text = file.readText()
            "insertCgmSourceData" in text || "PersistenceLayer" in text
        }.map { it.name }
        assertThat(offenders).isEmpty()
    }

    @Test
    fun `direct to watch sends only its link status to the phone`() {
        val sent = kotlinFiles(watchDirectSources).flatMap { file ->
            SENT_TO_PHONE.findAll(file.readText()).map { it.groupValues[1].trim() }.toList()
        }
        // Each EventWearToMobile in this package carries a CgmWatchStatus, never a reading.
        assertThat(sent).isNotEmpty()
        assertThat(sent.all { it == "status" }).isTrue()
    }

    @Test
    fun `a watch sample type never leaves the watch`() {
        // OnePlusGlucoseSample is the driver's reading. Inside the watch app it may only appear in the
        // link, which turns it into a WatchSensorReading for the faces.
        val users = kotlinFiles(mainSources).filter { "OnePlusGlucoseSample" in it.readText() }.map { it.name }
        assertThat(users).containsExactly("OnePlusWatchSensorLink.kt")
    }

    private companion object {

        val SENT_TO_PHONE = Regex("""EventWearToMobile\(([^)]*)\)""")
    }
}
