package app.aaps.plugins.aps.loop.events

import app.aaps.core.interfaces.rx.events.EventUpdateGui

/**
 * Created by mike on 05.08.2016.
 */
data class EventLoopSetLastRunGui(val text: String) : EventUpdateGui()
