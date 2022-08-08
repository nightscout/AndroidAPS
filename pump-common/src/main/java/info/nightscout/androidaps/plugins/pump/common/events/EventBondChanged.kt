package info.nightscout.androidaps.plugins.pump.common.events

import info.nightscout.androidaps.events.Event

class EventBondChanged(
    var connectionAddress: String,
    var bondStatus: Boolean
) : Event()
