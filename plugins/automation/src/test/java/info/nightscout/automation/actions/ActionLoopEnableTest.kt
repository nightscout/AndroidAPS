package info.nightscout.automation.actions

import info.nightscout.automation.R
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.queue.Callback
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class ActionLoopEnableTest : ActionsTestBase() {

    lateinit var sut: ActionLoopEnable

    @BeforeEach
    fun setup() {

        testPumpPlugin.pumpDescription.isTempBasalCapable = true
        `when`(rh.gs(info.nightscout.core.ui.R.string.enableloop)).thenReturn("Enable loop")
        `when`(context.getString(R.string.alreadyenabled)).thenReturn("Already enabled")

        sut = ActionLoopEnable(injector)
    }

    @Test fun friendlyNameTest() {
        Assertions.assertEquals(info.nightscout.core.ui.R.string.enableloop, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        Assertions.assertEquals("Enable loop", sut.shortDescription())
    }

    @Test fun iconTest() {
        Assertions.assertEquals(R.drawable.ic_play_circle_outline_24dp, sut.icon())
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