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

        verify(persistenceLayer).cleanupDatabase(eq(PeriodicMaintenance.KEEP_DAYS), eq(false))
        verify(preferences).put(LongNonKey.LastCleanupRun, now)
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
