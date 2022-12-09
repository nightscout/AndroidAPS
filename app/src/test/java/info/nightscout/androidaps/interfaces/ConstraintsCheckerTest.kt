package info.nightscout.androidaps.interfaces

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.HardLimitsMock
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.danar.DanaRPlugin
import info.nightscout.androidaps.implementations.ConfigImpl
import info.nightscout.androidaps.insight.database.InsightDatabase
import info.nightscout.androidaps.insight.database.InsightDatabaseDao
import info.nightscout.androidaps.insight.database.InsightDbHelper
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightPlugin
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.ApsMode
import info.nightscout.implementation.iob.GlucoseStatusProviderImpl
import info.nightscout.interfaces.bgQualityCheck.BgQualityCheck
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.constraints.Objectives
import info.nightscout.interfaces.maintenance.PrefFileListProvider
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.Instantiator
import info.nightscout.interfaces.profiling.Profiler
import info.nightscout.interfaces.pump.DetailedBolusInfoStorage
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.TemporaryBasalStorage
import info.nightscout.interfaces.pump.defs.PumpDescription
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.plugins.aps.openAPSAMA.OpenAPSAMAPlugin
import info.nightscout.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import info.nightscout.plugins.aps.openAPSSMBDynamicISF.OpenAPSSMBDynamicISFPlugin
import info.nightscout.plugins.constraints.ConstraintsImpl
import info.nightscout.plugins.constraints.objectives.ObjectivesPlugin
import info.nightscout.plugins.constraints.objectives.objectives.Objective
import info.nightscout.plugins.constraints.safety.SafetyPlugin
import info.nightscout.pump.combo.ComboPlugin
import info.nightscout.pump.combo.ruffyscripter.RuffyScripter
import info.nightscout.pump.dana.DanaPump
import info.nightscout.pump.dana.R
import info.nightscout.pump.dana.database.DanaHistoryDatabase
import info.nightscout.pump.virtual.VirtualPumpPlugin
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.source.GlimpPlugin
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

/**
 * Created by mike on 18.03.2018.
 */
class ConstraintsCheckerTest : TestBaseWithProfile() {

    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Mock lateinit var sp: SP
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Mock lateinit var glimpPlugin: GlimpPlugin
    @Mock lateinit var profiler: Profiler
    @Mock lateinit var fileListProvider: PrefFileListProvider
    @Mock lateinit var repository: AppRepository
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var insightDatabaseDao: InsightDatabaseDao
    @Mock lateinit var ruffyScripter: RuffyScripter
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var instantiator: Instantiator
    @Mock lateinit var danaHistoryDatabase: DanaHistoryDatabase
    @Mock lateinit var insightDatabase: InsightDatabase
    @Mock lateinit var bgQualityCheck: BgQualityCheck

    private lateinit var hardLimits: HardLimits
    private lateinit var danaPump: DanaPump
    private lateinit var insightDbHelper: InsightDbHelper
    private lateinit var constraintChecker: ConstraintsImpl
    private lateinit var safetyPlugin: SafetyPlugin
    private lateinit var objectivesPlugin: ObjectivesPlugin
    private lateinit var comboPlugin: ComboPlugin
    private lateinit var danaRPlugin: DanaRPlugin
    private lateinit var danaRSPlugin: info.nightscout.pump.danars.DanaRSPlugin
    private lateinit var insightPlugin: LocalInsightPlugin
    private lateinit var openAPSSMBPlugin: OpenAPSSMBPlugin
    private lateinit var openAPSAMAPlugin: OpenAPSAMAPlugin
    private lateinit var openAPSSMBDynamicISFPlugin: OpenAPSSMBDynamicISFPlugin

    private val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is Objective) {
                it.sp = sp
                it.dateUtil = dateUtil
            }
            if (it is PumpEnactResult) {
                it.context = context
            }
        }
    }

    @BeforeEach
    fun prepare() {
        `when`(rh.gs(info.nightscout.plugins.constraints.R.string.closed_loop_disabled_on_dev_branch)).thenReturn("Running dev version. Closed loop is disabled.")
        `when`(rh.gs(info.nightscout.plugins.constraints.R.string.closedmodedisabledinpreferences)).thenReturn("Closed loop mode disabled in preferences")
        `when`(rh.gs(info.nightscout.core.ui.R.string.no_valid_basal_rate)).thenReturn("No valid basal rate read from pump")
        `when`(rh.gs(info.nightscout.plugins.aps.R.string.autosens_disabled_in_preferences)).thenReturn("Autosens disabled in preferences")
        `when`(rh.gs(info.nightscout.plugins.aps.R.string.smb_disabled_in_preferences)).thenReturn("SMB disabled in preferences")
        `when`(rh.gs(info.nightscout.core.ui.R.string.pumplimit)).thenReturn("pump limit")
        `when`(rh.gs(info.nightscout.core.ui.R.string.itmustbepositivevalue)).thenReturn("it must be positive value")
        `when`(rh.gs(info.nightscout.plugins.constraints.R.string.maxvalueinpreferences)).thenReturn("max value in preferences")
        `when`(rh.gs(info.nightscout.plugins.aps.R.string.max_basal_multiplier)).thenReturn("max basal multiplier")
        `when`(rh.gs(info.nightscout.plugins.aps.R.string.max_daily_basal_multiplier)).thenReturn("max daily basal multiplier")
        `when`(rh.gs(info.nightscout.core.ui.R.string.pumplimit)).thenReturn("pump limit")
        `when`(rh.gs(info.nightscout.core.ui.R.string.limitingbolus)).thenReturn("Limiting bolus to %.1f U because of %s")
        `when`(rh.gs(info.nightscout.plugins.constraints.R.string.hardlimit)).thenReturn("hard limit")
        `when`(rh.gs(info.nightscout.core.utils.R.string.key_child)).thenReturn("child")
        `when`(rh.gs(info.nightscout.plugins.constraints.R.string.limitingcarbs)).thenReturn("Limiting carbs to %d g because of %s")
        `when`(rh.gs(info.nightscout.plugins.aps.R.string.limiting_iob)).thenReturn("Limiting IOB to %.1f U because of %s")
        `when`(rh.gs(info.nightscout.core.ui.R.string.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        `when`(rh.gs(info.nightscout.core.ui.R.string.limitingpercentrate)).thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")
        `when`(rh.gs(info.nightscout.core.ui.R.string.itmustbepositivevalue)).thenReturn("it must be positive value")
        `when`(rh.gs(info.nightscout.plugins.constraints.R.string.smbnotallowedinopenloopmode)).thenReturn("SMB not allowed in open loop mode")
        `when`(rh.gs(info.nightscout.core.ui.R.string.pumplimit)).thenReturn("pump limit")
        `when`(rh.gs(info.nightscout.plugins.constraints.R.string.smbalwaysdisabled)).thenReturn("SMB always and after carbs disabled because active BG source doesn\\'t support advanced filtering")
        `when`(rh.gs(info.nightscout.core.ui.R.string.limitingpercentrate)).thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")
        `when`(rh.gs(info.nightscout.core.ui.R.string.limitingbolus)).thenReturn("Limiting bolus to %1\$.1f U because of %2\$s")
        `when`(rh.gs(info.nightscout.core.ui.R.string.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        `when`(context.getString(info.nightscout.pump.combo.R.string.combo_pump_unsupported_operation)).thenReturn("Requested operation not supported by pump")
        `when`(rh.gs(info.nightscout.plugins.constraints.R.string.objectivenotstarted)).thenReturn("Objective %1\$d not started")

        // RS constructor
        `when`(sp.getString(R.string.key_danars_name, "")).thenReturn("")
        `when`(sp.getString(R.string.key_danars_address, "")).thenReturn("")
        // R
        `when`(sp.getString(R.string.key_danar_bt_name, "")).thenReturn("")

        //SafetyPlugin
        `when`(activePlugin.activePump).thenReturn(virtualPumpPlugin)
        constraintChecker = ConstraintsImpl(activePlugin)

        val glucoseStatusProvider = GlucoseStatusProviderImpl(aapsLogger = aapsLogger, iobCobCalculator = iobCobCalculator, dateUtil = dateUtil)

        hardLimits = HardLimitsMock(sp, rh)
        insightDbHelper = InsightDbHelper(insightDatabaseDao)
        danaPump = DanaPump(aapsLogger, sp, dateUtil, instantiator)
        objectivesPlugin = ObjectivesPlugin(injector, aapsLogger, rh, activePlugin, sp, config)
        comboPlugin = ComboPlugin(injector, aapsLogger, rxBus, rh, profileFunction, sp, commandQueue, pumpSync, dateUtil, ruffyScripter, uiInteraction)
        danaRPlugin = DanaRPlugin(injector, aapsLogger, aapsSchedulers, rxBus, context, rh, constraintChecker, activePlugin, sp, commandQueue, danaPump, dateUtil, fabricPrivacy, pumpSync,
                                  uiInteraction, danaHistoryDatabase)
        danaRSPlugin =
            info.nightscout.pump.danars.DanaRSPlugin(
                injector,
                aapsLogger,
                aapsSchedulers,
                rxBus,
                context,
                rh,
                constraintChecker,
                profileFunction,
                sp,
                commandQueue,
                danaPump,
                pumpSync,
                detailedBolusInfoStorage,
                temporaryBasalStorage,
                fabricPrivacy,
                dateUtil,
                uiInteraction,
                danaHistoryDatabase
            )
        insightPlugin = LocalInsightPlugin(injector, aapsLogger, rxBus, rh, sp, commandQueue, profileFunction, context, config, dateUtil, insightDbHelper, pumpSync, insightDatabase)
        openAPSSMBPlugin =
            OpenAPSSMBPlugin(
                injector,
                aapsLogger,
                rxBus,
                constraintChecker,
                rh,
                profileFunction,
                context,
                activePlugin,
                iobCobCalculator,
                hardLimits,
                profiler,
                sp,
                dateUtil,
                repository,
                glucoseStatusProvider,
                bgQualityCheck
            )
        openAPSSMBDynamicISFPlugin =
            OpenAPSSMBDynamicISFPlugin(
                injector,
                aapsLogger,
                rxBus,
                constraintChecker,
                rh,
                profileFunction,
                context,
                activePlugin,
                iobCobCalculator,
                hardLimits,
                profiler,
                sp,
                dateUtil,
                repository,
                glucoseStatusProvider,
                config,
                bgQualityCheck
            )
        openAPSAMAPlugin =
            OpenAPSAMAPlugin(
                injector,
                aapsLogger,
                rxBus,
                constraintChecker,
                rh,
                profileFunction,
                context,
                activePlugin,
                iobCobCalculator,
                hardLimits,
                profiler,
                fabricPrivacy,
                dateUtil,
                repository,
                glucoseStatusProvider,
                sp
            )
        safetyPlugin =
            SafetyPlugin(
                injector,
                aapsLogger,
                rh,
                sp,
                rxBus,
                constraintChecker,
                activePlugin,
                hardLimits,
                ConfigImpl(fileListProvider),
                iobCobCalculator,
                dateUtil,
                uiInteraction
            )
        val constraintsPluginsList = ArrayList<PluginBase>()
        constraintsPluginsList.add(safetyPlugin)
        constraintsPluginsList.add(objectivesPlugin)
        constraintsPluginsList.add(comboPlugin)
        constraintsPluginsList.add(danaRPlugin)
        constraintsPluginsList.add(danaRSPlugin)
        constraintsPluginsList.add(insightPlugin)
        constraintsPluginsList.add(openAPSAMAPlugin)
        constraintsPluginsList.add(openAPSSMBPlugin)
        `when`(activePlugin.getSpecificPluginsListByInterface(Constraints::class.java)).thenReturn(constraintsPluginsList)
        objectivesPlugin.onStart()
    }

    // Combo & Objectives
    @Test
    fun isLoopInvocationAllowedTest() {
        `when`(activePlugin.activePump).thenReturn(comboPlugin)
        comboPlugin.setPluginEnabled(PluginType.PUMP, true)
        comboPlugin.setValidBasalRateProfileSelectedOnPump(false)
        val c = constraintChecker.isLoopInvocationAllowed()
        Assertions.assertEquals(true, c.reasonList.size == 2) // Combo & Objectives
        Assertions.assertEquals(true, c.mostLimitedReasonList.size == 2) // Combo & Objectives
        Assertions.assertEquals(java.lang.Boolean.FALSE, c.value())
    }

    // Safety & Objectives
    // 2x Safety & Objectives
    @Test
    fun isClosedLoopAllowedTest() {
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name)).thenReturn(ApsMode.CLOSED.name)
        objectivesPlugin.objectives[Objectives.MAXIOB_ZERO_CL_OBJECTIVE].startedOn = 0
        var c: Constraint<Boolean> = constraintChecker.isClosedLoopAllowed()
        aapsLogger.debug("Reason list: " + c.reasonList.toString())
//        Assertions.assertTrue(c.reasonList[0].toString().contains("Closed loop is disabled")) // Safety & Objectives
        Assertions.assertEquals(false, c.value())
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name)).thenReturn(ApsMode.OPEN.name)
        c = constraintChecker.isClosedLoopAllowed()
        Assertions.assertTrue(c.reasonList[0].contains("Closed loop mode disabled in preferences")) // Safety & Objectives
//        Assertions.assertEquals(3, c.reasonList.size) // 2x Safety & Objectives
        Assertions.assertEquals(false, c.value())
    }

    // Safety & Objectives
    @Test
    fun isAutosensModeEnabledTest() {
        openAPSSMBPlugin.setPluginEnabled(PluginType.APS, true)
        objectivesPlugin.objectives[Objectives.AUTOSENS_OBJECTIVE].startedOn = 0
        `when`(sp.getBoolean(info.nightscout.plugins.aps.R.string.key_openapsama_use_autosens, false)).thenReturn(false)
        val c = constraintChecker.isAutosensModeEnabled()
        Assertions.assertEquals(true, c.reasonList.size == 2) // Safety & Objectives
        Assertions.assertEquals(true, c.mostLimitedReasonList.size == 2) // Safety & Objectives
        Assertions.assertEquals(java.lang.Boolean.FALSE, c.value())
    }

    // Safety
    @Test
    fun isAdvancedFilteringEnabledTest() {
        `when`(activePlugin.activeBgSource).thenReturn(glimpPlugin)
        val c = constraintChecker.isAdvancedFilteringEnabled()
        Assertions.assertEquals(true, c.reasonList.size == 1) // Safety
        Assertions.assertEquals(true, c.mostLimitedReasonList.size == 1) // Safety
        Assertions.assertEquals(false, c.value())
    }

    // SMB should limit
    @Test
    fun isSuperBolusEnabledTest() {
        openAPSSMBPlugin.setPluginEnabled(PluginType.APS, true)
        val c = constraintChecker.isSuperBolusEnabled()
        Assertions.assertEquals(java.lang.Boolean.FALSE, c.value()) // SMB should limit
    }

    // Safety & Objectives
    @Test
    fun isSMBModeEnabledTest() {
        openAPSSMBPlugin.setPluginEnabled(PluginType.APS, true)
        objectivesPlugin.objectives[Objectives.SMB_OBJECTIVE].startedOn = 0
        `when`(sp.getBoolean(info.nightscout.plugins.aps.R.string.key_use_smb, false)).thenReturn(false)
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name)).thenReturn(ApsMode.OPEN.name)
//        `when`(constraintChecker.isClosedLoopAllowed()).thenReturn(Constraint(true))
        val c = constraintChecker.isSMBModeEnabled()
        Assertions.assertEquals(true, c.reasonList.size == 3) // 2x Safety & Objectives
        Assertions.assertEquals(true, c.mostLimitedReasonList.size == 3) // 2x Safety & Objectives
        Assertions.assertEquals(false, c.value())
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
        `when`(sp.getDouble(info.nightscout.plugins.aps.R.string.key_openapsma_max_basal, 1.0)).thenReturn(1.0)
        `when`(sp.getDouble(info.nightscout.plugins.aps.R.string.key_openapsama_current_basal_safety_multiplier, 4.0)).thenReturn(4.0)
        `when`(sp.getDouble(info.nightscout.plugins.aps.R.string.key_openapsama_max_daily_safety_multiplier, 3.0)).thenReturn(3.0)
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_age, "")).thenReturn("child")

        // Apply all limits
        val d = constraintChecker.getMaxBasalAllowed(validProfile)
        Assertions.assertEquals(0.8, d.value(), 0.01)
        Assertions.assertEquals(3, d.reasonList.size)
        Assertions.assertEquals("DanaR: Limiting max basal rate to 0.80 U/h because of pump limit", d.getMostLimitedReasons(aapsLogger))
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
        `when`(sp.getDouble(info.nightscout.plugins.aps.R.string.key_openapsma_max_basal, 1.0)).thenReturn(1.0)
        `when`(sp.getDouble(info.nightscout.plugins.aps.R.string.key_openapsama_current_basal_safety_multiplier, 4.0)).thenReturn(4.0)
        `when`(sp.getDouble(info.nightscout.plugins.aps.R.string.key_openapsama_max_daily_safety_multiplier, 3.0)).thenReturn(3.0)
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_age, "")).thenReturn("child")

        // Apply all limits
        val i = constraintChecker.getMaxBasalPercentAllowed(validProfile)
        Assertions.assertEquals(200, i.value())
        Assertions.assertEquals(6, i.reasonList.size)
        Assertions.assertEquals("Safety: Limiting max percent rate to 200% because of pump limit", i.getMostLimitedReasons(aapsLogger))
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
        `when`(sp.getDouble(info.nightscout.core.utils.R.string.key_treatmentssafety_maxbolus, 3.0)).thenReturn(3.0)
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_age, "")).thenReturn("child")

        // Apply all limits
        val d = constraintChecker.getMaxBolusAllowed()
        Assertions.assertEquals(3.0, d.value(), 0.01)
        Assertions.assertEquals(4, d.reasonList.size) // 2x Safety & RS & R
        Assertions.assertEquals("Safety: Limiting bolus to 3.0 U because of max value in preferences", d.getMostLimitedReasons(aapsLogger))
    }

    // applyCarbsConstraints tests
    @Test
    fun carbsAmountShouldBeLimited() {
        // No limit by default
        `when`(sp.getInt(info.nightscout.core.utils.R.string.key_treatmentssafety_maxcarbs, 48)).thenReturn(48)

        // Apply all limits
        val i = constraintChecker.getMaxCarbsAllowed()
        Assertions.assertEquals(48, i.value())
        Assertions.assertEquals(true, i.reasonList.size == 1)
        Assertions.assertEquals("Safety: Limiting carbs to 48 g because of max value in preferences", i.getMostLimitedReasons(aapsLogger))
    }

    // applyMaxIOBConstraints tests
    @Test
    fun iobAMAShouldBeLimited() {
        // No limit by default
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name)).thenReturn(ApsMode.CLOSED.name)
        `when`(sp.getDouble(info.nightscout.plugins.aps.R.string.key_openapsma_max_iob, 1.5)).thenReturn(1.5)
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_age, "")).thenReturn("teenage")
        openAPSAMAPlugin.setPluginEnabled(PluginType.APS, true)
        openAPSSMBPlugin.setPluginEnabled(PluginType.APS, false)

        // Apply all limits
        val d = constraintChecker.getMaxIOBAllowed()
        Assertions.assertEquals(1.5, d.value(), 0.01)
        Assertions.assertEquals(2, d.reasonList.size)
        Assertions.assertEquals("OpenAPSAMA: Limiting IOB to 1.5 U because of max value in preferences", d.getMostLimitedReasons(aapsLogger))
    }

    @Test
    fun iobSMBShouldBeLimited() {
        // No limit by default
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name)).thenReturn(ApsMode.CLOSED.name)
        `when`(sp.getDouble(info.nightscout.plugins.aps.R.string.key_openapssmb_max_iob, 3.0)).thenReturn(3.0)
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_age, "")).thenReturn("teenage")
        openAPSSMBPlugin.setPluginEnabled(PluginType.APS, true)
        openAPSAMAPlugin.setPluginEnabled(PluginType.APS, false)

        // Apply all limits
        val d = constraintChecker.getMaxIOBAllowed()
        Assertions.assertEquals(3.0, d.value(), 0.01)
        Assertions.assertEquals(2, d.reasonList.size)
        Assertions.assertEquals("OpenAPSSMB: Limiting IOB to 3.0 U because of max value in preferences", d.getMostLimitedReasons(aapsLogger))
    }
}