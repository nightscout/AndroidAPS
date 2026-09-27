package app.aaps.cgm.dexcomg7.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class G7LifecycleTest {

    private val minute = 60 * 1000L
    private val hour = 60 * minute
    private val day = 24 * hour
    private val start = 1_700_000_000_000L
    private val tenDaySession = 907_200L // seconds, 10.5 days
    private val fifteenDaySession = 1_339_200L // seconds, 15.5 days

    @Test
    fun lifetimeLeavesOutTheGracePeriod() {
        assertThat(G7Lifecycle.lifetimeMs(tenDaySession)).isEqualTo(10 * day)
        assertThat(G7Lifecycle.lifetimeMs(fifteenDaySession)).isEqualTo(15 * day)
        assertThat(G7Lifecycle.lifetimeMs(null)).isEqualTo(10 * day)
        assertThat(G7Lifecycle.endsAt(start, tenDaySession)).isEqualTo(start + 10 * day + 12 * hour)
    }

    private fun state(known: Boolean = true, canAuth: Boolean = true, algorithm: Int? = 6, now: Long) =
        G7Lifecycle.state(known, canAuth, start, tenDaySession, algorithm?.let { AlgorithmState(it) }, now)

    @Test
    fun statesFollowTheSession() {
        assertThat(state(known = false, canAuth = false, now = start)).isEqualTo(G7LifecycleState.UNPAIRED)
        assertThat(state(known = false, canAuth = true, now = start)).isEqualTo(G7LifecycleState.CONNECTING)
        assertThat(state(algorithm = 2, now = start + hour)).isEqualTo(G7LifecycleState.WARMUP)
        assertThat(state(now = start + day)).isEqualTo(G7LifecycleState.OK)
        assertThat(state(algorithm = 25, now = start + day)).isEqualTo(G7LifecycleState.FAILED)
        assertThat(state(now = start + 10 * day + hour)).isEqualTo(G7LifecycleState.GRACE_PERIOD)
        assertThat(state(now = start + 11 * day)).isEqualTo(G7LifecycleState.EXPIRED)
        assertThat(state(algorithm = 26, now = start + 2 * day)).isEqualTo(G7LifecycleState.EXPIRED)
    }

    @Test
    fun sessionMilestonesComeInOrder() {
        fun at(time: Long) = G7Lifecycle.currentMilestone(start, tenDaySession, time)
        val expires = start + 10 * day
        assertThat(at(expires - 25 * hour)).isNull()
        assertThat(at(expires - 24 * hour)).isEqualTo(G7SessionMilestone.ENDS_IN_24H)
        assertThat(at(expires - 7 * hour)).isEqualTo(G7SessionMilestone.ENDS_IN_24H)
        assertThat(at(expires - 6 * hour)).isEqualTo(G7SessionMilestone.ENDS_IN_6H)
        assertThat(at(expires - 2 * hour)).isEqualTo(G7SessionMilestone.ENDS_IN_2H)
        assertThat(at(expires)).isEqualTo(G7SessionMilestone.GRACE_STARTED)
        assertThat(at(expires + 6 * hour)).isEqualTo(G7SessionMilestone.GRACE_ENDS_IN_6H)
        assertThat(at(expires + 10 * hour)).isEqualTo(G7SessionMilestone.GRACE_ENDS_IN_2H)
        assertThat(at(expires + 12 * hour)).isEqualTo(G7SessionMilestone.ENDED)
        assertThat(G7Lifecycle.currentMilestone(start, tenDaySession, start + day, sessionEnded = true)).isEqualTo(G7SessionMilestone.ENDED)
        assertThat(G7Lifecycle.milestoneAt(start, tenDaySession, G7SessionMilestone.GRACE_ENDS_IN_2H)).isEqualTo(expires + 10 * hour)
    }

    @Test
    fun fifteenDaySensorMovesTheAlerts() {
        assertThat(G7Lifecycle.currentMilestone(start, fifteenDaySession, start + 10 * day)).isNull()
    }

    @Test
    fun backfillAsksForTheGap() {
        val live = start + 2 * day
        val margin = G7Lifecycle.CLOCK_TOLERANCE_MS
        // Last stored reading an hour ago: from after it to before the live one.
        assertThat(G7Lifecycle.backfillRange(start, live - hour, live)).isEqualTo((2 * day - hour + margin) / 1000 to (2 * day - margin) / 1000)
        // Nothing stored: the default three hours.
        assertThat(G7Lifecycle.backfillRange(start, null, live)).isEqualTo((2 * day - 3 * hour) / 1000 to (2 * day - margin) / 1000)
        // A long outage is capped at a day.
        assertThat(G7Lifecycle.backfillRange(start, start + hour, live)).isEqualTo(day / 1000 to (2 * day - margin) / 1000)
        // Never before the sensor started.
        assertThat(G7Lifecycle.backfillRange(start, null, start + hour)).isEqualTo(0L to (hour - margin) / 1000)
        // One reading missed: asked for.
        assertThat(G7Lifecycle.backfillRange(start, live - 10 * minute, live)).isNotNull()
    }

    @Test
    fun noBackfillWithoutAGap() {
        val live = start + 2 * day
        // The normal 5-minute step, a little late, and the same reading again: nothing is missing.
        assertThat(G7Lifecycle.backfillRange(start, live - 5 * minute, live)).isNull()
        assertThat(G7Lifecycle.backfillRange(start, live - 6 * minute, live)).isNull()
        assertThat(G7Lifecycle.backfillRange(start, live, live)).isNull()
        // A stored reading newer than the live one (the phone clock went back).
        assertThat(G7Lifecycle.backfillRange(start, live + hour, live)).isNull()
    }

    @Test
    fun backfillDoesNotAskForTheStoredOrLiveReadingAgain() {
        // The case from a real log: sensor readings at 525191 s (stored) and 525491 s (live) plus a missed one between.
        val stored = start + 525_191_000L
        val live = stored + 10 * minute
        val (from, to) = G7Lifecycle.backfillRange(start, stored, live)!!
        assertThat(from).isGreaterThan(525_191L)
        assertThat(to).isLessThan(525_791L)
        assertThat(525_491L in from..to).isTrue()
    }

    @Test
    fun clockAnchorStaysPutThroughJitter() {
        // The first reading sets it.
        assertThat(G7Lifecycle.clockAnchor(null, start)).isEqualTo(start)
        // Up to a second of jitter each connection, and slow drift: the anchor does not move.
        assertThat(G7Lifecycle.clockAnchor(start, start + 900)).isEqualTo(start)
        assertThat(G7Lifecycle.clockAnchor(start, start - 900)).isEqualTo(start)
        assertThat(G7Lifecycle.clockAnchor(start, start + G7Lifecycle.CLOCK_TOLERANCE_MS)).isEqualTo(start)
        // The phone clock was changed: take the new one.
        assertThat(G7Lifecycle.clockAnchor(start, start + hour)).isEqualTo(start + hour)
    }
}
