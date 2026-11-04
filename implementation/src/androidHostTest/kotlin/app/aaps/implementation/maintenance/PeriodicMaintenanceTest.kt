package app.aaps.implementation.maintenance

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.maintenance.Maintenance
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The housekeeping every platform has to do, and used to do only on Android.
 *
 * All of this sat inside `KeepAliveWorker`. The bodies were already shared, so the code compiled for
 * iOS and desktop and ran on neither - which is why `AlertMissedBgReading` could be switched on
 * there and never fire. These pin the work itself; that each shell starts it is wiring, checked by
 * compiling the shells.
 */
class PeriodicMaintenanceTest : TestBase() {

    @Mock lateinit var localAlertUtils: LocalAlertUtils
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var maintenance: Maintenance
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var dateUtil: DateUtil

    private lateinit var sut: PeriodicMaintenance
    private val keep = T.days(PeriodicMaintenance.KEEP_DAYS).msecs()

    @BeforeEach
    fun init() {
        sut = PeriodicMaintenance(aapsLogger, localAlertUtils, persistenceLayer, maintenance, preferences, dateUtil)
    }

    /** The one a user notices: a follower whose CGM stops has to be told. */
    @Test
    fun `a pass checks for a missed reading`() = runTest {
        whenever(dateUtil.now()).thenReturn(1_000L)

        sut.runOnce()

        verify(localAlertUtils).checkStaleBGAlert()
    }

    @Test
    fun `a pass shortens the snooze interval`() = runTest {
        whenever(dateUtil.now()).thenReturn(1_000L)

        sut.runOnce()

        verify(localAlertUtils).shortenSnoozeInterval()
    }

    @Test
    fun `a pass trims the log files`() = runTest {
        whenever(dateUtil.now()).thenReturn(1_000L)

        sut.runOnce()

        verify(maintenance).deleteLogs(PeriodicMaintenance.KEEP_LOG_FILES)
    }

    /**
     * Once a day, not once a pass. The database trim is the expensive step and the ticker runs every
     * five minutes, so the due-check is what keeps it affordable.
     */
    @Test
    fun `the database is trimmed when a day has passed`() = runTest {
        val now = T.days(10).msecs()
        whenever(dateUtil.now()).thenReturn(now)
        whenever(preferences.get(LongNonKey.LastCleanupRun)).thenReturn(now - T.days(2).msecs())

        sut.runOnce()

        verify(persistenceLayer).cleanupDatabase(eq(now - keep), eq(false))
        verify(preferences).put(LongNonKey.LastCleanupRun, now)
        verify(preferences).put(LongNonKey.LastCleanupCutoff, now - keep)
    }

    /** The normal daily step: the cutoff moves one day, well inside the limit. */
    @Test
    fun `the cutoff follows the clock in normal use`() = runTest {
        val now = T.days(400).msecs()
        whenever(dateUtil.now()).thenReturn(now)
        whenever(preferences.get(LongNonKey.LastCleanupRun)).thenReturn(now - T.days(1).msecs() - 1)
        whenever(preferences.get(LongNonKey.LastCleanupCutoff)).thenReturn(now - keep - T.days(1).msecs())

        sut.runOnce()

        verify(persistenceLayer).cleanupDatabase(eq(now - keep), eq(false))
    }

    /**
     * #5210: the clock jumps forward by more than [PeriodicMaintenance.KEEP_DAYS]. `now - KEEP_DAYS`
     * is then after all real data, and without the limit the whole history - recent boluses that
     * still count for IOB included - would be deleted in one pass.
     */
    @Test
    fun `a forward clock jump cannot delete the whole history`() = runTest {
        val realNow = T.days(400).msecs()
        val jumpedNow = realNow + T.days(200).msecs()
        val lastCutoff = realNow - keep
        whenever(dateUtil.now()).thenReturn(jumpedNow)
        whenever(preferences.get(LongNonKey.LastCleanupRun)).thenReturn(realNow)
        whenever(preferences.get(LongNonKey.LastCleanupCutoff)).thenReturn(lastCutoff)

        sut.runOnce()

        val limited = lastCutoff + PeriodicMaintenance.MAX_CUTOFF_STEP.inWholeMilliseconds
        verify(persistenceLayer).cleanupDatabase(eq(limited), eq(false))
        verify(preferences).put(LongNonKey.LastCleanupCutoff, limited)
    }

    /** Installs from before the cutoff key existed: the last cutoff is taken from the last run. */
    @Test
    fun `without a stored cutoff the limit comes from the last run`() = runTest {
        val realNow = T.days(400).msecs()
        val jumpedNow = realNow + T.days(200).msecs()
        whenever(dateUtil.now()).thenReturn(jumpedNow)
        whenever(preferences.get(LongNonKey.LastCleanupRun)).thenReturn(realNow)

        sut.runOnce()

        verify(persistenceLayer).cleanupDatabase(eq(realNow - keep + PeriodicMaintenance.MAX_CUTOFF_STEP.inWholeMilliseconds), eq(false))
    }

    /** After a long pause the backlog goes over the next days, not in one pass. */
    @Test
    fun `after a long pause the cutoff catches up step by step`() = runTest {
        val now = T.days(400).msecs()
        val lastRun = now - T.days(30).msecs()
        whenever(dateUtil.now()).thenReturn(now)
        whenever(preferences.get(LongNonKey.LastCleanupRun)).thenReturn(lastRun)
        whenever(preferences.get(LongNonKey.LastCleanupCutoff)).thenReturn(lastRun - keep)

        sut.runOnce()

        verify(persistenceLayer).cleanupDatabase(eq(lastRun - keep + PeriodicMaintenance.MAX_CUTOFF_STEP.inWholeMilliseconds), eq(false))
    }

    /**
     * The clock is set back after a forward jump. The last run is then in the future: the cleanup
     * must still run, and with the real cutoff, not the one stored while the clock was wrong.
     */
    @Test
    fun `after the clock goes back the cleanup runs with the real cutoff`() = runTest {
        val now = T.days(400).msecs()
        val jumpedRun = now + T.days(200).msecs()
        whenever(dateUtil.now()).thenReturn(now)
        whenever(preferences.get(LongNonKey.LastCleanupRun)).thenReturn(jumpedRun)
        whenever(preferences.get(LongNonKey.LastCleanupCutoff)).thenReturn(jumpedRun - keep)

        sut.runOnce()

        verify(persistenceLayer).cleanupDatabase(eq(now - keep), eq(false))
        verify(preferences).put(LongNonKey.LastCleanupRun, now)
        verify(preferences).put(LongNonKey.LastCleanupCutoff, now - keep)
    }

    /** A new install has no history to protect, so there is no limit. */
    @Test
    fun `the first run ever is not limited`() = runTest {
        val now = T.days(400).msecs()
        whenever(dateUtil.now()).thenReturn(now)

        sut.runOnce()

        verify(persistenceLayer).cleanupDatabase(eq(now - keep), eq(false))
    }

    @Test
    fun `the database is not trimmed twice in a day`() = runTest {
        val now = T.days(10).msecs()
        whenever(dateUtil.now()).thenReturn(now)
        whenever(preferences.get(LongNonKey.LastCleanupRun)).thenReturn(now - T.hours(1).msecs())

        sut.runOnce()

        verify(persistenceLayer, never()).cleanupDatabase(any(), any())
    }

    /**
     * Every step decides for itself whether it is due, so the caller may run a pass as often as it
     * likes. That is what lets Android keep its fifteen-minute WorkManager schedule and the other
     * shells use a five-minute ticker without either needing to know about the other.
     */
    @Test
    fun `a pass is safe to repeat`() = runTest {
        val now = T.days(10).msecs()
        whenever(dateUtil.now()).thenReturn(now)
        whenever(preferences.get(LongNonKey.LastCleanupRun)).thenReturn(now)

        sut.runOnce()
        sut.runOnce()

        verify(persistenceLayer, never()).cleanupDatabase(any(), any())
    }
}
