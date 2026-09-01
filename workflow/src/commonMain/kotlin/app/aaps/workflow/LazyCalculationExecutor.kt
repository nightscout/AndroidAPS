package app.aaps.workflow

import app.aaps.core.interfaces.logging.AAPSLogger
import kotlinx.coroutines.CoroutineScope

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
 * Android never meets it because its executor hands the work to WorkManager, which constructs the
 * runners itself - they are not graph nodes there at all. Every platform without WorkManager has the
 * runners as ordinary injected objects, and the loop closes.
 *
 * Deferring is safe here rather than merely convenient. Nothing is looked up while the graph is
 * built; the first lookup happens when a calculation is actually started, by which time every object
 * in the loop exists.
 *
 * ## Why this is here and not in a shell
 *
 * It was written in `:ios:shell` first, described there as an iOS-only concern that shared code
 * should not carry. The desktop JVM then hit exactly the same cycle for exactly the same reason,
 * which makes it not an iOS concern but a **not-Android** one - and two identical copies in two
 * shells is how the two quietly stop matching.
 *
 * The runners are passed as `() -> T` rather than a DI provider type, so this stays plain Kotlin and
 * the module keeps no dependency on a DI library. Each shell hands it `{ provider() }`.
 */
class LazyCalculationExecutor(
    private val scope: CoroutineScope,
    private val aapsLogger: AAPSLogger,
    private val prepare: () -> PrepareGraphDataRunner,
    private val post: () -> PostCalculationRunner
) : CalculationExecutor {

    private val delegate: CoroutineCalculationExecutor by lazy {
        CoroutineCalculationExecutor(scope, aapsLogger, prepare(), post())
    }

    override fun start(job: String, generation: Long, runPost: Boolean) = delegate.start(job, generation, runPost)

    override fun startPostOnly(job: String, generation: Long) = delegate.startPostOnly(job, generation)

    override suspend fun stop(job: String, from: String) = delegate.stop(job, from)

    override suspend fun waitForPrepare(job: String, reason: String) = delegate.waitForPrepare(job, reason)
}
