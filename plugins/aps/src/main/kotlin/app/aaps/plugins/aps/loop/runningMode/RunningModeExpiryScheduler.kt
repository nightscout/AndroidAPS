package app.aaps.plugins.aps.loop.runningMode

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules [RunningModeExpiryWorker] to fire at the exact end of any temporary running mode.
 *
 * Reacts to any DB change to the RunningMode table. Looks up the current active mode and:
 *  - if it is a [RM.Mode.mustBeTemporary] mode with a future end time: schedules (or replaces)
 *    the worker at that end time
 *  - otherwise: cancels any pending scheduled worker
 *
 * Uses a single unique-work name; [ExistingWorkPolicy.REPLACE] ensures at most one pending
 * expiry job at a time. Gated by `config.APS`.
 */
@Singleton
class RunningModeExpiryScheduler @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val workManager: WorkManager,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    @ApplicationScope private val appScope: CoroutineScope
) {

    private var started = false

    fun start() {
        if (started) return
        started = true
        if (!config.APS) {
            aapsLogger.debug(LTag.APS, "RunningModeExpiryScheduler: config.APS=false, not scheduling")
            return
        }
        appScope.launch {
            rescheduleFromCurrentMode()
            persistenceLayer.observeChanges(RM::class.java).collect { _ ->
                rescheduleFromCurrentMode()
            }
        }
    }

    internal suspend fun rescheduleFromCurrentMode() {
        val now = dateUtil.now()
        val active = persistenceLayer.getRunningModeActiveAt(now)
        if (!active.mode.mustBeTemporary() || active.duration <= 0L) {
            cancelScheduled()
            return
        }
        // Long.MAX_VALUE duration is the "open-ended" sentinel (e.g. SUSPENDED_BY_PUMP from
        // precheck, constraint-forced modes). No expiry to schedule.
        if (active.duration == Long.MAX_VALUE) {
            cancelScheduled()
            return
        }
        // Overflow guard: timestamp + duration can wrap when duration is near Long.MAX_VALUE.
        val endTime = active.timestamp + active.duration
        if (endTime < active.timestamp) {
            cancelScheduled()
            return
        }
        val delayMs = endTime - now
        if (delayMs <= 0) {
            aapsLogger.debug(
                LTag.APS,
                "RunningModeExpiryScheduler: active RM (${active.mode}) already expired, canceling any pending work"
            )
            cancelScheduled()
            return
        }
        schedule(delayMs)
    }

    private fun schedule(delayMs: Long) {
        aapsLogger.debug(LTag.APS, "RunningModeExpiryScheduler: scheduling expiry in ${delayMs}ms")
        val request = OneTimeWorkRequest.Builder(RunningModeExpiryWorker::class.java)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(
            RunningModeExpiryWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun cancelScheduled() {
        workManager.cancelUniqueWork(RunningModeExpiryWorker.WORK_NAME)
    }
}
