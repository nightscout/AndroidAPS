package info.nightscout.androidaps.plugins.general.automation.actions

import android.content.Context
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
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(NSUpload::class, RxBusWrapper::class)
class ActionAlarmTest : TestBase() {

    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var rxBus: RxBusWrapper
    @Mock lateinit var nsUpload: NSUpload
    @Mock lateinit var context: Context

    private lateinit var sut: ActionAlarm
    var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is ActionAlarm) {
                it.resourceHelper = resourceHelper
                it.rxBus = rxBus
                it.nsUpload = nsUpload
                it.context = context
            }
            if (it is PumpEnactResult) {
                it.aapsLogger = aapsLogger
                it.resourceHelper = resourceHelper
            }
        }
    }

    @Before
    fun setup() {
        PowerMockito.mockStatic(NSUpload::class.java)
        `when`(resourceHelper.gs(R.string.ok)).thenReturn("OK")
        `when`(resourceHelper.gs(R.string.alarm)).thenReturn("Alarm")
        `when`(resourceHelper.gs(ArgumentMatchers.eq(R.string.alarm_message), ArgumentMatchers.anyString())).thenReturn("Alarm: %s")

        sut = ActionAlarm(injector)
    }

    @Test fun friendlyNameTest() {
        Assert.assertEquals(R.string.alarm, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        sut.text = InputString(injector, "Asd")
        Assert.assertEquals("Alarm: %s", sut.shortDescription())
    }

    @Test fun iconTest() {
        Assert.assertEquals(R.drawable.ic_access_alarm_24dp, sut.icon())
    }

    @Test fun doActionTest() {
        sut.doAction(object : Callback() {
            override fun run() {
                Assert.assertTrue(result.success)
            }
        })
        PowerMockito.verifyStatic(NSUpload::class.java, Mockito.times(1))
    }

    @Test fun hasDialogTest() {
        Assert.assertTrue(sut.hasDialog())
    }

    @Test fun toJSONTest() {
        sut.text = InputString(injector, "Asd")
        Assert.assertEquals("{\"data\":{\"text\":\"Asd\"},\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionAlarm\"}", sut.toJSON())
    }

    @Test fun fromJSONTest() {
        sut.text = InputString(injector, "Asd")
        sut.fromJSON("{\"text\":\"Asd\"}")
        Assert.assertEquals("Asd", sut.text.value)
    }
}