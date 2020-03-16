package info.nightscout.androidaps.plugins.general.automation.actions

import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.queue.Callback
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class ActionLoopEnableTest : ActionsTestBase() {

    lateinit var sut: ActionLoopEnable

    @Before
    fun setup() {

        `when`(virtualPumpPlugin.specialEnableCondition()).thenReturn(true)
        val pumpDescription = PumpDescription().apply { isTempBasalCapable = true }
        `when`(virtualPumpPlugin.pumpDescription).thenReturn(pumpDescription)
        `when`(activePlugin.activePump).thenReturn(virtualPumpPlugin)
        `when`(resourceHelper.gs(R.string.enableloop)).thenReturn("Enable loop")
        `when`(resourceHelper.gs(R.string.alreadyenabled)).thenReturn("Already enabled")

        sut = ActionLoopEnable(injector)
    }

    @Test fun friendlyNameTest() {
        Assert.assertEquals(R.string.enableloop, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        Assert.assertEquals("Enable loop", sut.shortDescription())
    }

    @Test fun iconTest() {
        Assert.assertEquals(R.drawable.ic_play_circle_outline_24dp, sut.icon())
    }

    @Test fun doActionTest() {
        `when`(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(false)
        sut.doAction(object : Callback() {
            override fun run() {}
        })
        Mockito.verify(loopPlugin, Mockito.times(1)).setPluginEnabled(PluginType.LOOP, true)
        Mockito.verify(configBuilderPlugin, Mockito.times(1)).storeSettings("ActionLoopEnable")

        `when`(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true)

        // another call should keep it disabled, no new invocation
        sut.doAction(object : Callback() {
            override fun run() {}
        })
        Mockito.verify(loopPlugin, Mockito.times(1)).setPluginEnabled(PluginType.LOOP, true)
        Mockito.verify(configBuilderPlugin, Mockito.times(1)).storeSettings("ActionLoopEnable")
    }
}