package info.nightscout.pump.common.events

import app.aaps.core.interfaces.rx.events.Event

class EventBondChanged(
    var connectionAddress: String,
    var bondStatus: Boolean
) : Event()
