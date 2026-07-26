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
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ProfileSwitchExpirySchedulerTest : TestBase() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var config: Config
    @Mock lateinit var dateUtil: DateUtil

    private lateinit var scheduler: ProfileSwitchExpiryScheduler
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = CoroutineScope(testDispatcher)
    private val now = 1_700_000_000_000L

    private var eventCount = 0
    private lateinit var disposable: Disposable

    @BeforeEach
    fun prepare() {
        whenever(dateUtil.now()).thenReturn(now)
        whenever(config.AAPSCLIENT).thenReturn(false)
        whenever(persistenceLayer.observeChanges(anyOrNull<Class<*>>())).thenReturn(emptyFlow())
        disposable = rxBus.toObservable(EventProfileChangeRequested::class.java).subscribe { eventCount++ }
        scheduler = ProfileSwitchExpiryScheduler(
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
        disposable.dispose()
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
        whenever(persistenceLayer.observeChanges(eq(PS::class.java))).thenReturn(flow)
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
