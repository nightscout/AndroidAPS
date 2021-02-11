package info.nightscout.androidaps.plugins.general.automation.actions

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.automation.elements.InputString
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(NSUpload::class, RxBusWrapper::class)
class ActionNotificationTest : TestBase() {

    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var rxBus: RxBusWrapper
    @Mock lateinit var nsUpload: NSUpload

    private lateinit var sut: ActionNotification
    var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is ActionNotification) {
                it.resourceHelper = resourceHelper
                it.rxBus = rxBus
                it.nsUpload = nsUpload
            }
            if (it is PumpEnactResult) {
                it.aapsLogger = aapsLogger
                it.resourceHelper = resourceHelper
            }
        }
    }

    @Before
    fun setup() {
        `when`(resourceHelper.gs(R.string.ok)).thenReturn("OK")
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
        Mockito.verify(nsUpload, Mockito.times(1)).uploadError(anyObject())
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