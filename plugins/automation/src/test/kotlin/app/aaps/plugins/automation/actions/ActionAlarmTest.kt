package app.aaps.plugins.automation.actions

import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputString
import app.aaps.plugins.automation.ui.TimerUtil
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.skyscreamer.jsonassert.JSONAssert

class ActionAlarmTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
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
        `when`(context.getString(app.aaps.core.ui.R.string.ok)).thenReturn("OK")
        `when`(context.getString(app.aaps.core.ui.R.string.alarm)).thenReturn("Alarm")
        `when`(rh.gs(ArgumentMatchers.eq(R.string.alarm_message), ArgumentMatchers.anyString())).thenReturn("Alarm: %s")
        timerUtil = TimerUtil(context)
        sut = ActionAlarm(injector)
    }

    @Test fun friendlyNameTest() {
        assertThat(sut.friendlyName()).isEqualTo(app.aaps.core.ui.R.string.alarm)
    }

    @Test fun shortDescriptionTest() {
        sut.text = InputString("Asd")
        assertThat(sut.shortDescription()).isEqualTo("Alarm: %s")
    }

    @Test fun iconTest() {
        assertThat(sut.icon()).isEqualTo(app.aaps.core.main.R.drawable.ic_access_alarm_24dp)
    }

    @Test fun doActionTest() {
        sut.text = InputString("Asd")
        sut.doAction(object : Callback() {
            override fun run() {
                assertThat(result.success).isTrue()
            }
        })
    }

    @Test fun hasDialogTest() {
        assertThat(sut.hasDialog()).isTrue()
    }

    @Test fun toJSONTest() {
        sut.text = InputString("Asd")
        JSONAssert.assertEquals("""{"data":{"text":"Asd"},"type":"ActionAlarm"}""", sut.toJSON(), true)
    }

    @Test fun fromJSONTest() {
        sut.text = InputString("Asd")
        sut.fromJSON("""{"text":"Asd"}""")
        assertThat(sut.text.value).isEqualTo("Asd")
    }
}
