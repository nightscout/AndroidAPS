package info.nightscout.androidaps.plugins.iob.iobCobCalculator.events

import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.workflow.CalculationWorkflow

class EventIobCalculationProgress(val pass: CalculationWorkflow.ProgressData, val progressPct: Int, val cause: Event?) : Event()