package info.nightscout.androidaps.plugins.general.automation.actions

import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.plugins.general.automation.elements.InputProfileName
import info.nightscout.androidaps.queue.Callback
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class ActionProfileSwitchTest : ActionsTestBase() {

    private lateinit var sut: ActionProfileSwitch

    private val stringJson = "{\"data\":{\"profileToSwitchTo\":\"Test\"},\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionProfileSwitch\"}"

    @Before fun setUp() {
        `when`(resourceHelper.gs(R.string.profilename)).thenReturn("Change profile to")
        `when`(resourceHelper.gs(ArgumentMatchers.eq(R.string.changengetoprofilename), ArgumentMatchers.anyString())).thenReturn("Change profile to %s")
        `when`(resourceHelper.gs(R.string.alreadyset)).thenReturn("Already set")
        `when`(resourceHelper.gs(R.string.notexists)).thenReturn("not exists")
        `when`(resourceHelper.gs(R.string.error_field_must_not_be_empty)).thenReturn("The field must not be empty")
        `when`(resourceHelper.gs(R.string.noprofile)).thenReturn("No profile loaded from NS yet")

        sut = ActionProfileSwitch(injector)
    }

    @Test fun friendlyName() {
        Assert.assertEquals(R.string.profilename, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        Assert.assertEquals("Change profile to %s", sut.shortDescription())
    }

    @Test fun doAction() {
        //Empty input
        `when`(profileFunction.getProfileName()).thenReturn("Test")
        sut.inputProfileName = InputProfileName(resourceHelper, activePlugin, "")
        sut.doAction(object : Callback() {
            override fun run() {
                Assert.assertFalse(result.success)
            }
        })

        //Not initialized profileStore
        `when`(profileFunction.getProfile()).thenReturn(null)
        sut.inputProfileName = InputProfileName(resourceHelper, activePlugin, "someProfile")
        sut.doAction(object : Callback() {
            override fun run() {
                Assert.assertFalse(result.success)
            }
        })

        //profile already set
        `when`(profileFunction.getProfile()).thenReturn(validProfile)
        `when`(profileFunction.getProfileName()).thenReturn("Test")
        sut.inputProfileName = InputProfileName(resourceHelper, activePlugin, "Test")
        sut.doAction(object : Callback() {
            override fun run() {
                Assert.assertTrue(result.success)
                Assert.assertEquals("Already set", result.comment)
            }
        })

        // profile doesn't exists
        `when`(profileFunction.getProfileName()).thenReturn("Active")
        sut.inputProfileName = InputProfileName(resourceHelper, activePlugin, "Test")
        sut.doAction(object : Callback() {
            override fun run() {
                Assert.assertFalse(result.success)
                Assert.assertEquals("not exists", result.comment)
            }
        })

        // do profile switch
        `when`(profileFunction.getProfileName()).thenReturn("Test")
        sut.inputProfileName = InputProfileName(resourceHelper, activePlugin, TESTPROFILENAME)
        sut.doAction(object : Callback() {
            override fun run() {
                Assert.assertTrue(result.success)
                Assert.assertEquals("OK", result.comment)
            }
        })
        Mockito.verify(profileFunction, Mockito.times(1)).createProfileSwitch(anyObject(), anyString(), anyInt(), anyInt(), anyInt(), anyLong())
    }

    @Test fun hasDialogTest() {
        Assert.assertTrue(sut.hasDialog())
    }

    @Test fun toJSONTest() {
        sut.inputProfileName = InputProfileName(resourceHelper, activePlugin, "Test")
        Assert.assertEquals(stringJson, sut.toJSON())
    }

    @Test fun fromJSONTest() {
        val data = "{\"profileToSwitchTo\":\"Test\"}"
        sut.fromJSON(data)
        Assert.assertEquals("Test", sut.inputProfileName.value)
    }

    @Test fun iconTest() {
        Assert.assertEquals(R.drawable.ic_actions_profileswitch, sut.icon())
    }
}