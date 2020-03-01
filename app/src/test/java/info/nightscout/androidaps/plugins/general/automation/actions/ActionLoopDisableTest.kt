package info.nightscout.androidaps.plugins.general.automation.actions

import android.content.Context
import com.google.common.base.Optional
import dagger.Lazy
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.TestBase
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
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class ActionLoopDisableTest : TestBase() {

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
    @Mock lateinit var loopPlugin: LoopPlugin

    lateinit var sut: ActionLoopDisable

    @Before
    fun setup() {

        `when`(virtualPumpPlugin.specialEnableCondition()).thenReturn(true)
        val pumpDescription = PumpDescription().apply { isTempBasalCapable = true }
        `when`(virtualPumpPlugin.pumpDescription).thenReturn(pumpDescription)
        `when`(configBuilderPlugin.activePump).thenReturn(virtualPumpPlugin)

        sut = ActionLoopDisable(HasAndroidInjector { AndroidInjector { Unit } }) // do nothing injector
            .also { // inject the mocks
                it.loopPlugin = loopPlugin
                it.resourceHelper = resourceHelper
                it.configBuilderPlugin = configBuilderPlugin
                it.commandQueue = commandQueue
                it.rxBus = rxBus
            }
    }

    @Test fun friendlyNameTest() {
        Assert.assertEquals(R.string.disableloop.toLong(), sut.friendlyName().toLong())
    }

    @Test fun shortDescriptionTest() {
        Assert.assertEquals("Disable loop", sut.shortDescription())
    }

    @Test fun iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_stop_24dp), sut.icon())
    }

    @Test fun doActionTest() {
        sut.doAction(object : Callback() {
            override fun run() {}
        })
        Mockito.verify(loopPlugin, Mockito.times(1)).setPluginEnabled(PluginType.LOOP, true)

        // another call should keep it disabled
        sut.doAction(object : Callback() {
            override fun run() {}
        })
        Mockito.verify(loopPlugin, Mockito.times(2)).setPluginEnabled(PluginType.LOOP, true)
    }
}