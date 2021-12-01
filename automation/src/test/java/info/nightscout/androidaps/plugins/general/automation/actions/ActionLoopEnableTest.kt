package info.nightscout.androidaps.plugins.general.automation.actions

import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.queue.Callback
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class ActionLoopEnableTest : ActionsTestBase() {

    lateinit var sut: ActionLoopEnable

    @Before
    fun setup() {

        testPumpPlugin.pumpDescription.isTempBasalCapable = true
        `when`(rh.gs(R.string.enableloop)).thenReturn("Enable loop")
        `when`(rh.gs(R.string.alreadyenabled)).thenReturn("Already enabled")

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
        `when`(loopPlugin.isEnabled()).thenReturn(false)
        sut.doAction(object : Callback() {
            override fun run() {}
        })
        Mockito.verify(loopPlugin, Mockito.times(1)).setPluginEnabled(PluginType.LOOP, true)
        Mockito.verify(configBuilder, Mockito.times(1)).storeSettings("ActionLoopEnable")

        `when`(loopPlugin.isEnabled()).thenReturn(true)

        // another call should keep it disabled, no new invocation
        sut.doAction(object : Callback() {
            override fun run() {}
        })
        Mockito.verify(loopPlugin, Mockito.times(1)).setPluginEnabled(PluginType.LOOP, true)
        Mockito.verify(configBuilder, Mockito.times(1)).storeSettings("ActionLoopEnable")
    }
}