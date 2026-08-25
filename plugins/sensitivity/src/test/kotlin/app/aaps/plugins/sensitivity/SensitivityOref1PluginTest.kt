package app.aaps.plugins.sensitivity

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.Sensitivity
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.keys.DoubleKey
import app.aaps.core.objects.constraints.ConstraintObject
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

class SensitivityOref1PluginTest : SensitivityTestBase() {

    private lateinit var sut: SensitivityOref1Plugin

    @BeforeEach fun prepare() {
        sut = SensitivityOref1Plugin(aapsLogger, rh, preferences, dateUtil)
    }

    private fun detect(
        ads: AutosensDataStore,
        profile: EffectiveProfile? = effectiveProfile,
        siteChanges: List<TE> = emptyList(),
        profileSwitches: List<PS> = emptyList()
    ) = sut.detectSensitivity(ads, fromTime, toTime, profile, siteChanges, profileSwitches)

    @Test
    fun isMinCarbsAbsorptionDynamic() {
        assertThat(sut.isMinCarbsAbsorptionDynamic).isFalse()
    }

    @Test
    fun isOref1() {
        assertThat(sut.isOref1).isTrue()
    }

    @Test
    fun getId() {
        assertThat(sut.id).isEqualTo(Sensitivity.SensitivityType.SENSITIVITY_OREF1)
    }

    // detectSensitivity - guard branches -------------------------------------------------------

    @Test
    fun detect_returnsDefault_whenNoProfile() {
        val result = detect(autosensDataStore(constantSeries(48, 0.5)), profile = null)
        assertThat(result.ratio).isWithin(0.001).of(1.0)
        assertThat(result.sensResult).isEqualTo("autosens not available")
    }

    @Test
    fun detect_returnsDefault_whenNotEnoughData() {
        val result = detect(autosensDataStore(constantSeries(3, 0.5)))
        assertThat(result.ratio).isWithin(0.001).of(1.0)
    }

    @Test
    fun detect_returnsDefault_whenNoCurrentData() {
        val result = detect(autosensDataStore(constantSeries(48, 0.5), current = null))
        assertThat(result.ratio).isWithin(0.001).of(1.0)
    }

    // detectSensitivity - direction & exact ratio ----------------------------------------------
    // A full 24 h of data (288 points) fills both the 8 h and 24 h buckets so no zero padding is
    // added and the median equals the (constant) deviation.

    @Test
    fun detect_resistance_forPositiveDeviation() {
        val result = detect(autosensDataStore(constantSeries(288, 0.5)))
        assertThat(result.ratio).isWithin(0.001).of(1.12)
        assertThat(result.sensResult).contains("resistance")
        assertThat(result.sensResult).contains("(24 hours)")
        assertThat(result.carbsAbsorbed).isWithin(0.001).of(cob)
    }

    @Test
    fun detect_sensitivity_forNegativeDeviation() {
        val result = detect(autosensDataStore(constantSeries(288, -0.5)))
        assertThat(result.ratio).isWithin(0.001).of(0.88)
        assertThat(result.sensResult).contains("sensitivity")
    }

    @Test
    fun detect_uses8hWindow_whenItGivesLowerRatio() {
        // last 8 h strongly sensitive, older data resistant -> 8 h ratio is lower -> 8 h wins
        val recent = (0 until 96).map {
            FakeAutosensData(time = toTime - T.mins((it * 5).toLong()).msecs(), deviation = -1.0, sens = sens, cob = cob)
        }
        val older = (96 until 288).map {
            FakeAutosensData(time = toTime - T.mins((it * 5).toLong()).msecs(), deviation = 1.0, sens = sens, cob = cob)
        }
        val result = detect(autosensDataStore(recent + older))
        assertThat(result.sensResult).contains("(8 hours)")
        assertThat(result.sensResult).contains("sensitivity")
        assertThat(result.ratio).isWithin(0.01).of(0.76)
    }

    @Test
    fun detect_smallSampleIsDampenedTowardsNormal() {
        // a strong resistance signal from only 8 points is padded with zeros -> median 0 -> ratio 1.0
        val result = detect(autosensDataStore(constantSeries(8, 1.0)))
        assertThat(result.ratio).isWithin(0.001).of(1.0)
        assertThat(result.sensResult).contains("normal")
    }

    @Test
    fun detect_tagsSiteChange() {
        val siteChange = TE(timestamp = toTime, type = TE.Type.CANNULA_CHANGE, glucoseUnit = GlucoseUnit.MGDL)
        val result = detect(autosensDataStore(constantSeries(48, 0.5)), siteChanges = listOf(siteChange))
        assertThat(result.pastSensitivity).contains("(SITECHANGE)")
    }

    @Test
    fun detect_tagsProfileSwitch() {
        // inherited profileSwitch has timestamp == now == newest data point and duration 0
        val result = detect(autosensDataStore(constantSeries(48, 0.5)), profileSwitches = listOf(profileSwitch))
        assertThat(result.pastSensitivity).contains("(PROFILESWITCH)")
    }

    // other overrides --------------------------------------------------------------------------

    @Test
    fun maxAbsorptionHours_readsPreference() {
        whenever(preferences.get(DoubleKey.AbsorptionCutOff)).thenReturn(5.0)
        assertThat(sut.maxAbsorptionHours()).isWithin(0.001).of(5.0)
    }

    @Test
    fun isUAMEnabled_deniedWhenPluginNotSelected() {
        whenever(rh.gs(R.string.uam_disabled_oref1_not_selected)).thenReturn("UAM disabled")
        val constraint = ConstraintObject(true, aapsLogger)
        sut.isUAMEnabled(constraint)
        assertThat(constraint.value()).isFalse()
    }
}
