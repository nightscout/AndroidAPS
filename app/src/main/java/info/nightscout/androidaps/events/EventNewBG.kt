package info.nightscout.androidaps.events

import info.nightscout.androidaps.database.entities.GlucoseValue

class EventNewBG(val glucoseValue: GlucoseValue?) : EventLoop()