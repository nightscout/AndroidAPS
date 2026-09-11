package app.aaps.core.objects.workflow

import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CalculationSignalsImpl : CalculationSignalsEmitter {

    private val _progress = MutableStateFlow(100)
    override val progress: StateFlow<Int> = _progress.asStateFlow()

    override fun emitProgress(pass: CalculationWorkflow.ProgressData, pct: Int) {
        _progress.value = pass.finalPercent(pct)
    }
}
