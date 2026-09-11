package app.aaps.plugins.sensitivity

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.Sensitivity
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SensitivityWeightedAveragePluginTest : SensitivityTestBase() {

    private lateinit var sut: SensitivityWeightedAveragePlugin

    @BeforeEach fun prepare() {
        sut = SensitivityWeightedAveragePlugin(aapsLogger, rh, preferences, dateUtil, activePlugin)
    }

    private fun detect(
        ads: AutosensDataStore,
        profile: EffectiveProfile? = effectiveProfile,
        siteChanges: List<TE> = emptyList(),
        profileSwitches: List<PS> = emptyList()
    ) = sut.detectSensitivity(ads, fromTime, toTime, profile, siteChanges, profileSwitches)

    @Test
    fun isMinCarbsAbsorptionDynamic() {
        assertThat(sut.isMinCarbsAbsorptionDynamic).isTrue()
    }

    @Test
    fun isOref1() {
        assertThat(sut.isOref1).isFalse()
    }

    @Test
    fun getId() {
        assertThat(sut.id).isEqualTo(Sensitivity.SensitivityType.SENSITIVITY_WEIGHTED)
    }

    // detectSensitivity - guard branches -------------------------------------------------------

    @Test
    fun detect_returnsDefault_whenNotEnoughData() {
        val result = detect(autosensDataStore(constantSeries(3, 0.5)))
        assertThat(result.ratio).isWithin(0.001).of(1.0)
        assertThat(result.sensResult).isEqualTo("autosens not available")
    }

    @Test
    fun detect_returnsDefault_whenNoCurrentData() {
        val result = detect(autosensDataStore(constantSeries(48, 0.5), current = null))
        assertThat(result.ratio).isWithin(0.001).of(1.0)
    }

    @Test
    fun detect_returnsDefault_whenNoProfile() {
        val result = detect(autosensDataStore(constantSeries(48, 0.5)), profile = null)
        assertThat(result.ratio).isWithin(0.001).of(1.0)
    }

    @Test
    fun detect_returnsDefault_whenNoValidDeviations() {
        // nothing appended to the weighted data set -> empty -> default
        val result = detect(autosensDataStore(constantSeries(48, 0.5, validDeviation = false)))
        assertThat(result.ratio).isWithin(0.001).of(1.0)
    }

    // detectSensitivity - direction & exact ratio ----------------------------------------------

    @Test
    fun detect_resistance_forPositiveDeviation() {
        val result = detect(autosensDataStore(constantSeries(48, 0.5)))
        assertThat(result.ratio).isWithin(0.001).of(1.12)
        assertThat(result.sensResult).contains("resistance")
        assertThat(result.carbsAbsorbed).isWithin(0.001).of(cob)
    }

    @Test
    fun detect_sensitivity_forNegativeDeviation() {
        val result = detect(autosensDataStore(constantSeries(48, -0.5)))
        assertThat(result.ratio).isWithin(0.001).of(0.88)
        assertThat(result.sensResult).contains("sensitivity")
    }

    @Test
    fun detect_normal_forZeroDeviation() {
        val result = detect(autosensDataStore(constantSeries(48, 0.0)))
        assertThat(result.ratio).isWithin(0.001).of(1.0)
        assertThat(result.sensResult).contains("normal")
    }

    @Test
    fun detect_positiveDeviationIgnored_whenBgBelow80() {
        val result = detect(autosensDataStore(constantSeries(48, 0.5, bg = 70.0)))
        assertThat(result.ratio).isWithin(0.001).of(1.0)
        assertThat(result.sensResult).contains("normal")
    }

    @Test
    fun detect_ignoresDataOlderThanWindow() {
        // window shrunk to 4 h; the older, opposite-sign band must not affect the ratio. Guards the
        // windowed scan start: an index that skipped in-window points would drop below full autosens.
        whenever(preferences.get(IntKey.AutosensPeriod)).thenReturn(4)
        val recent = constantSeries(48, 0.5) // last ~4 h -> resistance, full autosens -> 1.12
        val older = (48 until 96).map {
            FakeAutosensData(time = toTime - T.mins((it * 5).toLong()).msecs(), deviation = -2.0, sens = sens, cob = cob)
        }
        val result = detect(autosensDataStore(recent + older))
        assertThat(result.ratio).isWithin(0.001).of(1.12)
        assertThat(result.sensResult).contains("resistance")
    }

    @Test
    fun detect_tagsSiteChange() {
        // place the event on the oldest point so valid data still remains after the reset (weights > 0)
        val series = constantSeries(48, 0.5)
        val siteChange = TE(timestamp = series.last().time, type = TE.Type.CANNULA_CHANGE, glucoseUnit = GlucoseUnit.MGDL)
        val result = detect(autosensDataStore(series), siteChanges = listOf(siteChange))
        assertThat(result.pastSensitivity).contains("(SITECHANGE)")
    }

    @Test
    fun detect_tagsProfileSwitch() {
        val series = constantSeries(48, 0.5)
        val ps = profileSwitch.copy(timestamp = series.last().time)
        val result = detect(autosensDataStore(series), profileSwitches = listOf(ps))
        assertThat(result.pastSensitivity).contains("(PROFILESWITCH)")
    }

    // other overrides --------------------------------------------------------------------------

    @Test
    fun maxAbsorptionHours_readsPreference() {
        whenever(preferences.get(DoubleKey.AbsorptionMaxTime)).thenReturn(7.0)
        assertThat(sut.maxAbsorptionHours()).isWithin(0.001).of(7.0)
    }

    @Test
    fun hasPreferences_isAlwaysTrue() {
        assertThat(sut.hasPreferences()).isTrue()
    }

    @Test
    fun preferenceScreenContent_isNull_whenAapsPluginMissing() {
        whenever(activePlugin.getPluginsList()).thenReturn(arrayListOf<PluginBase>())
        assertThat(sut.getPreferenceScreenContent()).isNull()
    }

    @Test
    fun specialShowInListCondition_falseForSmb() {
        val aps = mock<APS>()
        whenever(aps.algorithm).thenReturn(APSResult.Algorithm.SMB)
        whenever(activePlugin.activeAPS).thenReturn(aps)
        assertThat(sut.specialShowInListCondition()).isFalse()
    }
}
