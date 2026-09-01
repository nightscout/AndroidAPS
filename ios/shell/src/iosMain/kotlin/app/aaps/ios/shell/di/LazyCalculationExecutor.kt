package app.aaps.ios.shell.di

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.workflow.CalculationExecutor
import app.aaps.workflow.CoroutineCalculationExecutor
import app.aaps.workflow.PostCalculationRunner
import app.aaps.workflow.PrepareGraphDataRunner
import dev.zacsweers.metro.Provider
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
 * runners itself - they are not graph nodes there at all. iOS has no such framework, so the runners
 * are ordinary injected objects and the loop closes.
 *
 * Deferring is safe here rather than merely convenient. Nothing is looked up while the graph is
 * built; the first lookup happens when a calculation is actually started, by which time every
 * object in the loop exists. The alternative was to change `CoroutineCalculationExecutor` to take
 * providers, which would put an iOS-only concern into shared code for no gain.
 */
internal class LazyCalculationExecutor(
    private val scope: CoroutineScope,
    private val aapsLogger: AAPSLogger,
    private val prepare: Provider<PrepareGraphDataRunner>,
    private val post: Provider<PostCalculationRunner>
) : CalculationExecutor {

    private val delegate: CoroutineCalculationExecutor by lazy {
        CoroutineCalculationExecutor(scope, aapsLogger, prepare(), post())
    }

    override fun start(job: String, generation: Long, runPost: Boolean) = delegate.start(job, generation, runPost)

    override fun startPostOnly(job: String, generation: Long) = delegate.startPostOnly(job, generation)

    override suspend fun stop(job: String, from: String) = delegate.stop(job, from)

    override suspend fun waitForPrepare(job: String, reason: String) = delegate.waitForPrepare(job, reason)
}
