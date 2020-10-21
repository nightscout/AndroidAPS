package info.nightscout.androidaps.plugins.general.automation.actions

import android.widget.LinearLayout
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.general.automation.elements.InputString
import info.nightscout.androidaps.queue.Callback
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class ActionSendSMSTest : ActionsTestBase() {

    private lateinit var sut: ActionSendSMS

    @Before
    fun setup() {

        `when`(resourceHelper.gs(eq(R.string.sendsmsactionlabel), anyString())).thenReturn("Send SMS: %s")
        `when`(resourceHelper.gs(R.string.sendsmsactiondescription)).thenReturn("Send SMS to all numbers")

        sut = ActionSendSMS(injector)
    }

    @Test fun friendlyNameTest() {
        Assert.assertEquals(R.string.sendsmsactiondescription, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        Assert.assertEquals("Send SMS: %s", sut.shortDescription())
    }

    @Test fun iconTest() {
        Assert.assertEquals(R.drawable.ic_notifications, sut.icon())
    }

    @Test fun doActionTest() {
        `when`(smsCommunicatorPlugin.sendNotificationToAllNumbers(anyString())).thenReturn(true)
        sut.text = InputString(injector, "Asd")
        sut.doAction(object : Callback() {
            override fun run() {
                Assert.assertTrue(result.success)
            }
        })
    }

    @Test fun hasDialogTest() {
        Assert.assertTrue(sut.hasDialog())
    }

    @Test fun toJSONTest() {
        sut.text = InputString(injector, "Asd")
        Assert.assertEquals("{\"data\":{\"text\":\"Asd\"},\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionSendSMS\"}", sut.toJSON())
    }

    @Test fun fromJSONTest() {
        sut.fromJSON("{\"text\":\"Asd\"}")
        Assert.assertEquals("Asd", sut.text.value)
    }
}