package info.nightscout.androidaps.utils.wizard

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
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
@PrepareForTest(ConstraintChecker::class, VirtualPumpPlugin::class)
class BolusWizardTest : TestBase() {

    private val PUMP_BOLUS_STEP = 0.1

    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var context: Context
    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var loopPlugin: LoopPlugin
    @Mock lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin
    @Mock lateinit var treatmentsPlugin: TreatmentsPlugin
    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin

    val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is BolusWizard) {
                it.aapsLogger = aapsLogger
                it.resourceHelper = resourceHelper
                it.rxBus = RxBusWrapper()
                it.profileFunction = profileFunction
                it.constraintChecker = constraintChecker
                it.activePlugin = activePlugin
                it.commandQueue = commandQueue
                it.loopPlugin = loopPlugin
                it.iobCobCalculatorPlugin = iobCobCalculatorPlugin
            }
            if (it is GlucoseStatus) {
                it.aapsLogger = aapsLogger
                it.iobCobCalculatorPlugin = iobCobCalculatorPlugin
            }
        }
    }

    private fun setupProfile(targetLow: Double, targetHigh: Double, insulinSensitivityFactor: Double, insulinToCarbRatio: Double): Profile {
        val profile = Mockito.mock(Profile::class.java)
        `when`(profile.targetLowMgdl).thenReturn(targetLow)
        `when`(profile.targetHighMgdl).thenReturn(targetHigh)
        `when`(profile.isfMgdl).thenReturn(insulinSensitivityFactor)
        `when`(profile.ic).thenReturn(insulinToCarbRatio)

        `when`(profileFunction.getUnits()).thenReturn(Constants.MGDL)
        `when`(iobCobCalculatorPlugin.dataLock).thenReturn(Unit)
        `when`(activePlugin.activeTreatments).thenReturn(treatmentsPlugin)
        `when`(treatmentsPlugin.lastCalculationTreatments).thenReturn(IobTotal(System.currentTimeMillis()))
        `when`(treatmentsPlugin.lastCalculationTempBasals).thenReturn(IobTotal(System.currentTimeMillis()))
        `when`(activePlugin.activePump).thenReturn(virtualPumpPlugin)
        val pumpDescription = PumpDescription()
        pumpDescription.bolusStep = PUMP_BOLUS_STEP
        `when`(virtualPumpPlugin.pumpDescription).thenReturn(pumpDescription)

        Mockito.doAnswer { invocation: InvocationOnMock ->
            invocation.getArgument<Constraint<Double>>(0)
        }.`when`(constraintChecker).applyBolusConstraints(anyObject())
        return profile
    }

    @Test
        /** Should calculate the same bolus when different blood glucose but both in target range  */
    fun shouldCalculateTheSameBolusWhenBGsInRange() {
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0)
        var bw = BolusWizard(injector).doCalc(profile, "", null, 20, 0.0, 4.2, 0.0, 100.0, true, true, true, true, false, false, false, false)
        val bolusForBg42 = bw.calculatedTotalInsulin
        bw = BolusWizard(injector).doCalc(profile, "", null, 20, 0.0, 5.4, 0.0, 100.0, true, true, true, true, false, false, false, false)
        val bolusForBg54 = bw.calculatedTotalInsulin
        Assert.assertEquals(bolusForBg42, bolusForBg54, 0.01)
    }

    @Test
    fun shouldCalculateHigherBolusWhenHighBG() {
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0)
        var bw = BolusWizard(injector).doCalc(profile, "", null, 20, 0.0, 9.8, 0.0, 100.0, true, true, true, true, false, false, false, false)
        val bolusForHighBg = bw.calculatedTotalInsulin
        bw = BolusWizard(injector).doCalc(profile, "", null, 20, 0.0, 5.4, 0.0, 100.0, true, true, true, true, false, false, false, false)
        val bolusForBgInRange = bw.calculatedTotalInsulin
        Assert.assertTrue(bolusForHighBg > bolusForBgInRange)
    }

    @Test
    fun shouldCalculateLowerBolusWhenLowBG() {
        val profile = setupProfile(4.0, 8.0, 20.0, 12.0)
        var bw = BolusWizard(injector).doCalc(profile, "", null, 20, 0.0, 3.6, 0.0, 100.0, true, true, true, true, false, false, false, false)
        val bolusForLowBg = bw.calculatedTotalInsulin
        bw = BolusWizard(injector).doCalc(profile, "", null, 20, 0.0, 5.4, 0.0, 100.0, true, true, true, true, false, false, false, false)
        val bolusForBgInRange = bw.calculatedTotalInsulin
        Assert.assertTrue(bolusForLowBg < bolusForBgInRange)
    }
}