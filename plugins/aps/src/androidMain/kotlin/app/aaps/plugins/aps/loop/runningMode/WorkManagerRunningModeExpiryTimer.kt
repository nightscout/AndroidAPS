package app.aaps.plugins.aps.loop.runningMode

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.concurrent.TimeUnit

/**
 * Runs [RunningModeExpiryWorker] with WorkManager, which keeps the work when the app is killed or the phone restarts.
 *
 * One unique work name with [ExistingWorkPolicy.REPLACE]: a new end time takes over the pending run.
 *
 * [WorkManager] is taken only when a run is scheduled or cancelled, not when the graph is built. Getting it
 * starts WorkManager, and building the graph should not start anything: the shared start sequence holds this
 * timer, and a test that builds the graph has no WorkManager.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class WorkManagerRunningModeExpiryTimer(
    private val workManager: Lazy<WorkManager>
) : RunningModeExpiryTimer {

    override fun schedule(delayMs: Long) {
        val request = OneTimeWorkRequest.Builder(RunningModeExpiryWorker::class.java)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        workManager.value.enqueueUniqueWork(RunningModeExpiryWorker.WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        workManager.value.cancelUniqueWork(RunningModeExpiryWorker.WORK_NAME)
    }
}
