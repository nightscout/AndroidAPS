package info.nightscout.core.events

import info.nightscout.core.workflow.CalculationWorkflow
import info.nightscout.rx.events.Event

class EventIobCalculationProgress(val pass: CalculationWorkflow.ProgressData, private val progressPct: Int, val cause: Event?) : Event() {

    override fun toString(): String =
        "EventIobCalculationProgress[cause=$cause,pass=$pass,progressPct=$progressPct,finalPercent=$finalPercent]"

    val finalPercent get() = pass.finalPercent(progressPct)
}