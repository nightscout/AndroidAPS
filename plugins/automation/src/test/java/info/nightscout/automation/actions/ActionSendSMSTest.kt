package info.nightscout.automation.actions

import info.nightscout.automation.R
import info.nightscout.automation.elements.InputString
import info.nightscout.interfaces.queue.Callback
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`

class ActionSendSMSTest : ActionsTestBase() {

    private lateinit var sut: ActionSendSMS

    @BeforeEach
    fun setup() {

        `when`(rh.gs(eq(R.string.sendsmsactionlabel), anyString())).thenReturn("Send SMS: %s")
        `when`(rh.gs(R.string.sendsmsactiondescription)).thenReturn("Send SMS to all numbers")

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
        `when`(smsCommunicator.sendNotificationToAllNumbers(anyString())).thenReturn(true)
        sut.text = InputString("Asd")
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
        sut.text = InputString("Asd")
        Assert.assertEquals("{\"data\":{\"text\":\"Asd\"},\"type\":\"ActionSendSMS\"}", sut.toJSON())
    }

    @Test fun fromJSONTest() {
        sut.fromJSON("{\"text\":\"Asd\"}")
        Assert.assertEquals("Asd", sut.text.value)
    }
}