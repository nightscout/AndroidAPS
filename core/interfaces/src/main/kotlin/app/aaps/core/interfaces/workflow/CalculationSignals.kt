package app.aaps.core.interfaces.workflow

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Signals emitted by the calculation workflow for a single scope
 * (e.g. the live overview pipeline or the history browser pipeline).
 *
 * Consumers subscribe to the instance injected for their scope and never
 * see signals from other scopes — there is no job discriminator on payloads.
 */
interface CalculationSignals {

    /** Emitted when a graph-level redraw is needed. */
    val graphUpdates: SharedFlow<GraphUpdateSignal>

    /** Final percent (0..100) of the current calculation. 100 = idle. */
    val progress: StateFlow<Int>
}

/**
 * Write side of [CalculationSignals]. Passed to workers through their
 * input data so they can emit into the scope that owns them.
 */
interface CalculationSignalsEmitter : CalculationSignals {

    fun emitGraphUpdate(from: String)

    fun emitProgress(pass: CalculationWorkflow.ProgressData, pct: Int)
}

data class GraphUpdateSignal(val from: String)
