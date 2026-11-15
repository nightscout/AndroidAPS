package app.aaps.plugins.aps.autotune

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import app.aaps.core.data.time.T
import app.aaps.core.keys.DoubleKey
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.plugins.aps.autotune.data.ATProfile
import app.aaps.plugins.aps.autotune.data.PreppedGlucose
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import java.io.File
import kotlin.time.Instant

/**
 * Which hour of the day a glucose value counts for, when [AutotuneCore] tunes the basal.
 *
 * [AutotuneCoreTest] compares the result with oref0, but only in a fixed GMT+2 zone on a May day,
 * where every hour is 60 minutes long. Here the same real day of data is moved over both daylight
 * saving changes in Prague and run again in Kathmandu, whose offset is not a whole hour. The log of
 * the summed deviations per hour shows where each value went.
 */
class AutotuneCoreZoneTest : TestBaseWithProfile() {

    @Mock lateinit var autotuneFS: AutotuneFS
    private val log = mutableListOf<String>()

    @BeforeEach
    fun captureLog() {
        doAnswer { log += it.getArgument<String>(0) }.whenever(autotuneFS).atLog(any())
        whenever(preferences.get(DoubleKey.AutosensMax)).thenReturn(1.2)
        whenever(preferences.get(DoubleKey.AutosensMin)).thenReturn(0.7)
        whenever(preferences.get(DoubleKey.ApsSmbMin5MinCarbsImpact)).thenReturn(8.0)
    }

    @Test
    fun `spring forward in Prague`() {
        inZone("Europe/Prague") { tune("core_prague_spring.txt", "2022-03-26T02:00:00Z") }
    }

    @Test
    fun `fall back in Prague`() {
        inZone("Europe/Prague") { tune("core_prague_autumn.txt", "2022-10-29T02:00:00Z") }
    }

    @Test
    fun `offset of five hours and 45 minutes`() {
        inZone("Asia/Kathmandu") { tune("core_kathmandu.txt", "2022-05-21T02:00:00Z") }
    }

    /** Runs the recorded day of test1, moved so that it starts at [start]. */
    private fun tune(golden: String, start: String) {
        val prep = PreppedGlucose(parseJsonObject(File("src/androidHostTest/res/autotune/test1/autotune.2022-05-21.json").readText()), dateUtil)
        // The recorded day starts at 4 AM in GMT+2.
        val shift = Instant.parse(start).toEpochMilliseconds() - Instant.parse("2022-05-21T02:00:00Z").toEpochMilliseconds()
        (prep.csfGlucoseData + prep.isfGlucoseData + prep.basalGlucoseData).forEach { it.date += shift }
        prep.crData.forEach {
            it.crInitialCarbTime += shift
            it.crEndTime += shift
        }

        val iCfg = ICfg("PumpInsulin", peak = 75, dia = 6.0, concentration = 1.0)
        val profile = ProfileSealed.EPS(
            EPS(
                timestamp = 0,
                basalBlocks = listOf(
                    Block(T.hours(2).msecs(), 0.7),
                    Block(T.hours(4).msecs(), 0.95),
                    Block(T.hours(6).msecs(), 0.85),
                    Block(T.hours(12).msecs(), 0.65)
                ),
                isfBlocks = listOf(Block(T.hours(24).msecs(), 86.0)),
                icBlocks = listOf(Block(T.hours(24).msecs(), 5.75)),
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
        val previous = ATProfile(preferences, profileUtil, dateUtil, rh, { profileStoreProvider() }, aapsLogger).with(profile, iCfg)
        val pump = ATProfile(preferences, profileUtil, dateUtil, rh, { profileStoreProvider() }, aapsLogger).with(profile, iCfg)

        val tuned = AutotuneCore(preferences, autotuneFS).tuneAllTheThings(prep, previous, pump)

        val report = StringBuilder()
        report.append("isf=${tuned.isf} ic=${tuned.ic}\n")
        for (h in 0..23) report.append("basal[$h]=${tuned.basal[h]} untuned=${tuned.basalUnTuned[h]}\n")
        report.append("== log\n")
        log.forEach { report.append(it).append('\n') }
        AutotuneGolden.assertMatches(golden, report.toString())
    }
}
