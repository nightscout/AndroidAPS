package app.aaps.plugins.aps.loop.runningMode

import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Has [RunningModeExpiryJob] run at the exact end of any temporary running mode.
 *
 * Reacts to any DB change to the RunningMode table. Looks up the current active mode and:
 *  - if it is a [RM.Mode.mustBeTemporary] mode with a future end time: schedules (or replaces)
 *    the job at that end time
 *  - otherwise: cancels any pending run
 *
 * The [RunningModeExpiryTimer] keeps at most one pending run. Gated by `config.APS`.
 */
@SingleIn(AppScope::class)
@Inject
class RunningModeExpiryScheduler(
    private val persistenceLayer: PersistenceLayer,
    private val timer: RunningModeExpiryTimer,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    // The graph binds the application scope here; it holds only one CoroutineScope, so no qualifier
    // is needed to pick it out.
    private val appScope: CoroutineScope
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
            persistenceLayer.observeChanges(RM::class).collect { _ ->
                rescheduleFromCurrentMode()
            }
        }
    }

    internal suspend fun rescheduleFromCurrentMode() {
        val now = dateUtil.now()
        val active = persistenceLayer.getRunningModeActiveAt(now)
        if (!active.mode.mustBeTemporary() || active.duration <= 0L) {
            timer.cancel()
            return
        }
        // Long.MAX_VALUE duration is the "open-ended" sentinel (e.g. SUSPENDED_BY_PUMP from
        // precheck, constraint-forced modes). No expiry to schedule.
        if (active.duration == Long.MAX_VALUE) {
            timer.cancel()
            return
        }
        // Overflow guard: timestamp + duration can wrap when duration is near Long.MAX_VALUE.
        val endTime = active.timestamp + active.duration
        if (endTime < active.timestamp) {
            timer.cancel()
            return
        }
        val delayMs = endTime - now
        if (delayMs <= 0) {
            aapsLogger.debug(
                LTag.APS,
                "RunningModeExpiryScheduler: active RM (${active.mode}) already expired, canceling any pending work"
            )
            timer.cancel()
            return
        }
        aapsLogger.debug(LTag.APS, "RunningModeExpiryScheduler: scheduling expiry in ${delayMs}ms")
        timer.schedule(delayMs)
    }
}
