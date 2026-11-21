package app.aaps.implementation.receivers

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.workflow.MetroWorkerCreator
import app.aaps.core.objects.workflow.RunnerWorker
import app.aaps.core.objects.workflow.WorkOutcome
import app.aaps.implementation.maintenance.KeepAliveRunner
import com.google.common.util.concurrent.ListenableFuture
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.minutes

/**
 * Runs the [KeepAliveRunner] every 5 minutes. All the checks are in the runner, which every platform shares; this
 * worker keeps only what belongs to WorkManager: the schedule and the WorkManager database checks.
 */
@AssistedInject
class KeepAliveWorker(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    aapsLogger: AAPSLogger,
    fabricPrivacy: FabricPrivacy,
    private val keepAliveRunner: KeepAliveRunner,
    private val workManager: WorkManager
) : RunnerWorker(context, params, Dispatchers.Default, aapsLogger, fabricPrivacy) {

    @AssistedFactory
    fun interface Factory : MetroWorkerCreator {

        override fun create(context: Context, params: WorkerParameters): KeepAliveWorker
    }

    companion object {

        const val KA_0 = "KeepAlive"
        private const val KA_5 = "KeepAlive_5"
        private const val KA_10 = "KeepAlive_10"

        /** The runs at +5 and +10 minutes are skipped when they come this soon after the last pass. */
        private val MIN_INTERVAL = 4.minutes

        fun scheduleIfNotRunning(context: Context, keepAliveRunner: KeepAliveRunner, aapsLogger: AAPSLogger, fabricPrivacy: FabricPrivacy) {
            val lastRun = keepAliveRunner.lastRun
            if (lastRun != 0L && lastRun + T.mins(20).msecs() < System.currentTimeMillis()) {
                schedule(context)
                aapsLogger.error(LTag.CORE, "KeepAliveRescheduled")
                fabricPrivacy.logCustom("KeepAliveRescheduled")
            }
        }

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                KA_0,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequest.Builder(KeepAliveWorker::class.java, 15, TimeUnit.MINUTES)
                    .setInputData(Data.Builder().putString("schedule", KA_0).build())
                    .setInitialDelay(5, TimeUnit.SECONDS)
                    .build()
            )

        }
    }

    override suspend fun runBody(isStopped: () -> Boolean): WorkOutcome {
        aapsLogger.debug(LTag.CORE, "KeepAlive received from: " + inputData.getString("schedule") + " Thread count: " + Thread.activeCount())

        // 15 min interval is WorkManager minimum so schedule another instances to have 5 min interval
        val periodic = inputData.getString("schedule") == KA_0
        if (periodic) {
            workManager.enqueueUniqueWork(
                KA_5,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(KeepAliveWorker::class.java)
                    .setInputData(Data.Builder().putString("schedule", KA_5).build())
                    .setInitialDelay(5, TimeUnit.MINUTES)
                    .build()
            )
            workManager.enqueueUniqueWork(
                KA_10,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(KeepAliveWorker::class.java)
                    .setInputData(Data.Builder().putString("schedule", KA_10).build())
                    .setInitialDelay(10, TimeUnit.MINUTES)
                    .build()
            )
        }

        // Sometimes schedule +5min, +10min gets broken. If this happens do nothing:
        // it was causing false Pump unreachable alerts.
        val outcome = keepAliveRunner.runOnce(minInterval = if (periodic) null else MIN_INTERVAL)
        if (outcome == WorkOutcome.Success) {
            workerDbStatus()
            workerActiveStatus()
        }
        return outcome
    }

    // When Worker DB grows too much, work operations become slow
    // Library is cleaning DB every 7 days which may not be sufficient for NSClient full sync
    private fun workerDbStatus() {
        val terminal = getWorkInfosSafe(listOf(WorkInfo.State.FAILED, WorkInfo.State.SUCCEEDED)) ?: return
        aapsLogger.debug(LTag.CORE, "WorkManager size is ${terminal.size}")
        if (terminal.size > 1000) {
            workManager.pruneWork()
            aapsLogger.debug(LTag.CORE, "WorkManager pruning ....")
        }
    }

    // Report ENQUEUED/RUNNING/BLOCKED counts and the top worker classes by count, so a leaking
    // chain is identifiable. Useful for the lead-up to a freeze; once WorkManager itself stalls,
    // KeepAlive stops firing and this stops logging too.
    private fun workerActiveStatus() {
        val active = getWorkInfosSafe(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)) ?: return
        val byState = active.groupingBy { it.state }.eachCount()
        aapsLogger.debug(
            LTag.CORE,
            "WorkManager active: total=${active.size}" +
                " enqueued=${byState[WorkInfo.State.ENQUEUED] ?: 0}" +
                " running=${byState[WorkInfo.State.RUNNING] ?: 0}" +
                " blocked=${byState[WorkInfo.State.BLOCKED] ?: 0}"
        )
        // WorkManager auto-adds the worker's fully-qualified class name as a tag; filter by '.'
        // to skip user-added tags, then keep the simple name for readability.
        val topTags = active.asSequence()
            .flatMap { it.tags.asSequence() }
            .filter { it.contains('.') }
            .groupingBy { it.substringAfterLast('.') }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .joinToString { "${it.key}=${it.value}" }
        if (topTags.isNotEmpty()) aapsLogger.debug(LTag.CORE, "WorkManager active top: $topTags")
    }

    // Bounded blocking get: if WorkManager itself is wedged (the very thing we're diagnosing),
    // an unbounded .get() would hang the KeepAliveWorker and take the diagnostic down with it.
    private fun getWorkInfosSafe(states: List<WorkInfo.State>): List<WorkInfo>? {
        val future: ListenableFuture<List<WorkInfo>> = workManager.getWorkInfos(WorkQuery.Builder.fromStates(states).build())
        return try {
            future.get(2, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            aapsLogger.error(LTag.CORE, "WorkManager getWorkInfos timeout for $states")
            future.cancel(true)
            null
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "WorkManager getWorkInfos failed for $states", e)
            null
        }
    }
}
