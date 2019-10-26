package info.nightscout.androidaps.events

import info.nightscout.androidaps.db.BgReading

class EventNewBG(val bgReading: BgReading?) : EventLoop()