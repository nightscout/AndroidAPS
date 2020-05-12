package info.nightscout.androidaps.plugins.constraints.safety

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.androidaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin
import info.nightscout.androidaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin
import info.nightscout.androidaps.plugins.source.GlimpPlugin
import info.nightscout.androidaps.utils.HardLimits
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(ConstraintChecker::class, BuildHelper::class, VirtualPumpPlugin::class, GlimpPlugin::class)
class SafetyPluginTest : TestBaseWithProfile() {

    @Mock lateinit var sp: SP
    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var openAPSAMAPlugin: OpenAPSAMAPlugin
    @Mock lateinit var openAPSSMBPlugin: OpenAPSSMBPlugin
    @Mock lateinit var sensitivityOref1Plugin: SensitivityOref1Plugin
    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var buildHelper: BuildHelper
    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Mock lateinit var glimpPlugin: GlimpPlugin
    @Mock lateinit var context: Context
    @Mock lateinit var nsUpload: NSUpload

    private lateinit var hardLimits: HardLimits
    private lateinit var safetyPlugin: SafetyPlugin

    val injector = HasAndroidInjector { AndroidInjector { } }
    val pumpDescription = PumpDescription()

    @Before
    fun prepare() {
        `when`(resourceHelper.gs(R.string.limitingbolus)).thenReturn("Limiting bolus to %1\$.1f U because of %2\$s")
        `when`(resourceHelper.gs(R.string.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        `when`(resourceHelper.gs(R.string.limitingiob)).thenReturn("Limiting IOB to %1\$.1f U because of %2\$s")
        `when`(resourceHelper.gs(R.string.limitingcarbs)).thenReturn("Limiting carbs to %1\$d g because of %2\$s")
        `when`(resourceHelper.gs(R.string.limitingpercentrate)).thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")
        `when`(resourceHelper.gs(R.string.pumpisnottempbasalcapable)).thenReturn("Pump is not temp basal capable")
        `when`(resourceHelper.gs(R.string.increasingmaxbasal)).thenReturn("Increasing max basal value because setting is lower than your max basal in profile")
        `when`(resourceHelper.gs(R.string.smbdisabledinpreferences)).thenReturn("SMB disabled in preferences")
        `when`(resourceHelper.gs(R.string.closedmodedisabledinpreferences)).thenReturn("Closed loop mode disabled in preferences")
        `when`(resourceHelper.gs(R.string.closed_loop_disabled_on_dev_branch)).thenReturn("Running dev version. Closed loop is disabled.")
        `when`(resourceHelper.gs(R.string.itmustbepositivevalue)).thenReturn("it must be positive value")
        `when`(resourceHelper.gs(R.string.pumplimit)).thenReturn("pump limit")
        `when`(resourceHelper.gs(R.string.smbalwaysdisabled)).thenReturn("SMB always and after carbs disabled because active BG source doesn\\'t support advanced filtering")
        `when`(resourceHelper.gs(R.string.smbnotallowedinopenloopmode)).thenReturn("SMB not allowed in open loop mode")
        `when`(resourceHelper.gs(R.string.maxvalueinpreferences)).thenReturn("max value in preferences")
        `when`(resourceHelper.gs(R.string.maxbasalmultiplier)).thenReturn("max basal multiplier")
        `when`(resourceHelper.gs(R.string.maxdailybasalmultiplier)).thenReturn("max daily basal multiplier")
        `when`(resourceHelper.gs(R.string.hardlimit)).thenReturn("hard limit")
        `when`(resourceHelper.gs(R.string.key_child)).thenReturn("child")

        `when`(activePlugin.activePump).thenReturn(virtualPumpPlugin)
        `when`(virtualPumpPlugin.pumpDescription).thenReturn(pumpDescription)
        hardLimits = HardLimits(aapsLogger, rxBus, sp, resourceHelper, context, nsUpload)
        safetyPlugin = SafetyPlugin(injector, aapsLogger, resourceHelper, sp, rxBus, constraintChecker, openAPSAMAPlugin, openAPSSMBPlugin, sensitivityOref1Plugin, activePlugin, hardLimits, buildHelper, treatmentsPlugin, Config())
    }

    @Test fun pumpDescriptionShouldLimitLoopInvocation() {
        pumpDescription.isTempBasalCapable = false
        var c = Constraint(true)
        c = safetyPlugin.isLoopInvocationAllowed(c)
        Assert.assertEquals("Safety: Pump is not temp basal capable", c.getReasons(aapsLogger))
        Assert.assertEquals(false, c.value())
    }

    @Test fun disabledEngineeringModeShouldLimitClosedLoop() {
        `when`(sp.getString(R.string.key_aps_mode, "open")).thenReturn("closed")
        `when`(buildHelper.isEngineeringModeOrRelease()).thenReturn(false)
        var c = Constraint(true)
        c = safetyPlugin.isClosedLoopAllowed(c)
        Assert.assertTrue(c.getReasons(aapsLogger).contains("Running dev version. Closed loop is disabled."))
        Assert.assertEquals(false, c.value())
    }

    @Test fun setOpenLoopInPreferencesShouldLimitClosedLoop() {
        `when`(sp.getString(R.string.key_aps_mode, "open")).thenReturn("open")
        var c = Constraint(true)
        c = safetyPlugin.isClosedLoopAllowed(c)
        Assert.assertTrue(c.getReasons(aapsLogger).contains("Closed loop mode disabled in preferences"))
        Assert.assertEquals(false, c.value())
    }

    @Test fun notEnabledSMBInPreferencesDisablesSMB() {
        `when`(sp.getBoolean(R.string.key_use_smb, false)).thenReturn(false)
        `when`(constraintChecker.isClosedLoopAllowed()).thenReturn(Constraint(true))
        var c = Constraint(true)
        c = safetyPlugin.isSMBModeEnabled(c)
        Assert.assertTrue(c.getReasons(aapsLogger).contains("SMB disabled in preferences"))
        Assert.assertEquals(false, c.value())
    }

    @Test fun openLoopPreventsSMB() {
        `when`(sp.getBoolean(R.string.key_use_smb, false)).thenReturn(true)
        `when`(constraintChecker.isClosedLoopAllowed()).thenReturn(Constraint(false))
        var c = Constraint(true)
        c = safetyPlugin.isSMBModeEnabled(c)
        Assert.assertTrue(c.getReasons(aapsLogger).contains("SMB not allowed in open loop mode"))
        Assert.assertEquals(false, c.value())
    }

    @Test fun bgSourceShouldPreventSMBAlways() {
        `when`(activePlugin.activeBgSource).thenReturn(glimpPlugin)
        var c = Constraint(true)
        c = safetyPlugin.isAdvancedFilteringEnabled(c)
        Assert.assertEquals("Safety: SMB always and after carbs disabled because active BG source doesn\\'t support advanced filtering", c.getReasons(aapsLogger))
        Assert.assertEquals(false, c.value())
    }

    @Test fun basalRateShouldBeLimited() {
        `when`(sp.getDouble(R.string.key_openapsma_max_basal, 1.0)).thenReturn(1.0)
        `when`(sp.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4.0)).thenReturn(4.0)
        `when`(sp.getDouble(R.string.key_openapsama_max_daily_safety_multiplier, 3.0)).thenReturn(3.0)
        `when`(sp.getString(R.string.key_age, "")).thenReturn("child")
        val c = Constraint(Constants.REALLYHIGHBASALRATE)
        safetyPlugin.applyBasalConstraints(c, validProfile)
        Assert.assertEquals(1.0, c.value(), 0.01)
        Assert.assertEquals("""
    Safety: Limiting max basal rate to 1.00 U/h because of max value in preferences
    Safety: Limiting max basal rate to 4.00 U/h because of max basal multiplier
    Safety: Limiting max basal rate to 3.00 U/h because of max daily basal multiplier
    Safety: Limiting max basal rate to 2.00 U/h because of hard limit
    """.trimIndent(), c.getReasons(aapsLogger))
        Assert.assertEquals("Safety: Limiting max basal rate to 1.00 U/h because of max value in preferences", c.getMostLimitedReasons(aapsLogger))
    }

    @Test fun doNotAllowNegativeBasalRate() {
        `when`(sp.getString(R.string.key_age, "")).thenReturn("child")
        val d = Constraint(-0.5)
        safetyPlugin.applyBasalConstraints(d, validProfile)
        Assert.assertEquals(0.0, d.value(), 0.01)
        Assert.assertEquals("""
    Safety: Limiting max basal rate to 0.00 U/h because of it must be positive value
    Safety: Increasing max basal value because setting is lower than your max basal in profile
    """.trimIndent(), d.getReasons(aapsLogger))
    }

    @Test fun percentBasalRateShouldBeLimited() {
        // No limit by default
        `when`(sp.getDouble(R.string.key_openapsma_max_basal, 1.0)).thenReturn(1.0)
        `when`(sp.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4.0)).thenReturn(4.0)
        `when`(sp.getDouble(R.string.key_openapsama_max_daily_safety_multiplier, 3.0)).thenReturn(3.0)
        `when`(sp.getString(R.string.key_age, "")).thenReturn("child")
        val i = Constraint(Constants.REALLYHIGHPERCENTBASALRATE)
        safetyPlugin.applyBasalPercentConstraints(i, validProfile)
        Assert.assertEquals(100, i.value())
        Assert.assertEquals("""
    Safety: Percent rate 1111111% recalculated to 11111.11 U/h with current basal 1.00 U/h
    Safety: Limiting max basal rate to 1.00 U/h because of max value in preferences
    Safety: Limiting max basal rate to 4.00 U/h because of max basal multiplier
    Safety: Limiting max basal rate to 3.00 U/h because of max daily basal multiplier
    Safety: Limiting max basal rate to 2.00 U/h because of hard limit
    Safety: Limiting max percent rate to 100% because of pump limit
    Safety: Limiting max basal rate to 500.00 U/h because of pump limit
    """.trimIndent(), i.getReasons(aapsLogger))
        Assert.assertEquals("Safety: Limiting max percent rate to 100% because of pump limit", i.getMostLimitedReasons(aapsLogger))
    }

    @Test fun doNotAllowNegativePercentBasalRate() {
        `when`(sp.getString(R.string.key_age, "")).thenReturn("child")
        val i = Constraint(-22)
        safetyPlugin.applyBasalPercentConstraints(i, validProfile)
        Assert.assertEquals(0, i.value())
        Assert.assertEquals("""
    Safety: Percent rate -22% recalculated to -0.22 U/h with current basal 1.00 U/h
    Safety: Limiting max basal rate to 0.00 U/h because of it must be positive value
    Safety: Increasing max basal value because setting is lower than your max basal in profile
    Safety: Limiting max percent rate to 0% because of pump limit
    """.trimIndent(), i.getReasons(aapsLogger))
        Assert.assertEquals("Safety: Limiting max percent rate to 0% because of pump limit", i.getMostLimitedReasons(aapsLogger))
    }

    @Test fun bolusAmountShouldBeLimited() {
        `when`(sp.getDouble(R.string.key_treatmentssafety_maxbolus, 3.0)).thenReturn(3.0)
        `when`(sp.getString(R.string.key_age, "")).thenReturn("child")
        var d = Constraint(Constants.REALLYHIGHBOLUS)
        d = safetyPlugin.applyBolusConstraints(d)
        Assert.assertEquals(3.0, d.value()!!, 0.01)
        Assert.assertEquals("""
    Safety: Limiting bolus to 3.0 U because of max value in preferences
    Safety: Limiting bolus to 5.0 U because of hard limit
    """.trimIndent(), d.getReasons(aapsLogger))
        Assert.assertEquals("Safety: Limiting bolus to 3.0 U because of max value in preferences", d.getMostLimitedReasons(aapsLogger))
    }

    @Test fun doNotAllowNegativeBolusAmount() {
        `when`(sp.getDouble(R.string.key_treatmentssafety_maxbolus, 3.0)).thenReturn(3.0)
        `when`(sp.getString(R.string.key_age, "")).thenReturn("child")
        var d = Constraint(-22.0)
        d = safetyPlugin.applyBolusConstraints(d)
        Assert.assertEquals(0.0, d.value(), 0.01)
        Assert.assertEquals("Safety: Limiting bolus to 0.0 U because of it must be positive value", d.getReasons(aapsLogger))
        Assert.assertEquals("Safety: Limiting bolus to 0.0 U because of it must be positive value", d.getMostLimitedReasons(aapsLogger))
    }

    @Test fun carbsAmountShouldBeLimited() {
        // No limit by default
        `when`(sp.getInt(R.string.key_treatmentssafety_maxcarbs, 48)).thenReturn(48)

        // Negative carbs not allowed
        var i = Constraint(-22)
        safetyPlugin.applyCarbsConstraints(i)
        Assert.assertEquals(0, i.value())
        Assert.assertEquals("Safety: Limiting carbs to 0 g because of it must be positive value", i.getReasons(aapsLogger))

        // Apply all limits
        i = safetyPlugin.applyCarbsConstraints(Constraint(Constants.REALLYHIGHCARBS))
        Assert.assertEquals(48, i.value())
        Assert.assertEquals("Safety: Limiting carbs to 48 g because of max value in preferences", i.getReasons(aapsLogger))
    }

    @Test fun iobShouldBeLimited() {
        `when`(sp.getString(R.string.key_aps_mode, "open")).thenReturn("closed")
        `when`(sp.getDouble(R.string.key_openapsma_max_iob, 1.5)).thenReturn(1.5)
        `when`(sp.getString(R.string.key_age, "")).thenReturn("teenage")

        // Apply all limits
        var d = Constraint(Constants.REALLYHIGHIOB)
        d = safetyPlugin.applyMaxIOBConstraints(d)
        Assert.assertEquals(1.5, d.value()!!, 0.01)
        Assert.assertEquals("""
    Safety: Limiting IOB to 1.5 U because of max value in preferences
    """.trimIndent(), d.getReasons(aapsLogger))
        Assert.assertEquals("Safety: Limiting IOB to 1.5 U because of max value in preferences", d.getMostLimitedReasons(aapsLogger))
    }
}