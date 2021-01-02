package info.nightscout.androidaps.plugins.general.automation.actions

import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.general.automation.elements.InputProfileName
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
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
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(LocalProfilePlugin::class)
class ActionProfileSwitchTest : ActionsTestBase() {

    private lateinit var sut: ActionProfileSwitch

    private val stringJson = "{\"data\":{\"profileToSwitchTo\":\"Test\"},\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionProfileSwitch\"}"

    @Before fun setUp() {
        `when`(activePlugin.activeTreatments).thenReturn(treatmentsPlugin)
        `when`(resourceHelper.gs(R.string.profilename)).thenReturn("Change profile to")
        `when`(resourceHelper.gs(ArgumentMatchers.eq(R.string.changengetoprofilename), ArgumentMatchers.anyString())).thenReturn("Change profile to %s")
        `when`(resourceHelper.gs(R.string.alreadyset)).thenReturn("Already set")
        `when`(resourceHelper.gs(R.string.notexists)).thenReturn("not exists")
        `when`(resourceHelper.gs(R.string.ok)).thenReturn("OK")

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
        sut.inputProfileName = InputProfileName(injector, "")
        sut.doAction(object : Callback() {
            override fun run() {
                Assert.assertFalse(result.success)
            }
        })

        //Not initialized profileStore
        `when`(profileFunction.getProfile()).thenReturn(null)
        sut.inputProfileName = InputProfileName(injector, "someProfile")
        sut.doAction(object : Callback() {
            override fun run() {
                Assert.assertFalse(result.success)
            }
        })

        //profile already set
        `when`(profileFunction.getProfile()).thenReturn(validProfile)
        `when`(profileFunction.getProfileName()).thenReturn("Test")
        sut.inputProfileName = InputProfileName(injector, "Test")
        sut.doAction(object : Callback() {
            override fun run() {
                Assert.assertTrue(result.success)
                Assert.assertEquals("Already set", result.comment)
            }
        })

        // profile doesn't exists
        `when`(activePlugin.activeProfileInterface).thenReturn(localProfilePlugin)
        `when`(localProfilePlugin.profile).thenReturn(getValidProfileStore())
        `when`(profileFunction.getProfileName()).thenReturn("Active")
        sut.inputProfileName = InputProfileName(injector, "Test")
        sut.doAction(object : Callback() {
            override fun run() {
                Assert.assertFalse(result.success)
                Assert.assertEquals("not exists", result.comment)
            }
        })

        // do profileswitch
        `when`(activePlugin.activeProfileInterface).thenReturn(localProfilePlugin)
        `when`(localProfilePlugin.profile).thenReturn(getValidProfileStore())
        `when`(profileFunction.getProfileName()).thenReturn("Test")
        sut.inputProfileName = InputProfileName(injector, TESTPROFILENAME)
        sut.doAction(object : Callback() {
            override fun run() {
                Assert.assertTrue(result.success)
                Assert.assertEquals("OK", result.comment)
            }
        })
        Mockito.verify(treatmentsPlugin, Mockito.times(1)).doProfileSwitch(anyObject(), anyString(), anyInt(), anyInt(), anyInt(), anyLong())
    }

    @Test fun hasDialogTest() {
        Assert.assertTrue(sut.hasDialog())
    }

    @Test fun toJSONTest() {
        sut.inputProfileName = InputProfileName(injector, "Test")
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