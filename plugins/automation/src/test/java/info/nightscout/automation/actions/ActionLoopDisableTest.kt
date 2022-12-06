package info.nightscout.automation.actions

import info.nightscout.automation.R
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.queue.Callback
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class ActionLoopDisableTest : ActionsTestBase() {

    lateinit var sut: ActionLoopDisable

    @BeforeEach
    fun setup() {

        testPumpPlugin.pumpDescription.isTempBasalCapable = true
        `when`(context.getString(info.nightscout.core.ui.R.string.disableloop)).thenReturn("Disable loop")
        `when`(rh.gs(info.nightscout.core.ui.R.string.disableloop)).thenReturn("Disable loop")
        `when`(context.getString(R.string.alreadydisabled)).thenReturn("Already disabled")

        sut = ActionLoopDisable(injector)
    }

    @Test
    fun friendlyNameTest() {
        Assert.assertEquals(info.nightscout.core.ui.R.string.disableloop, sut.friendlyName())
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
        `when`(loopPlugin.isEnabled()).thenReturn(true)
        sut.doAction(object : Callback() {
            override fun run() {}
        })
        Mockito.verify(loopPlugin, Mockito.times(1)).setPluginEnabled(PluginType.LOOP, false)
        Mockito.verify(configBuilder, Mockito.times(1)).storeSettings("ActionLoopDisable")
        Mockito.verify(commandQueue, Mockito.times(1)).cancelTempBasal(eq(true), anyObject())

        `when`(loopPlugin.isEnabled()).thenReturn(false)

        // another call should keep it disabled, no new invocation
        sut.doAction(object : Callback() {
            override fun run() {}
        })
        Mockito.verify(loopPlugin, Mockito.times(1)).setPluginEnabled(PluginType.LOOP, false)
        Mockito.verify(configBuilder, Mockito.times(1)).storeSettings("ActionLoopDisable")
        Mockito.verify(commandQueue, Mockito.times(1)).cancelTempBasal(eq(true), anyObject())
    }
}