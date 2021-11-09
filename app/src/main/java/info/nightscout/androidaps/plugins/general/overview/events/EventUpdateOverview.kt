package info.nightscout.androidaps.plugins.general.overview.events

import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.plugins.general.overview.OverviewData

class EventUpdateOverview(val from: String, val what: OverviewData.Property) : Event()