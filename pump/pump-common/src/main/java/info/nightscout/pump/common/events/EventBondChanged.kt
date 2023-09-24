package info.nightscout.pump.common.events

import app.aaps.interfaces.rx.events.Event

class EventBondChanged(
    var connectionAddress: String,
    var bondStatus: Boolean
) : Event()
