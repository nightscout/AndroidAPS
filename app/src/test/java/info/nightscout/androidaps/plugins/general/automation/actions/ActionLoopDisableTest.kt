package info.nightscout.androidaps.plugins.general.automation.actions

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.TestBase
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(VirtualPumpPlugin::class, RxBusWrapper::class)
class ActionLoopDisableTest : TestBase() {

    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var rxBus: RxBusWrapper
    @Mock lateinit var sp: SP
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var configBuilderPlugin: ConfigBuilderPlugin
    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Mock lateinit var loopPlugin: LoopPlugin

    lateinit var sut: ActionLoopDisable

    private var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is ActionLoopDisable) {
                it.loopPlugin = loopPlugin
                it.resourceHelper = resourceHelper
                it.configBuilderPlugin = configBuilderPlugin
                it.commandQueue = commandQueue
                it.rxBus = rxBus
            }
            if (it is PumpEnactResult) {
                it.aapsLogger = aapsLogger
                it.resourceHelper = resourceHelper
            }
        }
    }

    @Before
    fun setup() {

        `when`(virtualPumpPlugin.specialEnableCondition()).thenReturn(true)
        val pumpDescription = PumpDescription().apply { isTempBasalCapable = true }
        `when`(virtualPumpPlugin.pumpDescription).thenReturn(pumpDescription)
        `when`(configBuilderPlugin.activePump).thenReturn(virtualPumpPlugin)
        `when`(resourceHelper.gs(R.string.disableloop)).thenReturn("Disable loop")
        `when`(resourceHelper.gs(R.string.alreadydisabled)).thenReturn("Disable loop")

        sut = ActionLoopDisable(injector) // do nothing injector
    }

    @Test
    fun friendlyNameTest() {
        Assert.assertEquals(R.string.disableloop.toLong(), sut.friendlyName().toLong())
    }

    @Test
    fun shortDescriptionTest() {
        Assert.assertEquals("Disable loop", sut.shortDescription())
    }

    @Test
    fun iconTest() {
        Assert.assertEquals(R.drawable.ic_stop_24dp, sut.icon())
    }

    @Test
    fun doActionTest() {
        `when`(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true)
        sut.doAction(object : Callback() {
            override fun run() {}
        })
        Mockito.verify(loopPlugin, Mockito.times(1)).setPluginEnabled(PluginType.LOOP, false)
        Mockito.verify(configBuilderPlugin, Mockito.times(1)).storeSettings("ActionLoopDisable")
        Mockito.verify(commandQueue, Mockito.times(1)).cancelTempBasal(eq(true), anyObject())

        `when`(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(false)

        // another call should keep it disabled, no new invocation
        sut.doAction(object : Callback() {
            override fun run() {}
        })
        Mockito.verify(loopPlugin, Mockito.times(1)).setPluginEnabled(PluginType.LOOP, false)
        Mockito.verify(configBuilderPlugin, Mockito.times(1)).storeSettings("ActionLoopDisable")
        Mockito.verify(commandQueue, Mockito.times(1)).cancelTempBasal(eq(true), anyObject())
    }
}