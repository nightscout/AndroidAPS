package info.nightscout.pump.combo

import android.content.Context
import app.aaps.core.main.constraints.ConstraintObject
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.combo.ruffyscripter.RuffyScripter
import info.nightscout.pump.combo.ruffyscripter.history.Bolus
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
        `when`(rh.gs(app.aaps.core.ui.R.string.no_valid_basal_rate)).thenReturn("No valid basal rate read from pump")
        `when`(context.getString(R.string.combo_pump_unsupported_operation)).thenReturn("Requested operation not supported by pump")
        comboPlugin = ComboPlugin(injector, aapsLogger, rxBus, rh, profileFunction, sp, commandQueue, pumpSync, dateUtil, ruffyScripter, uiInteraction)
    }

    @Test
    fun invalidBasalRateOnComboPumpShouldLimitLoopInvocation() {
        comboPlugin.setPluginEnabled(PluginType.PUMP, true)
        comboPlugin.setValidBasalRateProfileSelectedOnPump(false)
        val c = comboPlugin.isLoopInvocationAllowed(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).isEqualTo("Combo: No valid basal rate read from pump")
        assertThat(c.value()).isFalse()
        comboPlugin.setPluginEnabled(PluginType.PUMP, false)
    }

    @Test
    fun `generate bolus ID from timestamp and amount`() {
        val now = System.currentTimeMillis()
        val pumpTimestamp = now - now % 1000
        // same timestamp, different bolus leads to different fake timestamp
        assertThat(
            comboPlugin.generatePumpBolusId(Bolus(pumpTimestamp, 0.3, true))
        ).isNotEqualTo(comboPlugin.generatePumpBolusId(Bolus(pumpTimestamp, 0.1, true)))
        // different timestamp, same bolus leads to different fake timestamp
        assertThat(
            comboPlugin.generatePumpBolusId(Bolus(pumpTimestamp + 60 * 1000, 0.3, true))
        ).isNotEqualTo(comboPlugin.generatePumpBolusId(Bolus(pumpTimestamp, 0.3, true)))
        // generated timestamp has second-precision
        val bolus = Bolus(pumpTimestamp, 0.2, true)
        val calculatedTimestamp = comboPlugin.generatePumpBolusId(bolus)
        assertThat(calculatedTimestamp - calculatedTimestamp % 1000).isEqualTo(calculatedTimestamp)
    }
}
