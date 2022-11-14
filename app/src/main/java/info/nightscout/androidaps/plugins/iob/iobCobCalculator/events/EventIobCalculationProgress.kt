package info.nightscout.androidaps.plugins.iob.iobCobCalculator.events

import info.nightscout.androidaps.workflow.CalculationWorkflow
import info.nightscout.rx.events.Event

class EventIobCalculationProgress(val pass: CalculationWorkflow.ProgressData, val progressPct: Int, val cause: Event?) : Event()