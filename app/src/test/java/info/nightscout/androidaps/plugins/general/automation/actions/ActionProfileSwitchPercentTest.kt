package info.nightscout.androidaps.plugins.general.automation.actions

import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration
import info.nightscout.androidaps.plugins.general.automation.elements.InputPercent
import info.nightscout.androidaps.queue.Callback
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class ActionProfileSwitchPercentTest : ActionsTestBase() {

    private lateinit var sut: ActionProfileSwitchPercent

    @Before
    fun setup() {

        `when`(activePlugin.activeTreatments).thenReturn(treatmentsPlugin)
        `when`(resourceHelper.gs(R.string.startprofileforever)).thenReturn("Start profile %d%%")
        `when`(resourceHelper.gs(R.string.startprofile)).thenReturn("Start profile %d%% for %d min")

        sut = ActionProfileSwitchPercent(injector)
    }

    @Test fun friendlyNameTest() {
        Assert.assertEquals(R.string.profilepercentage, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        sut.pct = InputPercent(injector, 100.0)
        sut.duration = InputDuration(injector, 30, InputDuration.TimeUnit.MINUTES)
        Assert.assertNull(sut.shortDescription()) // not mocked
    }

    @Test fun iconTest() {
        Assert.assertEquals(R.drawable.ic_actions_profileswitch, sut.icon())
    }

    @Test fun doActionTest() {
        sut.pct = InputPercent(injector, 110.0)
        sut.duration = InputDuration(injector, 30, InputDuration.TimeUnit.MINUTES)
        sut.doAction(object : Callback() {
            override fun run() {
                Assert.assertTrue(result.success)
            }
        })
        Mockito.verify(treatmentsPlugin, Mockito.times(1)).doProfileSwitch(30, 110, 0)
    }

    @Test fun hasDialogTest() {
        Assert.assertTrue(sut.hasDialog())
    }

    @Test fun toJSONTest() {
        sut.pct = InputPercent(injector, 100.0)
        sut.duration = InputDuration(injector, 30, InputDuration.TimeUnit.MINUTES)
        Assert.assertEquals("{\"data\":{\"percentage\":100,\"durationInMinutes\":30},\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionProfileSwitchPercent\"}", sut.toJSON())
    }

    @Test fun fromJSONTest() {
        sut.fromJSON("{\"percentage\":100,\"durationInMinutes\":30}")
        Assert.assertEquals(100.0, sut.pct.value, 0.001)
        Assert.assertEquals(30.0, sut.duration.getMinutes().toDouble(), 0.001)
    }
}