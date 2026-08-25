package app.aaps.plugins.automation

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mockConstruction
import org.mockito.MockedConstruction.MockInitializer
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Covers the Android implementation of [app.aaps.core.interfaces.alerts.ReminderScheduler].
 *
 * The point of this class is *which* AlarmManager call it makes. Reminders used to be handed to the
 * system Clock app with `startActivity(ACTION_SET_TIMER)`, which Android blocks from the background,
 * so a reminder scheduled while AAPS was backgrounded - e.g. a Bolus-Wizard "Set alarm" relayed from
 * a client and delivered on the master - was silently lost. `setAlarmClock` is exact, fires while the
 * device is idle, and needs no SCHEDULE_EXACT_ALARM permission. Anything weaker quietly breaks the
 * feature again, and nothing else in the tree pins it.
 */
class ReminderSchedulerImplTest : TestBase() {

    @Mock lateinit var context: Context
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var alarmManager: AlarmManager

    // TestBase hands out a real RxBusImpl, which cannot be verified. This class only ever sends, so
    // a mock bus is enough and lets the failure path be asserted.
    @Mock lateinit var bus: RxBus

    private lateinit var sut: ReminderSchedulerImpl

    @BeforeEach
    fun prepare() {
        whenever(dateUtil.now()).thenReturn(1_000_000L)
        sut = ReminderSchedulerImpl(context, rh, bus, dateUtil)
    }

    @Test
    fun usesSetAlarmClockSoTheReminderSurvivesTheBackground() {
        whenever(context.getSystemService(Context.ALARM_SERVICE)).thenReturn(alarmManager)

        // Constructors of framework classes throw "Stub!" under the unit-test Android jar - only
        // methods get defaulted - so both of these have to be mocked or the class falls into its own
        // catch and the test passes while proving nothing. That is exactly how this call went
        // unasserted until now, and why the original author gave up and tested nothing.
        // putExtra has to return the intent itself: the class chains off it, and a mock returning null
        // would make PendingIntent.getBroadcast reject a null argument, landing in the catch again.
        val intentInit = MockInitializer<Intent> { mock, _ ->
            whenever(mock.putExtra(anyString(), anyString())).thenReturn(mock)
        }
        mockConstruction(Intent::class.java, intentInit).use {
            mockConstruction(AlarmManager.AlarmClockInfo::class.java).use {
                sut.scheduleReminder(60, "Time to eat")
            }
        }

        verify(alarmManager).setAlarmClock(anyOrNull(), anyOrNull())
        // The weaker calls would not fire while the device is idle, which is the whole point.
        verify(alarmManager, never()).set(anyInt(), anyLong(), anyOrNull())
        verify(alarmManager, never()).setExact(anyInt(), anyLong(), anyOrNull())
        verify(context, never()).startActivity(anyOrNull())
        verify(bus, never()).send(any())
    }

    @Test
    fun reportsFailureToTheUserInsteadOfThrowing() {
        // A reminder that cannot be scheduled must say so - failing silently is how the previous
        // background-reminder bug stayed hidden.
        whenever(context.getSystemService(Context.ALARM_SERVICE)).thenThrow(RuntimeException("no alarm service"))
        whenever(rh.gs(anyInt())).thenReturn("Cannot set reminder")

        val events = argumentCaptor<Event>()
        sut.scheduleReminder(60, "Time to eat")

        verify(bus).send(events.capture())
        val event = events.allValues.filterIsInstance<EventShowSnackbar>().single()
        assertThat(event.message).isEqualTo("Cannot set reminder")
        assertThat(event.type).isEqualTo(EventShowSnackbar.Type.Error)
    }
}
