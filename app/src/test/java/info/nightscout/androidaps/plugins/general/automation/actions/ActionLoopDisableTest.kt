package info.nightscout.androidaps.plugins.general.automation.actions

import android.content.Context
import com.google.common.base.Optional
import dagger.Lazy
import info.AAPSMocker
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.general.wear.ActionStringHandler
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(ConstraintChecker::class, VirtualPumpPlugin::class)
class ActionLoopDisableTest : ActionTestBase() {

    @Mock lateinit var rxBus: RxBusWrapper
    @Mock lateinit var sp: SP
    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var context: Context
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var configBuilderPlugin: ConfigBuilderPlugin
    @Mock lateinit var treatmentsPlugin: TreatmentsPlugin
    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Mock lateinit var lazyActionStringHandler: Lazy<ActionStringHandler>
    @Mock lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin
    lateinit var loopPlugin: LoopPlugin

    lateinit var actionLoopDisable : ActionLoopDisable

    @Test fun friendlyNameTest() {
        Assert.assertEquals(R.string.disableloop.toLong(), actionLoopDisable.friendlyName().toLong())
    }

    @Test fun shortDescriptionTest() {
        Assert.assertEquals("Disable loop", actionLoopDisable.shortDescription())
    }

    @Test fun iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_stop_24dp), actionLoopDisable.icon())
    }

    @Test fun doActionTest() {
        loopPlugin.setPluginEnabled(PluginType.LOOP, true)
        actionLoopDisable.doAction(object : Callback() {
            override fun run() {}
        })
        Assert.assertEquals(false, loopPlugin.isEnabled(PluginType.LOOP))
        // another call should keep it disabled
        actionLoopDisable.doAction(object : Callback() {
            override fun run() {}
        })
        Assert.assertEquals(false, loopPlugin.isEnabled(PluginType.LOOP))
    }

    @Before fun prepareTest() {
        actionLoopDisable = ActionLoopDisable(actionInjector)

        val pump = PowerMockito.mock(VirtualPumpPlugin::class.java)
        PowerMockito.`when`(pump.specialEnableCondition()).thenReturn(true)
        val pumpDescription = PumpDescription()
        pumpDescription.isTempBasalCapable = true
        PowerMockito.`when`(pump.pumpDescription).thenReturn(pumpDescription)
        PowerMockito.`when`(configBuilderPlugin.activePump).thenReturn(pump)

        loopPlugin = LoopPlugin(aapsLogger, rxBus, sp, constraintChecker, resourceHelper, profileFunction, context, commandQueue, configBuilderPlugin, treatmentsPlugin, virtualPumpPlugin, lazyActionStringHandler, iobCobCalculatorPlugin)
    }
}