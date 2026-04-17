package app.aaps.core.objects.workflow

import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.interfaces.workflow.GraphUpdateSignal
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class CalculationSignalsImpl : CalculationSignalsEmitter {

    private val _graphUpdates = MutableSharedFlow<GraphUpdateSignal>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val graphUpdates: SharedFlow<GraphUpdateSignal> = _graphUpdates.asSharedFlow()

    private val _progress = MutableStateFlow(100)
    override val progress: StateFlow<Int> = _progress.asStateFlow()

    override fun emitGraphUpdate(from: String) {
        _graphUpdates.tryEmit(GraphUpdateSignal(from))
    }

    override fun emitProgress(pass: CalculationWorkflow.ProgressData, pct: Int) {
        _progress.value = pass.finalPercent(pct)
    }
}
