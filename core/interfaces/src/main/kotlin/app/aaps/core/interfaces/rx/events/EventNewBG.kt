package app.aaps.core.interfaces.rx.events

/**
 * Fired when a new blood glucose value is received.
 *
 * @param glucoseValueTimestamp The timestamp of the new blood glucose value, or null if not available.
 */
class EventNewBG(val glucoseValueTimestamp: Long?) : EventLoop()