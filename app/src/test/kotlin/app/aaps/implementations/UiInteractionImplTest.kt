package app.aaps.implementations

import android.content.Context
import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.core.interfaces.alerts.EatReminderState
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.implementation.androidNotification.AlarmNotificationManager
import app.aaps.shared.tests.TestBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Which alarm a Stop reaches (#5408). There is one full-screen alarm at a time, and a newer one takes its place.
 *
 * - Stopping the "time to eat" alarm shown is the timer's Stop, and it leaves every other alarm alone.
 * - Stopping another alarm leaves the timer alone, also a pump alarm that took the eat alarm's place after it rang.
 * - The timer's own Stop and +5 min silence only its own alarm.
 */
class UiInteractionImplTest : TestBase() {

    @Mock lateinit var context: Context
    @Mock lateinit var alarmNotificationManager: AlarmNotificationManager
    @Mock lateinit var notificationManager: NotificationManager
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var config: Config
    @Mock lateinit var eatReminder: EatReminder

    private val eatReminderState = MutableStateFlow<EatReminderState?>(null)
    private lateinit var sut: UiInteractionImpl

    @BeforeEach
    fun setUp() {
        whenever(eatReminder.state).thenReturn(eatReminderState)
        sut = UiInteractionImpl(
            context, alarmNotificationManager, { notificationManager }, aapsLogger, persistenceLayer, config,
            CoroutineScope(Dispatchers.Unconfined), { eatReminder }
        )
    }

    private fun eatAlarmRings() {
        eatReminderState.value = EatReminderState(startedAt = 0L, endsAt = 1_000L, durationMs = 1_000L, alarmRang = true)
        sut.runEatReminderAlarm(status = "Time to eat", title = "AAPS")
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
}
