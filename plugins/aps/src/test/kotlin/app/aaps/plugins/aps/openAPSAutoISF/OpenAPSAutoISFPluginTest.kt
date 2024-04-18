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

class OpenAPSAutoISFPluginTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Mock lateinit var determineBasalSMB: DetermineBasalAutoISF
    @Mock lateinit var sharedPrefs: SharedPreferences
    @Mock lateinit var bgQualityCheck: BgQualityCheck
    @Mock lateinit var tddCalculator: TddCalculator
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
        val smb_delivery_ratio = 0.3        //preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatio)
        val smb_delivery_ratio_min = 0.4    //preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioMin)
        val smb_delivery_ratio_max = 0.6    //preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioMax)
        val smb_delivery_ratio_bg_range = preferences.get(UnitDoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange)
        val smbMaxRangeExtension = 2.0      //preferences.get(DoubleKey.ApsAutoIsfSmbMaxRangeExtension)
        val oapsProfile = OapsProfile(
            dia = 0.0, // not used
            min_5m_carbimpact = 0.0, // not used
            max_iob = 8.0, //constraintsChecker.getMaxIOBAllowed().also { inputConstraints.copyReasons(it) }.value(),
            max_daily_basal = 0.4, //profile.getMaxDailyBasal(),
            max_basal = 0.4, //constraintsChecker.getMaxBasalAllowed(profile).also { inputConstraints.copyReasons(it) }.value(),
            min_bg = 90.0,
            max_bg = 90.0,
            target_bg = 90.0,
            carb_ratio = 10.0, //profile.getIc(),
            sens = 100.0, //sens,
            autosens_adjust_targets = false, // not used
            max_daily_safety_multiplier = preferences.get(DoubleKey.ApsMaxDailyMultiplier),
            current_basal_safety_multiplier = preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier),
            lgsThreshold = profileUtil.convertToMgdlDetect(preferences.get(UnitDoubleKey.ApsLgsThreshold)).toInt(),
            high_temptarget_raises_sensitivity = preferences.get(BooleanKey.ApsAutoIsfHighTtRaisesSens), //exerciseMode || highTemptargetRaisesSensitivity, //was false,
            low_temptarget_lowers_sensitivity = preferences.get(BooleanKey.ApsAutoIsfLowTtLowersSens), // was false,
            sensitivity_raises_target = preferences.get(BooleanKey.ApsSensitivityRaisesTarget),
            resistance_lowers_target = preferences.get(BooleanKey.ApsResistanceLowersTarget),
            adv_target_adjustments = SMBDefaults.adv_target_adjustments,
            exercise_mode = SMBDefaults.exercise_mode,
            half_basal_exercise_target = preferences.get(IntKey.ApsAutoIsfHalfBasalExerciseTarget),
            maxCOB = SMBDefaults.maxCOB,
            skip_neutral_temps = false,  //pump.setNeutralTempAtFullHour(),
            remainingCarbsCap = SMBDefaults.remainingCarbsCap,
            enableUAM = true, //constraintsChecker.isUAMEnabled().also { inputConstraints.copyReasons(it) }.value(),
            A52_risk_enable = SMBDefaults.A52_risk_enable,
            SMBInterval = preferences.get(IntKey.ApsMaxSmbFrequency),
            enableSMB_with_COB = preferences.get(BooleanKey.ApsUseSmbWithCob), //smbEnabled &&
            enableSMB_with_temptarget = preferences.get(BooleanKey.ApsUseSmbWithLowTt), //smbEnabled &&
            allowSMB_with_high_temptarget = preferences.get(BooleanKey.ApsUseSmbWithHighTt), //smbEnabled &&
            enableSMB_always = preferences.get(BooleanKey.ApsUseSmbAlways), //smbEnabled &&  && advancedFiltering,
            enableSMB_after_carbs = preferences.get(BooleanKey.ApsUseSmbAfterCarbs), //smbEnabled &&  && advancedFiltering,
            maxSMBBasalMinutes = preferences.get(IntKey.ApsMaxMinutesOfBasalToLimitSmb),
            maxUAMSMBBasalMinutes = preferences.get(IntKey.ApsUamMaxMinutesOfBasalToLimitSmb),
            bolus_increment = 0.1, //pump.pumpDescription.bolusStep,
            carbsReqThreshold = preferences.get(IntKey.ApsCarbsRequestThreshold),
            current_basal = activePlugin.activePump.baseBasalRate,
            temptargetSet = false,
            autosens_max = preferences.get(DoubleKey.AutosensMax),
            out_units = if (profileFunction.getUnits() == GlucoseUnit.MMOL) "mmol/L" else "mg/dl",
            variable_sens = 100.0, //variableSensitivity,
            insulinDivisor = 0,
            TDD = 0.0               // TODO complete with AutoISF dedicated parameters
        )
        assertThat(openAPSAutoISFPlugin.determine_varSMBratio(oapsProfile,100, 90.0, "fullLoop")).isEqualTo(0.5)
    }
}
