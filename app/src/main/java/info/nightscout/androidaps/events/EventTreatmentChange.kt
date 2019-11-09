package info.nightscout.androidaps.events

import info.nightscout.androidaps.plugins.treatments.Treatment

class EventTreatmentChange(val treatment: Treatment?) : EventLoop()
