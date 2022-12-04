package info.nightscout.core.events

import info.nightscout.core.workflow.CalculationWorkflow
import info.nightscout.rx.events.Event

class EventIobCalculationProgress(val pass: CalculationWorkflow.ProgressData, val progressPct: Int, val cause: Event?) : Event()