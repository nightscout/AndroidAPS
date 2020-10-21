package info.nightscout.androidaps.plugins.general.overview.events

import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.events.Event

class EventDismissBolusProgressIfRunning(val result: PumpEnactResult?) : Event()