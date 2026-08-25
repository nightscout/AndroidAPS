package app.aaps.workflow

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.workflow.CalculationWorkflow.Companion.HISTORY_CALCULATION
import app.aaps.core.interfaces.workflow.CalculationWorkflow.Companion.MAIN_CALCULATION
import app.aaps.core.interfaces.workflow.CalculationWorkflow.Companion.UPDATE_PREDICTIONS
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the input data for in-flight calculation chains.
 *
 * Three fixed slots, one per WorkManager unique-work name:
 *  - MAIN_CALCULATION → prepare + post
 *  - HISTORY_CALCULATION → prepare only
 *  - UPDATE_PREDICTIONS → post only
 *
 * Replace-on-start semantics: when a new chain is started under a given name,
 * its slot is overwritten atomically. Cancellation by WorkManager (REPLACE policy)
 * can therefore never orphan input data — the next runCalculation just overwrites
 * whatever was there. Map size is bounded by the slot count (≤3 references).
 *
 * Workers identify their slot via [JOB_KEY] in their input [androidx.work.Data];
 * the chain owner ([CalculationWorkflowImpl]) sets it when enqueueing.
 *
 * Each [startMain]/[startHistory]/[startPredictions] increments a monotonic
 * generation counter and stores it alongside the chain data. The chain owner
 * plumbs that generation into [androidx.work.Data] via [GEN_KEY]. Workers pass
 * their generation to [prepareFor]/[postFor] which returns null when the
 * generation no longer matches — closing the race where a tail worker from a
 * superseded chain dispatches in the narrow window between slot overwrite and
 * WorkManager's REPLACE-cancel taking effect.
 */
@Singleton
class WorkflowChainData @Inject constructor(
    private val aapsLogger: AAPSLogger
) {

    private sealed interface ChainSlot {
        val generation: Long
    }

    private data class MainChain(
        override val generation: Long,
        val prepare: PrepareGraphDataWorker.PrepareGraphData,
        val post: PostCalculationWorker.PostCalculationData
    ) : ChainSlot

    private data class HistoryChain(
        override val generation: Long,
        val prepare: PrepareGraphDataWorker.PrepareGraphData
    ) : ChainSlot

    private data class PredictionsChain(
        override val generation: Long,
        val post: PostCalculationWorker.PostCalculationData
    ) : ChainSlot

    @Volatile private var mainChain: MainChain? = null
    @Volatile private var historyChain: HistoryChain? = null
    @Volatile private var predictionsChain: PredictionsChain? = null
    private val generator = AtomicLong()

    // @Synchronized: `incrementAndGet` + slot write must be atomic together. Otherwise two
    // concurrent callers can interleave so the slot ends up holding the older generation
    // while WorkManager runs work tagged with the newer one, causing the worker's gen check
    // to fail and the calculation to silently drop. Lock contention is negligible — startX
    // is on the fast path, called at most once per BG cycle.
    @Synchronized
    fun startMain(
        prepare: PrepareGraphDataWorker.PrepareGraphData,
        post: PostCalculationWorker.PostCalculationData
    ): Long {
        val gen = generator.incrementAndGet()
        mainChain = MainChain(gen, prepare, post)
        return gen
    }

    @Synchronized
    fun startHistory(prepare: PrepareGraphDataWorker.PrepareGraphData): Long {
        val gen = generator.incrementAndGet()
        historyChain = HistoryChain(gen, prepare)
        return gen
    }

    @Synchronized
    fun startPredictions(post: PostCalculationWorker.PostCalculationData): Long {
        val gen = generator.incrementAndGet()
        predictionsChain = PredictionsChain(gen, post)
        return gen
    }

    // Each safe-call performs a single volatile read of the slot reference and
    // dereferences the snapshot — no double load, no torn reads possible.
    fun prepareFor(job: String?, expectedGen: Long): PrepareGraphDataWorker.PrepareGraphData? =
        when (job) {
            MAIN_CALCULATION    -> mainChain.validate(expectedGen, job)?.prepare
            HISTORY_CALCULATION -> historyChain.validate(expectedGen, job)?.prepare
            else                -> warnUnknown("prepareFor", job)
        }

    // HISTORY_CALCULATION has no post phase — only MAIN and UPDATE_PREDICTIONS enqueue
    // PostCalculationWorker. A HISTORY job key reaching this method would be a wiring bug.
    fun postFor(job: String?, expectedGen: Long): PostCalculationWorker.PostCalculationData? =
        when (job) {
            MAIN_CALCULATION   -> mainChain.validate(expectedGen, job)?.post
            UPDATE_PREDICTIONS -> predictionsChain.validate(expectedGen, job)?.post
            else               -> warnUnknown("postFor", job)
        }

    private fun <T : ChainSlot> T?.validate(expectedGen: Long, job: String): T? {
        val slot = this ?: return null
        if (slot.generation != expectedGen) {
            aapsLogger.warn(
                LTag.WORKER,
                "WorkflowChainData: stale gen for '$job' (expected=$expectedGen current=${slot.generation})"
            )
            return null
        }
        return slot
    }

    private fun warnUnknown(method: String, job: String?): Nothing? {
        aapsLogger.warn(LTag.WORKER, "WorkflowChainData.$method: unknown job key '$job'")
        return null
    }

    companion object {

        const val JOB_KEY = "job"
        const val GEN_KEY = "gen"
    }
}
