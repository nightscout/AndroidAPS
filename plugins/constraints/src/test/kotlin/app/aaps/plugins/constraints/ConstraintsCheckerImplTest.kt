package app.aaps.plugins.constraints

import app.aaps.core.data.model.RM
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.plugins.aps.openAPSAMA.DetermineBasalAMA
import app.aaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin
import app.aaps.plugins.aps.openAPSSMB.DetermineBasalSMB
import app.aaps.plugins.aps.openAPSSMB.GlucoseStatusCalculatorSMB
import app.aaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.objectives.objectives.Objective0
import app.aaps.plugins.constraints.objectives.objectives.Objective1
import app.aaps.plugins.constraints.objectives.objectives.Objective2
import app.aaps.plugins.constraints.objectives.objectives.Objective3
import app.aaps.plugins.constraints.objectives.objectives.Objective4
import app.aaps.plugins.constraints.objectives.objectives.Objective5
import app.aaps.plugins.constraints.objectives.objectives.Objective6
import app.aaps.plugins.constraints.objectives.objectives.Objective7
import app.aaps.plugins.constraints.objectives.objectives.Objective8
import app.aaps.plugins.constraints.objectives.objectives.Objective9
import app.aaps.plugins.constraints.safety.SafetyPlugin
import app.aaps.plugins.source.GlimpPlugin
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.dana.keys.DanaStringKey
import app.aaps.pump.danar.DanaRPlugin
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.insight.InsightPlugin
import app.aaps.pump.insight.database.InsightDatabase
import app.aaps.pump.insight.database.InsightDatabaseDao
import app.aaps.pump.insight.database.InsightDbHelper
import app.aaps.pump.virtual.VirtualPumpPlugin
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

/**
 * Created by mike on 18.03.2018.
 */
class ConstraintsCheckerImplTest : TestBaseWithProfile() {

    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Mock lateinit var glimpPlugin: GlimpPlugin
    @Mock lateinit var profiler: Profiler
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var insightDatabaseDao: InsightDatabaseDao
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var danaHistoryDatabase: DanaHistoryDatabase
    @Mock lateinit var insightDatabase: InsightDatabase
    @Mock lateinit var bgQualityCheck: BgQualityCheck
    @Mock lateinit var tddCalculator: TddCalculator
    @Mock lateinit var determineBasalSMB: DetermineBasalSMB
    @Mock lateinit var determineBasalAMA: DetermineBasalAMA
    @Mock lateinit var loop: Loop
    @Mock lateinit var passwordCheck: PasswordCheck

    private lateinit var danaPump: DanaPump
    private lateinit var insightDbHelper: InsightDbHelper
    private lateinit var constraintChecker: ConstraintsCheckerImpl
    private lateinit var safetyPlugin: SafetyPlugin
    private lateinit var objectivesPlugin: ObjectivesPlugin
    private lateinit var danaRPlugin: DanaRPlugin
    private lateinit var danaRSPlugin: DanaRSPlugin
    private lateinit var insightPlugin: InsightPlugin
    private lateinit var openAPSSMBPlugin: OpenAPSSMBPlugin
    private lateinit var openAPSAMAPlugin: OpenAPSAMAPlugin

    @BeforeEach
    fun prepare() {
        whenever(rh.gs(R.string.closed_loop_disabled_on_dev_branch)).thenReturn("Running dev version. Closed loop is disabled.")
        whenever(rh.gs(app.aaps.core.ui.R.string.no_valid_basal_rate)).thenReturn("No valid basal rate read from pump")
        whenever(rh.gs(app.aaps.plugins.aps.R.string.autosens_disabled_in_preferences)).thenReturn("Autosens disabled in preferences")
        whenever(rh.gs(app.aaps.plugins.aps.R.string.smb_disabled_in_preferences)).thenReturn("SMB disabled in preferences")
        whenever(rh.gs(app.aaps.core.ui.R.string.pumplimit)).thenReturn("pump limit")
        whenever(rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)).thenReturn("it must be positive value")
        whenever(rh.gs(R.string.maxvalueinpreferences)).thenReturn("max value in preferences")
        whenever(rh.gs(app.aaps.plugins.aps.R.string.max_basal_multiplier)).thenReturn("max basal multiplier")
        whenever(rh.gs(app.aaps.plugins.aps.R.string.max_daily_basal_multiplier)).thenReturn("max daily basal multiplier")
        whenever(rh.gs(app.aaps.core.ui.R.string.pumplimit)).thenReturn("pump limit")
        whenever(rh.gs(app.aaps.core.ui.R.string.limitingbolus)).thenReturn("Limiting bolus to %.1f U because of %s")
        whenever(rh.gs(R.string.hardlimit)).thenReturn("hard limit")
        whenever(rh.gs(R.string.limitingcarbs)).thenReturn("Limiting carbs to %d g because of %s")
        whenever(rh.gs(app.aaps.plugins.aps.R.string.limiting_iob)).thenReturn("Limiting IOB to %.1f U because of %s")
        whenever(rh.gs(app.aaps.core.ui.R.string.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        whenever(rh.gs(app.aaps.core.ui.R.string.limitingpercentrate)).thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")
        whenever(rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)).thenReturn("it must be positive value")
        whenever(rh.gs(R.string.smbnotallowedinopenloopmode)).thenReturn("SMB not allowed in open loop mode")
        whenever(rh.gs(app.aaps.core.ui.R.string.pumplimit)).thenReturn("pump limit")
        whenever(rh.gs(R.string.smbalwaysdisabled)).thenReturn("SMB always and after carbs disabled because active BG source doesn\\'t support advanced filtering")
        whenever(rh.gs(app.aaps.core.ui.R.string.limitingpercentrate)).thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")
        whenever(rh.gs(app.aaps.core.ui.R.string.limitingbolus)).thenReturn("Limiting bolus to %1\$.1f U because of %2\$s")
        whenever(rh.gs(app.aaps.core.ui.R.string.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        whenever(rh.gs(R.string.objectivenotstarted)).thenReturn("Objective %1\$d not started")

        // RS constructor
        whenever(preferences.get(DanaStringKey.RsName)).thenReturn("")
        whenever(preferences.get(DanaStringKey.MacAddress)).thenReturn("")
        // R
        whenever(preferences.get(DanaStringKey.RName)).thenReturn("")

        //SafetyPlugin
        constraintChecker = ConstraintsCheckerImpl(activePlugin, aapsLogger)

        insightDbHelper = InsightDbHelper(insightDatabaseDao)
        danaPump = DanaPump(aapsLogger, preferences, dateUtil, decimalFormatter, profileStoreProvider)
        val objectives = listOf(
            Objective0(preferences, rh, dateUtil, activePlugin, virtualPumpPlugin, persistenceLayer, loop, iobCobCalculator, passwordCheck),
            Objective1(preferences, rh, dateUtil, activePlugin),
            Objective2(preferences, rh, dateUtil),
            Objective3(preferences, rh, dateUtil),
            Objective4(preferences, rh, dateUtil, profileFunction),
            Objective5(preferences, rh, dateUtil),
            Objective6(preferences, rh, dateUtil, constraintsChecker, loop),
            Objective7(preferences, rh, dateUtil),
            Objective8(preferences, rh, dateUtil),
            Objective9(preferences, rh, dateUtil)
        )
        objectivesPlugin = ObjectivesPlugin(aapsLogger, rh, preferences, config, objectives)
        objectivesPlugin.onStart()
        danaRPlugin = DanaRPlugin(
            aapsLogger, rh, preferences, commandQueue, aapsSchedulers, rxBus, context, constraintChecker, activePlugin, danaPump, dateUtil, fabricPrivacy, pumpSync,
            uiInteraction, danaHistoryDatabase, decimalFormatter, pumpEnactResultProvider
        )
        danaRSPlugin =
            DanaRSPlugin(
                aapsLogger, rh, preferences, commandQueue, aapsSchedulers, rxBus, context, constraintChecker, profileFunction,
                danaPump, pumpSync, detailedBolusInfoStorage, temporaryBasalStorage,
                fabricPrivacy, dateUtil, uiInteraction, danaHistoryDatabase, decimalFormatter, pumpEnactResultProvider
            )
        insightPlugin = InsightPlugin(
            aapsLogger, rh, preferences, commandQueue, rxBus, profileFunction,
            context, dateUtil, insightDbHelper, pumpSync, insightDatabase, pumpEnactResultProvider
        )
        openAPSSMBPlugin =
            OpenAPSSMBPlugin(
                aapsLogger, rxBus, constraintChecker, rh, profileFunction, profileUtil, config, activePlugin, iobCobCalculator,
                hardLimits, preferences, dateUtil, processedTbrEbData, persistenceLayer, smbGlucoseStatusProvider, tddCalculator, bgQualityCheck,
                uiInteraction, determineBasalSMB, profiler, GlucoseStatusCalculatorSMB(aapsLogger, iobCobCalculator, dateUtil, decimalFormatter, deltaCalculator), apsResultProvider
            )
        openAPSAMAPlugin =
            OpenAPSAMAPlugin(
                aapsLogger, rxBus, constraintChecker, rh, config, profileFunction, activePlugin, iobCobCalculator, processedTbrEbData,
                hardLimits, dateUtil, persistenceLayer, smbGlucoseStatusProvider, preferences, determineBasalAMA,
                GlucoseStatusCalculatorSMB(aapsLogger, iobCobCalculator, dateUtil, decimalFormatter, deltaCalculator), apsResultProvider
            )
        safetyPlugin =
            SafetyPlugin(
                aapsLogger, rh, preferences, constraintChecker, activePlugin, hardLimits,
                config, persistenceLayer, dateUtil, uiInteraction, decimalFormatter
            )
        val constraintsPluginsList = ArrayList<PluginBase>()
        constraintsPluginsList.add(safetyPlugin)
        constraintsPluginsList.add(objectivesPlugin)
        constraintsPluginsList.add(danaRPlugin)
        constraintsPluginsList.add(danaRSPlugin)
        constraintsPluginsList.add(insightPlugin)
        constraintsPluginsList.add(openAPSAMAPlugin)
        constraintsPluginsList.add(openAPSSMBPlugin)
        whenever(activePlugin.getSpecificPluginsListByInterface(PluginConstraints::class.java)).thenReturn(constraintsPluginsList)
    }

    // Combo & Objectives
    @Test
    fun isLoopInvocationAllowedTest() {
        val c = constraintChecker.isLoopInvocationAllowed()
        assertThat(c.reasonList).hasSize(1) // Objectives
        assertThat(c.mostLimitedReasonList).hasSize(1) // Objectives
        assertThat(c.value()).isFalse()
    }

    // Safety & Objectives
    // 2x Safety & Objectives
    @Test
    fun isClosedLoopAllowedTest() {
        whenever(config.isEngineeringModeOrRelease()).thenReturn(true)
        whenever(loop.runningMode).thenReturn(RM.Mode.CLOSED_LOOP)
        objectivesPlugin.objectives[Objectives.CLOSED_LOOP_OBJECTIVE].startedOn = 0
        val c: Constraint<Boolean> = constraintChecker.isClosedLoopAllowed()
        aapsLogger.debug("Reason list: " + c.reasonList.toString())
        assertThat(c.reasonList[0]).contains("Objectives: Objective 7 not started") // Safety & Objectives
        assertThat(c.value()).isFalse()
    }

    // Safety & Objectives
    @Test
    fun isAutosensModeEnabledTest() {
        openAPSSMBPlugin.setPluginEnabledBlocking(PluginType.APS, true)
        objectivesPlugin.objectives[Objectives.AUTOSENS_OBJECTIVE].startedOn = 0
        whenever(preferences.get(BooleanKey.ApsUseAutosens)).thenReturn(false)
        val c = constraintChecker.isAutosensModeEnabled()
        assertThat(c.reasonList).hasSize(2) // Safety & Objectives
        assertThat(c.mostLimitedReasonList).hasSize(2) // Safety & Objectives
        assertThat(c.value()).isFalse()
    }

    // Safety
    @Test
    fun isAdvancedFilteringEnabledTest() {
        whenever(activePlugin.activeBgSource).thenReturn(glimpPlugin)
        val c = constraintChecker.isAdvancedFilteringEnabled()
        assertThat(c.reasonList).hasSize(1) // Safety
        assertThat(c.mostLimitedReasonList).hasSize(1) // Safety
        assertThat(c.value()).isFalse()
    }

    // SMB should limit
    @Test
    fun isSuperBolusEnabledTest() {
        openAPSSMBPlugin.setPluginEnabledBlocking(PluginType.APS, true)
        val c = constraintChecker.isSuperBolusEnabled()
        assertThat(c.value()).isFalse() // SMB should limit
    }

    // Safety & Objectives
    @Test
    fun isSMBModeEnabledTest() {
        openAPSSMBPlugin.setPluginEnabledBlocking(PluginType.APS, true)
        objectivesPlugin.objectives[Objectives.SMB_OBJECTIVE].startedOn = 0
        whenever(preferences.get(BooleanKey.ApsUseSmb)).thenReturn(false)
        whenever(loop.runningMode).thenReturn(RM.Mode.OPEN_LOOP)
//        whenever(constraintChecker.isClosedLoopAllowed()).thenReturn(ConstraintObject(true))
        val c = constraintChecker.isSMBModeEnabled()
        assertThat(c.reasonList).hasSize(3) // 2x Safety & Objectives
        assertThat(c.mostLimitedReasonList).hasSize(3) // 2x Safety & Objectives
        assertThat(c.value()).isFalse()
    }

    // applyBasalConstraints tests
    @Test
    fun basalRateShouldBeLimited() {
        whenever(activePlugin.activePump).thenReturn(danaRPlugin)
        // DanaR, RS
        danaRPlugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        danaRSPlugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8

        // Insight
//        insightPlugin.setPluginEnabledBlocking(PluginType.PUMP, true);
//        StatusTaskRunner.Result result = new StatusTaskRunner.Result();
//        result.maximumBasalAmount = 1.1d;
//        insightPlugin.setStatusResult(result);

        // No limit by default
        whenever(preferences.get(DoubleKey.ApsMaxBasal)).thenReturn(1.0)
        whenever(preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier)).thenReturn(4.0)
        whenever(preferences.get(DoubleKey.ApsMaxDailyMultiplier)).thenReturn(3.0)
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("child")

        // Apply all limits
        val d = constraintChecker.getMaxBasalAllowed(validProfile)
        assertThat(d.value()).isWithin(0.01).of(0.8)
        assertThat(d.reasonList).hasSize(3)
        assertThat(d.getMostLimitedReasons()).isEqualTo("DanaR: Limiting max basal rate to 0.80 U/h because of pump limit")
    }

    @Test
    fun percentBasalRateShouldBeLimited() {
        whenever(activePlugin.activePump).thenReturn(danaRPlugin)
        // DanaR, RS
        danaRPlugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        danaRSPlugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8

        // Insight
//        insightPlugin.setPluginEnabledBlocking(PluginType.PUMP, true);
//        StatusTaskRunner.Result result = new StatusTaskRunner.Result();
//        result.maximumBasalAmount = 1.1d;
//        insightPlugin.setStatusResult(result);

        // No limit by default
        whenever(preferences.get(DoubleKey.ApsMaxBasal)).thenReturn(1.0)
        whenever(preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier)).thenReturn(4.0)
        whenever(preferences.get(DoubleKey.ApsMaxDailyMultiplier)).thenReturn(3.0)
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("child")

        // Apply all limits
        val i = constraintChecker.getMaxBasalPercentAllowed(validProfile)
        assertThat(i.value()).isEqualTo(200)
        assertThat(i.reasonList).hasSize(6)
        assertThat(i.getMostLimitedReasons()).isEqualTo("Safety: Limiting max percent rate to 200% because of pump limit")
    }

    // applyBolusConstraints tests
    @Test
    fun bolusAmountShouldBeLimited() {
        whenever(activePlugin.activePump).thenReturn(virtualPumpPlugin)
        whenever(virtualPumpPlugin.pumpDescription).thenReturn(PumpDescription())
        // DanaR, RS
        danaRPlugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        danaRSPlugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        danaPump.maxBolus = 6.0

        // Insight
//        insightPlugin.setPluginEnabledBlocking(PluginType.PUMP, true);
//        StatusTaskRunner.Result result = new StatusTaskRunner.Result();
//        result.maximumBolusAmount = 7d;
//        insightPlugin.setStatusResult(result);

        // No limit by default
        whenever(preferences.get(DoubleKey.SafetyMaxBolus)).thenReturn(3.0)
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("child")

        // Apply all limits
        val d = constraintChecker.getMaxBolusAllowed()
        assertThat(d.value()).isWithin(0.01).of(3.0)
        assertThat(d.reasonList).hasSize(4) // 2x Safety & RS & R
        assertThat(d.getMostLimitedReasons()).isEqualTo("Safety: Limiting bolus to 3.0 U because of max value in preferences")
    }

    // applyCarbsConstraints tests
    @Test
    fun carbsAmountShouldBeLimited() {
        // No limit by default
        whenever(preferences.get(IntKey.SafetyMaxCarbs)).thenReturn(48)

        // Apply all limits
        val i = constraintChecker.getMaxCarbsAllowed()
        assertThat(i.value()).isEqualTo(48)
        assertThat(i.reasonList).hasSize(1)
        assertThat(i.getMostLimitedReasons()).isEqualTo("Safety: Limiting carbs to 48 g because of max value in preferences")
    }

    // applyMaxIOBConstraints tests
    @Test
    fun iobAMAShouldBeLimited() {
        // No limit by default
        whenever(loop.runningMode).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(preferences.get(DoubleKey.ApsAmaMaxIob)).thenReturn(1.5)
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("teenage")
        openAPSAMAPlugin.setPluginEnabledBlocking(PluginType.APS, true)
        openAPSSMBPlugin.setPluginEnabledBlocking(PluginType.APS, false)

        // Apply all limits
        val d = constraintChecker.getMaxIOBAllowed()
        assertThat(d.value()).isWithin(0.01).of(1.5)
        assertThat(d.reasonList).hasSize(2)
        assertThat(d.getMostLimitedReasons()).isEqualTo("OpenAPSAMA: Limiting IOB to 1.5 U because of max value in preferences")
    }

    @Test
    fun iobSMBShouldBeLimited() {
        // No limit by default
        whenever(loop.runningMode).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(preferences.get(DoubleKey.ApsSmbMaxIob)).thenReturn(3.0)
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("teenage")
        openAPSSMBPlugin.setPluginEnabledBlocking(PluginType.APS, true)
        openAPSAMAPlugin.setPluginEnabledBlocking(PluginType.APS, false)

        // Apply all limits
        val d = constraintChecker.getMaxIOBAllowed()
        assertThat(d.value()).isWithin(0.01).of(3.0)
        assertThat(d.reasonList).hasSize(2)
        assertThat(d.getMostLimitedReasons()).isEqualTo("OpenAPSSMB: Limiting IOB to 3.0 U because of max value in preferences")
    }
}
