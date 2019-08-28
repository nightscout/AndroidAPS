package info.nightscout.androidaps.plugins.iob.iobCobCalculator.events

import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.events.EventLoop

class EventAutosensCalculationFinished(var cause: Event) : EventLoop()
