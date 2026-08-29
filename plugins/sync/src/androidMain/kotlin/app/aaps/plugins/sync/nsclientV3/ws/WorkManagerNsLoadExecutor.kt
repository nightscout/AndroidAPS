package app.aaps.plugins.sync.nsclientV3.ws

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.aaps.plugins.sync.nsclientV3.workers.DataSyncWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadBgWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadDeviceStatusWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadFoodsWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadLastModificationWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadProfileStoreWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadSettingsWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadStatusWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadTreatmentsWorker
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * [NsLoadExecutor] on WorkManager.
 *
 * Android runs the round as workers because a round started from the overview should finish even if
 * the screen goes away.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class WorkManagerNsLoadExecutor @Inject constructor(
    private val context: Context
) : NsLoadExecutor {

    private val workManager get() = WorkManager.getInstance(context)

    override val isRunning: Boolean
        get() = workManager.getWorkInfosForUniqueWork(JOB_NAME).get().any { it.state.isActive() }

    override val idle: Flow<Unit>
        get() =
            // WorkManager.isInitialized() guards the case where nothing bootstrapped WorkManager -
            // unit tests that only want the surrounding handlers would otherwise crash here.
            if (!WorkManager.isInitialized()) emptyFlow()
            else workManager.getWorkInfosForUniqueWorkFlow(JOB_NAME)
                .map { infos -> infos.any { it.state.isActive() } }
                .distinctUntilChanged()
                .filter { active -> !active }
                .map { }

    override fun runChain(steps: List<NsLoadStep>) {
        if (steps.isEmpty()) return
        var continuation = workManager.beginUniqueWork(JOB_NAME, ExistingWorkPolicy.REPLACE, request(steps.first()))
        for (step in steps.drop(1)) continuation = continuation.then(request(step))
        continuation.enqueue()
    }

    override fun runReplacing(step: NsLoadStep) {
        workManager.enqueueUniqueWork(JOB_NAME, ExistingWorkPolicy.REPLACE, request(step))
    }

    override fun runDetached(step: NsLoadStep) {
        workManager.enqueue(request(step))
    }

    override fun cancel() {
        workManager.cancelUniqueWork(JOB_NAME)
    }

    private fun request(step: NsLoadStep): OneTimeWorkRequest =
        OneTimeWorkRequest.Builder(workerFor(step)).build()

    private fun workerFor(step: NsLoadStep): Class<out ListenableWorker> = when (step) {
        NsLoadStep.STATUS            -> LoadStatusWorker::class.java
        NsLoadStep.LAST_MODIFICATION -> LoadLastModificationWorker::class.java
        NsLoadStep.BG                -> LoadBgWorker::class.java
        NsLoadStep.TREATMENTS        -> LoadTreatmentsWorker::class.java
        NsLoadStep.FOODS             -> LoadFoodsWorker::class.java
        NsLoadStep.PROFILE_STORE     -> LoadProfileStoreWorker::class.java
        NsLoadStep.SETTINGS          -> LoadSettingsWorker::class.java
        NsLoadStep.DEVICE_STATUS     -> LoadDeviceStatusWorker::class.java
        NsLoadStep.DATA_SYNC         -> DataSyncWorker::class.java
    }

    /** Shared by [isRunning] and [idle] so both sides agree on what "active" means. */
    private fun WorkInfo.State.isActive(): Boolean =
        this == WorkInfo.State.BLOCKED || this == WorkInfo.State.ENQUEUED || this == WorkInfo.State.RUNNING

    companion object {

        /**
         * The unique name of the round. It was the plugin's simple name before this moved, and it
         * stays that string: WorkManager keys persisted work on it, so changing it would orphan
         * anything already enqueued by an older build.
         */
        private const val JOB_NAME = "NSClientV3Plugin"
    }
}
