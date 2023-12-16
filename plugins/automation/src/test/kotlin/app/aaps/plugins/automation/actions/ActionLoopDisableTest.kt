package app.aaps.plugins.automation.actions

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.queue.Callback
import app.aaps.plugins.automation.R
import com.google.common.truth.Truth.assertThat
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
        `when`(rh.gs(app.aaps.core.ui.R.string.disableloop)).thenReturn("Disable loop")
        `when`(rh.gs(app.aaps.core.ui.R.string.disableloop)).thenReturn("Disable loop")
        `when`(rh.gs(R.string.alreadydisabled)).thenReturn("Already disabled")

        sut = ActionLoopDisable(injector)
    }

    @Test
    fun friendlyNameTest() {
        assertThat(sut.friendlyName()).isEqualTo(app.aaps.core.ui.R.string.disableloop)
    }

    @Test
    fun shortDescriptionTest() {
        assertThat(sut.shortDescription()).isEqualTo("Disable loop")
    }

    @Test
    fun iconTest() {
        assertThat(sut.icon()).isEqualTo(R.drawable.ic_stop_24dp)
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
