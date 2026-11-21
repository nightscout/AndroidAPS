package app.aaps.plugins.aps.loop.runningMode

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs [RunningModeExpiryJob] with a coroutine delay, which is all iOS offers.
 *
 * Android keeps this work in WorkManager, so it runs at the right time even when the app was killed.
 * iOS has nothing of that kind: there is no way to run code at an exact time while the app sleeps.
 * The interface asks an implementation that cannot keep the time to run the job **late rather than
 * never**, and that is what this does, in two steps:
 *
 * - While the process lives, the delay is the timer. iOS suspends the process instead of running it,
 *   so a delay that comes due during a suspension fires when the app is resumed - late by however
 *   long the app was away, which is the honest outcome and still cancels the zero temp basal.
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
    private val scope: CoroutineScope
) : RunningModeExpiryTimer {

    private var pending: Job? = null

    /** Replaces a pending run: only one temporary mode is active at a time. */
    override fun schedule(delayMs: Long) {
        cancel()
        aapsLogger.debug(LTag.APS, "Running mode ends in ${delayMs}ms")
        pending = scope.launch {
            delay(delayMs)
            expiryJob().run()
        }
    }

    override fun cancel() {
        pending?.cancel()
        pending = null
    }
}
