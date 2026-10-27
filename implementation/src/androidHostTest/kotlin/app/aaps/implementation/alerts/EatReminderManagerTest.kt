package app.aaps.implementation.alerts

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.alerts.EatReminderState
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The "time to eat" timer (#5327, #5372): start, stop, +5 min, the link to every way of stopping its alarm,
 * the restart, and the client's copy.
 */
class EatReminderManagerTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var config: Config
    @Mock lateinit var uiInteraction: UiInteraction

    /** Records what the manager asks of the platform; [canSchedule] false acts like a phone without exact alarms. */
    private class FakeAlarm : EatReminderAlarm {

        var canSchedule = true
        var scheduledEnd: Long? = null
        var cancels = 0

        override fun schedule(startedAt: Long, endsAt: Long): Boolean {
            if (!canSchedule) return false
            scheduledEnd = endsAt
            return true
        }

        override fun cancel() {
            scheduledEnd = null
            cancels++
        }
    }

    private val alarm = FakeAlarm()
    private var now = 1_000_000L
    private var saved = ""

    @BeforeEach
    fun setUp() {
        whenever(dateUtil.now()).thenAnswer { now }
        whenever(preferences.get(StringNonKey.EatReminder)).thenAnswer { saved }
        whenever(preferences.put(eq(StringNonKey.EatReminder), any<String>())).thenAnswer { saved = it.getArgument(1); Unit }
    }

    private fun manager() = EatReminderManager(preferences, alarm, { uiInteraction }, dateUtil, config, aapsLogger)

    private val twentyMinutes = T.mins(20).msecs()
    private val fiveMinutes = T.mins(5).msecs()

    @Test
    fun startSetsTheAlarmAndShowsTheTimer() {
        val sut = manager()

        sut.start(twentyMinutes)

        assertThat(sut.state.value).isEqualTo(EatReminderState(startedAt = now, endsAt = now + twentyMinutes, durationMs = twentyMinutes))
        assertThat(alarm.scheduledEnd).isEqualTo(now + twentyMinutes)
    }

    /** No countdown on screen that would never ring. */
    @Test
    fun startWithoutAnAlarmShowsNoTimer() {
        alarm.canSchedule = false
        val sut = manager()

        sut.start(twentyMinutes)

        assertThat(sut.state.value).isNull()
        assertThat(saved).isEmpty()
    }

    @Test
    fun theTimerSurvivesARestart() {
        manager().start(twentyMinutes)

        val afterRestart = manager()

        assertThat(afterRestart.state.value).isEqualTo(EatReminderState(startedAt = now, endsAt = now + twentyMinutes, durationMs = twentyMinutes))
    }

    @Test
    fun extendBeforeItRangMovesTheEndAndGrowsTheWholeWait() {
        val sut = manager()
        sut.start(twentyMinutes)
        now += T.mins(3).msecs()

        sut.extend()

        val state = sut.state.value!!
        assertThat(state.endsAt).isEqualTo(1_000_000L + twentyMinutes + fiveMinutes)
        assertThat(state.durationMs).isEqualTo(twentyMinutes + fiveMinutes)
        assertThat(alarm.scheduledEnd).isEqualTo(state.endsAt)
        verify(uiInteraction, never()).stopAlarm(any())
    }

    /** After it rang, +5 min counts from now, and the ringing alarm stops. The time waited is part of the whole wait. */
    @Test
    fun extendAfterItRangCountsFromNowAndSilencesTheAlarm() {
        val sut = manager()
        sut.start(twentyMinutes)
        now += twentyMinutes + T.mins(2).msecs()
        sut.onRang()

        sut.extend()

        assertThat(sut.state.value!!.endsAt).isEqualTo(now + fiveMinutes)
        assertThat(sut.state.value!!.durationMs).isEqualTo(twentyMinutes + T.mins(2).msecs() + fiveMinutes)
        assertThat(sut.state.value!!.alarmRang).isFalse()
        verify(uiInteraction).stopAlarm(any())
    }

    @Test
    fun stopBeforeItRangCancelsWithoutTouchingOtherAlarms() {
        val sut = manager()
        sut.start(twentyMinutes)

        sut.stop()

        assertThat(sut.state.value).isNull()
        assertThat(alarm.scheduledEnd).isNull()
        assertThat(saved).isEmpty()
        verify(uiInteraction, never()).stopAlarm(any())
    }

    @Test
    fun stopAfterItRangSilencesTheAlarm() {
        val sut = manager()
        sut.start(twentyMinutes)
        now += twentyMinutes
        sut.onRang()

        sut.stop()

        assertThat(sut.state.value).isNull()
        verify(uiInteraction).stopAlarm(any())
    }

    /** OK on the alarm screen, Mute on the notification, the watch: all end the timer once it rang. */
    @Test
    fun stoppingTheAlarmAfterItRangEndsTheTimer() {
        val sut = manager()
        sut.start(twentyMinutes)
        now += twentyMinutes + 1_000L
        sut.onRang()

        sut.onAlarmStopped()

        assertThat(sut.state.value).isNull()
    }

    /**
     * Android may deliver the alarm a little after the end time. A BG alarm muted in that gap is not the timer's
     * alarm, and must not end the timer, or its alarm would come to nothing.
     */
    @Test
    fun stoppingAnotherAlarmAfterTheEndButBeforeTheTimerRangChangesNothing() {
        val sut = manager()
        sut.start(twentyMinutes)
        now += twentyMinutes + 1_000L

        sut.onAlarmStopped()

        assertThat(sut.state.value).isNotNull()
        assertThat(alarm.scheduledEnd).isEqualTo(1_000_000L + twentyMinutes)
    }

    @Test
    fun theRangMarkSurvivesARestart() {
        manager().apply {
            start(twentyMinutes)
            now += twentyMinutes
            onRang()
        }

        val afterRestart = manager()
        afterRestart.onAlarmStopped()

        assertThat(afterRestart.state.value).isNull()
    }

    /** Muting a BG alarm while the timer still runs must not end the timer. */
    @Test
    fun stoppingAnotherAlarmWhileTheTimerRunsChangesNothing() {
        val sut = manager()
        sut.start(twentyMinutes)
        now += T.mins(10).msecs()

        sut.onAlarmStopped()

        assertThat(sut.state.value).isNotNull()
        assertThat(alarm.scheduledEnd).isEqualTo(1_000_000L + twentyMinutes)
    }

    @Test
    fun restoreSetsTheAlarmAgainForATimerStillRunning() {
        manager().start(twentyMinutes)
        alarm.scheduledEnd = null // a reboot removed it
        now += T.mins(5).msecs()

        manager().restore()

        assertThat(alarm.scheduledEnd).isEqualTo(1_000_000L + twentyMinutes)
    }

    @Test
    fun restoreDropsATimerThatEndedLongAgo() {
        manager().start(twentyMinutes)
        now += twentyMinutes + T.hours(2).msecs()
        val sut = manager()

        sut.restore()

        assertThat(sut.state.value).isNull()
        assertThat(saved).isEmpty()
    }

    /** A short time after it rang the bar still shows it; ringing a second time is not wanted. */
    @Test
    fun restoreKeepsATimerThatRangShortlyAgoWithoutRingingAgain() {
        manager().start(twentyMinutes)
        alarm.scheduledEnd = null
        now += twentyMinutes + T.mins(5).msecs()
        val sut = manager()

        sut.restore()

        assertThat(sut.state.value).isNotNull()
        assertThat(alarm.scheduledEnd).isNull()
    }

    @Test
    fun aClientTakesTheMastersTimerAndNeverSetsAnAlarm() {
        whenever(config.AAPSCLIENT).thenReturn(true)
        val sut = manager()
        val remote = EatReminderState(startedAt = 5L, endsAt = 10L + twentyMinutes, durationMs = twentyMinutes)

        sut.applyRemote(remote)
        sut.start(twentyMinutes)
        sut.restore()

        assertThat(sut.state.value).isEqualTo(remote)
        assertThat(alarm.scheduledEnd).isNull()

        sut.applyRemote(null)
        assertThat(sut.state.value).isNull()
    }

    /** A client's own alarms (a BG alarm) must not end its copy; only the master ends the timer. */
    @Test
    fun onAClientStoppingAnAlarmLeavesTheCopy() {
        whenever(config.AAPSCLIENT).thenReturn(true)
        val sut = manager()
        sut.applyRemote(EatReminderState(startedAt = 0L, endsAt = now - 1_000L, durationMs = twentyMinutes))

        sut.onAlarmStopped()

        assertThat(sut.state.value).isNotNull()
    }

    @Test
    fun aMasterIgnoresARemoteTimer() {
        val sut = manager()

        sut.applyRemote(EatReminderState(startedAt = 0L, endsAt = twentyMinutes, durationMs = twentyMinutes))

        assertThat(sut.state.value).isNull()
    }
}
