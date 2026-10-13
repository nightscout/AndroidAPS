package app.aaps.implementation.profile

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.PS
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.rx.events.EventProfileChangeRequested
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.tests.TestBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.reflect.KClass

class ProfileSwitchSchedulerTest : TestBase() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var config: Config
    @Mock lateinit var dateUtil: DateUtil

    private lateinit var scheduler: ProfileSwitchScheduler
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = CoroutineScope(testDispatcher)
    private val now = 1_700_000_000_000L

    private var eventCount = 0
    private lateinit var collector: Job

    @BeforeEach
    fun prepare() {
        whenever(dateUtil.now()).thenReturn(now)
        whenever(config.AAPSCLIENT).thenReturn(false)
        whenever(persistenceLayer.observeChanges(anyOrNull<KClass<*>>())).thenReturn(emptyFlow())
        // No future switch unless a test says so — the default for "only an expiry is pending".
        // runBlocking because stubbing a suspend function has to happen inside a coroutine.
        runBlocking { whenever(persistenceLayer.getProfileSwitchesFromTime(anyLong(), anyBoolean())).thenReturn(emptyList()) }
        // UNDISPATCHED so the collector is subscribed before the scheduler under test sends anything;
        // RxBus has no replay, so a scheduled collector would miss those events.
        collector = CoroutineScope(Dispatchers.Unconfined).launch(start = CoroutineStart.UNDISPATCHED) {
            rxBus.toFlow(EventProfileChangeRequested::class).collect { eventCount++ }
        }
        scheduler = ProfileSwitchScheduler(
            persistenceLayer = persistenceLayer,
            rxBus = rxBus,
            dateUtil = dateUtil,
            config = config,
            aapsLogger = aapsLogger,
            appScope = testScope
        )
    }

    @AfterEach
    fun tearDown() {
        collector.cancel()
    }

    @Test
    fun `does nothing on AAPSCLIENT`() = runTest(testDispatcher) {
        whenever(config.AAPSCLIENT).thenReturn(true)
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(tempPs(now, T.mins(30).msecs()))
        scheduler.start()
        advanceUntilIdle()
        assertEquals(0, eventCount)
    }

    @Test
    fun `permanent profile switch does not schedule a revert`() = runTest(testDispatcher) {
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(tempPs(now, 0L))
        scheduler.start()
        advanceUntilIdle()
        assertEquals(0, eventCount)
    }

    @Test
    fun `no active profile switch does nothing`() = runTest(testDispatcher) {
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(null)
        scheduler.start()
        advanceUntilIdle()
        assertEquals(0, eventCount)
    }

    @Test
    fun `active temporary profile switch requests profile change at expiry`() = runTest(testDispatcher) {
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(tempPs(now, T.mins(30).msecs()))
        scheduler.start()
        runCurrent()
        assertEquals(0, eventCount) // not yet — timer is armed but the duration has not elapsed
        advanceUntilIdle()
        assertEquals(1, eventCount)
    }

    @Test
    fun `event does not fire before the full duration elapses`() = runTest(testDispatcher) {
        val duration = T.mins(30).msecs()
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(tempPs(now, duration))
        scheduler.start()
        runCurrent()
        advanceTimeBy(duration - 1)
        assertEquals(0, eventCount)
        advanceUntilIdle()
        assertEquals(1, eventCount)
    }

    @Test
    fun `already-expired temporary profile switch does not fire`() = runTest(testDispatcher) {
        // end = (now - 60min) + 30min is in the past → delay would be negative
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong()))
            .thenReturn(tempPs(now - T.mins(60).msecs(), T.mins(30).msecs()))
        scheduler.start()
        advanceUntilIdle()
        assertEquals(0, eventCount)
    }

    @Test
    fun `overflowing duration does not fire`() = runTest(testDispatcher) {
        // timestamp + Long.MAX_VALUE wraps negative → guarded, no event
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(tempPs(now, Long.MAX_VALUE))
        scheduler.start()
        advanceUntilIdle()
        assertEquals(0, eventCount)
    }

    @Test
    fun `a PS change cancels the previous timer before it fires`() = runTest(testDispatcher) {
        val flow = MutableSharedFlow<List<PS>>(replay = 0)
        whenever(persistenceLayer.observeChanges(eq(PS::class))).thenReturn(flow)
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(tempPs(now, T.mins(30).msecs()))

        scheduler.start()
        runCurrent()
        advanceTimeBy(T.mins(10).msecs()) // 10 of 30 min elapsed, timer still pending

        // The temporary PS is cancelled → a permanent PS is now active. The old timer must not fire.
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(tempPs(now, 0L))
        flow.emit(listOf(tempPs(now, 0L)))
        advanceUntilIdle()

        assertEquals(0, eventCount)
    }

    // --- The start of a switch the user put in the future (issue #5239) ---

    @Test
    fun `future profile switch requests the profile change at its start time`() = runTest(testDispatcher) {
        // Nothing active yet; the user scheduled a permanent switch for 2 hours from now.
        val scheduled = tempPs(now + T.hours(2).msecs(), 0L)
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(null)
        whenever(persistenceLayer.getProfileSwitchesFromTime(anyLong(), anyBoolean())).thenReturn(listOf(scheduled))

        scheduler.start()
        runCurrent()
        assertEquals(0, eventCount) // the point of the fix: nothing is applied at the time of entry

        // From its start time on it is the active switch, and nothing is pending any more.
        whenever(dateUtil.now()).thenReturn(now + T.hours(2).msecs())
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(scheduled)
        whenever(persistenceLayer.getProfileSwitchesFromTime(anyLong(), anyBoolean())).thenReturn(emptyList())

        advanceTimeBy(T.hours(2).msecs() - 1)
        assertEquals(0, eventCount)
        advanceUntilIdle()
        assertEquals(1, eventCount)
    }

    @Test
    fun `the nearest of a pending expiry and a future start wins`() = runTest(testDispatcher) {
        // A temporary switch ends in 30 min, another switch is scheduled to start in 10 min.
        val scheduled = tempPs(now + T.mins(10).msecs(), 0L)
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(tempPs(now, T.mins(30).msecs()))
        whenever(persistenceLayer.getProfileSwitchesFromTime(anyLong(), anyBoolean())).thenReturn(listOf(scheduled))

        scheduler.start()
        runCurrent()

        whenever(dateUtil.now()).thenReturn(now + T.mins(10).msecs())
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(scheduled)
        whenever(persistenceLayer.getProfileSwitchesFromTime(anyLong(), anyBoolean())).thenReturn(emptyList())

        advanceTimeBy(T.mins(10).msecs() - 1)
        assertEquals(0, eventCount)
        advanceUntilIdle()
        // Fired at the 10 min start, well before the 30 min end — and only once.
        assertEquals(1, eventCount)
    }

    @Test
    fun `a future start hands over to the expiry of the switch it starts`() = runTest(testDispatcher) {
        // A temporary switch starts in 10 min and runs for 30 min. Its own end is armed only by the
        // re-schedule that follows the start, because the EPS write it causes is not a PS change.
        val scheduled = tempPs(now + T.mins(10).msecs(), T.mins(30).msecs())
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(null)
        whenever(persistenceLayer.getProfileSwitchesFromTime(anyLong(), anyBoolean())).thenReturn(listOf(scheduled))

        scheduler.start()
        runCurrent()

        // The world as it is from the start time on: the scheduled switch is the active temporary one.
        whenever(dateUtil.now()).thenReturn(now + T.mins(10).msecs())
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(scheduled)
        whenever(persistenceLayer.getProfileSwitchesFromTime(anyLong(), anyBoolean())).thenReturn(emptyList())

        advanceTimeBy(T.mins(10).msecs() - 1)
        assertEquals(0, eventCount)
        advanceTimeBy(2)
        assertEquals(1, eventCount) // started

        // The world after its end: the base profile is active again.
        whenever(dateUtil.now()).thenReturn(now + T.mins(40).msecs())
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(tempPs(now + T.mins(40).msecs(), 0L))

        advanceUntilIdle()
        assertEquals(2, eventCount) // expired, with no PS change in between
    }

    @Test
    fun `an expiry fires once and does not arm the same moment again`() = runTest(testDispatcher) {
        // The active switch produces no database change when it expires, so its end is still a candidate
        // right after it elapses. The hand-over that arms the next moment must skip it, or the scheduler
        // re-arms that identical moment forever. Deliberately asserted with advanceTimeBy and no
        // advanceUntilIdle: a spinning scheduler then fails this on the count instead of hanging the suite.
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(tempPs(now, T.mins(30).msecs()))

        scheduler.start()
        runCurrent()
        advanceTimeBy(T.hours(4).msecs()) // eight further 30 min windows, if it were re-arming

        assertEquals(1, eventCount)
    }

    @Test
    fun `a switch at the current time is not treated as a future start`() = runTest(testDispatcher) {
        // Only a time strictly after now arms a start timer; a row at now is already handled by the
        // DB change that inserted it.
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(null)
        whenever(persistenceLayer.getProfileSwitchesFromTime(anyLong(), anyBoolean())).thenReturn(listOf(tempPs(now, 0L)))

        scheduler.start()
        advanceUntilIdle()
        assertEquals(0, eventCount)
    }

    // --- Helpers ---

    // The scheduler only reads timestamp + duration; the rest is filler (iCfg is never touched).
    private fun tempPs(timestamp: Long, durationMs: Long): PS =
        PS(
            timestamp = timestamp,
            basalBlocks = emptyList(),
            isfBlocks = emptyList(),
            icBlocks = emptyList(),
            targetBlocks = emptyList(),
            glucoseUnit = GlucoseUnit.MGDL,
            profileName = "Test",
            timeshift = 0L,
            percentage = 100,
            duration = durationMs,
            iCfg = mock<ICfg>()
        )
}
