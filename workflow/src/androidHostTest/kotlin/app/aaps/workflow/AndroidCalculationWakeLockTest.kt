package app.aaps.workflow

import android.content.Context
import android.os.PowerManager
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * The Android wake lock behind each calculation run.
 *
 * Two choices in it are easy to undo by accident: the lock is a partial one (the CPU only, not the
 * screen), and it is not counted, so the second release() that [CalculationWakeLock.Held] allows does
 * not throw "WakeLock under-locked".
 */
class AndroidCalculationWakeLockTest {

    private val wakeLock = mock<PowerManager.WakeLock>()
    private val powerManager = mock<PowerManager> {
        on { newWakeLock(any(), any()) } doReturn wakeLock
    }
    private val context = mock<Context> {
        on { getSystemService(Context.POWER_SERVICE) } doReturn powerManager
    }

    @Test
    fun `takes a partial wake lock that is not counted and has the timeout`() {
        AndroidCalculationWakeLock(context).acquire(60_000L)

        verify(powerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, AndroidCalculationWakeLock.TAG)
        inOrder(wakeLock) {
            verify(wakeLock).setReferenceCounted(false)
            verify(wakeLock).acquire(60_000L)
        }
    }

    @Test
    fun `release gives the lock back`() {
        AndroidCalculationWakeLock(context).acquire(60_000L).release()

        verify(wakeLock).release()
    }
}
