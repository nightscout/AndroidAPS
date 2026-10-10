package app.aaps.core.interfaces.rx.events

data class EventAutosensCalculationFinished(val triggeredByNewBG: Boolean) : EventLoop()
