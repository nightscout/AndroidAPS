package app.aaps.plugins.aps.loop.runningMode

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import app.aaps.core.data.time.T
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class WorkManagerRunningModeExpiryTimerTest {

    private val workManager: WorkManager = mock()
    private val timer = WorkManagerRunningModeExpiryTimer(lazyOf(workManager))

    @Test
    fun `building the timer does not start WorkManager`() {
        WorkManagerRunningModeExpiryTimer(lazy { error("WorkManager was taken when the timer was built") })
    }

    @Test
    fun `schedule replaces the pending run with the worker at the delay`() {
        timer.schedule(T.mins(20).msecs())

        val request = argumentCaptor<OneTimeWorkRequest>()
        verify(workManager).enqueueUniqueWork(eq(RunningModeExpiryWorker.WORK_NAME), eq(ExistingWorkPolicy.REPLACE), request.capture())
        assertThat(request.firstValue.workSpec.initialDelay).isEqualTo(T.mins(20).msecs())
        assertThat(request.firstValue.workSpec.workerClassName).isEqualTo(RunningModeExpiryWorker::class.java.name)
    }

    @Test
    fun `cancel drops the pending run`() {
        timer.cancel()

        verify(workManager).cancelUniqueWork(RunningModeExpiryWorker.WORK_NAME)
    }
}
