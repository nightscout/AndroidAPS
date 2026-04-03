package app.aaps.core.interfaces.rx.events

import app.aaps.core.interfaces.workflow.CalculationWorkflow

class EventIobCalculationProgress(val pass: CalculationWorkflow.ProgressData, private val progressPct: Int, val triggeredByNewBG: Boolean) : Event() {

    override fun toString(): String =
        "EventIobCalculationProgress[triggeredByNewBG=$triggeredByNewBG,pass=$pass,progressPct=$progressPct,finalPercent=$finalPercent]"

    val finalPercent get() = pass.finalPercent(progressPct)
}
