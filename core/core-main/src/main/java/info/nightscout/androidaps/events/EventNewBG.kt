package info.nightscout.androidaps.events

import info.nightscout.database.entities.GlucoseValue
import info.nightscout.rx.events.EventLoop

class EventNewBG(val glucoseValue: GlucoseValue?) : EventLoop()