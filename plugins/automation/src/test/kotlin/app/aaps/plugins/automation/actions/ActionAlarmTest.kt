package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.queue.Callback
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputString
import app.aaps.plugins.automation.ui.TimerUtil
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class ActionAlarmTest : TestBaseWithProfile() {

    private lateinit var timerUtil: TimerUtil
    private lateinit var sut: ActionAlarm

    init {
        addInjector {
            if (it is ActionAlarm) {
                it.rh = rh
                it.rxBus = rxBus
                it.context = context
                it.timerUtil = timerUtil
                it.dateUtil = dateUtil
                it.config = config
                it.pumpEnactResultProvider = pumpEnactResultProvider
            }
        }
    }

    @BeforeEach
    fun setup() {
        whenever(rh.gs(app.aaps.core.ui.R.string.alarm)).thenReturn("Alarm")
        whenever(rh.gs(ArgumentMatchers.eq(R.string.alarm_message), any())).thenReturn("Alarm: %s")
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
        assertThat(sut.icon()).isEqualTo(app.aaps.core.objects.R.drawable.ic_access_alarm_24dp)
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
