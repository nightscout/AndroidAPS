package info.nightscout.androidaps.interfaces

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin
import info.nightscout.androidaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.constraints.objectives.ObjectivesPlugin
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective
import info.nightscout.androidaps.plugins.constraints.safety.SafetyPlugin
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.pump.combo.ComboPlugin
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import info.nightscout.androidaps.danar.DanaRPlugin
import info.nightscout.androidaps.danars.DanaRSPlugin
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightPlugin
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin
import info.nightscout.androidaps.plugins.source.GlimpPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentService
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.HardLimits
import info.nightscout.androidaps.utils.Profiler
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
import java.util.*

/**
 * Created by mike on 18.03.2018.
 */
@RunWith(PowerMockRunner::class)
@PrepareForTest(MainApp::class, ConfigBuilderPlugin::class, ConstraintChecker::class, SP::class, Context::class, OpenAPSAMAPlugin::class, OpenAPSSMBPlugin::class, TreatmentsPlugin::class, TreatmentService::class, VirtualPumpPlugin::class, DetailedBolusInfoStorage::class, GlimpPlugin::class, Profiler::class)
class ConstraintsCheckerTest : TestBaseWithProfile() {

    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Mock lateinit var sp: SP
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var context: Context
    @Mock lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin
    @Mock lateinit var glimpPlugin: GlimpPlugin
    @Mock lateinit var sensitivityOref1Plugin: SensitivityOref1Plugin
    @Mock lateinit var profiler: Profiler
    @Mock lateinit var nsUpload: NSUpload
    @Mock lateinit var uploadQueue: UploadQueue

    private var buildHelper = BuildHelper(Config())
    lateinit var danaPump: DanaPump

    lateinit var constraintChecker: ConstraintChecker
    private lateinit var safetyPlugin: SafetyPlugin
    private lateinit var objectivesPlugin: ObjectivesPlugin
    private lateinit var comboPlugin: ComboPlugin
    private lateinit var danaRPlugin: DanaRPlugin
    private lateinit var danaRSPlugin: DanaRSPlugin
    private lateinit var insightPlugin: LocalInsightPlugin
    private lateinit var openAPSSMBPlugin: OpenAPSSMBPlugin
    private lateinit var openAPSAMAPlugin: OpenAPSAMAPlugin
    private lateinit var hardLimits: HardLimits

    val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is Objective) {
                it.sp = sp
            }
        }
    }

    @Before
    fun prepare() {
        `when`(resourceHelper.gs(R.string.closed_loop_disabled_on_dev_branch)).thenReturn("Running dev version. Closed loop is disabled.")
        `when`(resourceHelper.gs(R.string.closedmodedisabledinpreferences)).thenReturn("Closed loop mode disabled in preferences")
        `when`(resourceHelper.gs(R.string.objectivenotstarted)).thenReturn("Objective %d not started")
        `when`(resourceHelper.gs(R.string.novalidbasalrate)).thenReturn("No valid basal rate read from pump")
        `when`(resourceHelper.gs(R.string.autosensdisabledinpreferences)).thenReturn("Autosens disabled in preferences")
        `when`(resourceHelper.gs(R.string.smbdisabledinpreferences)).thenReturn("SMB disabled in preferences")
        `when`(resourceHelper.gs(R.string.pumplimit)).thenReturn("pump limit")
        `when`(resourceHelper.gs(R.string.itmustbepositivevalue)).thenReturn("it must be positive value")
        `when`(resourceHelper.gs(R.string.maxvalueinpreferences)).thenReturn("max value in preferences")
        `when`(resourceHelper.gs(R.string.maxbasalmultiplier)).thenReturn("max basal multiplier")
        `when`(resourceHelper.gs(R.string.maxdailybasalmultiplier)).thenReturn("max daily basal multiplier")
        `when`(resourceHelper.gs(R.string.pumplimit)).thenReturn("pump limit")
        `when`(resourceHelper.gs(R.string.limitingbolus)).thenReturn("Limiting bolus to %.1f U because of %s")
        `when`(resourceHelper.gs(R.string.hardlimit)).thenReturn("hard limit")
        `when`(resourceHelper.gs(R.string.key_child)).thenReturn("child")
        `when`(resourceHelper.gs(R.string.limitingcarbs)).thenReturn("Limiting carbs to %d g because of %s")
        `when`(resourceHelper.gs(R.string.limitingiob)).thenReturn("Limiting IOB to %.1f U because of %s")
        `when`(resourceHelper.gs(R.string.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        `when`(resourceHelper.gs(R.string.limitingpercentrate)).thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")

        // RS constructor
        `when`(sp.getString(R.string.key_danars_address, "")).thenReturn("")

        //SafetyPlugin
        `when`(activePlugin.activePump).thenReturn(virtualPumpPlugin)
        constraintChecker = ConstraintChecker(activePlugin)

        danaPump = DanaPump(aapsLogger, sp, injector)
        hardLimits = HardLimits(aapsLogger, rxBus, sp, resourceHelper, context, nsUpload)
        objectivesPlugin = ObjectivesPlugin(injector, aapsLogger, resourceHelper, activePlugin, sp, Config())
        comboPlugin = ComboPlugin(injector, aapsLogger, rxBus, resourceHelper, profileFunction, treatmentsPlugin, sp, commandQueue, context)
        danaRPlugin = DanaRPlugin(injector, aapsLogger, rxBus, context, resourceHelper, constraintChecker, activePlugin, sp, commandQueue, danaPump, dateUtil, fabricPrivacy)
        danaRSPlugin = DanaRSPlugin(injector, aapsLogger, rxBus, context, resourceHelper, constraintChecker, profileFunction, activePluginProvider, sp, commandQueue, danaPump, detailedBolusInfoStorage, fabricPrivacy, dateUtil)
        insightPlugin = LocalInsightPlugin(injector, aapsLogger, rxBus, resourceHelper, treatmentsPlugin, sp, commandQueue, profileFunction, nsUpload, context, uploadQueue, Config(), dateUtil)
        openAPSSMBPlugin = OpenAPSSMBPlugin(injector, aapsLogger, rxBus, constraintChecker, resourceHelper, profileFunction, context, activePlugin, treatmentsPlugin, iobCobCalculatorPlugin, hardLimits, profiler, fabricPrivacy, sp)
        openAPSAMAPlugin = OpenAPSAMAPlugin(injector, aapsLogger, rxBus, constraintChecker, resourceHelper, profileFunction, context, activePlugin, treatmentsPlugin, iobCobCalculatorPlugin, hardLimits, profiler, fabricPrivacy)
        safetyPlugin = SafetyPlugin(injector, aapsLogger, resourceHelper, sp, rxBus, constraintChecker, openAPSAMAPlugin, openAPSSMBPlugin, sensitivityOref1Plugin, activePlugin, hardLimits, buildHelper, treatmentsPlugin, Config())
        val constraintsPluginsList = ArrayList<PluginBase>()
        constraintsPluginsList.add(safetyPlugin)
        constraintsPluginsList.add(objectivesPlugin)
        constraintsPluginsList.add(comboPlugin)
        constraintsPluginsList.add(danaRPlugin)
        constraintsPluginsList.add(danaRSPlugin)
        constraintsPluginsList.add(insightPlugin)
        constraintsPluginsList.add(openAPSSMBPlugin)
        `when`(activePlugin.getSpecificPluginsListByInterface(ConstraintsInterface::class.java)).thenReturn(constraintsPluginsList)
        objectivesPlugin.onStart()
    }

    // Combo & Objectives
    @Test
    fun isLoopInvocationAllowedTest() {
        `when`(activePlugin.activePump).thenReturn(comboPlugin)
        comboPlugin.setPluginEnabled(PluginType.PUMP, true)
        comboPlugin.setValidBasalRateProfileSelectedOnPump(false)
        val c = constraintChecker.isLoopInvocationAllowed()
        Assert.assertEquals(true, c.reasonList.size == 2) // Combo & Objectives
        Assert.assertEquals(true, c.mostLimitedReasonList.size == 2) // Combo & Objectives
        Assert.assertEquals(java.lang.Boolean.FALSE, c.value())
    }

    // Safety & Objectives
    // 2x Safety & Objectives
    @Test
    fun isClosedLoopAllowedTest() {
        `when`(sp.getString(R.string.key_aps_mode, "open")).thenReturn("closed")
        objectivesPlugin.objectives[ObjectivesPlugin.MAXIOB_ZERO_CL_OBJECTIVE].startedOn = 0
        var c: Constraint<Boolean> = constraintChecker.isClosedLoopAllowed()
        aapsLogger.debug("Reason list: " + c.reasonList.toString())
//        Assert.assertTrue(c.reasonList[0].toString().contains("Closed loop is disabled")) // Safety & Objectives
        Assert.assertEquals(false, c.value())
        `when`(sp.getString(R.string.key_aps_mode, "open")).thenReturn("open")
        c = constraintChecker.isClosedLoopAllowed()
        Assert.assertTrue(c.reasonList[0].toString().contains("Closed loop mode disabled in preferences")) // Safety & Objectives
//        Assert.assertEquals(3, c.reasonList.size) // 2x Safety & Objectives
        Assert.assertEquals(false, c.value())
    }

    // Safety & Objectives
    @Test
    fun isAutosensModeEnabledTest() {
        objectivesPlugin.objectives[ObjectivesPlugin.AUTOSENS_OBJECTIVE].startedOn = 0
        `when`(sp.getBoolean(R.string.key_openapsama_useautosens, false)).thenReturn(false)
        val c = constraintChecker.isAutosensModeEnabled()
        Assert.assertEquals(true, c.reasonList.size == 2) // Safety & Objectives
        Assert.assertEquals(true, c.mostLimitedReasonList.size == 2) // Safety & Objectives
        Assert.assertEquals(java.lang.Boolean.FALSE, c.value())
    }

    // Objectives
    @Test
    fun isAMAModeEnabledTest() {
        objectivesPlugin.objectives[ObjectivesPlugin.AMA_OBJECTIVE].startedOn = 0
        val c = constraintChecker.isAMAModeEnabled()
        Assert.assertEquals(true, c.reasonList.size == 1) // Objectives
        Assert.assertEquals(true, c.mostLimitedReasonList.size == 1) // Objectives
        Assert.assertEquals(java.lang.Boolean.FALSE, c.value())
    }

    // Safety
    @Test
    fun isAdvancedFilteringEnabledTest() {
        `when`(activePlugin.activeBgSource).thenReturn(glimpPlugin)
        val c = constraintChecker.isAdvancedFilteringEnabled()
        Assert.assertEquals(true, c.reasonList.size == 1) // Safety
        Assert.assertEquals(true, c.mostLimitedReasonList.size == 1) // Safety
        Assert.assertEquals(false, c.value())
    }

    // SMB should limit
    @Test
    fun isSuperBolusEnabledTest() {
        openAPSSMBPlugin.setPluginEnabled(PluginType.APS, true)
        val c = constraintChecker.isSuperBolusEnabled()
        Assert.assertEquals(java.lang.Boolean.FALSE, c.value()) // SMB should limit
    }

    // Safety & Objectives
    @Test
    fun isSMBModeEnabledTest() {
        objectivesPlugin.objectives[ObjectivesPlugin.SMB_OBJECTIVE].startedOn = 0
        `when`(sp.getBoolean(R.string.key_use_smb, false)).thenReturn(false)
        `when`(sp.getString(R.string.key_aps_mode, "open")).thenReturn("open")
//        `when`(constraintChecker.isClosedLoopAllowed()).thenReturn(Constraint(true))
        val c = constraintChecker.isSMBModeEnabled()
        Assert.assertEquals(true, c.reasonList.size == 3) // 2x Safety & Objectives
        Assert.assertEquals(true, c.mostLimitedReasonList.size == 3) // 2x Safety & Objectives
        Assert.assertEquals(false, c.value())
    }

    // applyBasalConstraints tests
    @Test
    fun basalRateShouldBeLimited() {
        `when`(activePlugin.activePump).thenReturn(danaRPlugin)
        // DanaR, RS
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaRSPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8

        // Insight
//        insightPlugin.setPluginEnabled(PluginType.PUMP, true);
//        StatusTaskRunner.Result result = new StatusTaskRunner.Result();
//        result.maximumBasalAmount = 1.1d;
//        insightPlugin.setStatusResult(result);

        // No limit by default
        `when`(sp.getDouble(R.string.key_openapsma_max_basal, 1.0)).thenReturn(1.0)
        `when`(sp.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4.0)).thenReturn(4.0)
        `when`(sp.getDouble(R.string.key_openapsama_max_daily_safety_multiplier, 3.0)).thenReturn(3.0)
        `when`(sp.getString(R.string.key_age, "")).thenReturn("child")

        // Apply all limits
        val d = constraintChecker.getMaxBasalAllowed(validProfile)
        Assert.assertEquals(0.8, d.value(), 0.01)
        Assert.assertEquals(6, d.reasonList.size)
        Assert.assertEquals("DanaR: Limiting max basal rate to 0.80 U/h because of pump limit", d.getMostLimitedReasons(aapsLogger))
    }

    @Test
    fun percentBasalRateShouldBeLimited() {
        `when`(activePlugin.activePump).thenReturn(danaRPlugin)
        // DanaR, RS
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaRSPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8

        // Insight
//        insightPlugin.setPluginEnabled(PluginType.PUMP, true);
//        StatusTaskRunner.Result result = new StatusTaskRunner.Result();
//        result.maximumBasalAmount = 1.1d;
//        insightPlugin.setStatusResult(result);

        // No limit by default
        `when`(sp.getDouble(R.string.key_openapsma_max_basal, 1.0)).thenReturn(1.0)
        `when`(sp.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4.0)).thenReturn(4.0)
        `when`(sp.getDouble(R.string.key_openapsama_max_daily_safety_multiplier, 3.0)).thenReturn(3.0)
        `when`(sp.getString(R.string.key_age, "")).thenReturn("child")

        // Apply all limits
        val i = constraintChecker.getMaxBasalPercentAllowed(validProfile)
        Assert.assertEquals(100, i.value())
        Assert.assertEquals(9, i.reasonList.size) // 7x Safety & RS & R
        Assert.assertEquals("Safety: Limiting max percent rate to 100% because of pump limit", i.getMostLimitedReasons(aapsLogger))
    }

    // applyBolusConstraints tests
    @Test
    fun bolusAmountShouldBeLimited() {
        `when`(activePlugin.activePump).thenReturn(virtualPumpPlugin)
        `when`(virtualPumpPlugin.pumpDescription).thenReturn(PumpDescription())
        // DanaR, RS
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaRSPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaPump.maxBolus = 6.0

        // Insight
//        insightPlugin.setPluginEnabled(PluginType.PUMP, true);
//        StatusTaskRunner.Result result = new StatusTaskRunner.Result();
//        result.maximumBolusAmount = 7d;
//        insightPlugin.setStatusResult(result);

        // No limit by default
        `when`(sp.getDouble(R.string.key_treatmentssafety_maxbolus, 3.0)).thenReturn(3.0)
        `when`(sp.getString(R.string.key_age, "")).thenReturn("child")

        // Apply all limits
        val d = constraintChecker.getMaxBolusAllowed()
        Assert.assertEquals(3.0, d.value(), 0.01)
        Assert.assertEquals(4, d.reasonList.size) // 2x Safety & RS & R
        Assert.assertEquals("Safety: Limiting bolus to 3.0 U because of max value in preferences", d.getMostLimitedReasons(aapsLogger))
    }

    // applyCarbsConstraints tests
    @Test
    fun carbsAmountShouldBeLimited() {
        // No limit by default
        `when`(sp.getInt(R.string.key_treatmentssafety_maxcarbs, 48)).thenReturn(48)

        // Apply all limits
        val i = constraintChecker.getMaxCarbsAllowed()
        Assert.assertEquals(48, i.value())
        Assert.assertEquals(true, i.reasonList.size == 1)
        Assert.assertEquals("Safety: Limiting carbs to 48 g because of max value in preferences", i.getMostLimitedReasons(aapsLogger))
    }

    // applyMaxIOBConstraints tests
    @Test
    fun iobAMAShouldBeLimited() {
        // No limit by default
        `when`(sp.getString(R.string.key_aps_mode, "open")).thenReturn("closed")
        `when`(sp.getDouble(R.string.key_openapsma_max_iob, 1.5)).thenReturn(1.5)
        `when`(sp.getString(R.string.key_age, "")).thenReturn("teenage")
        openAPSAMAPlugin.setPluginEnabled(PluginType.APS, true)
        openAPSSMBPlugin.setPluginEnabled(PluginType.APS, false)

        // Apply all limits
        val d = constraintChecker.getMaxIOBAllowed()
        Assert.assertEquals(1.5, d.value(), 0.01)
        Assert.assertEquals(d.reasonList.toString(), 2, d.reasonList.size)
        Assert.assertEquals("Safety: Limiting IOB to 1.5 U because of max value in preferences", d.getMostLimitedReasons(aapsLogger))
    }

    @Test
    fun iobSMBShouldBeLimited() {
        // No limit by default
        `when`(sp.getString(R.string.key_aps_mode, "open")).thenReturn("closed")
        `when`(sp.getDouble(R.string.key_openapssmb_max_iob, 3.0)).thenReturn(3.0)
        `when`(sp.getString(R.string.key_age, "")).thenReturn("teenage")
        openAPSSMBPlugin.setPluginEnabled(PluginType.APS, true)
        openAPSAMAPlugin.setPluginEnabled(PluginType.APS, false)

        // Apply all limits
        val d = constraintChecker.getMaxIOBAllowed()
        Assert.assertEquals(3.0, d.value(), 0.01)
        Assert.assertEquals(d.reasonList.toString(), 2, d.reasonList.size)
        Assert.assertEquals("Safety: Limiting IOB to 3.0 U because of max value in preferences", d.getMostLimitedReasons(aapsLogger))
    }
}