package info.nightscout.androidaps.plugins.general.automation.actions

import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.androidaps.queue.Callback
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class ActionLoopDisableTest : ActionsTestBase() {

    lateinit var sut: ActionLoopDisable

    @Before
    fun setup() {

        testPumpPlugin.pumpDescription.isTempBasalCapable = true
        `when`(resourceHelper.gs(R.string.disableloop)).thenReturn("Disable loop")
        `when`(resourceHelper.gs(R.string.alreadydisabled)).thenReturn("Disable loop")

        sut = ActionLoopDisable(injector)
    }

    @Test
    fun friendlyNameTest() {
        Assert.assertEquals(R.string.disableloop, sut.friendlyName())
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