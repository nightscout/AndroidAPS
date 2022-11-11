package info.nightscout.automation.actions

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.data.PumpEnactResultObject
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.interfaces.queue.Callback
import info.nightscout.shared.utils.DateUtil
import info.nightscout.interfaces.utils.TimerUtil
import info.nightscout.automation.R
import info.nightscout.automation.elements.InputString
import info.nightscout.interfaces.Config
import info.nightscout.rx.bus.RxBus
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.`when`

class ActionAlarmTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var rxBus: RxBus
    @Mock lateinit var context: Context
    @Mock lateinit var timerUtil: TimerUtil
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var config: Config

    private lateinit var sut: ActionAlarm
    var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is ActionAlarm) {
                it.rh = rh
                it.rxBus = rxBus
                it.context = context
                it.timerUtil = timerUtil
                it.dateUtil = dateUtil
                it.config = config
            }
            if (it is PumpEnactResultObject) {
                it.rh = rh
            }
        }
    }

    @Before
    fun setup() {
        `when`(rh.gs(info.nightscout.core.main.R.string.ok)).thenReturn("OK")
        `when`(rh.gs(info.nightscout.core.main.R.string.alarm)).thenReturn("Alarm")
        `when`(rh.gs(ArgumentMatchers.eq(R.string.alarm_message), ArgumentMatchers.anyString())).thenReturn("Alarm: %s")

        sut = ActionAlarm(injector)
    }

    @Test fun friendlyNameTest() {
        Assert.assertEquals(info.nightscout.core.main.R.string.alarm, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        sut.text = InputString("Asd")
        Assert.assertEquals("Alarm: %s", sut.shortDescription())
    }

    @Test fun iconTest() {
        Assert.assertEquals(info.nightscout.core.main.R.drawable.ic_access_alarm_24dp, sut.icon())
    }

    @Test fun doActionTest() {
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
        Assert.assertEquals("{\"data\":{\"text\":\"Asd\"},\"type\":\"ActionAlarm\"}", sut.toJSON())
    }

    @Test fun fromJSONTest() {
        sut.text = InputString("Asd")
        sut.fromJSON("{\"text\":\"Asd\"}")
        Assert.assertEquals("Asd", sut.text.value)
    }
}