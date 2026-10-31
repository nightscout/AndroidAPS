package app.aaps.workflow

import app.aaps.core.objects.workflow.WorkOutcome

/**
 * One phase of the calculation chain, as [CoroutineCalculationExecutor] runs it.
 *
 * [PrepareGraphDataRunner] and [PostCalculationRunner] are the two phases. The executor needs only
 * this much of them, and that lets its tests run on every platform with plain fakes, iOS included -
 * the executor is shared code on the loop's path.
 */
fun interface CalculationPhase {

    /**
     * Runs the phase for [job] and the chain [generation]. [isStopped] is asked between steps, so a
     * replaced or stopped run gives up promptly.
     */
    suspend fun run(job: String, generation: Long, isStopped: () -> Boolean): WorkOutcome
}
