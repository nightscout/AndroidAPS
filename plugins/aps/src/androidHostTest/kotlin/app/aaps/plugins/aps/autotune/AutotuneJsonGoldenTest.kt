package app.aaps.plugins.aps.autotune

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import app.aaps.core.data.time.T
import app.aaps.core.keys.DoubleKey
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.autotune.data.ATProfile
import app.aaps.plugins.aps.autotune.data.BGDatum
import app.aaps.plugins.aps.autotune.data.CRDatum
import app.aaps.plugins.aps.autotune.data.DiaDeviation
import app.aaps.plugins.aps.autotune.data.PeakDeviation
import app.aaps.plugins.aps.autotune.data.PreppedGlucose
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import java.io.File
import kotlin.time.Instant

/**
 * The exact text of the JSON that Autotune writes to its files.
 *
 * These files are read by people and by oref0, to compare the two. The text comes from `org.json`
 * today, which writes a whole double without its fraction (`8`, not `8.0`) and leaves out a key
 * whose value is null. A move to another JSON library would change that silently, so the text is
 * pinned here byte for byte.
 */
class AutotuneJsonGoldenTest : TestBaseWithProfile() {

    @BeforeEach
    fun preferences() {
        whenever(preferences.get(DoubleKey.ApsAmaMin5MinCarbsImpact)).thenReturn(8.0)
        whenever(preferences.get(DoubleKey.AutosensMax)).thenReturn(1.2)
        whenever(preferences.get(DoubleKey.AutosensMin)).thenReturn(0.7)
        whenever(rh.gs(R.string.autotune_tunedprofile_name)).thenReturn("Tuned")
    }

    private fun atProfile(iCfg: ICfg): ATProfile {
        val profile = ProfileSealed.EPS(
            EPS(
                timestamp = 0,
                basalBlocks = listOf(
                    Block(T.hours(6).msecs(), 0.725),
                    Block(T.hours(6).msecs(), 1.0),
                    Block(T.hours(12).msecs(), 0.85)
                ),
                isfBlocks = listOf(Block(T.hours(8).msecs(), 54.0), Block(T.hours(16).msecs(), 45.5)),
                icBlocks = listOf(Block(T.hours(12).msecs(), 8.0), Block(T.hours(12).msecs(), 10.25)),
                targetBlocks = listOf(TargetBlock(T.hours(24).msecs(), 100.0, 110.0)),
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
            it.profileName = "My/profile"
        }
    }

    @Test
    fun `oref0 profile for each insulin curve`() {
        inZone("Europe/Prague") {
            val text = StringBuilder()
            for (peak in listOf(75, 55, 45, 60)) {
                text.append("== peak $peak\n").append(AutotuneGolden.json(atProfile(ICfg("Insulin", peak = peak, dia = 6.0, concentration = 1.0)).profileToOrefJSON())).append('\n')
            }
            AutotuneGolden.assertMatches("oref_profile.txt", text.toString())
        }
    }

    @Test
    fun `the profile store of the tuned profile`() {
        inZone("Europe/Prague") {
            val atProfile = atProfile(ICfg("Insulin", peak = 75, dia = 6.0, concentration = 1.0))
            atProfile.basal[3] = 0.812
            atProfile.isf = 47.0
            atProfile.ic = 9.5
            atProfile.updateProfile()
            for (circadian in listOf(false, true)) {
                val store = atProfile.profileStore(circadian)!!
                assertThat(store.getDefaultProfileName()).isEqualTo("My/profile")
                assertThat(store.getProfileList().map { it.toString() }).containsExactly("My/profile")
                val pure = store.getSpecificProfile("My/profile")!!
                val expected = (if (circadian) atProfile.circadianProfile else atProfile.profile).convertToNonCustomizedProfile(dateUtil)
                assertThat(pure.basalBlocks.hourly()).isEqualTo(expected.basalBlocks.hourly())
                assertThat(pure.isfBlocks.hourly()).isEqualTo(expected.isfBlocks.hourly())
                assertThat(pure.icBlocks.hourly()).isEqualTo(expected.icBlocks.hourly())
                assertThat(pure.targetBlocks.flatMap { b -> List((b.duration / 3600000).toInt()) { b.lowTarget to b.highTarget } }).isEqualTo(expected.targetBlocks.flatMap { b -> List((b.duration / 3600000).toInt()) { b.lowTarget to b.highTarget } })
                assertThat(pure.glucoseUnit).isEqualTo(expected.glucoseUnit)
                assertThat(store.getStartDate()).isEqualTo(dateUtil.now())
            }
        }
    }

    /** The value of each hour. The store keeps one block per hour, the profile joins equal hours. */
    private fun List<Block>.hourly(): List<Double> = flatMap { b -> List((b.duration / T.hours(1).msecs()).toInt()) { b.amount } }

    @Test
    fun `a nameless profile is stored under the tuned name`() {
        val atProfile = atProfile(ICfg("Insulin", peak = 75, dia = 6.0, concentration = 1.0)).also { it.profileName = "" }
        val store = atProfile.profileStore()!!
        assertThat(store.getDefaultProfileName()).isEqualTo("Tuned")
        assertThat(atProfile.profileName).isEqualTo("Tuned")
    }

    /** All kinds of value the prep data can hold: whole and fractional, negative, missing direction. */
    private fun preppedGlucose(withDeviations: Boolean): PreppedGlucose {
        val t0 = Instant.parse("2022-05-21T02:00:00Z").toEpochMilliseconds()
        fun bg(i: Int, value: Double, direction: TrendArrow?, deviation: Double) = BGDatum(dateUtil).also {
            it.id = 1000L + i
            it.date = t0 + i * T.mins(5).msecs()
            it.value = value
            it.direction = direction
            it.deviation = deviation
            it.bgi = -1.25 * i
            it.avgDelta = if (i == 0) 0.0 else 2.0 / i
            it.mealAbsorption = if (i == 1) "start" else if (i == 2) "end" else ""
            it.mealCarbs = 15 * i
        }
        val cr = CRDatum(dateUtil).also {
            it.crInitialIOB = 1.5
            it.crInitialBG = 132.6
            it.crInitialCarbTime = t0
            it.crEndIOB = -0.25
            it.crEndBG = 98.0
            it.crEndTime = t0 + T.hours(3).msecs()
            it.crCarbs = 45.7
            it.crInsulin = 4.0
        }
        return PreppedGlucose(
            from = t0,
            crData = listOf(cr),
            csfGlucoseData = listOf(bg(0, 120.0, TrendArrow.FLAT, 3.5), bg(1, 121.5, null, -2.0)),
            isfGlucoseData = listOf(bg(2, 180.0, TrendArrow.SINGLE_DOWN, -0.0)),
            basalGlucoseData = listOf(bg(3, 95.0, TrendArrow.FORTY_FIVE_UP, 10.0), bg(4, 97.25, TrendArrow.NONE, 1.0E-7)),
            dateUtil = dateUtil
        ).also {
            if (withDeviations) {
                it.diaDeviations = listOf(DiaDeviation(5.0, 12.6, 3.25, 4.75), DiaDeviation(6.5, -1.0, 0.0, 2.0))
                it.peakDeviations = listOf(PeakDeviation(45, 10.4, 2.5, 8.9), PeakDeviation(75, 0.0, -1.5, 3.0))
            }
        }
    }

    @Test
    fun `prep data as written to the file`() {
        // Kathmandu: the utcOffset written for each value is in whole hours and cuts the 45 minutes.
        inZone("Asia/Kathmandu") {
            val text = StringBuilder()
            text.append("== indented, with deviations\n").append(AutotuneGolden.json(preppedGlucose(true).toString(2))).append('\n')
            text.append("== indented, no deviations\n").append(AutotuneGolden.json(preppedGlucose(false).toString(2))).append('\n')
            text.append("== one line\n").append(AutotuneGolden.json(preppedGlucose(true).toString())).append('\n')
            AutotuneGolden.assertMatches("prepped_glucose.txt", text.toString())
        }
    }

    @Test
    fun `oref0 prep file read and written again`() {
        inZone("Europe/Prague") {
            val prep = PreppedGlucose(parseJsonObject(File("src/androidHostTest/res/autotune/test1/autotune.2022-05-21.json").readText()), dateUtil)
            AutotuneGolden.assertMatches("prepped_glucose_test1.json", AutotuneGolden.json(prep.toString(2)))
        }
    }
}
