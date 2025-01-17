package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.queue.Callback
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputProfileName
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.skyscreamer.jsonassert.JSONAssert

private const val STRING_JSON = """{"data":{"profileToSwitchTo":"Test"},"type":"ActionProfileSwitch"}"""

class ActionProfileSwitchTest : ActionsTestBase() {

    private lateinit var sut: ActionProfileSwitch

    @BeforeEach fun setUp() {
        `when`(rh.gs(R.string.profilename)).thenReturn("Change profile to")
        `when`(rh.gs(R.string.changengetoprofilename)).thenReturn("Change profile to %s")
        `when`(rh.gs(R.string.alreadyset)).thenReturn("Already set")
        `when`(rh.gs(app.aaps.core.ui.R.string.notexists)).thenReturn("not exists")
        `when`(rh.gs(app.aaps.core.validators.R.string.error_field_must_not_be_empty)).thenReturn("The field must not be empty")
        `when`(rh.gs(app.aaps.core.ui.R.string.noprofile)).thenReturn("No profile loaded from NS yet")

        sut = ActionProfileSwitch(injector)
    }

    @Test fun friendlyName() {
        assertThat(sut.friendlyName()).isEqualTo(R.string.profilename)
    }

    @Test fun shortDescriptionTest() {
        assertThat(sut.shortDescription()).isEqualTo("Change profile to ")
    }

    @Test fun doAction() {
        //Empty input
        `when`(profileFunction.getProfileName()).thenReturn("Test")
        sut.inputProfileName = InputProfileName(rh, activePlugin, "")
        sut.doAction(object : Callback() {
            override fun run() {
                assertThat(result.success).isFalse()
            }
        })

        //Not initialized profileStore
        `when`(profileFunction.getProfile()).thenReturn(null)
        sut.inputProfileName = InputProfileName(rh, activePlugin, "someProfile")
        sut.doAction(object : Callback() {
            override fun run() {
                assertThat(result.success).isFalse()
            }
        })

        //profile already set
        `when`(profileFunction.getProfile()).thenReturn(validProfile)
        `when`(profileFunction.getProfileName()).thenReturn("Test")
        sut.inputProfileName = InputProfileName(rh, activePlugin, "Test")
        sut.doAction(object : Callback() {
            override fun run() {
                assertThat(result.success).isTrue()
                assertThat(result.comment).isEqualTo("Already set")
            }
        })

        // profile doesn't exists
        `when`(profileFunction.getProfileName()).thenReturn("Active")
        sut.inputProfileName = InputProfileName(rh, activePlugin, "Test")
        sut.doAction(object : Callback() {
            override fun run() {
                assertThat(result.success).isFalse()
                assertThat(result.comment).isEqualTo("not exists")
            }
        })

        // do profile switch
        `when`(profileFunction.getProfileName()).thenReturn("Test")
        `when`(profileFunction.createProfileSwitch(anyObject(), anyString(), anyInt(), anyInt(), anyInt(), anyLong(), any(), any(), any(), any())).thenReturn(true)
        sut.inputProfileName = InputProfileName(rh, activePlugin, TESTPROFILENAME)
        sut.doAction(object : Callback() {
            override fun run() {
                assertThat(result.success).isTrue()
                assertThat(result.comment).isEqualTo("OK")
            }
        })
        Mockito.verify(profileFunction, Mockito.times(1)).createProfileSwitch(anyObject(), anyString(), anyInt(), anyInt(), anyInt(), anyLong(), any(), any(), any(), any())
    }

    @Test fun hasDialogTest() {
        assertThat(sut.hasDialog()).isTrue()
    }

    @Test fun toJSONTest() {
        sut.inputProfileName = InputProfileName(rh, activePlugin, "Test")
        JSONAssert.assertEquals(STRING_JSON, sut.toJSON(), true)
    }

    @Test fun fromJSONTest() {
        sut.fromJSON("""{"profileToSwitchTo":"Test"}""")
        assertThat(sut.inputProfileName.value).isEqualTo("Test")
    }

    @Test fun iconTest() {
        assertThat(sut.icon()).isEqualTo(app.aaps.core.ui.R.drawable.ic_actions_profileswitch_24dp)
    }
}
