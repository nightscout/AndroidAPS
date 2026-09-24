package app.aaps.plugins.constraints

import app.aaps.core.data.model.RM
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.constraints.PumpPluginConstraints
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.implementation.pump.PumpWithConcentrationImpl
import app.aaps.plugins.aps.openAPSAMA.DetermineBasalAMA
import app.aaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin
import app.aaps.plugins.aps.openAPSSMB.DetermineBasalSMB
import app.aaps.plugins.aps.openAPSSMB.GlucoseStatusCalculatorSMB
import app.aaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.objectives.objectives.Objective0
import app.aaps.plugins.constraints.objectives.objectives.PlainDurationText
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
import app.aaps.pump.virtual.VirtualPumpPlugin
import app.aaps.shared.tests.TestBaseWithProfile
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The active pump, as far as the checker can see it: a [Pump] with a basal cap of its own. This used
 * to be a real `DanaRPlugin`, with `DanaRSPlugin` and `InsightPlugin` built beside it but never asked
 * anything. That made the pump modules a dependency of this module's tests - so removing a pump from
 * `settings.gradle` failed the configuration of the whole build. The checker only needs something to
 * fold in; each driver tests its own cap (`DanaRPluginTest` and the Korean and v2 tests).
 */
private class CappedPumpPlugin(private val maxBasal: Double) : Pump by mock(), PumpPluginConstraints {

    override fun applyBasalConstraints(absoluteRate: PumpRate): PumpRate = PumpRate(absoluteRate.cU.coerceAtMost(maxBasal))
}

/**
 * Created by mike on 18.03.2018.
 */
class ConstraintsCheckerImplTest : TestBaseWithProfile() {

    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Mock lateinit var profiler: Profiler
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var bgQualityCheck: BgQualityCheck
    @Mock lateinit var tddCalculator: TddCalculator
    @Mock lateinit var determineBasalSMB: DetermineBasalSMB
    @Mock lateinit var determineBasalAMA: DetermineBasalAMA
    @Mock lateinit var loop: Loop
    @Mock lateinit var passwordCheck: PasswordCheck
    @Mock lateinit var pumpWithConcentration: PumpWithConcentrationImpl

    /**
     * Real English for every reason the checker builds, so the sentences asserted below are the ones the
     * user reads. `:shared:tests` cannot see this module, so the generated map is handed over here.
     */
    private val text = generatedTextResolver("constraints" to ConstraintsStringsValues::textOf)

    private lateinit var constraintChecker: ConstraintsCheckerImpl
    private lateinit var safetyPlugin: SafetyPlugin
    private lateinit var objectivesPlugin: ObjectivesPlugin
    private lateinit var openAPSSMBPlugin: OpenAPSSMBPlugin
    private lateinit var openAPSAMAPlugin: OpenAPSAMAPlugin

    @BeforeEach
    fun prepare() {
        // Mock persistenceLayer for OpenAPSSMBPlugin.onStart()
        runTest {
            whenever(persistenceLayer.getApsResults(any(), any())).thenReturn(emptyList())
        }

        whenever(activePlugin.activePump).thenReturn(pumpWithConcentration)
        whenever(pumpWithConcentration.pumpDescription).thenReturn(PumpDescription())

        //SafetyPlugin
        constraintChecker = ConstraintsCheckerImpl(activePlugin, aapsLogger, ch, text)

        // The real formatter rather than a mock: it is pure arithmetic over a duration, and the
        // objectives only read it for display.
        val durationText = PlainDurationText()
        val objectives = listOf(
            Objective0(preferences, text, durationText, dateUtil, activePlugin, virtualPumpPlugin, persistenceLayer, loop, iobCobCalculator, passwordCheck),
            Objective1(preferences, text, durationText, dateUtil),
            Objective2(preferences, text, durationText, dateUtil),
            Objective3(preferences, text, durationText, dateUtil),
            Objective4(preferences, text, durationText, dateUtil, profileFunction),
            Objective5(preferences, text, durationText, dateUtil),
            Objective6(preferences, text, durationText, dateUtil, constraintsChecker, loop),
            Objective7(preferences, text, durationText, dateUtil),
            Objective8(preferences, text, durationText, dateUtil),
            Objective9(preferences, text, durationText, dateUtil)
        )
        objectivesPlugin = ObjectivesPlugin(aapsLogger, text, preferences, config, objectives, mock())
        runBlocking { objectivesPlugin.onStart() }
        openAPSSMBPlugin =
            OpenAPSSMBPlugin(
                aapsLogger, rxBus, constraintChecker, text, profileFunction, profileUtil, config, activePlugin, iobCobCalculator,
                hardLimits, preferences, dateUtil, processedTbrEbData, persistenceLayer, smbGlucoseStatusProvider, tddCalculator, bgQualityCheck,
                notificationManager, determineBasalSMB, profiler, GlucoseStatusCalculatorSMB(aapsLogger, iobCobCalculator, dateUtil, decimalFormatter, deltaCalculator), { apsResultProvider() }, ch,
                fabricPrivacy
            )
        openAPSAMAPlugin =
            OpenAPSAMAPlugin(
                aapsLogger, rxBus, constraintChecker, text, config, profileFunction, activePlugin, iobCobCalculator, processedTbrEbData,
                hardLimits, dateUtil, persistenceLayer, smbGlucoseStatusProvider, preferences, determineBasalAMA,
                GlucoseStatusCalculatorSMB(aapsLogger, iobCobCalculator, dateUtil, decimalFormatter, deltaCalculator), { apsResultProvider() }, ch, fabricPrivacy, mock()
            )
        safetyPlugin =
            SafetyPlugin(
                aapsLogger, text, preferences, constraintChecker, activePlugin, hardLimits,
                config, persistenceLayer, dateUtil, notificationManager, decimalFormatter
            )
        val constraintsPluginsList = ArrayList<PluginBase>()
        constraintsPluginsList.add(safetyPlugin)
        constraintsPluginsList.add(objectivesPlugin)
        // Pump plugins are no longer PluginConstraints — their cU delivery caps are PumpPluginConstraints,
        // folded into the scan by ConstraintsCheckerImpl via activePumpInternal (stubbed per test).
        constraintsPluginsList.add(openAPSAMAPlugin)
        constraintsPluginsList.add(openAPSSMBPlugin)
        whenever(activePlugin.getSpecificPluginsListByInterface(PluginConstraints::class)).thenReturn(constraintsPluginsList)
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
    fun isClosedLoopAllowedTest() = runTest {
        whenever(config.isEngineeringModeOrRelease()).thenReturn(true)
        whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP)
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
    fun isAdvancedFilteringEnabledTest() = runTest {
        whenever(persistenceLayer.isAdvancedFilteringSupported()).thenReturn(false)
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
    fun isSMBModeEnabledTest() = runTest {
        openAPSSMBPlugin.setPluginEnabledBlocking(PluginType.APS, true)
        objectivesPlugin.objectives[Objectives.SMB_OBJECTIVE].startedOn = 0
        whenever(preferences.get(BooleanKey.ApsUseSmb)).thenReturn(false)
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
//        whenever(constraintChecker.isClosedLoopAllowed()).thenReturn(ConstraintObject(true))
        val c = constraintChecker.isSMBModeEnabled()
        assertThat(c.reasonList).hasSize(3) // 2x Safety & Objectives
        assertThat(c.mostLimitedReasonList).hasSize(3) // 2x Safety & Objectives
        assertThat(c.value()).isFalse()
    }

    // applyBasalConstraints tests
    @Test
    fun basalRateShouldBeLimited() {
        val pump = CappedPumpPlugin(maxBasal = 0.8)
        whenever(pumpWithConcentration.activePumpInternal).thenReturn(pump)
        // The active pump's cU cap is folded into the IU scan by ConstraintsChecker via activePumpInternal.
        whenever(activePlugin.activePumpInternal).thenReturn(pump)

        // No limit by default
        whenever(preferences.get(DoubleKey.ApsMaxBasal)).thenReturn(1.0)
        whenever(preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier)).thenReturn(4.0)
        whenever(preferences.get(DoubleKey.ApsMaxDailyMultiplier)).thenReturn(3.0)
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("child")

        // Apply all limits
        val d = constraintChecker.getMaxBasalAllowed(validProfile)
        assertThat(d.value()).isWithin(0.01).of(0.8)
        // Safety hard-limit + the active pump's cU cap, folded into the IU scan by ConstraintsChecker.
        assertThat(d.reasonList).hasSize(2)
        assertThat(d.getMostLimitedReasons()).isEqualTo("CappedPump: Limiting max basal rate to 0.80 U/h because of pump limit")
    }

    @Test
    fun percentBasalRateShouldBeLimited() {
        whenever(pumpWithConcentration.activePumpInternal).thenReturn(CappedPumpPlugin(maxBasal = 0.8))

        // No limit by default
        whenever(preferences.get(DoubleKey.ApsMaxBasal)).thenReturn(1.0)
        whenever(preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier)).thenReturn(4.0)
        whenever(preferences.get(DoubleKey.ApsMaxDailyMultiplier)).thenReturn(3.0)
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("child")

        // Apply all limits
        val i = constraintChecker.getMaxBasalPercentAllowed(validProfile)
        assertThat(i.value()).isEqualTo(200)
        // Pump plugins no longer contribute percent reasons — their percent cap was redundant with SafetyPlugin,
        // which still caps to the same value (tbrSettings.maxDose); remaining reasons are all from SafetyPlugin.
        assertThat(i.reasonList).hasSize(4)
        assertThat(i.getMostLimitedReasons()).isEqualTo("Safety: Limiting max percent rate to 200% because of pump limit")
    }

    // applyBolusConstraints tests
    @Test
    fun bolusAmountShouldBeLimited() {
        whenever(pumpWithConcentration.activePumpInternal).thenReturn(virtualPumpPlugin)
        whenever(virtualPumpPlugin.pumpDescription).thenReturn(PumpDescription())

        // No limit by default
        whenever(preferences.get(DoubleKey.SafetyMaxBolus)).thenReturn(3.0)
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("child")

        // Apply all limits
        val d = constraintChecker.getMaxBolusAllowed()
        assertThat(d.value()).isWithin(0.01).of(3.0)
        // 2x Safety only. A pump's own bolus cap is folded in the same way as the basal cap, but the active
        // pump here is the virtual pump, which has none.
        assertThat(d.reasonList).hasSize(2)
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
    fun iobAMAShouldBeLimited() = runTest {
        // No limit by default
        whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP)
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
    fun iobSMBShouldBeLimited() = runTest {
        // No limit by default
        whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP)
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
