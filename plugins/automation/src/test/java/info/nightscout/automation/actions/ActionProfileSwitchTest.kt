package info.nightscout.automation.actions

import info.nightscout.automation.R
import info.nightscout.automation.elements.InputProfileName
import info.nightscout.interfaces.queue.Callback
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.`when`

class ActionProfileSwitchTest : ActionsTestBase() {

    private lateinit var sut: ActionProfileSwitch

    private val stringJson = "{\"data\":{\"profileToSwitchTo\":\"Test\"},\"type\":\"ActionProfileSwitch\"}"

    @BeforeEach fun setUp() {
        `when`(rh.gs(R.string.profilename)).thenReturn("Change profile to")
        `when`(rh.gs(R.string.changengetoprofilename)).thenReturn("Change profile to %s")
        `when`(context.getString(R.string.alreadyset)).thenReturn("Already set")
        `when`(context.getString(info.nightscout.core.ui.R.string.notexists)).thenReturn("not exists")
        `when`(context.getString(info.nightscout.core.validators.R.string.error_field_must_not_be_empty)).thenReturn("The field must not be empty")
        `when`(context.getString(info.nightscout.core.ui.R.string.noprofile)).thenReturn("No profile loaded from NS yet")

        sut = ActionProfileSwitch(injector)
    }

    @Test fun friendlyName() {
        Assertions.assertEquals(R.string.profilename, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        Assertions.assertEquals("Change profile to ", sut.shortDescription())
    }

    @Test fun doAction() {
        //Empty input
        `when`(profileFunction.getProfileName()).thenReturn("Test")
        sut.inputProfileName = InputProfileName(rh, activePlugin, "")
        sut.doAction(object : Callback() {
            override fun run() {
                Assertions.assertFalse(result.success)
            }
        })

        //Not initialized profileStore
        `when`(profileFunction.getProfile()).thenReturn(null)
        sut.inputProfileName = InputProfileName(rh, activePlugin, "someProfile")
        sut.doAction(object : Callback() {
            override fun run() {
                Assertions.assertFalse(result.success)
            }
        })

        //profile already set
        `when`(profileFunction.getProfile()).thenReturn(validProfile)
        `when`(profileFunction.getProfileName()).thenReturn("Test")
        sut.inputProfileName = InputProfileName(rh, activePlugin, "Test")
        sut.doAction(object : Callback() {
            override fun run() {
                Assertions.assertTrue(result.success)
                Assertions.assertEquals("Already set", result.comment)
            }
        })

        // profile doesn't exists
        `when`(profileFunction.getProfileName()).thenReturn("Active")
        sut.inputProfileName = InputProfileName(rh, activePlugin, "Test")
        sut.doAction(object : Callback() {
            override fun run() {
                Assertions.assertFalse(result.success)
                Assertions.assertEquals("not exists", result.comment)
            }
        })

        // do profile switch
        `when`(profileFunction.getProfileName()).thenReturn("Test")
        `when`(profileFunction.createProfileSwitch(anyObject(), anyString(), anyInt(), anyInt(), anyInt(), anyLong())).thenReturn(true)
        sut.inputProfileName = InputProfileName(rh, activePlugin, TESTPROFILENAME)
        sut.doAction(object : Callback() {
            override fun run() {
                Assertions.assertTrue(result.success)
                Assertions.assertEquals("OK", result.comment)
            }
        })
        Mockito.verify(profileFunction, Mockito.times(1)).createProfileSwitch(anyObject(), anyString(), anyInt(), anyInt(), anyInt(), anyLong())
    }

    @Test fun hasDialogTest() {
        Assertions.assertTrue(sut.hasDialog())
    }

    @Test fun toJSONTest() {
        sut.inputProfileName = InputProfileName(rh, activePlugin, "Test")
        Assertions.assertEquals(stringJson, sut.toJSON())
    }

    @Test fun fromJSONTest() {
        val data = "{\"profileToSwitchTo\":\"Test\"}"
        sut.fromJSON(data)
        Assertions.assertEquals("Test", sut.inputProfileName.value)
    }

    @Test fun iconTest() {
        Assertions.assertEquals(info.nightscout.core.ui.R.drawable.ic_actions_profileswitch, sut.icon())
    }
}