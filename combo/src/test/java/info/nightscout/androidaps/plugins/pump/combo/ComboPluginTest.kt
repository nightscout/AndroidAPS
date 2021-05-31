package info.nightscout.androidaps.plugins.pump.combo

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.combo.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history.Bolus
import info.nightscout.androidaps.utils.DateUtil
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
@PrepareForTest(Context::class)
class ComboPluginTest : TestBase() {

    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var sp: SP
    @Mock lateinit var context: Context
    @Mock lateinit var dateUtil: DateUtil

    val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is PumpEnactResult) {
                it.resourceHelper = resourceHelper
            }
        }
    }

    private lateinit var comboPlugin: ComboPlugin

    @Before
    fun prepareMocks() {
        `when`(resourceHelper.gs(R.string.novalidbasalrate)).thenReturn("No valid basal rate read from pump")
        `when`(resourceHelper.gs(R.string.combo_pump_unsupported_operation)).thenReturn("Requested operation not supported by pump")
        comboPlugin = ComboPlugin(injector, aapsLogger, RxBusWrapper(aapsSchedulers), resourceHelper, profileFunction, sp, commandQueue, context, pumpSync, dateUtil)
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
    fun `generate bolus ID from timestamp and amount`() {
        val now = System.currentTimeMillis()
        val pumpTimestamp = now - now % 1000
        // same timestamp, different bolus leads to different fake timestamp
        Assert.assertNotEquals(
            comboPlugin.generatePumpBolusId(Bolus(pumpTimestamp, 0.1, true)),
            comboPlugin.generatePumpBolusId(Bolus(pumpTimestamp, 0.3, true))
        )
        // different timestamp, same bolus leads to different fake timestamp
        Assert.assertNotEquals(
            comboPlugin.generatePumpBolusId(Bolus(pumpTimestamp, 0.3, true)),
            comboPlugin.generatePumpBolusId(Bolus(pumpTimestamp + 60 * 1000, 0.3, true))
        )
        // generated timestamp has second-precision
        val bolus = Bolus(pumpTimestamp, 0.2, true)
        val calculatedTimestamp = comboPlugin.generatePumpBolusId(bolus)
        Assert.assertEquals(calculatedTimestamp, calculatedTimestamp - calculatedTimestamp % 1000)
    }
}