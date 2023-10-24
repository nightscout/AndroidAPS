package app.aaps.plugins.automation.actions

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.queue.Callback
import app.aaps.plugins.automation.R
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class ActionLoopEnableTest : ActionsTestBase() {

    lateinit var sut: ActionLoopEnable

    @BeforeEach
    fun setup() {

        testPumpPlugin.pumpDescription.isTempBasalCapable = true
        `when`(rh.gs(app.aaps.core.ui.R.string.enableloop)).thenReturn("Enable loop")
        `when`(rh.gs(R.string.alreadyenabled)).thenReturn("Already enabled")

        sut = ActionLoopEnable(injector)
    }

    @Test fun friendlyNameTest() {
        assertThat(sut.friendlyName()).isEqualTo(app.aaps.core.ui.R.string.enableloop)
    }

    @Test fun shortDescriptionTest() {
        assertThat(sut.shortDescription()).isEqualTo("Enable loop")
    }

    @Test fun iconTest() {
        assertThat(sut.icon()).isEqualTo(R.drawable.ic_play_circle_outline_24dp)
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
