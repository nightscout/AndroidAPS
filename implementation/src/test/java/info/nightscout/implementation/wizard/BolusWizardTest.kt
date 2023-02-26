package info.nightscout.implementation.wizard

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.TestPumpPlugin
import info.nightscout.core.wizard.BolusWizard
import info.nightscout.implementation.iob.GlucoseStatusProviderImpl
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.aps.AutosensDataStore
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.iob.IobTotal
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.pump.defs.PumpDescription
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import org.junit.Assert
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock

class BolusWizardTest : TestBase() {

    private val pumpBolusStep = 0.1

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var constraintChecker: Constraints
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var loop: Loop
    @Mock lateinit var iobCobCalculator: IobCobCalculator
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var autosensDataStore: AutosensDataStore

    val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is BolusWizard) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.rxBus = RxBus(aapsSchedulers, aapsLogger)
                it.profileFunction = profileFunction
                it.constraintChecker = constraintChecker
                it.activePlugin = activePlugin
                it.commandQueue = commandQueue
                it.loop = loop
                it.dateUtil = dateUtil
                it.iobCobCalculator = iobCobCalculator
                it.glucoseStatusProvider = GlucoseStatusProviderImpl(aapsLogger = aapsLogger, iobCobCalculator = iobCobCalculator, dateUtil = dateUtil)
            }
        }
    }

    val testPumpPlugin = TestPumpPlugin(injector)

    @Suppress("SameParameterValue")
    private fun setupProfile(targetLow: Double, targetHigh: Double, insulinSensitivityFactor: Double, insulinToCarbRatio: Double): Profile {
        val profile = Mockito.mock(Profile::class.java)
        Mockito.`when`(profile.getTargetLowMgdl()).thenReturn(targetLow)
        Mockito.`when`(profile.getTargetLowMgdl()).thenReturn(targetHigh)
        Mockito.`when`(profile.getIsfMgdl()).thenReturn(insulinSensitivityFactor)
        Mockito.`when`(profile.getIc()).thenReturn(insulinToCarbRatio)

        Mockito.`when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        Mockito.`when`(iobCobCalculator.calculateIobFromBolus()).thenReturn(IobTotal(System.currentTimeMillis()))
        Mockito.`when`(iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended()).thenReturn(IobTotal(System.currentTimeMillis()))
        Mockito.`when`(activePlugin.activePump).thenReturn(testPumpPlugin)
        testPumpPlugin.pumpDescription = PumpDescription().also {
            it.bolusStep = pumpBolusStep
        }
        Mockito.`when`(iobCobCalculator.ads).thenReturn(autosensDataStore)

        Mockito.doAnswer { invocation: InvocationOnMock ->
            invocation.getArgument<Constraint<Double>>(0)
        }.`when`(constraintChecker).applyBolusConstraints(anyObject())
        return profile
    }

    @Test
        /** Should calculate the same bolus when different blood glucose but both in target range  */
    fun shouldCalculateTheSameBolusWhenBGsInRange() {
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0)
        var bw = BolusWizard(injector).doCalc(profile, "", null, 20, 0.0, 4.2, 0.0, 100, useBg = true, useCob = true, includeBolusIOB = true, includeBasalIOB = true, useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false)
        val bolusForBg42 = bw.calculatedTotalInsulin
        bw = BolusWizard(injector).doCalc(profile, "", null, 20, 0.0, 5.4, 0.0, 100, useBg = true, useCob = true, includeBolusIOB = true, includeBasalIOB = true, useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false)
        val bolusForBg54 = bw.calculatedTotalInsulin
        Assert.assertEquals(bolusForBg42, bolusForBg54, 0.01)
    }

    @Test
    fun shouldCalculateHigherBolusWhenHighBG() {
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0)
        var bw = BolusWizard(injector).doCalc(profile, "", null, 20, 0.0, 9.8, 0.0, 100, useBg = true, useCob = true, includeBolusIOB = true, includeBasalIOB = true, useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false)
        val bolusForHighBg = bw.calculatedTotalInsulin
        bw = BolusWizard(injector).doCalc(profile, "", null, 20, 0.0, 5.4, 0.0, 100, useBg = true, useCob = true, includeBolusIOB = true, includeBasalIOB = true, useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false)
        val bolusForBgInRange = bw.calculatedTotalInsulin
        Assert.assertTrue(bolusForHighBg > bolusForBgInRange)
    }

    @Test
    fun shouldCalculateLowerBolusWhenLowBG() {
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0)
        var bw = BolusWizard(injector).doCalc(profile, "", null, 20, 0.0, 3.6, 0.0, 100, useBg = true, useCob = true, includeBolusIOB = true, includeBasalIOB = true, useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false)
        val bolusForLowBg = bw.calculatedTotalInsulin
        bw = BolusWizard(injector).doCalc(profile, "", null, 20, 0.0, 5.4, 0.0, 100, useBg = true, useCob = true, includeBolusIOB = true, includeBasalIOB = true, useSuperBolus = false, useTT = false, useTrend = false, useAlarm = false)
        val bolusForBgInRange = bw.calculatedTotalInsulin
        Assert.assertTrue(bolusForLowBg < bolusForBgInRange)
    }
}