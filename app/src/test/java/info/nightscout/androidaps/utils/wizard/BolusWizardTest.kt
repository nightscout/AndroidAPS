package info.nightscout.androidaps.utils.wizard

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensDataStore
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatusProvider
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.invocation.InvocationOnMock
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(ConstraintChecker::class, VirtualPumpPlugin::class, DateUtil::class, AutosensDataStore::class)
class BolusWizardTest : TestBase() {

    private val pumpBolusStep = 0.1

    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var context: Context
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var loopPlugin: LoopPlugin
    @Mock lateinit var iobCobCalculator: IobCobCalculator
    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var autosensDataStore: AutosensDataStore

    val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is BolusWizard) {
                it.aapsLogger = aapsLogger
                it.resourceHelper = resourceHelper
                it.rxBus = RxBusWrapper(aapsSchedulers)
                it.profileFunction = profileFunction
                it.constraintChecker = constraintChecker
                it.activePlugin = activePlugin
                it.commandQueue = commandQueue
                it.loopPlugin = loopPlugin
                it.iobCobCalculator = iobCobCalculator
                it.glucoseStatusProvider = GlucoseStatusProvider(aapsLogger = aapsLogger, iobCobCalculator = iobCobCalculator, dateUtil = dateUtil)
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun setupProfile(targetLow: Double, targetHigh: Double, insulinSensitivityFactor: Double, insulinToCarbRatio: Double): Profile {
        val profile = Mockito.mock(Profile::class.java)
        `when`(profile.getTargetLowMgdl()).thenReturn(targetLow)
        `when`(profile.getTargetLowMgdl()).thenReturn(targetHigh)
        `when`(profile.getIsfMgdl()).thenReturn(insulinSensitivityFactor)
        `when`(profile.getIc()).thenReturn(insulinToCarbRatio)

        `when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        `when`(iobCobCalculator.calculateIobFromBolus()).thenReturn(IobTotal(System.currentTimeMillis()))
        `when`(iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended()).thenReturn(IobTotal(System.currentTimeMillis()))
        `when`(activePlugin.activePump).thenReturn(virtualPumpPlugin)
        val pumpDescription = PumpDescription()
        pumpDescription.bolusStep = pumpBolusStep
        `when`(virtualPumpPlugin.pumpDescription).thenReturn(pumpDescription)
        `when`(iobCobCalculator.ads).thenReturn(autosensDataStore)

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