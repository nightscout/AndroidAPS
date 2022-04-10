package info.nightscout.androidaps.plugins.iob.iobCobCalculator.events

import info.nightscout.androidaps.events.Event

class EventIobCalculationProgress(val progress: String, val cause: Event?) : Event()