package info.nightscout.automation.actions

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.automation.R
import info.nightscout.automation.elements.InputString
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.automation.ui.TimerUtil
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.`when`

class ActionAlarmTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var rxBus: RxBus
    @Mock lateinit var context: Context
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var config: Config

    private lateinit var timerUtil: TimerUtil
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
            if (it is PumpEnactResult) {
                it.context = context
            }
        }
    }

    @BeforeEach
    fun setup() {
        `when`(context.getString(info.nightscout.core.ui.R.string.ok)).thenReturn("OK")
        `when`(context.getString(info.nightscout.core.ui.R.string.alarm)).thenReturn("Alarm")
        `when`(rh.gs(ArgumentMatchers.eq(R.string.alarm_message), ArgumentMatchers.anyString())).thenReturn("Alarm: %s")
        timerUtil = TimerUtil(context)
        sut = ActionAlarm(injector)
    }

    @Test fun friendlyNameTest() {
        Assert.assertEquals(info.nightscout.core.ui.R.string.alarm, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        sut.text = InputString("Asd")
        Assert.assertEquals("Alarm: %s", sut.shortDescription())
    }

    @Test fun iconTest() {
        Assert.assertEquals(info.nightscout.core.main.R.drawable.ic_access_alarm_24dp, sut.icon())
    }

    @Test fun doActionTest() {
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
        Assert.assertEquals("{\"data\":{\"text\":\"Asd\"},\"type\":\"ActionAlarm\"}", sut.toJSON())
    }

    @Test fun fromJSONTest() {
        sut.text = InputString("Asd")
        sut.fromJSON("{\"text\":\"Asd\"}")
        Assert.assertEquals("Asd", sut.text.value)
    }
}