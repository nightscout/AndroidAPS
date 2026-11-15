package app.aaps.implementations

import android.content.Context
import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.core.interfaces.alerts.EatReminderState
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventAlarmDismissed
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.implementation.androidNotification.AlarmNotificationManager
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Which alarm a Stop reaches (#5408). There is one full-screen alarm at a time, and a newer one takes its place.
 *
 * - Stopping the "time to eat" alarm shown is the timer's Stop, and it leaves every other alarm alone.
 * - Stopping another alarm leaves the timer alone, also a pump alarm that took the eat alarm's place after it rang.
 * - The timer's own Stop and +5 min silence only its own alarm.
 *
 * And what the watch is told: every alarm but the eat alarm, and every stop. A mute rings the same alarm again
 * after its minutes, on the phone and on the watch, unless it was stopped or replaced.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UiInteractionImplTest : TestBase() {

    @Mock lateinit var context: Context
    @Mock lateinit var alarmNotificationManager: AlarmNotificationManager
    @Mock lateinit var notificationManager: NotificationManager
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var config: Config
    @Mock lateinit var eatReminder: EatReminder
    @Mock lateinit var bus: RxBus

    private val eatReminderState = MutableStateFlow<EatReminderState?>(null)
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private lateinit var sut: UiInteractionImpl

    @BeforeEach
    fun setUp() {
        // The re-ring of a mute hops to the main thread
        Dispatchers.setMain(dispatcher)
        whenever(eatReminder.state).thenReturn(eatReminderState)
        sut = UiInteractionImpl(
            context, alarmNotificationManager, { notificationManager }, aapsLogger, persistenceLayer, config,
            CoroutineScope(dispatcher), bus, { eatReminder }
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun eatAlarmRings() {
        eatReminderState.value = EatReminderState(startedAt = 0L, endsAt = 1_000L, durationMs = 1_000L, alarmRang = true)
        sut.runEatReminderAlarm(status = "Time to eat", title = "AAPS")
    }

    private fun sentEvents(): List<Event> {
        val captor = argumentCaptor<Event>()
        verify(bus, atLeast(0)).send(captor.capture())
        return captor.allValues
    }

    private fun sentToWear(): List<EventData> = sentEvents().filterIsInstance<EventMobileToWear>().map { it.payload }

    private fun ringingOnWear(): List<EventData.AlarmRinging> = sentToWear().filterIsInstance<EventData.AlarmRinging>()

    private fun minutesPass(minutes: Int) {
        scheduler.advanceTimeBy(minutes * 60_000L)
        scheduler.runCurrent()
    }

    /** OK and Silence on its screen, Mute on its notification, the watch: the timer ends, other alarms sound on. */
    @Test
    fun stoppingTheEatAlarmShownIsTheTimersStopAndLeavesOtherAlarms() {
        eatAlarmRings()

        sut.stopAlarm("Mute")

        verify(eatReminder).stop()
        verify(alarmNotificationManager).cancelAlarm()
        verify(notificationManager, never()).muteAllAlarms()
    }

    /** The user silenced the pump alarm: the eat timer stays, its bar shows "time is up", the reminder is not lost. */
    @Test
    fun stoppingAnAlarmThatTookTheEatAlarmsPlaceLeavesTheTimer() {
        eatAlarmRings()
        sut.runAlarm(status = "Pump unreachable", title = "AAPS")

        sut.stopAlarm("Mute")

        verify(notificationManager).muteAllAlarms()
        verify(eatReminder, never()).stop()
    }

    /** Stop or +5 min of the timer from a client or the bar must not silence a pump alarm nobody has seen. */
    @Test
    fun theTimersStopLeavesAnAlarmThatTookItsPlace() {
        eatAlarmRings()
        sut.runAlarm(status = "Pump unreachable", title = "AAPS")

        sut.stopEatReminderAlarm()

        verify(alarmNotificationManager, never()).cancelAlarm()
        verify(notificationManager, never()).muteAllAlarms()
    }

    @Test
    fun theTimersStopSilencesOnlyItsOwnAlarm() {
        eatAlarmRings()

        sut.stopEatReminderAlarm()

        verify(alarmNotificationManager).cancelAlarm()
        verify(notificationManager, never()).muteAllAlarms()
    }

    /** A new timer was started while the old eat alarm was still shown: OK on that alarm leaves the new timer. */
    @Test
    fun stoppingTheOldEatAlarmLeavesANewTimer() {
        eatAlarmRings()
        eatReminderState.value = EatReminderState(startedAt = 2_000L, endsAt = 3_000L, durationMs = 1_000L)

        sut.stopAlarm("Dismiss")

        verify(eatReminder, never()).stop()
        verify(alarmNotificationManager).cancelAlarm()
    }

    /**
     * Android may deliver the eat alarm a little after the end time. A BG alarm muted in that gap is not the timer's
     * alarm, and must not end the timer, or its alarm would come to nothing.
     */
    @Test
    fun stoppingAnotherAlarmBeforeTheEatAlarmRangLeavesTheTimer() {
        eatReminderState.value = EatReminderState(startedAt = 0L, endsAt = 1_000L, durationMs = 1_000L)
        sut.runAlarm(status = "BG low", title = "AAPS")

        sut.stopAlarm("Mute")

        verify(notificationManager).muteAllAlarms()
        verify(eatReminder, never()).stop()
    }

    /** The watch gets the alarm's title and text, so it can tell why the phone rings, and the stop when it ends. */
    @Test
    fun theWatchIsToldOfAnAlarmAndOfItsStop() {
        sut.runAlarm(status = "Pump unreachable", title = "AAPS")

        val ringing = ringingOnWear().single()
        assertThat(ringing.title).isEqualTo("AAPS")
        assertThat(ringing.status).isEqualTo("Pump unreachable")
        assertThat(ringing.canMute).isTrue()

        sut.stopAlarm("Mute")

        assertThat(sentToWear().last()).isInstanceOf(EventData.AlarmStopped::class.java)
    }

    /** The watch rings the eat alarm by itself: only the alarm it takes the place of goes away there. */
    @Test
    fun theEatAlarmIsNotSentToTheWatch() {
        eatAlarmRings()

        assertThat(sentToWear().single()).isInstanceOf(EventData.AlarmStopped::class.java)

        sut.stopAlarm("Mute")

        assertThat(sentToWear()).hasSize(1)
    }

    /** Silence on the watch: the alarm stops, and its screen is told to close. */
    @Test
    fun silenceFromTheWatchStopsTheAlarmAndClosesItsScreen() {
        sut.runAlarm(status = "Pump unreachable", title = "AAPS")

        sut.dismissAlarm("Dismissed from wear")

        verify(notificationManager).muteAllAlarms()
        assertThat(sentEvents().filterIsInstance<EventAlarmDismissed>()).hasSize(1)
    }

    /** The quick snooze from the watch: the alarm stops, its screen stays, so the message can still be read. */
    @Test
    fun theQuickSnoozeFromTheWatchLeavesTheAlarmScreen() {
        sut.runAlarm(status = "Pump unreachable", title = "AAPS")

        sut.stopAlarm("Muted from wear")

        verify(notificationManager).muteAllAlarms()
        assertThat(sentEvents().filterIsInstance<EventAlarmDismissed>()).isEmpty()
    }

    /** "Mute for 5 minutes", on the screen or from the watch: silent now, the same alarm again after the minutes, the watch told both times. */
    @Test
    fun aMuteRingsTheSameAlarmAgainAfterItsMinutes() {
        sut.runAlarm(status = "Pump unreachable", title = "AAPS")

        sut.muteAlarm(5, "Mute 5 min")

        verify(notificationManager).muteAllAlarms()
        assertThat(sentToWear().last()).isInstanceOf(EventData.AlarmStopped::class.java)
        minutesPass(4)
        assertThat(ringingOnWear()).hasSize(1)

        minutesPass(1)

        assertThat(ringingOnWear()).hasSize(2)
        assertThat(ringingOnWear().last().status).isEqualTo("Pump unreachable")
    }

    /** Stopped before the minutes are over, on the screen or from the watch: the alarm does not come back. */
    @Test
    fun aStopEndsThePendingReRing() {
        sut.runAlarm(status = "Pump unreachable", title = "AAPS")
        sut.muteAlarm(5, "Mute 5 min")

        sut.dismissAlarm("Dismiss")
        minutesPass(6)

        assertThat(ringingOnWear()).hasSize(1)
    }

    /** A new alarm in the meantime takes the place of the muted one: only the new one is shown, the old one stays muted. */
    @Test
    fun aNewAlarmTakesThePlaceOfTheMutedOne() {
        sut.runAlarm(status = "Pump unreachable", title = "AAPS")
        sut.muteAlarm(5, "Mute 5 min")

        sut.runAlarm(status = "BG low", title = "AAPS")
        minutesPass(6)

        assertThat(ringingOnWear().map { it.status }).containsExactly("Pump unreachable", "BG low").inOrder()
    }

    /** The eat alarm has no mute, +5 min moves its timer: a mute reaching it is its stop, and nothing comes back. */
    @Test
    fun aMuteOnTheEatAlarmIsTheTimersStop() {
        eatAlarmRings()

        sut.muteAlarm(5, "Muted from wear for 5 min")
        minutesPass(6)

        verify(eatReminder).stop()
        assertThat(ringingOnWear()).isEmpty()
    }
}
