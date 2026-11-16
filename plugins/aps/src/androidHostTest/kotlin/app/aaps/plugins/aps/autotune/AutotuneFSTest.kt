package app.aaps.plugins.aps.autotune

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.LoggerUtils
import app.aaps.core.keys.DoubleKey
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.implementation.maintenance.AndroidAutotuneFileStore
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.autotune.data.ATProfile
import app.aaps.plugins.aps.autotune.data.PreppedGlucose
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.util.zip.ZipFile
import kotlin.time.Instant

/**
 * The files of an Autotune run: their folders, names and text, and the zip made from them.
 *
 * The names carry dates in the phone's time zone. Kiritimati is 14 hours ahead of UTC, so a date
 * taken in UTC by mistake would show up here as the day before.
 */
class AutotuneFSTest : TestBaseWithProfile() {

    @Mock lateinit var loggerUtils: LoggerUtils
    @TempDir lateinit var logDirectory: File

    @BeforeEach
    fun prepare() {
        whenever(loggerUtils.logDirectory).thenReturn(logDirectory.absolutePath)
        whenever(rh.gs(R.string.autotune_tunedprofile_name)).thenReturn("Tuned profile")
        whenever(preferences.get(DoubleKey.ApsAmaMin5MinCarbsImpact)).thenReturn(8.0)
        whenever(preferences.get(DoubleKey.AutosensMax)).thenReturn(1.2)
        whenever(preferences.get(DoubleKey.AutosensMin)).thenReturn(0.7)
    }

    private fun atProfile(from: Long): ATProfile {
        val iCfg = ICfg("Insulin", peak = 75, dia = 6.0, concentration = 1.0)
        val profile = ProfileSealed.EPS(
            EPS(
                timestamp = 0,
                basalBlocks = listOf(Block(T.hours(24).msecs(), 0.8)),
                isfBlocks = listOf(Block(T.hours(24).msecs(), 50.0)),
                icBlocks = listOf(Block(T.hours(24).msecs(), 10.0)),
                targetBlocks = listOf(TargetBlock(T.hours(24).msecs(), 100.0, 100.0)),
                glucoseUnit = GlucoseUnit.MGDL,
                originalProfileName = "Test",
                originalCustomizedName = "Test",
                originalTimeshift = 0,
                originalPercentage = 100,
                originalDuration = 0,
                originalEnd = 0,
                iCfg = iCfg
            ),
            activePlugin
        )
        return ATProfile(preferences, profileUtil, dateUtil, rh, { profileStoreProvider() }, aapsLogger).with(profile, iCfg).also {
            it.profileName = "Pump"
            it.from = from
        }
    }

    @Test
    fun `a run writes these files and zips them`() {
        inZone("Pacific/Kiritimati") {
            // 2022-05-21 11:30 UTC is 2022-05-22 01:30 in Kiritimati.
            val start = Instant.parse("2022-05-21T11:30:00Z").toEpochMilliseconds()
            val lastRun = Instant.parse("2022-05-23T11:45:10Z").toEpochMilliseconds()
            val autotuneIob = mock<AutotuneIob>()
            whenever(autotuneIob.startBG).thenReturn(start)
            whenever(autotuneIob.glucoseToJSON()).thenReturn("[\"glucose\"]")
            whenever(autotuneIob.nsHistoryToJSON()).thenReturn("[\"treatments\"]")
            whenever(autotuneIob.bolusesToJSON()).thenReturn("[\"boluses\"]")
            val prepped = PreppedGlucose(start, emptyList(), emptyList(), emptyList(), emptyList(), dateUtil)

            val fs = AutotuneFS(rh, AndroidAutotuneFileStore(loggerUtils), aapsLogger)
            fs.createAutotuneFolder()
            // Left from an earlier run: deleted before this one.
            File(logDirectory, "autotune/old.json").writeText("old")
            File(logDirectory, "settings/old.json").writeText("old")
            fs.deleteAutotuneFiles()
            fs.exportSettings("{\"settings\":1}")
            fs.exportPumpProfile(atProfile(start))
            fs.exportEntries(autotuneIob)
            fs.exportTreatments(autotuneIob)
            fs.exportPreppedGlucose(prepped)
            fs.exportTunedProfile(atProfile(start))
            fs.exportLog(lastRun, 1)
            fs.exportResult("result text")
            fs.atLog("a line of my own")
            fs.exportLogAndZip(lastRun)

            // Each file ends with one line break. A JSON file is compared in its canonical form.
            fun content(name: String, text: String): String {
                val body = text.replace("\r\n", "\n")
                assertThat(body).endsWith("\n")
                return if (name.endsWith(".json")) AutotuneGolden.json(body.removeSuffix("\n")) + "\n" else body
            }

            val report = StringBuilder()
            logDirectory.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(logDirectory).invariantSeparatorsPath }.forEach { file ->
                val name = file.relativeTo(logDirectory).invariantSeparatorsPath
                report.append("== file $name\n")
                if (name.endsWith(".zip")) {
                    ZipFile(file).use { zip ->
                        zip.entries().toList().sortedBy { it.name }.forEach { entry ->
                            report.append("-- entry ${entry.name}\n")
                            report.append(content(entry.name, zip.getInputStream(entry).readBytes().decodeToString()))
                        }
                    }
                } else report.append(content(name, file.readText()))
            }
            // The log names the folder, which is new for each run of the test.
            AutotuneGolden.assertMatches("files.txt", report.toString().replace(logDirectory.absolutePath, "<logDirectory>"))
        }
    }

    @Test
    fun `the log is written once and then starts again`() {
        val fs = AutotuneFS(rh, AndroidAutotuneFileStore(loggerUtils), aapsLogger)
        fs.createAutotuneFolder()
        val lastRun = Instant.parse("2022-05-23T11:45:10Z").toEpochMilliseconds()
        fs.atLog("first")
        fs.exportLog(lastRun)
        fs.atLog("second")
        fs.exportLog(lastRun, 2)
        val logs = File(logDirectory, "autotune").listFiles()!!.filter { it.name.endsWith(".log") }.sortedBy { it.name }
        assertThat(logs.map { it.name }).hasSize(2)
        assertThat(logs[0].readText()).contains("first")
        assertThat(logs[1].readText()).contains("second")
        assertThat(logs[1].readText()).doesNotContain("first")
    }
}
