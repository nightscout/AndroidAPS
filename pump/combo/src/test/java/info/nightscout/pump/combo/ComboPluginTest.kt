package info.nightscout.pump.combo

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.pump.combo.ruffyscripter.RuffyScripter
import info.nightscout.pump.combo.ruffyscripter.history.Bolus
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class ComboPluginTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var sp: SP
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var ruffyScripter: RuffyScripter
    @Mock lateinit var context: Context
    @Mock lateinit var uiInteraction: UiInteraction

    private val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is PumpEnactResult) {
                it.context = context
            }
        }
    }

    private lateinit var comboPlugin: ComboPlugin

    @BeforeEach
    fun prepareMocks() {
        `when`(rh.gs(info.nightscout.core.ui.R.string.no_valid_basal_rate)).thenReturn("No valid basal rate read from pump")
        `when`(context.getString(R.string.combo_pump_unsupported_operation)).thenReturn("Requested operation not supported by pump")
        comboPlugin = ComboPlugin(injector, aapsLogger, RxBus(aapsSchedulers, aapsLogger), rh, profileFunction, sp, commandQueue, pumpSync, dateUtil, ruffyScripter, uiInteraction)
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