package app.aaps.implementation.wizard

import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.wizard.BolusWizard
import app.aaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BolusWizardTest : TestBaseWithProfile() {

    private val pumpBolusStep = 0.1

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var loop: Loop
    @Mock lateinit var autosensDataStore: AutosensDataStore
    @Mock lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Mock lateinit var openAPSSMBPlugin: OpenAPSSMBPlugin
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var automation: Automation
    @Mock lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var persistenceLayer: PersistenceLayer

    @BeforeEach
    fun prepare() {
        whenever(activePlugin.activeAPS).thenReturn(openAPSSMBPlugin)
    }

    /**
     * Setup profile for testing.
     * All values are in mg/dL (profileUtil.fromMgdlToUnits is identity with default MGDL units).
     * @param bolusIob bolus IOB value (positive = active insulin on board)
     * @param basalIob basal IOB value
     */
    private fun setupProfile(
        targetLow: Double, targetHigh: Double,
        insulinSensitivityFactor: Double, insulinToCarbRatio: Double,
        bolusIob: Double = 0.0, basalIob: Double = 0.0
    ): Profile {
        val profile: Profile = mock()
        whenever(profile.getTargetLowMgdl()).thenReturn(targetLow)
        whenever(profile.getTargetHighMgdl()).thenReturn(targetHigh)
        whenever(profile.getIsfMgdlForCarbs(any(), any(), any(), any())).thenReturn(insulinSensitivityFactor)
        whenever(profile.getIc()).thenReturn(insulinToCarbRatio)

        val bolusIobTotal = IobTotal(System.currentTimeMillis()).also { it.iob = bolusIob }
        val basalIobTotal = IobTotal(System.currentTimeMillis()).also { it.basaliob = basalIob }
        runBlocking { whenever(iobCobCalculator.calculateIobFromBolus()).thenReturn(bolusIobTotal) }
        runBlocking { whenever(iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended()).thenReturn(basalIobTotal) }

        testPumpPlugin.pumpDescription = PumpDescription().also {
            it.bolusStep = pumpBolusStep
        }
        whenever(iobCobCalculator.ads).thenReturn(autosensDataStore)

        doAnswer { invocation: InvocationOnMock ->
            invocation.getArgument<Constraint<Double>>(0)
        }.whenever(constraintChecker).applyBolusConstraints(anyOrNull())

        whenever(constraintChecker.getMaxBolusAllowed()).thenReturn(ConstraintObject(100.0, aapsLogger))

        return profile
    }

    private fun createWizard() = BolusWizard(
        aapsLogger, rh, rxBus, preferences, profileFunction, profileUtil, constraintChecker, activePlugin,
        commandQueue, loop, iobCobCalculator, dateUtil, config, uel, automation, glucoseStatusProvider, uiInteraction,
        persistenceLayer, decimalFormatter, processedDeviceStatusData
    )

    // ==========================================
    // Group 1: Basic tests (existing, kept as-is)
    // ==========================================

    @Test
        /** Should calculate the same bolus when different blood glucose but both in target range */
    fun shouldCalculateTheSameBolusWhenBGsInRange() = runBlocking {
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0)
        var bw = createWizard().doCalc(
            profile, "", null, 20, 0.0, 4.2, 0.0, 100,
            useBg = true, useCob = true, includeBolusIOB = true, includeBasalIOB = true,
            useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false
        )
        val bolusForBg42 = bw.calculatedTotalInsulin
        bw = createWizard().doCalc(
            profile, "", null, 20, 0.0, 5.4, 0.0, 100,
            useBg = true, useCob = true, includeBolusIOB = true, includeBasalIOB = true,
            useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false
        )
        val bolusForBg54 = bw.calculatedTotalInsulin
        assertThat(bolusForBg54).isWithin(0.01).of(bolusForBg42)
    }

    @Test
    fun shouldCalculateHigherBolusWhenHighBG() = runBlocking {
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0)
        var bw = createWizard().doCalc(
            profile, "", null, 20, 0.0, 9.8, 0.0, 100,
            useBg = true, useCob = true, includeBolusIOB = true, includeBasalIOB = true,
            useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false
        )
        val bolusForHighBg = bw.calculatedTotalInsulin
        bw = createWizard().doCalc(
            profile, "", null, 20, 0.0, 5.4, 0.0, 100,
            useBg = true, useCob = true, includeBolusIOB = true, includeBasalIOB = true,
            useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false
        )
        val bolusForBgInRange = bw.calculatedTotalInsulin
        assertThat(bolusForHighBg).isGreaterThan(bolusForBgInRange)
    }

    @Test
    fun shouldCalculateLowerBolusWhenLowBG() = runBlocking {
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0)
        var bw = createWizard().doCalc(
            profile, "", null, 20, 0.0, 3.6, 0.0, 100,
            useBg = true, useCob = true, includeBolusIOB = true, includeBasalIOB = true,
            useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false
        )
        val bolusForLowBg = bw.calculatedTotalInsulin
        bw = createWizard().doCalc(
            profile, "", null, 20, 0.0, 5.4, 0.0, 100,
            useBg = true, useCob = true, includeBolusIOB = true, includeBasalIOB = true,
            useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false
        )
        val bolusForBgInRange = bw.calculatedTotalInsulin
        assertThat(bolusForLowBg).isLessThan(bolusForBgInRange)
    }

    // ==========================================
    // Group 2: Individual Components (pct=100)
    // ==========================================

    @Test
    fun carbsOnlyNoCorrection() = runBlocking {
        // carbs=60, bg=0, IOB=0 → insulinFromCarbs=60/12=5.0
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0)
        val bw = createWizard().doCalc(
            profile, "", null, carbs = 60, cob = 0.0, bg = 0.0, correction = 0.0, percentageCorrection = 100,
            useBg = false, useCob = false, includeBolusIOB = false, includeBasalIOB = false,
            useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false
        )
        assertThat(bw.insulinFromCarbs).isWithin(0.001).of(5.0)
        assertThat(bw.calculatedTotalInsulin).isWithin(0.001).of(5.0)
    }

    @Test
    fun bgCorrectionHighOnly() = runBlocking {
        // bg=9.8, bgDiff=9.8-8.0=1.8, insulinFromBG=1.8/20=0.09, rounded to 0.1
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0)
        val bw = createWizard().doCalc(
            profile, "", null, carbs = 0, cob = 0.0, bg = 9.8, correction = 0.0, percentageCorrection = 100,
            useBg = true, useCob = false, includeBolusIOB = false, includeBasalIOB = false,
            useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false
        )
        assertThat(bw.insulinFromBG).isWithin(0.001).of(0.09) // 1.8/20
        assertThat(bw.calculatedTotalInsulin).isWithin(0.001).of(0.1) // rounded to 0.1 step
    }

    @Test
    fun bgCorrectionLowOnly() = runBlocking {
        // bg=3.0, bgDiff=3.0-4.0=-1.0, insulinFromBG=-1.0/20=-0.05
        // total<0 → clamped to 0, carbsEquivalent=0.05*12=0.6
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0)
        val bw = createWizard().doCalc(
            profile, "", null, carbs = 0, cob = 0.0, bg = 3.0, correction = 0.0, percentageCorrection = 100,
            useBg = true, useCob = false, includeBolusIOB = false, includeBasalIOB = false,
            useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false
        )
        assertThat(bw.insulinFromBG).isWithin(0.001).of(-0.05)
        assertThat(bw.calculatedTotalInsulin).isWithin(0.001).of(0.0) // clamped
        assertThat(bw.carbsEquivalent).isWithin(0.001).of(0.6) // 0.05 * 12
    }

    @Test
    fun iobSubtractsFromTotal() = runBlocking {
        // carbs=60 → 5.0U, IOB bolus=2.0 → -2.0, total=3.0
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0, bolusIob = 2.0)
        val bw = createWizard().doCalc(
            profile, "", null, carbs = 60, cob = 0.0, bg = 0.0, correction = 0.0, percentageCorrection = 100,
            useBg = false, useCob = false, includeBolusIOB = true, includeBasalIOB = true,
            useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false
        )
        assertThat(bw.insulinFromCarbs).isWithin(0.001).of(5.0)
        assertThat(bw.insulinFromBolusIOB).isWithin(0.001).of(2.0)
        assertThat(bw.calculatedTotalInsulin).isWithin(0.001).of(3.0) // 5.0 - 2.0
    }

    @Test
    fun directCorrectionAddsToTotal() = runBlocking {
        // carbs=60 → 5.0U, correction=0.5, total=5.5
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0)
        val bw = createWizard().doCalc(
            profile, "", null, carbs = 60, cob = 0.0, bg = 0.0, correction = 0.5, percentageCorrection = 100,
            useBg = false, useCob = false, includeBolusIOB = false, includeBasalIOB = false,
            useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false
        )
        assertThat(bw.insulinFromCarbs).isWithin(0.001).of(5.0)
        assertThat(bw.insulinFromCorrection).isWithin(0.001).of(0.5)
        assertThat(bw.calculatedTotalInsulin).isWithin(0.001).of(5.5)
    }

    @Test
    fun cobAddsToTotal() = runBlocking {
        // cob=24, useCob=true → insulinFromCOB=24/12=2.0
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0)
        val bw = createWizard().doCalc(
            profile, "", null, carbs = 0, cob = 24.0, bg = 0.0, correction = 0.0, percentageCorrection = 100,
            useBg = false, useCob = true, includeBolusIOB = false, includeBasalIOB = false,
            useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false
        )
        assertThat(bw.insulinFromCOB).isWithin(0.001).of(2.0)
        assertThat(bw.calculatedTotalInsulin).isWithin(0.001).of(2.0)
    }

    // ==========================================
    // Group 3: Current Percentage Behavior
    // (These tests verify current behavior that will change in Phase 2)
    // ==========================================

    @Test
    fun percentageScalesSuggestionsOnly() = runBlocking {
        // carbs=60 → 5.0, bg=9.8 → 0.09, IOB=1.0 → -1.0
        // NEW: scaled = (5.0 + 0.09) * 0.5 = 2.545, unscaled = -1.0
        // total = 2.545 - 1.0 = 1.545 → rounded to 1.5
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0, bolusIob = 1.0)
        val bw = createWizard().doCalc(
            profile, "", null, carbs = 60, cob = 0.0, bg = 9.8, correction = 0.0, percentageCorrection = 50,
            useBg = true, useCob = false, includeBolusIOB = true, includeBasalIOB = true,
            useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false
        )
        assertThat(bw.totalBeforePercentageAdjustment).isWithin(0.001).of(4.09)
        // NEW: only suggestions scaled → (5.0 + 0.09)*0.5 + (-1.0) = 1.545 → 1.5
        assertThat(bw.calculatedTotalInsulin).isWithin(0.001).of(1.5)
    }

    @Test
    fun percentageDoesNotScaleIOB() = runBlocking {
        // carbs=60 → 5.0, IOB=2.0 → -2.0
        // NEW: scaled = 5.0 * 0.5 = 2.5, unscaled = -2.0
        // total = 2.5 - 2.0 = 0.5
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0, bolusIob = 2.0)
        val bw = createWizard().doCalc(
            profile, "", null, carbs = 60, cob = 0.0, bg = 0.0, correction = 0.0, percentageCorrection = 50,
            useBg = false, useCob = false, includeBolusIOB = true, includeBasalIOB = true,
            useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false
        )
        assertThat(bw.totalBeforePercentageAdjustment).isWithin(0.001).of(3.0)
        // NEW: IOB NOT scaled → 5.0*0.5 + (-2.0) = 0.5
        assertThat(bw.calculatedTotalInsulin).isWithin(0.001).of(0.5)
    }

    @Test
    fun directCorrectionNotScaledByPercentage() = runBlocking {
        // carbs=60 → 5.0, correction=1.0, pct=80
        // NEW: scaled = 5.0 * 0.8 = 4.0, unscaled = 1.0
        // total = 4.0 + 1.0 = 5.0
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0)
        val bw = createWizard().doCalc(
            profile, "", null, carbs = 60, cob = 0.0, bg = 0.0, correction = 1.0, percentageCorrection = 80,
            useBg = false, useCob = false, includeBolusIOB = false, includeBasalIOB = false,
            useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false
        )
        assertThat(bw.totalBeforePercentageAdjustment).isWithin(0.001).of(6.0)
        // NEW: correction NOT scaled → 5.0*0.8 + 1.0 = 5.0
        assertThat(bw.calculatedTotalInsulin).isWithin(0.001).of(5.0)
    }

    @Test
    fun usePercentageModeScalesTotal() = runBlocking {
        // usePercentage=true, totalPercentage=70, carbs=60 → 5.0
        // total_before = 5.0
        // pct=70 → 5.0 * 0.7 = 3.5
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0)
        val bw = createWizard().doCalc(
            profile, "", null, carbs = 60, cob = 0.0, bg = 0.0, correction = 0.0, percentageCorrection = 100,
            useBg = false, useCob = false, includeBolusIOB = false, includeBasalIOB = false,
            useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false,
            usePercentage = true, totalPercentage = 70.0
        )
        assertThat(bw.totalBeforePercentageAdjustment).isWithin(0.001).of(5.0)
        assertThat(bw.calculatedTotalInsulin).isWithin(0.001).of(3.5)
    }

    // ==========================================
    // Group 4: Edge Cases
    // ==========================================

    @Test
    fun negativeTotalSetsCarbsEquivalent() = runBlocking {
        // bg=3.0 (low), IOB=0 → insulinFromBG=-0.05, total<0, carbsEquivalent>0
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0)
        val bw = createWizard().doCalc(
            profile, "", null, carbs = 0, cob = 0.0, bg = 3.0, correction = 0.0, percentageCorrection = 100,
            useBg = true, useCob = false, includeBolusIOB = false, includeBasalIOB = false,
            useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false
        )
        assertThat(bw.calculatedTotalInsulin).isWithin(0.001).of(0.0)
        assertThat(bw.carbsEquivalent).isGreaterThan(0.0)
    }

    @Test
    fun roundingToBolusStep() = runBlocking {
        // carbs=25 → 25/12 = 2.0833, rounded to 0.1 step → 2.1
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0)
        val bw = createWizard().doCalc(
            profile, "", null, carbs = 25, cob = 0.0, bg = 0.0, correction = 0.0, percentageCorrection = 100,
            useBg = false, useCob = false, includeBolusIOB = false, includeBasalIOB = false,
            useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false
        )
        assertThat(bw.calculatedTotalInsulin).isWithin(0.001).of(2.1)
    }

    @Test
    fun percentage100NoChange() = runBlocking {
        // carbs=60, pct=100 → same result as without percentage
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0)
        val bw = createWizard().doCalc(
            profile, "", null, carbs = 60, cob = 0.0, bg = 0.0, correction = 0.0, percentageCorrection = 100,
            useBg = false, useCob = false, includeBolusIOB = false, includeBasalIOB = false,
            useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false
        )
        assertThat(bw.calculatedTotalInsulin).isWithin(0.001).of(5.0)
        assertThat(bw.totalBeforePercentageAdjustment).isWithin(0.001).of(5.0)
    }
}
