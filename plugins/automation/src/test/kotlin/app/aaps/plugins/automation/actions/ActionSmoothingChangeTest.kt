package app.aaps.plugins.automation.actions

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.automation.R
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

private const val STRING_JSON = """{"data":{"smoothingPlugin":"AvgSmoothingPlugin"},"type":"ActionSmoothingChange"}"""

class ActionSmoothingChangeTest : ActionsTestBase() {

    @Mock lateinit var avgSmoothingPlugin: PluginBase
    @Mock lateinit var noSmoothingPlugin: PluginBase

    private lateinit var sut: ActionSmoothingChange

    @BeforeEach fun setUp() {
        whenever(rh.gs(R.string.change_smoothing)).thenReturn("Change smoothing")
        whenever(rh.gs(R.string.change_smoothing_to)).thenReturn("Change smoothing to %1\$s")
        whenever(rh.gs(R.string.alreadyset)).thenReturn("Already set")

        whenever(avgSmoothingPlugin.pluginId).thenReturn("AvgSmoothingPlugin")
        whenever(avgSmoothingPlugin.name).thenReturn("Average smoothing")
        whenever(noSmoothingPlugin.pluginId).thenReturn("NoSmoothingPlugin")
        whenever(activePlugin.getSpecificPluginsList(PluginType.SMOOTHING))
            .thenReturn(arrayListOf(noSmoothingPlugin, avgSmoothingPlugin))

        sut = ActionSmoothingChange(injector)
        sut.smoothingPlugin.value = "AvgSmoothingPlugin"
    }

    @Test fun friendlyName() = runTest {
        assertThat(sut.friendlyName()).isEqualTo(R.string.change_smoothing)
    }

    @Test fun shortDescriptionTest() = runTest {
        assertThat(sut.shortDescription()).isEqualTo("Change smoothing to Average smoothing")
        sut.smoothingPlugin.value = ""
        assertThat(sut.shortDescription()).isEqualTo("Change smoothing")
    }

    @Test fun doActionSwitchesPlugin() = runTest {
        whenever(avgSmoothingPlugin.isEnabled(PluginType.SMOOTHING)).thenReturn(false)
        val result = sut.doAction()
        assertThat(result.success).isTrue()
        assertThat(result.comment).isEqualTo("OK")
        verify(configBuilder).performPluginSwitch(avgSmoothingPlugin, true, PluginType.SMOOTHING)
    }

    @Test fun doActionAlreadyActive() = runTest {
        whenever(avgSmoothingPlugin.isEnabled(PluginType.SMOOTHING)).thenReturn(true)
        val result = sut.doAction()
        assertThat(result.success).isTrue()
        assertThat(result.comment).isEqualTo("Already set")
        verify(configBuilder, never()).performPluginSwitch(anyOrNull(), any(), anyOrNull())
    }

    @Test fun doActionUnknownPlugin() = runTest {
        sut.smoothingPlugin.value = "RemovedPlugin"
        val result = sut.doAction()
        assertThat(result.success).isFalse()
        verify(configBuilder, never()).performPluginSwitch(anyOrNull(), any(), anyOrNull())
    }

    @Test fun isValidTest() = runTest {
        assertThat(sut.isValid()).isTrue()
        sut.smoothingPlugin.value = "RemovedPlugin"
        assertThat(sut.isValid()).isFalse()
    }

    @Test fun hasDialogTest() = runTest {
        assertThat(sut.hasDialog()).isTrue()
    }

    @Test fun toJSONTest() = runTest {
        JSONAssert.assertEquals(STRING_JSON, sut.toJSON(), true)
    }

    @Test fun fromJSONTest() = runTest {
        val clone = ActionSmoothingChange(injector).fromJSON("""{"smoothingPlugin":"NoSmoothingPlugin"}""") as ActionSmoothingChange
        assertThat(clone.smoothingPlugin.value).isEqualTo("NoSmoothingPlugin")
    }
}
