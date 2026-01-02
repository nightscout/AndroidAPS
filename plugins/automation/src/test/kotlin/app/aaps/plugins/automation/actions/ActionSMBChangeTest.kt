package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputDropdownOnOffMenu
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

private const val STRING_JSON = """{"data":{"smbState":true},"type":"ActionSMBChange"}"""

class ActionSMBChangeTest : ActionsTestBase() {

    private lateinit var sut: ActionSMBChange

    @BeforeEach fun setUp() {
        whenever(rh.gs(R.string.changeSmbState)).thenReturn("Enable/disable SMB")
        whenever(rh.gs(R.string.changeSmbTo)).thenReturn("Change SMB to %1\$s")
        whenever(rh.gs(R.string.on)).thenReturn("ON")
        whenever(rh.gs(R.string.off)).thenReturn("OFF")

        sut = ActionSMBChange(injector)
    }

    @Test fun friendlyName() {
        assertThat(sut.friendlyName()).isEqualTo(R.string.changeSmbState)
    }

    @Test fun shortDescriptionTest() {
        assertThat(sut.shortDescription()).isEqualTo("Change SMB to ON")
    }

    @Test fun doAction() {
        sut.smbState = InputDropdownOnOffMenu(rh, true)
        sut.doAction(object : Callback() {
            override fun run() {
                assertThat(result.success).isTrue()
                assertThat(result.comment).isEqualTo("OK")
                verify(preferences, times(1)).put(any<BooleanPreferenceKey>(), anyBoolean())
            }
        })
    }

    @Test fun hasDialogTest() {
        assertThat(sut.hasDialog()).isTrue()
    }

    @Test fun toJSONTest() {
        sut.smbState = InputDropdownOnOffMenu(rh, true)
        JSONAssert.assertEquals(STRING_JSON, sut.toJSON(), true)
    }

    @Test fun fromJSONTest() {
        sut.fromJSON("""{"smbState":"false"}""")
        assertThat(sut.smbState.value).isEqualTo(false)
    }

    @Test fun iconTest() {
        assertThat(sut.icon()).isEqualTo(app.aaps.core.ui.R.drawable.ic_running_mode)
    }
}
