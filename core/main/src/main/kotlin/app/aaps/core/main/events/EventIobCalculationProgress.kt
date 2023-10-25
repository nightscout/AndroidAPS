package app.aaps.core.main.events

import app.aaps.core.main.workflow.CalculationWorkflow
import app.aaps.core.interfaces.rx.events.Event

class EventIobCalculationProgress(val pass: CalculationWorkflow.ProgressData, private val progressPct: Int, val cause: Event?) : Event() {

    override fun toString(): String =
        "EventIobCalculationProgress[cause=$cause,pass=$pass,progressPct=$progressPct,finalPercent=$finalPercent]"

    val finalPercent get() = pass.finalPercent(progressPct)
}