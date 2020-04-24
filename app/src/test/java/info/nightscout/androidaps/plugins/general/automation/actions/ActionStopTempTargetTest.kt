package info.nightscout.androidaps.plugins.general.automation.actions

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.queue.Callback
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(MainApp::class)
class ActionStopTempTargetTest : ActionsTestBase() {

    private lateinit var sut: ActionStopTempTarget

    @Before
    fun setup() {
        `when`(resourceHelper.gs(R.string.stoptemptarget)).thenReturn("Stop temp target")

        sut = ActionStopTempTarget(injector)
    }

    @Test fun friendlyNameTest() {
        Assert.assertEquals(R.string.stoptemptarget, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        Assert.assertEquals("Stop temp target", sut.shortDescription())
    }

    @Test fun iconTest() {
        Assert.assertEquals(R.drawable.ic_stop_24dp, sut.icon())
    }

    @Test fun doActionTest() {
        `when`(activePlugin.activeTreatments).thenReturn(treatmentsPlugin)
        sut.doAction(object : Callback() {
            override fun run() {
                Assert.assertTrue(result.success)
            }
        })
        Mockito.verify(treatmentsPlugin, Mockito.times(1)).addToHistoryTempTarget(anyObject())
    }

    @Test fun hasDialogTest() {
        Assert.assertFalse(sut.hasDialog())
    }

    @Test fun toJSONTest() {
        Assert.assertEquals("{\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionStopTempTarget\"}", sut.toJSON())
    }

    @Test fun fromJSONTest() {
        sut.fromJSON("{\"reason\":\"Test\"}")
        Assert.assertNotNull(sut)
    }
}