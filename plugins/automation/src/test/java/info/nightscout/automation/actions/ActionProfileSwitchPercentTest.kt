package info.nightscout.automation.actions

import info.nightscout.automation.R
import info.nightscout.automation.elements.InputDuration
import info.nightscout.automation.elements.InputPercent
import info.nightscout.interfaces.queue.Callback
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class ActionProfileSwitchPercentTest : ActionsTestBase() {

    private lateinit var sut: ActionProfileSwitchPercent

    @BeforeEach
    fun setup() {

        `when`(rh.gs(R.string.startprofileforever)).thenReturn("Start profile %d%%")
        `when`(rh.gs(info.nightscout.core.main.R.string.startprofile)).thenReturn("Start profile %d%% for %d min")

        sut = ActionProfileSwitchPercent(injector)
    }

    @Test fun friendlyNameTest() {
        Assert.assertEquals(R.string.profilepercentage, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        sut.pct = InputPercent(100.0)
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        Assert.assertNull(sut.shortDescription()) // not mocked
    }

    @Test fun iconTest() {
        Assert.assertEquals(info.nightscout.core.main.R.drawable.ic_actions_profileswitch, sut.icon())
    }

    @Test fun doActionTest() {
        `when`(profileFunction.createProfileSwitch(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(true)
        sut.pct = InputPercent(110.0)
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        sut.doAction(object : Callback() {
            override fun run() {
                Assert.assertTrue(result.success)
            }
        })
        Mockito.verify(profileFunction, Mockito.times(1)).createProfileSwitch(30, 110, 0)
    }

    @Test fun hasDialogTest() {
        Assert.assertTrue(sut.hasDialog())
    }

    @Test fun toJSONTest() {
        sut.pct = InputPercent(100.0)
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        Assert.assertEquals("{\"data\":{\"percentage\":100,\"durationInMinutes\":30},\"type\":\"ActionProfileSwitchPercent\"}", sut.toJSON())
    }

    @Test fun fromJSONTest() {
        sut.fromJSON("{\"percentage\":100,\"durationInMinutes\":30}")
        Assert.assertEquals(100.0, sut.pct.value, 0.001)
        Assert.assertEquals(30.0, sut.duration.getMinutes().toDouble(), 0.001)
    }
}