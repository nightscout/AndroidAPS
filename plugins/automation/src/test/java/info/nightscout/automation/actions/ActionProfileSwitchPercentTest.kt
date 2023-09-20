package info.nightscout.automation.actions

import info.nightscout.automation.R
import info.nightscout.automation.elements.InputDuration
import info.nightscout.automation.elements.InputPercent
import info.nightscout.interfaces.queue.Callback
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class ActionProfileSwitchPercentTest : ActionsTestBase() {

    private lateinit var sut: ActionProfileSwitchPercent

    @BeforeEach
    fun setup() {

        `when`(rh.gs(R.string.startprofileforever)).thenReturn("Start profile %d%%")
        `when`(rh.gs(info.nightscout.core.ui.R.string.startprofile)).thenReturn("Start profile %d%% for %d min")

        sut = ActionProfileSwitchPercent(injector)
    }

    @Test fun friendlyNameTest() {
        Assertions.assertEquals(R.string.profilepercentage, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        sut.pct = InputPercent(100.0)
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        Assertions.assertEquals("Start profile 100% for 30 min", sut.shortDescription())
    }

    @Test fun iconTest() {
        Assertions.assertEquals(info.nightscout.core.ui.R.drawable.ic_actions_profileswitch, sut.icon())
    }

    @Test fun doActionTest() {
        `when`(profileFunction.createProfileSwitch(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(true)
        sut.pct = InputPercent(110.0)
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        sut.doAction(object : Callback() {
            override fun run() {
                Assertions.assertTrue(result.success)
            }
        })
        Mockito.verify(profileFunction, Mockito.times(1)).createProfileSwitch(30, 110, 0)
    }

    @Test fun hasDialogTest() {
        Assertions.assertTrue(sut.hasDialog())
    }

    @Test fun toJSONTest() {
        sut.pct = InputPercent(100.0)
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        Assertions.assertEquals("{\"data\":{\"percentage\":100,\"durationInMinutes\":30},\"type\":\"ActionProfileSwitchPercent\"}", sut.toJSON())
    }

    @Test fun fromJSONTest() {
        sut.fromJSON("{\"percentage\":100,\"durationInMinutes\":30}")
        Assertions.assertEquals(100.0, sut.pct.value, 0.001)
        Assertions.assertEquals(30.0, sut.duration.getMinutes().toDouble(), 0.001)
    }
}