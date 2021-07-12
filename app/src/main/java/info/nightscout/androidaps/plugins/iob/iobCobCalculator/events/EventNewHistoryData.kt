package info.nightscout.androidaps.plugins.iob.iobCobCalculator.events

import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.events.Event

class EventNewHistoryData(val oldDataTimestamp: Long, val reloadBgData: Boolean, val newestGlucoseValue : GlucoseValue? = null) : Event()