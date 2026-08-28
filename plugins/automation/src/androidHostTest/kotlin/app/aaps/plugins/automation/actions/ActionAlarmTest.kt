package app.aaps.plugins.automation.actions

import app.aaps.plugins.automation.AutomationStrings
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.interfaces.alerts.ReminderScheduler
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputString
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dev.zacsweers.metro.Provider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class ActionAlarmTest : TestBaseWithProfile() {

    private lateinit var reminderScheduler: ReminderScheduler
    private lateinit var sut: ActionAlarm


    @BeforeEach
    fun setup() {
        whenever(rh.gs(CoreUiStrings.alarm)).thenReturn("Alarm")
        whenever(rh.gs(eq(AutomationStrings.alarm_message), any())).thenReturn("Alarm: %s")
        // ReminderScheduler is an interface now - the AlarmManager implementation lives in :app,
        // because a reminder that rings is an Android platform concern. That makes this a plain mock
        // instead of a real object that silently fell into its own catch block in a JVM test.
        reminderScheduler = mock()
        sut = ActionAlarm(aapsLogger, rh, Provider { pumpEnactResultProvider.get() }, rxBus, dateUtil, reminderScheduler, config)
    }

    @Test fun friendlyNameTest() = runTest {
        assertThat(sut.friendlyName()).isEqualTo(CoreUiStrings.alarm)
    }

    @Test fun shortDescriptionTest() = runTest {
        sut.text = InputString("Asd")
        assertThat(sut.shortDescription()).isEqualTo("Alarm: Asd")
    }

    @Test fun doActionTest() = runTest {
        sut.text = InputString("Asd")
        val result = sut.doAction()
        assertThat(result.success).isTrue()
        // Now checkable: the old test built a real scheduler that could not reach AlarmManager from a
        // JVM test, so it only ever proved doAction() did not throw.
        verify(reminderScheduler).scheduleReminder(10, "Asd")
    }

    @Test fun hasDialogTest() = runTest {
        assertThat(sut.hasDialog()).isTrue()
    }

    @Test fun toJSONTest() = runTest {
        sut.text = InputString("Asd")
        JSONAssert.assertEquals("""{"data":{"text":"Asd"},"type":"ActionAlarm"}""", sut.toJSON(), true)
    }

    @Test fun fromJSONTest() = runTest {
        sut.text = InputString("Asd")
        sut.fromJSON("""{"text":"Asd"}""")
        assertThat(sut.text.value).isEqualTo("Asd")
    }
}
