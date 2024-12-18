package app.aaps.plugins.constraints

import app.aaps.core.data.aps.ApsMode
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profiling.Profiler
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
import app.aaps.implementation.iob.GlucoseStatusProviderImpl
import app.aaps.plugins.aps.openAPSAMA.DetermineBasalAMA
import app.aaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin
import app.aaps.plugins.aps.openAPSSMB.DetermineBasalSMB
import app.aaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.objectives.objectives.Objective
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
import org.mockito.Mockito.`when`

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

    init {
        addInjector {
            if (it is Objective) {
                it.sp = sp
                it.preferences = preferences
                it.dateUtil = dateUtil
            }
        }
    }

    @BeforeEach
    fun prepare() {
        `when`(rh.gs(R.string.closed_loop_disabled_on_dev_branch)).thenReturn("Running dev version. Closed loop is disabled.")
        `when`(rh.gs(R.string.closedmodedisabledinpreferences)).thenReturn("Closed loop mode disabled in preferences")
        `when`(rh.gs(app.aaps.core.ui.R.string.no_valid_basal_rate)).thenReturn("No valid basal rate read from pump")
        `when`(rh.gs(app.aaps.plugins.aps.R.string.autosens_disabled_in_preferences)).thenReturn("Autosens disabled in preferences")
        `when`(rh.gs(app.aaps.plugins.aps.R.string.smb_disabled_in_preferences)).thenReturn("SMB disabled in preferences")
        `when`(rh.gs(app.aaps.core.ui.R.string.pumplimit)).thenReturn("pump limit")
        `when`(rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)).thenReturn("it must be positive value")
        `when`(rh.gs(R.string.maxvalueinpreferences)).thenReturn("max value in preferences")
        `when`(rh.gs(app.aaps.plugins.aps.R.string.max_basal_multiplier)).thenReturn("max basal multiplier")
        `when`(rh.gs(app.aaps.plugins.aps.R.string.max_daily_basal_multiplier)).thenReturn("max daily basal multiplier")
        `when`(rh.gs(app.aaps.core.ui.R.string.pumplimit)).thenReturn("pump limit")
        `when`(rh.gs(app.aaps.core.ui.R.string.limitingbolus)).thenReturn("Limiting bolus to %.1f U because of %s")
        `when`(rh.gs(R.string.hardlimit)).thenReturn("hard limit")
        `when`(rh.gs(R.string.limitingcarbs)).thenReturn("Limiting carbs to %d g because of %s")
        `when`(rh.gs(app.aaps.plugins.aps.R.string.limiting_iob)).thenReturn("Limiting IOB to %.1f U because of %s")
        `when`(rh.gs(app.aaps.core.ui.R.string.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        `when`(rh.gs(app.aaps.core.ui.R.string.limitingpercentrate)).thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")
        `when`(rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)).thenReturn("it must be positive value")
        `when`(rh.gs(R.string.smbnotallowedinopenloopmode)).thenReturn("SMB not allowed in open loop mode")
        `when`(rh.gs(app.aaps.core.ui.R.string.pumplimit)).thenReturn("pump limit")
        `when`(rh.gs(R.string.smbalwaysdisabled)).thenReturn("SMB always and after carbs disabled because active BG source doesn\\'t support advanced filtering")
        `when`(rh.gs(app.aaps.core.ui.R.string.limitingpercentrate)).thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")
        `when`(rh.gs(app.aaps.core.ui.R.string.limitingbolus)).thenReturn("Limiting bolus to %1\$.1f U because of %2\$s")
        `when`(rh.gs(app.aaps.core.ui.R.string.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        `when`(rh.gs(R.string.objectivenotstarted)).thenReturn("Objective %1\$d not started")

        // RS constructor
        `when`(preferences.get(DanaStringKey.DanaRsName)).thenReturn("")
        `when`(preferences.get(DanaStringKey.DanaMacAddress)).thenReturn("")
        // R
        `when`(preferences.get(DanaStringKey.DanaRName)).thenReturn("")

        //SafetyPlugin
        constraintChecker = ConstraintsCheckerImpl(activePlugin, aapsLogger)

        val glucoseStatusProvider = GlucoseStatusProviderImpl(aapsLogger, iobCobCalculator, dateUtil, decimalFormatter)

        insightDbHelper = InsightDbHelper(insightDatabaseDao)
        danaPump = DanaPump(aapsLogger, preferences, dateUtil, instantiator, decimalFormatter)
        objectivesPlugin = ObjectivesPlugin(injector, aapsLogger, rh, sp)
        danaRPlugin = DanaRPlugin(
            aapsLogger, aapsSchedulers, rxBus, context, rh, constraintChecker, activePlugin, commandQueue, danaPump, dateUtil, fabricPrivacy, pumpSync,
            preferences, uiInteraction, danaHistoryDatabase, decimalFormatter, instantiator
        )
        danaRSPlugin =
            DanaRSPlugin(
                aapsLogger, aapsSchedulers, rxBus, context, rh, constraintChecker, profileFunction,
                commandQueue, danaPump, pumpSync, preferences, detailedBolusInfoStorage, temporaryBasalStorage,
                fabricPrivacy, dateUtil, uiInteraction, danaHistoryDatabase, decimalFormatter, instantiator
            )
        insightPlugin = InsightPlugin(
            aapsLogger, rxBus, rh, sp, commandQueue, profileFunction,
            context, config, dateUtil, insightDbHelper, pumpSync, insightDatabase, instantiator
        )
        openAPSSMBPlugin =
            OpenAPSSMBPlugin(
                injector, aapsLogger, rxBus, constraintChecker, rh, profileFunction, profileUtil, config, activePlugin, iobCobCalculator,
                hardLimits, preferences, dateUtil, processedTbrEbData, persistenceLayer, glucoseStatusProvider, tddCalculator, bgQualityCheck,
                uiInteraction, determineBasalSMB, profiler
            )
        openAPSAMAPlugin =
            OpenAPSAMAPlugin(
                injector, aapsLogger, rxBus, constraintChecker, rh, config, profileFunction, activePlugin, iobCobCalculator, processedTbrEbData,
                hardLimits, dateUtil, persistenceLayer, glucoseStatusProvider, preferences, determineBasalAMA
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
        `when`(activePlugin.getSpecificPluginsListByInterface(PluginConstraints::class.java)).thenReturn(constraintsPluginsList)
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
        `when`(preferences.get(StringKey.LoopApsMode)).thenReturn(ApsMode.CLOSED.name)
        objectivesPlugin.objectives[Objectives.MAXIOB_ZERO_CL_OBJECTIVE].startedOn = 0
        var c: Constraint<Boolean> = constraintChecker.isClosedLoopAllowed()
        aapsLogger.debug("Reason list: " + c.reasonList.toString())
//        assertThat(c.reasonList[0].toString()).contains("Closed loop is disabled") // Safety & Objectives
        assertThat(c.value()).isFalse()
        `when`(preferences.get(StringKey.LoopApsMode)).thenReturn(ApsMode.OPEN.name)
        c = constraintChecker.isClosedLoopAllowed()
        assertThat(c.reasonList[0]).contains("Closed loop mode disabled in preferences") // Safety & Objectives
//        assertThat(c.reasonList).hasThat(3) // 2x Safety & Objectives
        assertThat(c.value()).isFalse()
    }

    // Safety & Objectives
    @Test
    fun isAutosensModeEnabledTest() {
        openAPSSMBPlugin.setPluginEnabled(PluginType.APS, true)
        objectivesPlugin.objectives[Objectives.AUTOSENS_OBJECTIVE].startedOn = 0
        `when`(preferences.get(BooleanKey.ApsUseAutosens)).thenReturn(false)
        val c = constraintChecker.isAutosensModeEnabled()
        assertThat(c.reasonList).hasSize(2) // Safety & Objectives
        assertThat(c.mostLimitedReasonList).hasSize(2) // Safety & Objectives
        assertThat(c.value()).isFalse()
    }

    // Safety
    @Test
    fun isAdvancedFilteringEnabledTest() {
        `when`(activePlugin.activeBgSource).thenReturn(glimpPlugin)
        val c = constraintChecker.isAdvancedFilteringEnabled()
        assertThat(c.reasonList).hasSize(1) // Safety
        assertThat(c.mostLimitedReasonList).hasSize(1) // Safety
        assertThat(c.value()).isFalse()
    }

    // SMB should limit
    @Test
    fun isSuperBolusEnabledTest() {
        openAPSSMBPlugin.setPluginEnabled(PluginType.APS, true)
        val c = constraintChecker.isSuperBolusEnabled()
        assertThat(c.value()).isFalse() // SMB should limit
    }

    // Safety & Objectives
    @Test
    fun isSMBModeEnabledTest() {
        openAPSSMBPlugin.setPluginEnabled(PluginType.APS, true)
        objectivesPlugin.objectives[Objectives.SMB_OBJECTIVE].startedOn = 0
        `when`(preferences.get(BooleanKey.ApsUseSmb)).thenReturn(false)
        `when`(preferences.get(StringKey.LoopApsMode)).thenReturn(ApsMode.OPEN.name)
//        `when`(constraintChecker.isClosedLoopAllowed()).thenReturn(ConstraintObject(true))
        val c = constraintChecker.isSMBModeEnabled()
        assertThat(c.reasonList).hasSize(3) // 2x Safety & Objectives
        assertThat(c.mostLimitedReasonList).hasSize(3) // 2x Safety & Objectives
        assertThat(c.value()).isFalse()
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
        `when`(preferences.get(DoubleKey.ApsMaxBasal)).thenReturn(1.0)
        `when`(preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier)).thenReturn(4.0)
        `when`(preferences.get(DoubleKey.ApsMaxDailyMultiplier)).thenReturn(3.0)
        `when`(preferences.get(StringKey.SafetyAge)).thenReturn("child")

        // Apply all limits
        val d = constraintChecker.getMaxBasalAllowed(validProfile)
        assertThat(d.value()).isWithin(0.01).of(0.8)
        assertThat(d.reasonList).hasSize(3)
        assertThat(d.getMostLimitedReasons()).isEqualTo("DanaR: Limiting max basal rate to 0.80 U/h because of pump limit")
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
        `when`(preferences.get(DoubleKey.ApsMaxBasal)).thenReturn(1.0)
        `when`(preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier)).thenReturn(4.0)
        `when`(preferences.get(DoubleKey.ApsMaxDailyMultiplier)).thenReturn(3.0)
        `when`(preferences.get(StringKey.SafetyAge)).thenReturn("child")

        // Apply all limits
        val i = constraintChecker.getMaxBasalPercentAllowed(validProfile)
        assertThat(i.value()).isEqualTo(200)
        assertThat(i.reasonList).hasSize(6)
        assertThat(i.getMostLimitedReasons()).isEqualTo("Safety: Limiting max percent rate to 200% because of pump limit")
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
        `when`(preferences.get(DoubleKey.SafetyMaxBolus)).thenReturn(3.0)
        `when`(preferences.get(StringKey.SafetyAge)).thenReturn("child")

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
        `when`(preferences.get(IntKey.SafetyMaxCarbs)).thenReturn(48)

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
        `when`(preferences.get(StringKey.LoopApsMode)).thenReturn(ApsMode.CLOSED.name)
        `when`(preferences.get(DoubleKey.ApsAmaMaxIob)).thenReturn(1.5)
        `when`(preferences.get(StringKey.SafetyAge)).thenReturn("teenage")
        openAPSAMAPlugin.setPluginEnabled(PluginType.APS, true)
        openAPSSMBPlugin.setPluginEnabled(PluginType.APS, false)

        // Apply all limits
        val d = constraintChecker.getMaxIOBAllowed()
        assertThat(d.value()).isWithin(0.01).of(1.5)
        assertThat(d.reasonList).hasSize(2)
        assertThat(d.getMostLimitedReasons()).isEqualTo("OpenAPSAMA: Limiting IOB to 1.5 U because of max value in preferences")
    }

    @Test
    fun iobSMBShouldBeLimited() {
        // No limit by default
        `when`(preferences.get(StringKey.LoopApsMode)).thenReturn(ApsMode.CLOSED.name)
        `when`(preferences.get(DoubleKey.ApsSmbMaxIob)).thenReturn(3.0)
        `when`(preferences.get(StringKey.SafetyAge)).thenReturn("teenage")
        openAPSSMBPlugin.setPluginEnabled(PluginType.APS, true)
        openAPSAMAPlugin.setPluginEnabled(PluginType.APS, false)

        // Apply all limits
        val d = constraintChecker.getMaxIOBAllowed()
        assertThat(d.value()).isWithin(0.01).of(3.0)
        assertThat(d.reasonList).hasSize(2)
        assertThat(d.getMostLimitedReasons()).isEqualTo("OpenAPSSMB: Limiting IOB to 3.0 U because of max value in preferences")
    }
}
