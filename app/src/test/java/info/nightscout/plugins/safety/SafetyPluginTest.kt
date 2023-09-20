package info.nightscout.plugins.safety

import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.core.constraints.ConstraintObject
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.ApsMode
import info.nightscout.interfaces.bgQualityCheck.BgQualityCheck
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.ConstraintsChecker
import info.nightscout.interfaces.iob.GlucoseStatusProvider
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profiling.Profiler
import info.nightscout.interfaces.pump.defs.PumpDescription
import info.nightscout.interfaces.stats.TddCalculator
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.plugins.aps.openAPSAMA.OpenAPSAMAPlugin
import info.nightscout.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import info.nightscout.plugins.constraints.safety.SafetyPlugin
import info.nightscout.pump.virtual.VirtualPumpPlugin
import info.nightscout.sharedtests.TestBaseWithProfile
import info.nightscout.source.GlimpPlugin
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class SafetyPluginTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Mock lateinit var glimpPlugin: GlimpPlugin
    @Mock lateinit var profiler: Profiler
    @Mock lateinit var repository: AppRepository
    @Mock lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Mock lateinit var bgQualityCheck: BgQualityCheck
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var tddCalculator: TddCalculator

    private lateinit var safetyPlugin: SafetyPlugin
    private lateinit var openAPSAMAPlugin: OpenAPSAMAPlugin
    private lateinit var openAPSSMBPlugin: OpenAPSSMBPlugin

    private val injector = HasAndroidInjector {
        AndroidInjector {
        }
    }
    private val pumpDescription = PumpDescription()

    @BeforeEach
    fun prepare() {
        `when`(rh.gs(info.nightscout.plugins.constraints.R.string.hardlimit)).thenReturn("hard limit")
        `when`(rh.gs(info.nightscout.core.ui.R.string.itmustbepositivevalue)).thenReturn("it must be positive value")
        `when`(rh.gs(info.nightscout.core.ui.R.string.pumplimit)).thenReturn("pump limit")
        `when`(rh.gs(info.nightscout.plugins.constraints.R.string.maxvalueinpreferences)).thenReturn("max value in preferences")
        `when`(rh.gs(info.nightscout.plugins.aps.R.string.max_daily_basal_multiplier)).thenReturn("max daily basal multiplier")
        `when`(rh.gs(info.nightscout.plugins.aps.R.string.max_basal_multiplier)).thenReturn("max basal multiplier")
        `when`(rh.gs(info.nightscout.core.ui.R.string.limitingbolus)).thenReturn("Limiting bolus to %1\$.1f U because of %2\$s")
        `when`(rh.gs(info.nightscout.core.ui.R.string.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        `when`(rh.gs(info.nightscout.core.ui.R.string.limiting_iob)).thenReturn("Limiting IOB to %1\$.1f U because of %2\$s")
        `when`(rh.gs(info.nightscout.plugins.constraints.R.string.limitingcarbs)).thenReturn("Limiting carbs to %1\$d g because of %2\$s")
        `when`(rh.gs(info.nightscout.core.ui.R.string.limitingpercentrate)).thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")
        `when`(rh.gs(info.nightscout.plugins.constraints.R.string.pumpisnottempbasalcapable)).thenReturn("Pump is not temp basal capable")
        `when`(rh.gs(info.nightscout.plugins.aps.R.string.increasing_max_basal)).thenReturn("Increasing max basal value because setting is lower than your max basal in profile")
        `when`(rh.gs(info.nightscout.plugins.aps.R.string.smb_disabled_in_preferences)).thenReturn("SMB disabled in preferences")
        `when`(rh.gs(info.nightscout.plugins.constraints.R.string.closedmodedisabledinpreferences)).thenReturn("Closed loop mode disabled in preferences")
        `when`(rh.gs(info.nightscout.plugins.constraints.R.string.closed_loop_disabled_on_dev_branch)).thenReturn("Running dev version. Closed loop is disabled.")
        `when`(rh.gs(info.nightscout.plugins.constraints.R.string.smbalwaysdisabled)).thenReturn("SMB always and after carbs disabled because active BG source doesn\\'t support advanced filtering")
        `when`(rh.gs(info.nightscout.plugins.constraints.R.string.smbnotallowedinopenloopmode)).thenReturn("SMB not allowed in open loop mode")
        `when`(rh.gs(info.nightscout.core.utils.R.string.key_child)).thenReturn("child")
        `when`(rh.gs(info.nightscout.core.ui.R.string.lowglucosesuspend)).thenReturn("Low Glucose Suspend")

        `when`(activePlugin.activePump).thenReturn(virtualPumpPlugin)
        `when`(virtualPumpPlugin.pumpDescription).thenReturn(pumpDescription)
        `when`(config.APS).thenReturn(true)
        safetyPlugin = SafetyPlugin(injector, aapsLogger, rh, sp, constraintChecker, activePlugin, hardLimits, config, iobCobCalculator, dateUtil, uiInteraction, decimalFormatter)
        openAPSAMAPlugin = OpenAPSAMAPlugin(
            injector, aapsLogger, rxBus, constraintChecker, rh, profileFunction, context, activePlugin, iobCobCalculator, hardLimits, profiler, fabricPrivacy,
            dateUtil, repository, glucoseStatusProvider, sp
        )
        openAPSSMBPlugin = OpenAPSSMBPlugin(
            injector, aapsLogger, rxBus, constraintChecker, rh, profileFunction, context, activePlugin, iobCobCalculator, hardLimits, profiler, sp,
            dateUtil, repository, glucoseStatusProvider, bgQualityCheck, tddCalculator
        )
    }

    @Test
    fun pumpDescriptionShouldLimitLoopInvocation() {
        pumpDescription.isTempBasalCapable = false
        val c = safetyPlugin.isLoopInvocationAllowed(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).isEqualTo("Safety: Pump is not temp basal capable")
        assertThat(c.value()).isFalse()
    }

    @Test
    fun disabledEngineeringModeShouldLimitClosedLoop() {
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name)).thenReturn(ApsMode.CLOSED.name)
        `when`(config.isEngineeringModeOrRelease()).thenReturn(false)
        val c = safetyPlugin.isClosedLoopAllowed(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).contains("Running dev version. Closed loop is disabled.")
        assertThat(c.value()).isFalse()
    }

    @Test
    fun setOpenLoopInPreferencesShouldLimitClosedLoop() {
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name)).thenReturn(ApsMode.OPEN.name)
        val c = safetyPlugin.isClosedLoopAllowed(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).contains("Closed loop mode disabled in preferences")
        assertThat(c.value()).isFalse()
    }

    @Test
    fun notEnabledSMBInPreferencesDisablesSMB() {
        `when`(sp.getBoolean(info.nightscout.plugins.aps.R.string.key_use_smb, false)).thenReturn(false)
        `when`(constraintChecker.isClosedLoopAllowed(anyObject())).thenReturn(ConstraintObject(true, aapsLogger))
        val c = openAPSSMBPlugin.isSMBModeEnabled(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).contains("SMB disabled in preferences")
        assertThat(c.value()).isFalse()
    }

    @Test
    fun openLoopPreventsSMB() {
        `when`(sp.getBoolean(info.nightscout.plugins.aps.R.string.key_use_smb, false)).thenReturn(true)
        `when`(constraintChecker.isClosedLoopAllowed()).thenReturn(ConstraintObject(false, aapsLogger))
        val c = safetyPlugin.isSMBModeEnabled(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).contains("SMB not allowed in open loop mode")
        assertThat(c.value()).isFalse()
    }

    @Test
    fun bgSourceShouldPreventSMBAlways() {
        `when`(activePlugin.activeBgSource).thenReturn(glimpPlugin)
        val c = safetyPlugin.isAdvancedFilteringEnabled(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).isEqualTo("Safety: SMB always and after carbs disabled because active BG source doesn\\'t support advanced filtering")
        assertThat(c.value()).isFalse()
    }

    @Test
    fun basalRateShouldBeLimited() {
        `when`(sp.getDouble(info.nightscout.plugins.aps.R.string.key_openapsma_max_basal, 1.0)).thenReturn(1.0)
        `when`(sp.getDouble(info.nightscout.plugins.aps.R.string.key_openapsama_current_basal_safety_multiplier, 4.0)).thenReturn(4.0)
        `when`(sp.getDouble(info.nightscout.plugins.aps.R.string.key_openapsama_max_daily_safety_multiplier, 3.0)).thenReturn(3.0)
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_age, "")).thenReturn("child")
        val c = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        safetyPlugin.applyBasalConstraints(c, validProfile)
        assertThat(c.value()).isWithin(0.01).of(2.0)
        assertThat(c.getReasons()).isEqualTo(
            """
    Safety: Limiting max basal rate to 2.00 U/h because of hard limit
    """.trimIndent()
        )
        assertThat(c.getMostLimitedReasons()).isEqualTo("Safety: Limiting max basal rate to 2.00 U/h because of hard limit")
    }

    @Test
    fun doNotAllowNegativeBasalRate() {
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_age, "")).thenReturn("child")
        val d = ConstraintObject(-0.5, aapsLogger)
        safetyPlugin.applyBasalConstraints(d, validProfile)
        assertThat(d.value()).isWithin(0.01).of(0.0)
        assertThat(d.getReasons()).isEqualTo(
            "Safety: Limiting max basal rate to 0.00 U/h because of it must be positive value"
        )
    }

    @Test
    fun percentBasalRateShouldBeLimited() {
        // No limit by default
        `when`(sp.getDouble(info.nightscout.plugins.aps.R.string.key_openapsma_max_basal, 1.0)).thenReturn(1.0)
        `when`(sp.getDouble(info.nightscout.plugins.aps.R.string.key_openapsama_current_basal_safety_multiplier, 4.0)).thenReturn(4.0)
        `when`(sp.getDouble(info.nightscout.plugins.aps.R.string.key_openapsama_max_daily_safety_multiplier, 3.0)).thenReturn(3.0)
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_age, "")).thenReturn("child")
        val i = ConstraintObject(Int.MAX_VALUE, aapsLogger)
        safetyPlugin.applyBasalPercentConstraints(i, validProfile)
        assertThat(i.value()).isEqualTo(200)
        assertThat(i.getReasons()).isEqualTo(
            """
Safety: Percent rate 2147483647% recalculated to 21474836.47 U/h with current basal 1.00 U/h
Safety: Limiting max basal rate to 2.00 U/h because of hard limit
Safety: Limiting max percent rate to 200% because of pump limit
Safety: Limiting max basal rate to 500.00 U/h because of pump limit
    """.trimIndent()
        )
        assertThat(i.getMostLimitedReasons()).isEqualTo(
            "Safety: Limiting max percent rate to 200% because of pump limit"
        )
    }

    @Test
    fun percentBasalShouldBeLimitedBySMB() {
        // No limit by default
        `when`(sp.getDouble(info.nightscout.plugins.aps.R.string.key_openapsma_max_basal, 1.0)).thenReturn(1.0)
        `when`(sp.getDouble(info.nightscout.plugins.aps.R.string.key_openapsama_current_basal_safety_multiplier, 4.0)).thenReturn(4.0)
        `when`(sp.getDouble(info.nightscout.plugins.aps.R.string.key_openapsama_max_daily_safety_multiplier, 3.0)).thenReturn(3.0)
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_age, "")).thenReturn("child")
        openAPSSMBPlugin.setPluginEnabled(PluginType.APS, true)
        val i = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        openAPSSMBPlugin.applyBasalConstraints(i, validProfile)
        assertThat(i.value()).isWithin(0.01).of(1.0)
        assertThat(i.getReasons()).isEqualTo(
            """
            OpenAPSSMB: Limiting max basal rate to 1.00 U/h because of max value in preferences
            OpenAPSSMB: Limiting max basal rate to 4.00 U/h because of max basal multiplier
            OpenAPSSMB: Limiting max basal rate to 3.00 U/h because of max daily basal multiplier
            """.trimIndent()
        )
        assertThat(i.getMostLimitedReasons()).isEqualTo("OpenAPSSMB: Limiting max basal rate to 1.00 U/h because of max value in preferences")
    }

    @Test
    fun doNotAllowNegativePercentBasalRate() {
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_age, "")).thenReturn("child")
        val i = ConstraintObject(-22, aapsLogger)
        safetyPlugin.applyBasalPercentConstraints(i, validProfile)
        assertThat(i.value()).isEqualTo(0)
        assertThat(i.getReasons()).isEqualTo(
            """
    Safety: Percent rate -22% recalculated to -0.22 U/h with current basal 1.00 U/h
    Safety: Limiting max basal rate to 0.00 U/h because of it must be positive value
    Safety: Limiting max percent rate to 0% because of pump limit
    """.trimIndent()
        )
        assertThat(i.getMostLimitedReasons()).isEqualTo("Safety: Limiting max percent rate to 0% because of pump limit")
    }

    @Test
    fun bolusAmountShouldBeLimited() {
        `when`(sp.getDouble(info.nightscout.core.utils.R.string.key_treatmentssafety_maxbolus, 3.0)).thenReturn(3.0)
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_age, "")).thenReturn("child")
        val d = safetyPlugin.applyBolusConstraints(ConstraintObject(Double.MAX_VALUE, aapsLogger))
        assertThat(d.value()).isWithin(0.01).of(3.0)
        assertThat(d.getReasons()).isEqualTo(
            """
    Safety: Limiting bolus to 3.0 U because of max value in preferences
    Safety: Limiting bolus to 5.0 U because of hard limit
    """.trimIndent()
        )
        assertThat(d.getMostLimitedReasons()).isEqualTo("Safety: Limiting bolus to 3.0 U because of max value in preferences")
    }

    @Test
    fun doNotAllowNegativeBolusAmount() {
        `when`(sp.getDouble(info.nightscout.core.utils.R.string.key_treatmentssafety_maxbolus, 3.0)).thenReturn(3.0)
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_age, "")).thenReturn("child")
        val d = safetyPlugin.applyBolusConstraints(ConstraintObject(-22.0, aapsLogger))
        assertThat(d.value()).isWithin(0.01).of(0.0)
        assertThat(d.getReasons()).isEqualTo("Safety: Limiting bolus to 0.0 U because of it must be positive value")
        assertThat(d.getMostLimitedReasons()).isEqualTo("Safety: Limiting bolus to 0.0 U because of it must be positive value")
    }

    @Test
    fun carbsAmountShouldBeLimited() {
        // No limit by default
        `when`(sp.getInt(info.nightscout.core.utils.R.string.key_treatmentssafety_maxcarbs, 48)).thenReturn(48)

        // Negative carbs not allowed
        var i: Constraint<Int> = ConstraintObject(-22, aapsLogger)
        safetyPlugin.applyCarbsConstraints(i)
        assertThat(i.value()).isEqualTo(0)
        assertThat(i.getReasons()).isEqualTo("Safety: Limiting carbs to 0 g because of it must be positive value")

        // Apply all limits
        i = safetyPlugin.applyCarbsConstraints(ConstraintObject(Int.MAX_VALUE, aapsLogger))
        assertThat(i.value()).isEqualTo(48)
        assertThat(i.getReasons()).isEqualTo("Safety: Limiting carbs to 48 g because of max value in preferences")
    }

    @Test
    fun iobShouldBeLimited() {
        openAPSAMAPlugin.setPluginEnabled(PluginType.APS, true)
        openAPSSMBPlugin.setPluginEnabled(PluginType.APS, true)
        //`when`(openAPSSMBPlugin.isEnabled()).thenReturn(true)
        //`when`(openAPSAMAPlugin.isEnabled()).thenReturn(false)
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name)).thenReturn(ApsMode.LGS.name)
        `when`(sp.getDouble(info.nightscout.plugins.aps.R.string.key_openapsma_max_iob, 1.5)).thenReturn(1.5)
        `when`(sp.getDouble(info.nightscout.plugins.aps.R.string.key_openapssmb_max_iob, 3.0)).thenReturn(3.0)
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_age, "")).thenReturn("teenage")

        // Apply all limits
        var d: Constraint<Double> = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        d = safetyPlugin.applyMaxIOBConstraints(d)
        assertThat(d.value()).isWithin(0.01).of(HardLimits.MAX_IOB_LGS)
        assertThat(d.getReasons()).isEqualTo("Safety: Limiting IOB to 0.0 U because of Low Glucose Suspend")
        assertThat(d.getMostLimitedReasons()).isEqualTo("Safety: Limiting IOB to 0.0 U because of Low Glucose Suspend")

        // Apply all limits
        d = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        val a = openAPSAMAPlugin.applyMaxIOBConstraints(d)
        assertThat(a.value()).isWithin(0.01).of(1.5)
        assertThat(d.getReasons()).isEqualTo("OpenAPSAMA: Limiting IOB to 1.5 U because of max value in preferences\nOpenAPSAMA: Limiting IOB to 7.0 U because of hard limit")
        assertThat(d.getMostLimitedReasons()).isEqualTo("OpenAPSAMA: Limiting IOB to 1.5 U because of max value in preferences")

        // Apply all limits
        d = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        val s = openAPSSMBPlugin.applyMaxIOBConstraints(d)
        assertThat(s.value()).isWithin(0.01).of(3.0)
        assertThat(d.getReasons()).isEqualTo("OpenAPSSMB: Limiting IOB to 3.0 U because of max value in preferences\nOpenAPSSMB: Limiting IOB to 22.0 U because of hard limit")
        assertThat(d.getMostLimitedReasons()).isEqualTo("OpenAPSSMB: Limiting IOB to 3.0 U because of max value in preferences")
    }
}
