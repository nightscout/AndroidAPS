package info.nightscout.androidaps.events

import info.nightscout.androidaps.db.Treatment

class EventTreatmentChange(val treatment: Treatment?) : EventLoop()
