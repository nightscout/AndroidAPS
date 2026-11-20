package app.aaps.implementation.alerts.cgm

import app.aaps.core.interfaces.alerts.CgmAlarmType
import app.aaps.core.interfaces.notifications.AlarmSound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules of [CgmAlarmEngine]. In commonTest, so they run on the JVM and on the iOS simulator.
 *
 * Times are minutes from a fixed start, to keep the cases readable.
 */
class CgmAlarmEngineTest {

    private val start = 1_000_000_000L
    private fun at(minutes: Int): Long = start + minutes * 60_000L

    /** Every alarm on, no delay, repeat 15 min, with a sound. Change one alarm with [with]. */
    private fun settings(
        with: Map<CgmAlarmType, CgmAlarmSettings> = emptyMap(),
    ): (CgmAlarmType) -> CgmAlarmSettings = { type ->
        with[type] ?: CgmAlarmSettings(enabled = true, repeatMinutes = 15, sound = AlarmSound.ALARM)
    }

    private fun on(vararg types: CgmAlarmType): Map<CgmAlarmType, CgmAlarmCondition> = types.associateWith { CgmAlarmCondition() }

    private fun raised(step: CgmAlarmStep): List<CgmAlarmType> = step.actions.filterIsInstance<CgmAlarmAction.Raise>().map { it.type }
    private fun cleared(step: CgmAlarmStep): List<CgmAlarmType> = step.actions.filterIsInstance<CgmAlarmAction.Clear>().map { it.type }

    @Test
    fun anAlarmGoesOffWhenItsConditionHolds() {
        val step = CgmAlarmEngine.step(on(CgmAlarmType.HIGH), settings(), CgmAlarmRuntime(), at(0))
        assertEquals(listOf(CgmAlarmType.HIGH), raised(step))
        assertTrue(step.runtime[CgmAlarmType.HIGH].isRaised)
        assertEquals(at(0) + CgmAlarmEngine.REMINDER_MS, step.nextCheckAt)
    }

    @Test
    fun aSwitchedOffAlarmNeverGoesOff() {
        val off = settings(mapOf(CgmAlarmType.HIGH to CgmAlarmSettings(enabled = false)))
        val step = CgmAlarmEngine.step(on(CgmAlarmType.HIGH), off, CgmAlarmRuntime(), at(0))
        assertTrue(step.actions.isEmpty())
    }

    @Test
    fun switchingAnAlarmOffWhileItRingsStopsIt() {
        val ringing = CgmAlarmEngine.step(on(CgmAlarmType.HIGH), settings(), CgmAlarmRuntime(), at(0)).runtime
        val off = settings(mapOf(CgmAlarmType.HIGH to CgmAlarmSettings(enabled = false)))
        val step = CgmAlarmEngine.step(on(CgmAlarmType.HIGH), off, ringing, at(1))
        assertEquals(listOf(CgmAlarmType.HIGH), cleared(step))
    }

    @Test
    fun theDelayHoldsTheFirstAlarmBack() {
        val delayed = settings(mapOf(CgmAlarmType.HIGH to CgmAlarmSettings(enabled = true, delayMinutes = 30, repeatMinutes = 15)))
        var step = CgmAlarmEngine.step(on(CgmAlarmType.HIGH), delayed, CgmAlarmRuntime(), at(0))
        assertTrue(raised(step).isEmpty())
        assertEquals(at(30), step.nextCheckAt)
        step = CgmAlarmEngine.step(on(CgmAlarmType.HIGH), delayed, step.runtime, at(29))
        assertTrue(raised(step).isEmpty())
        step = CgmAlarmEngine.step(on(CgmAlarmType.HIGH), delayed, step.runtime, at(30))
        assertEquals(listOf(CgmAlarmType.HIGH), raised(step))
    }

    @Test
    fun theDelayStartsAgainWhenTheConditionGoesAway() {
        val delayed = settings(mapOf(CgmAlarmType.HIGH to CgmAlarmSettings(enabled = true, delayMinutes = 30, repeatMinutes = 15)))
        var step = CgmAlarmEngine.step(on(CgmAlarmType.HIGH), delayed, CgmAlarmRuntime(), at(0))
        step = CgmAlarmEngine.step(emptyMap(), delayed, step.runtime, at(20))
        step = CgmAlarmEngine.step(on(CgmAlarmType.HIGH), delayed, step.runtime, at(25))
        step = CgmAlarmEngine.step(on(CgmAlarmType.HIGH), delayed, step.runtime, at(40))
        assertTrue(raised(step).isEmpty(), "only 15 min since the condition came back")
        step = CgmAlarmEngine.step(on(CgmAlarmType.HIGH), delayed, step.runtime, at(55))
        assertEquals(listOf(CgmAlarmType.HIGH), raised(step))
    }

    @Test
    fun theDelayCountsFromTheStartTheSourceKnows() {
        val lossSince = at(0)
        val condition = mapOf(CgmAlarmType.SIGNAL_LOSS to CgmAlarmCondition(episode = lossSince.toString(), sinceMs = lossSince))
        val delayed = settings(mapOf(CgmAlarmType.SIGNAL_LOSS to CgmAlarmSettings(enabled = true, delayMinutes = 10, repeatMinutes = 30)))
        // Seen for the first time at minute 10: the signal has been lost for 10 minutes already.
        val step = CgmAlarmEngine.step(condition, delayed, CgmAlarmRuntime(), at(10))
        assertEquals(listOf(CgmAlarmType.SIGNAL_LOSS), raised(step))
    }

    /** Review point 1 of PR 5397: a dismissed notification is not an acknowledge. */
    @Test
    fun anAlarmThatIsNotAcknowledgedComesBackEveryFiveMinutes() {
        var step = CgmAlarmEngine.step(on(CgmAlarmType.LOW), settings(), CgmAlarmRuntime(), at(0))
        step = CgmAlarmEngine.step(on(CgmAlarmType.LOW), settings(), step.runtime, at(4))
        assertTrue(step.actions.isEmpty())
        assertEquals(at(5), step.nextCheckAt)
        step = CgmAlarmEngine.step(on(CgmAlarmType.LOW), settings(), step.runtime, at(5))
        assertEquals(listOf(CgmAlarmAction.Raise(CgmAlarmType.LOW, reminder = true)), step.actions)
        step = CgmAlarmEngine.step(on(CgmAlarmType.LOW), settings(), step.runtime, at(10))
        assertEquals(listOf(CgmAlarmAction.Raise(CgmAlarmType.LOW, reminder = true)), step.actions)
    }

    @Test
    fun anAlarmStopsWhenItsConditionGoesAway() {
        var step = CgmAlarmEngine.step(on(CgmAlarmType.LOW), settings(), CgmAlarmRuntime(), at(0))
        step = CgmAlarmEngine.step(emptyMap(), settings(), step.runtime, at(3))
        assertEquals(listOf(CgmAlarmType.LOW), cleared(step))
        assertFalse(step.runtime[CgmAlarmType.LOW].isRaised)
    }

    @Test
    fun acknowledgeStopsTheAlarmForItsRepeatTimeThenItComesBack() {
        var runtime = CgmAlarmEngine.step(on(CgmAlarmType.LOW), settings(), CgmAlarmRuntime(), at(0)).runtime
        val ack = CgmAlarmEngine.acknowledge(runtime, settings(), CgmAlarmType.LOW, at(1))
        assertEquals(listOf(CgmAlarmType.LOW), cleared(ack))
        assertEquals(at(16), ack.nextCheckAt)
        runtime = ack.runtime
        var step = CgmAlarmEngine.step(on(CgmAlarmType.LOW), settings(), runtime, at(6))
        assertTrue(step.actions.isEmpty(), "no reminder while acknowledged")
        step = CgmAlarmEngine.step(on(CgmAlarmType.LOW), settings(), step.runtime, at(15))
        assertTrue(step.actions.isEmpty())
        step = CgmAlarmEngine.step(on(CgmAlarmType.LOW), settings(), step.runtime, at(16))
        assertEquals(listOf(CgmAlarmAction.Raise(CgmAlarmType.LOW, reminder = false)), step.actions)
    }

    @Test
    fun acknowledgeWithNoTypeStopsEveryRingingAlarm() {
        val runtime = CgmAlarmEngine.step(on(CgmAlarmType.LOW, CgmAlarmType.SIGNAL_LOSS), settings(), CgmAlarmRuntime(), at(0)).runtime
        val ack = CgmAlarmEngine.acknowledge(runtime, settings(), null, at(1))
        assertEquals(setOf(CgmAlarmType.LOW, CgmAlarmType.SIGNAL_LOSS), cleared(ack).toSet())
        assertTrue(ack.runtime.raised.isEmpty())
    }

    @Test
    fun repeatZeroKeepsTheAlarmQuietUntilTheConditionHasGone() {
        val once = settings(mapOf(CgmAlarmType.SENSOR_FAILED to CgmAlarmSettings(enabled = true, repeatMinutes = 0, sound = AlarmSound.ALARM)))
        val failed = on(CgmAlarmType.SENSOR_FAILED)
        var runtime = CgmAlarmEngine.step(failed, once, CgmAlarmRuntime(), at(0)).runtime
        runtime = CgmAlarmEngine.acknowledge(runtime, once, CgmAlarmType.SENSOR_FAILED, at(1)).runtime
        var step = CgmAlarmEngine.step(failed, once, runtime, at(300))
        assertTrue(step.actions.isEmpty(), "still quiet hours later")
        // Gone for less than CLEAR_MS: still quiet when it comes back.
        step = CgmAlarmEngine.step(emptyMap(), once, step.runtime, at(301))
        step = CgmAlarmEngine.step(failed, once, step.runtime, at(305))
        assertTrue(step.actions.isEmpty())
        // Gone for CLEAR_MS: a new alarm.
        step = CgmAlarmEngine.step(emptyMap(), once, step.runtime, at(306))
        step = CgmAlarmEngine.step(emptyMap(), once, step.runtime, at(321))
        step = CgmAlarmEngine.step(failed, once, step.runtime, at(322))
        assertEquals(listOf(CgmAlarmType.SENSOR_FAILED), raised(step))
    }

    @Test
    fun aNewEpisodeIsANewAlertEvenAfterAnAcknowledge() {
        val once = settings(mapOf(CgmAlarmType.SIGNAL_LOSS to CgmAlarmSettings(enabled = true, repeatMinutes = 0, sound = AlarmSound.ALARM)))
        val first = mapOf(CgmAlarmType.SIGNAL_LOSS to CgmAlarmCondition(episode = "a"))
        val second = mapOf(CgmAlarmType.SIGNAL_LOSS to CgmAlarmCondition(episode = "b"))
        var runtime = CgmAlarmEngine.step(first, once, CgmAlarmRuntime(), at(0)).runtime
        runtime = CgmAlarmEngine.acknowledge(runtime, once, null, at(1)).runtime
        val step = CgmAlarmEngine.step(second, once, runtime, at(2))
        assertEquals(listOf(CgmAlarmType.SIGNAL_LOSS), raised(step))
    }

    /** Review point 2 of PR 5397: the mute timer can never mute urgent low. */
    @Test
    fun theMuteNeverSilencesUrgentLow() {
        val muteEnd = at(240)
        val step = CgmAlarmEngine.step(on(CgmAlarmType.URGENT_LOW), settings(), CgmAlarmRuntime(), at(0), mutedUntil = muteEnd)
        assertEquals(listOf(CgmAlarmType.URGENT_LOW), raised(step))
        assertFalse(CgmAlarmEngine.isMuted(CgmAlarmType.URGENT_LOW, at(0), muteEnd))
        // Its reminder is not muted either.
        val again = CgmAlarmEngine.step(on(CgmAlarmType.URGENT_LOW), settings(), step.runtime, at(5), mutedUntil = muteEnd)
        assertEquals(listOf(CgmAlarmAction.Raise(CgmAlarmType.URGENT_LOW, reminder = true)), again.actions)
    }

    @Test
    fun onlyUrgentLowIsNeverMuted() {
        assertEquals(listOf(CgmAlarmType.URGENT_LOW), CgmAlarmType.entries.filter { it.neverMuted })
        CgmAlarmType.entries.filter { !it.neverMuted }.forEach { assertTrue(CgmAlarmEngine.isMuted(it, at(0), at(10)), it.id) }
    }

    @Test
    fun theMuteStopsOtherAlarmsAndTheyComeBackWhenItEnds() {
        val ringing = CgmAlarmEngine.step(on(CgmAlarmType.HIGH), settings(), CgmAlarmRuntime(), at(0)).runtime
        var step = CgmAlarmEngine.step(on(CgmAlarmType.HIGH), settings(), ringing, at(1), mutedUntil = at(31))
        assertEquals(listOf(CgmAlarmType.HIGH), cleared(step))
        assertEquals(at(31), step.nextCheckAt)
        step = CgmAlarmEngine.step(on(CgmAlarmType.HIGH), settings(), step.runtime, at(20), mutedUntil = at(31))
        assertTrue(step.actions.isEmpty())
        step = CgmAlarmEngine.step(on(CgmAlarmType.HIGH), settings(), step.runtime, at(31), mutedUntil = at(31))
        assertEquals(listOf(CgmAlarmAction.Raise(CgmAlarmType.HIGH, reminder = false)), step.actions)
    }

    @Test
    fun anUrgentLowAcknowledgeIsNeverLongerThanItsRepeatTime() {
        val runtime = CgmAlarmEngine.step(on(CgmAlarmType.URGENT_LOW, CgmAlarmType.LOW), settings(), CgmAlarmRuntime(), at(0)).runtime
        // "Hypo treated" asks for 20 min; urgent low has a 15 min repeat here, so it gets 15.
        val ack = CgmAlarmEngine.acknowledge(runtime, settings(), null, at(0), snoozeMs = 20 * 60_000L)
        assertEquals(at(15), ack.runtime[CgmAlarmType.URGENT_LOW].snoozedUntil)
    }

    @Test
    fun theMostImportantAlarmOfAGroupWins() {
        var step = CgmAlarmEngine.step(on(CgmAlarmType.URGENT_LOW, CgmAlarmType.LOW, CgmAlarmType.FALL_RATE), settings(), CgmAlarmRuntime(), at(0))
        assertEquals(listOf(CgmAlarmType.URGENT_LOW), raised(step))
        // A low that rings is stopped when urgent low takes over.
        step = CgmAlarmEngine.step(on(CgmAlarmType.LOW), settings(), CgmAlarmRuntime(), at(0))
        step = CgmAlarmEngine.step(on(CgmAlarmType.URGENT_LOW, CgmAlarmType.LOW), settings(), step.runtime, at(1))
        assertEquals(listOf(CgmAlarmType.URGENT_LOW), raised(step))
        assertEquals(listOf(CgmAlarmType.LOW), cleared(step))
    }

    @Test
    fun alarmsOfDifferentGroupsRingTogether() {
        val step = CgmAlarmEngine.step(on(CgmAlarmType.LOW, CgmAlarmType.SIGNAL_LOSS), settings(), CgmAlarmRuntime(), at(0))
        assertEquals(setOf(CgmAlarmType.LOW, CgmAlarmType.SIGNAL_LOSS), raised(step).toSet())
    }

    @Test
    fun anInformationIsShownOncePerEpisode() {
        val ready = mapOf(CgmAlarmType.WARMUP_DONE to CgmAlarmCondition(episode = "e1"))
        var step = CgmAlarmEngine.step(ready, settings(), CgmAlarmRuntime(), at(0))
        assertEquals(listOf(CgmAlarmAction.Raise(CgmAlarmType.WARMUP_DONE, reminder = false)), step.actions)
        step = CgmAlarmEngine.step(ready, settings(), step.runtime, at(10))
        assertTrue(step.actions.isEmpty(), "no reminder for an information")
        step = CgmAlarmEngine.acknowledge(step.runtime, settings(), null, at(11))
        step = CgmAlarmEngine.step(ready, settings(), step.runtime, at(12))
        assertTrue(step.actions.isEmpty(), "not shown again for the same episode")
        step = CgmAlarmEngine.step(mapOf(CgmAlarmType.WARMUP_DONE to CgmAlarmCondition(episode = "e2")), settings(), step.runtime, at(13))
        assertEquals(listOf(CgmAlarmType.WARMUP_DONE), raised(step))
    }

    @Test
    fun aRestartForgetsTheNotificationsButKeepsTheAcknowledge() {
        var runtime = CgmAlarmEngine.step(on(CgmAlarmType.HIGH), settings(), CgmAlarmRuntime(), at(0)).runtime
        runtime = CgmAlarmEngine.acknowledge(runtime, settings(), null, at(1)).runtime
        val restarted = runtime.afterRestart()
        val step = CgmAlarmEngine.step(on(CgmAlarmType.HIGH), settings(), restarted, at(2))
        assertTrue(step.actions.isEmpty())
        // A ringing alarm that was not acknowledged rings again after a restart.
        val ringing = CgmAlarmEngine.step(on(CgmAlarmType.LOW), settings(), CgmAlarmRuntime(), at(0)).runtime.afterRestart()
        assertEquals(listOf(CgmAlarmType.LOW), raised(CgmAlarmEngine.step(on(CgmAlarmType.LOW), settings(), ringing, at(1))))
    }

    @Test
    fun clearAllStopsEverything() {
        val runtime = CgmAlarmEngine.step(on(CgmAlarmType.LOW, CgmAlarmType.SENSOR_ENDED), settings(), CgmAlarmRuntime(), at(0)).runtime
        val step = CgmAlarmEngine.clearAll(runtime)
        assertEquals(setOf(CgmAlarmType.LOW, CgmAlarmType.SENSOR_ENDED), cleared(step).toSet())
        assertTrue(step.runtime.tracks.isEmpty())
        assertNull(step.nextCheckAt)
    }

    @Test
    fun soundChoicesMapToSounds() {
        assertEquals(AlarmSound.URGENT_ALARM, CgmAlarmKeys.soundOf(CgmAlarmType.URGENT_LOW, CgmAlarmKeys.DEFAULT))
        assertEquals(AlarmSound.URGENT_ALARM, CgmAlarmKeys.soundOf(CgmAlarmType.URGENT_LOW, CgmAlarmKeys.NONE), "urgent low always has a sound")
        assertNull(CgmAlarmKeys.soundOf(CgmAlarmType.HIGH, CgmAlarmKeys.NONE))
        assertEquals(AlarmSound.ALARM, CgmAlarmKeys.soundOf(CgmAlarmType.HIGH, "broken value"), "a broken choice is never silent")
        assertEquals(AlarmSound.TRIO_ACOUSTIC_HIGH_HARSH, CgmAlarmKeys.soundOf(CgmAlarmType.HIGH, "trio_acoustic_hg_harsh"))
    }
}
