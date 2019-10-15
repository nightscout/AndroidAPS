package info.nightscout.androidaps.plugins.general.overview.events

import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.events.Event

class EventDismissBolusprogressIfRunning(val result: PumpEnactResult) : Event()