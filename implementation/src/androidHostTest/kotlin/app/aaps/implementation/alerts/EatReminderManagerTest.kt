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
 * The "time to eat" timer (#5327, #5372): start, stop, +5 min, the restart, and the client's copy. Which alarm a
 * Stop of the alarm screen, the notification or the watch reaches is decided by `UiInteractionImpl`, see its test.
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

        assertThat(sut.extend()).isTrue()

        val state = sut.state.value!!
        assertThat(state.endsAt).isEqualTo(1_000_000L + twentyMinutes + fiveMinutes)
        assertThat(state.durationMs).isEqualTo(twentyMinutes + fiveMinutes)
        assertThat(alarm.scheduledEnd).isEqualTo(state.endsAt)
        verify(uiInteraction, never()).stopEatReminderAlarm()
        verify(uiInteraction, never()).stopAlarm(any())
    }

    /**
     * After it rang, +5 min counts from now, and its alarm stops - only its own alarm, never the others (#5408). The
     * time waited is part of the whole wait.
     */
    @Test
    fun extendAfterItRangCountsFromNowAndSilencesOnlyItsAlarm() {
        val sut = manager()
        sut.start(twentyMinutes)
        now += twentyMinutes + T.mins(2).msecs()
        sut.onRang()

        assertThat(sut.extend()).isTrue()

        assertThat(sut.state.value!!.endsAt).isEqualTo(now + fiveMinutes)
        assertThat(sut.state.value!!.durationMs).isEqualTo(twentyMinutes + T.mins(2).msecs() + fiveMinutes)
        assertThat(sut.state.value!!.alarmRang).isFalse()
        verify(uiInteraction).stopEatReminderAlarm()
        verify(uiInteraction, never()).stopAlarm(any())
    }

    /**
     * +5 min means "remind me in 5 minutes" everywhere. After Silence on the alarm screen ended the timer, its +5 min
     * is still there, and the watch counts on the same: a new timer of 5 minutes starts.
     */
    @Test
    fun extendWithNoTimerStartsANewOneOfFiveMinutes() {
        val sut = manager()
        sut.start(twentyMinutes)
        now += twentyMinutes
        sut.onRang()
        sut.stop()

        assertThat(sut.extend()).isTrue()

        assertThat(sut.state.value).isEqualTo(EatReminderState(startedAt = now, endsAt = now + fiveMinutes, durationMs = fiveMinutes))
        assertThat(alarm.scheduledEnd).isEqualTo(now + fiveMinutes)
    }

    /**
     * When the new alarm cannot be set (exact alarms not allowed any more), +5 min changes nothing: the timer stays
     * rung and its alarm is not silenced, so the reminder is not lost. The caller hears it failed (#5408).
     */
    @Test
    fun extendThatCannotSetTheAlarmChangesNothing() {
        val sut = manager()
        sut.start(twentyMinutes)
        now += twentyMinutes
        sut.onRang()
        val before = sut.state.value
        alarm.canSchedule = false

        assertThat(sut.extend()).isFalse()

        assertThat(sut.state.value).isEqualTo(before)
        verify(uiInteraction, never()).stopEatReminderAlarm()
        verify(uiInteraction, never()).stopAlarm(any())
    }

    @Test
    fun stopBeforeItRangCancelsWithoutTouchingAnyAlarm() {
        val sut = manager()
        sut.start(twentyMinutes)

        sut.stop()

        assertThat(sut.state.value).isNull()
        assertThat(alarm.scheduledEnd).isNull()
        assertThat(saved).isEmpty()
        verify(uiInteraction, never()).stopEatReminderAlarm()
        verify(uiInteraction, never()).stopAlarm(any())
    }

    /** Stop after it rang silences its own alarm and leaves every other alarm alone (#5408). */
    @Test
    fun stopAfterItRangSilencesOnlyItsAlarm() {
        val sut = manager()
        sut.start(twentyMinutes)
        now += twentyMinutes
        sut.onRang()

        sut.stop()

        assertThat(sut.state.value).isNull()
        verify(uiInteraction).stopEatReminderAlarm()
        verify(uiInteraction, never()).stopAlarm(any())
    }

    /** The rang mark survives a restart, so a Stop after it still silences the alarm. */
    @Test
    fun theRangMarkSurvivesARestart() {
        manager().apply {
            start(twentyMinutes)
            now += twentyMinutes
            onRang()
        }

        val afterRestart = manager()
        assertThat(afterRestart.state.value!!.alarmRang).isTrue()
        afterRestart.stop()

        assertThat(afterRestart.state.value).isNull()
        verify(uiInteraction).stopEatReminderAlarm()
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

    /** A client asks the master; changing its own copy here would only be undone by the next sync. */
    @Test
    fun onAClientStopAndExtendLeaveTheCopy() {
        whenever(config.AAPSCLIENT).thenReturn(true)
        val sut = manager()
        val remote = EatReminderState(startedAt = 0L, endsAt = now - 1_000L, durationMs = twentyMinutes)
        sut.applyRemote(remote)

        sut.stop()
        assertThat(sut.extend()).isFalse()

        assertThat(sut.state.value).isEqualTo(remote)
        assertThat(alarm.scheduledEnd).isNull()
    }

    @Test
    fun aMasterIgnoresARemoteTimer() {
        val sut = manager()

        sut.applyRemote(EatReminderState(startedAt = 0L, endsAt = twentyMinutes, durationMs = twentyMinutes))

        assertThat(sut.state.value).isNull()
    }
}
