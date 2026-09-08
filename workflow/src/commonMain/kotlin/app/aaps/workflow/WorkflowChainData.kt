package app.aaps.workflow

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.workflow.CalculationWorkflow.Companion.HISTORY_CALCULATION
import app.aaps.core.interfaces.workflow.CalculationWorkflow.Companion.MAIN_CALCULATION
import app.aaps.core.interfaces.workflow.CalculationWorkflow.Companion.UPDATE_PREDICTIONS
import app.aaps.workflow.WorkflowChainData.Companion.GEN_KEY
import app.aaps.workflow.WorkflowChainData.Companion.JOB_KEY

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

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
@OptIn(ExperimentalAtomicApi::class)
@SingleIn(AppScope::class)
class WorkflowChainData @Inject constructor(
    private val aapsLogger: AAPSLogger
) {

    private sealed interface ChainSlot {
        val generation: Long
    }

    private data class MainChain(
        override val generation: Long,
        val prepare: PrepareGraphData,
        val post: PostCalculationData
    ) : ChainSlot

    private data class HistoryChain(
        override val generation: Long,
        val prepare: PrepareGraphData
    ) : ChainSlot

    private data class PredictionsChain(
        override val generation: Long,
        val post: PostCalculationData
    ) : ChainSlot

    /**
     * All three slots and the generation counter in one atomic value.
     *
     * They are one value on purpose. The invariant is that a slot always holds the generation the
     * work was tagged with, and that needs the counter bump and the slot write to happen together.
     * A lock did that before, but `@Synchronized` is JVM only. A single `updateAndGet` is atomic by
     * construction, so the invariant survives and the class compiles for every target.
     */
    private data class Chains(
        val generation: Long = 0L,
        val main: MainChain? = null,
        val history: HistoryChain? = null,
        val predictions: PredictionsChain? = null
    )

    private val chains = AtomicReference(Chains())

    /**
     * Bumps the generation and writes the slot as one atomic step, retrying on contention.
     *
     * [withGen] is called again on every retry, so it must only build a new value from the old one.
     */
    private inline fun bump(withGen: (Chains, Long) -> Chains): Long {
        while (true) {
            val old = chains.load()
            val gen = old.generation + 1
            if (chains.compareAndSet(old, withGen(old, gen))) return gen
        }
    }

    fun startMain(prepare: PrepareGraphData, post: PostCalculationData): Long =
        bump { old, gen -> old.copy(generation = gen, main = MainChain(gen, prepare, post)) }

    fun startHistory(prepare: PrepareGraphData): Long =
        bump { old, gen -> old.copy(generation = gen, history = HistoryChain(gen, prepare)) }

    fun startPredictions(post: PostCalculationData): Long =
        bump { old, gen -> old.copy(generation = gen, predictions = PredictionsChain(gen, post)) }

    // Each safe-call performs a single volatile read of the slot reference and
    // dereferences the snapshot — no double load, no torn reads possible.
    fun prepareFor(job: String?, expectedGen: Long): PrepareGraphData? =
        when (job) {
            MAIN_CALCULATION    -> chains.load().main.validate(expectedGen, job)?.prepare
            HISTORY_CALCULATION -> chains.load().history.validate(expectedGen, job)?.prepare
            else                -> warnUnknown("prepareFor", job)
        }

    // HISTORY_CALCULATION has no post phase — only MAIN and UPDATE_PREDICTIONS enqueue
    // PostCalculationWorker. A HISTORY job key reaching this method would be a wiring bug.
    fun postFor(job: String?, expectedGen: Long): PostCalculationData? =
        when (job) {
            MAIN_CALCULATION   -> chains.load().main.validate(expectedGen, job)?.post
            UPDATE_PREDICTIONS -> chains.load().predictions.validate(expectedGen, job)?.post
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
