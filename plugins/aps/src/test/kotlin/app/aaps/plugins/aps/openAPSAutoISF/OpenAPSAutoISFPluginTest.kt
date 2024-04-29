package app.aaps.plugins.aps.openAPSAutoISF

import android.content.SharedPreferences
import app.aaps.core.data.aps.SMBDefaults
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.aps.OapsProfile
import app.aaps.core.keys.AdaptiveIntentPreference
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.validators.AdaptiveDoublePreference
import app.aaps.core.validators.AdaptiveIntPreference
import app.aaps.core.validators.AdaptiveSwitchPreference
import app.aaps.core.validators.AdaptiveUnitPreference
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class OpenAPSAutoISFPluginTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Mock lateinit var determineBasalSMB: DetermineBasalAutoISF
    @Mock lateinit var sharedPrefs: SharedPreferences
    @Mock lateinit var bgQualityCheck: BgQualityCheck
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var profiler: Profiler
    private lateinit var openAPSAutoISFPlugin: OpenAPSAutoISFPlugin

    init {
        addInjector {
            if (it is AdaptiveDoublePreference) {
                it.profileUtil = profileUtil
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
            }
            if (it is AdaptiveIntPreference) {
                it.profileUtil = profileUtil
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
                it.config = config
            }
            if (it is AdaptiveIntentPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
            }
            if (it is AdaptiveUnitPreference) {
                it.profileUtil = profileUtil
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
            }
            if (it is AdaptiveSwitchPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
                it.config = config
            }
        }
    }

    @BeforeEach fun prepare() {
        openAPSAutoISFPlugin = OpenAPSAutoISFPlugin(
            injector, aapsLogger, rxBus, constraintChecker, rh, profileFunction, profileUtil, config, activePlugin,
            iobCobCalculator, hardLimits, preferences, dateUtil, processedTbrEbData, persistenceLayer, glucoseStatusProvider,
            bgQualityCheck, uiInteraction, determineBasalSMB, profiler
        )
    }

    @Test
    fun specialEnableConditionTest() {
        assertThat(openAPSAutoISFPlugin.specialEnableCondition()).isTrue()
    }

    @Test
    fun specialShowInListConditionTest() {
        assertThat(openAPSAutoISFPlugin.specialShowInListCondition()).isTrue()
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        openAPSAutoISFPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }

    @Test
    fun withinISFlimitsTest() {
        var autoIsfMin = 0.7
        var autoIsfMax = 1.2
        var sens = 1.1  // from Autosens
        val origin_sens = ""
        var ttSet = false
        var exerciseMode = false
        var targetBg = 120.0
        val normalTarget = 100
        assertThat(openAPSAutoISFPlugin.withinISFlimits(1.7, autoIsfMin, autoIsfMax, sens, origin_sens, ttSet, exerciseMode, targetBg, normalTarget)).isEqualTo(1.2) // upper limit
        assertThat(openAPSAutoISFPlugin.withinISFlimits(0.5, autoIsfMin, autoIsfMax, sens, origin_sens, ttSet, exerciseMode, targetBg, normalTarget)).isEqualTo(0.7) // lower limit
        sens = 1.5  // from Autosens
        assertThat(openAPSAutoISFPlugin.withinISFlimits(1.7, autoIsfMin, autoIsfMax, sens, origin_sens, ttSet, exerciseMode, targetBg, normalTarget)).isEqualTo(1.5) // autosens 1.5 wins
        sens = 0.5  // from Autosens
        assertThat(openAPSAutoISFPlugin.withinISFlimits(0.5, autoIsfMin, autoIsfMax, sens, origin_sens, ttSet, exerciseMode, targetBg, normalTarget)).isEqualTo(0.5) // autosens 0.5 wins
        exerciseMode = true
        ttSet = true
        assertThat(openAPSAutoISFPlugin.withinISFlimits(0.5, autoIsfMin, autoIsfMax, sens, origin_sens, ttSet, exerciseMode, targetBg, normalTarget)).isEqualTo(0.35) // exercise mode
    }

    @Test
    fun determine_varSMBratioTest() {
        `when`(preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatio)).thenReturn(0.3)
        `when`(preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioMin)).thenReturn(0.4)
        `when`(preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioMax)).thenReturn(0.6)
        `when`(preferences.get(UnitDoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange)).thenReturn(20.0)
        `when`(preferences.get(DoubleKey.ApsAutoIsfSmbMaxRangeExtension)).thenReturn(1.0)

        assertThat(openAPSAutoISFPlugin.determine_varSMBratio(100, 90.0, "fullLoop")).isEqualTo(0.5)
    }
}
