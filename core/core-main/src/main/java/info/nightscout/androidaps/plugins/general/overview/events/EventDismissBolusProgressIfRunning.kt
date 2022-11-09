package info.nightscout.androidaps.plugins.general.overview.events

import info.nightscout.interfaces.data.PumpEnactResult
import info.nightscout.rx.events.Event

class EventDismissBolusProgressIfRunning(val result: PumpEnactResult?, val id: Long?) : Event()