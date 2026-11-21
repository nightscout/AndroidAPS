package app.aaps.plugins.aps.loop.runningMode

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.delayUntil
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs [RunningModeExpiryJob] from a coroutine timer, which is all iOS offers.
 *
 * Android keeps this work in WorkManager, so it runs at the right time even when the app was killed.
 * iOS has nothing of that kind: there is no way to run code at an exact time while the app sleeps.
 * The interface asks an implementation that cannot keep the time to run the job **late rather than
 * never**, and that is what this does, in two steps:
 *
 * - While the process lives, the timer waits for the end on the wall clock ([delayUntil]). iOS
 *   suspends the process instead of running it, so an end that comes during a suspension is run when
 *   the app runs again - within half a minute of that, which still cancels the zero temp basal. It
 *   used to be one long `delay`, which counts only the time the phone is awake: after a night with
 *   the phone locked, the end came hours late even with the app open.
 * - When the process is gone, nothing here survives. The next start calls
 *   [RunningModeExpiryScheduler.start], which reads the active mode from the database and schedules
 *   the end again, and an end that is already past is handled there. That is the real safety net,
 *   and it is shared code rather than anything this class does.
 *
 * So the mode is never left with insulin switched off for good, only possibly later than on Android.
 * Making that worse would need a background task Apple may or may not run; this is deliberately the
 * simple, visible behaviour instead.
 *
 * [RunningModeExpiryJob] arrives as a function rather than outright, the same way `IosLoopNotifier`
 * takes `Loop`: the job is looked up when it runs, not when the graph is built.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IosRunningModeExpiryTimer(
    private val aapsLogger: AAPSLogger,
    private val expiryJob: () -> RunningModeExpiryJob,
    // Plain CoroutineScope, not @ApplicationScope: that qualifier is javax and does not exist on
    // Kotlin/Native. AppCoroutineBindings.unqualifiedAppScope binds the very same scope without it.
    private val scope: CoroutineScope,
    private val clock: Clock
) : RunningModeExpiryTimer {

    private var pending: Job? = null

    /** Replaces a pending run: only one temporary mode is active at a time. */
    override fun schedule(delayMs: Long) {
        cancel()
        aapsLogger.debug(LTag.APS, "Running mode ends in ${delayMs}ms")
        val endMs = clock.now().toEpochMilliseconds() + delayMs
        pending = scope.launch {
            clock.delayUntil(endMs)
            expiryJob().run()
        }
    }

    override fun cancel() {
        pending?.cancel()
        pending = null
    }
}
