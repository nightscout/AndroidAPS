package info.nightscout.androidaps.plugins.pump.combo

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history.Bolus
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.queue.CommandQueue
import info.nightscout.androidaps.utils.resources.ResourceHelper
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
@PrepareForTest(MainApp::class, ConfigBuilderPlugin::class, ConstraintChecker::class, Context::class, CommandQueue::class)
class ComboPluginTest : TestBase() {

    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var treatmentsPlugin: TreatmentsPlugin
    @Mock lateinit var sp: SP
    @Mock lateinit var context: Context

    val injector = HasAndroidInjector {
        AndroidInjector {
        }
    }

    private lateinit var comboPlugin: ComboPlugin

    @Before
    fun prepareMocks() {
        `when`(resourceHelper.gs(R.string.novalidbasalrate)).thenReturn("No valid basal rate read from pump")
        comboPlugin = ComboPlugin(injector, aapsLogger, RxBusWrapper(aapsSchedulers), resourceHelper, profileFunction, treatmentsPlugin, sp, commandQueue, context)
    }

    @Test
    fun invalidBasalRateOnComboPumpShouldLimitLoopInvocation() {
        comboPlugin.setPluginEnabled(PluginType.PUMP, true)
        comboPlugin.setValidBasalRateProfileSelectedOnPump(false)
        var c = Constraint(true)
        c = comboPlugin.isLoopInvocationAllowed(c)
        Assert.assertEquals("Combo: No valid basal rate read from pump", c.getReasons(aapsLogger))
        Assert.assertEquals(false, c.value())
        comboPlugin.setPluginEnabled(PluginType.PUMP, false)
    }

    @Test
    fun calculateFakePumpTimestamp() {
        val now = System.currentTimeMillis()
        val pumpTimestamp = now - now % 1000
        // same timestamp, different bolus leads to different fake timestamp
        Assert.assertNotEquals(
            comboPlugin.calculateFakeBolusDate(Bolus(pumpTimestamp, 0.1, true)),
            comboPlugin.calculateFakeBolusDate(Bolus(pumpTimestamp, 0.3, true))
        )
        // different timestamp, same bolus leads to different fake timestamp
        Assert.assertNotEquals(
            comboPlugin.calculateFakeBolusDate(Bolus(pumpTimestamp, 0.3, true)),
            comboPlugin.calculateFakeBolusDate(Bolus(pumpTimestamp + 60 * 1000, 0.3, true))
        )
        // generated timestamp has second-precision
        val bolus = Bolus(pumpTimestamp, 0.2, true)
        val calculatedTimestamp = comboPlugin.calculateFakeBolusDate(bolus)
        Assert.assertEquals(calculatedTimestamp, calculatedTimestamp - calculatedTimestamp % 1000)
    }
}