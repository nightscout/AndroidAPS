package info.nightscout.androidaps.plugins.general.automation.actions

import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.general.automation.elements.InputString
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.queue.Callback
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(NSUpload::class)
class ActionNotificationTest : ActionsTestBase() {

    private lateinit var sut: ActionNotification

    @Before
    fun setup() {
        PowerMockito.mockStatic(NSUpload::class.java)
        `when`(resourceHelper.gs(R.string.notification)).thenReturn("Notification")
        `when`(resourceHelper.gs(ArgumentMatchers.eq(R.string.notification_message), ArgumentMatchers.anyString())).thenReturn("Notification: %s")

        sut = ActionNotification(injector)
    }

    @Test fun friendlyNameTest() {
        Assert.assertEquals(R.string.notification, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        sut.text = InputString(injector, "Asd")
        Assert.assertEquals("Notification: %s", sut.shortDescription())
    }

    @Test fun iconTest() {
        Assert.assertEquals(R.drawable.ic_notifications, sut.icon())
    }

    @Test fun doActionTest() {
        sut.doAction(object : Callback() {
            override fun run() {
                Assert.assertTrue(result.success)
            }
        })
        Mockito.verify(rxBus, Mockito.times(2)).send(anyObject())
        PowerMockito.verifyStatic(NSUpload::class.java, Mockito.times(1))
    }

    @Test fun hasDialogTest() {
        Assert.assertTrue(sut.hasDialog())
    }

    @Test fun toJSONTest() {
        sut.text = InputString(injector, "Asd")
        Assert.assertEquals("{\"data\":{\"text\":\"Asd\"},\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionNotification\"}", sut.toJSON())
    }

    @Test fun fromJSONTest() {
        sut.text = InputString(injector, "Asd")
        sut.fromJSON("{\"text\":\"Asd\"}")
        Assert.assertEquals("Asd", sut.text.value)
    }
}