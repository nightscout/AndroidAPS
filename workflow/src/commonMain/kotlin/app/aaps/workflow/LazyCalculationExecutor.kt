package app.aaps.workflow

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.workflow.CalculationWakeLock
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration

/**
 * [CoroutineCalculationExecutor], built on first use instead of when the graph is assembled.
 *
 * There is a cycle otherwise, and it is a real one rather than a quirk of Metro:
 *
 * ```
 * CalculationExecutor -> PostCalculationRunner -> IobCobCalculator
 *                     -> CalculationWorkflow -> CalculationExecutor
 * ```
 *
 * Every platform has the runners as ordinary injected objects, so the loop closes everywhere. Android
 * did not meet it while its executor handed the work to WorkManager, which constructed the runners
 * itself.
 *
 * Deferring is safe here rather than merely convenient. Nothing is looked up while the graph is
 * built; the first lookup happens when a calculation is actually started, by which time every object
 * in the loop exists.
 *
 * ## Why this is here and not in a shell
 *
 * It was written in `:ios:shell` first, described there as an iOS-only concern that shared code
 * should not carry. The desktop JVM then hit exactly the same cycle for exactly the same reason, and
 * Android did too once it left WorkManager - and identical copies in several shells is how they
 * quietly stop matching.
 *
 * The runners are passed as `() -> T` rather than a DI provider type, so this class stays plain
 * Kotlin. Each caller hands it a function that returns the runner.
 */
class LazyCalculationExecutor(
    private val scope: CoroutineScope,
    private val aapsLogger: AAPSLogger,
    private val prepare: () -> PrepareGraphDataRunner,
    private val post: () -> PostCalculationRunner,
    private val wakeLock: CalculationWakeLock?
) : CalculationExecutor {

    private val delegate: CoroutineCalculationExecutor by lazy {
        CoroutineCalculationExecutor(scope, aapsLogger, prepare(), post(), wakeLock)
    }

    override suspend fun start(job: String, generation: Long, runPost: Boolean) = delegate.start(job, generation, runPost)

    override suspend fun startPostOnly(job: String, generation: Long) = delegate.startPostOnly(job, generation)

    override suspend fun stop(job: String, from: String) = delegate.stop(job, from)

    override suspend fun waitForPrepare(job: String, reason: String) = delegate.waitForPrepare(job, reason)

    override suspend fun awaitIdle(job: String, timeout: Duration): Boolean = delegate.awaitIdle(job, timeout)

    // Answered here, not by the delegate: a BG can ask for this before the first run, and building the
    // delegate looks up the runners, which is what this class defers.
    override fun keepAwake(timeout: Duration): CalculationWakeLock.Held? = wakeLock?.acquire(timeout.inWholeMilliseconds)
}
