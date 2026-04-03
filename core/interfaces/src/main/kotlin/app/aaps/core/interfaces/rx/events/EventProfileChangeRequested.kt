package app.aaps.core.interfaces.rx.events

/**
 * Fired when a pump driver or keep-alive check detects that the pump's
 * basal profile does not match the requested profile and needs to be updated.
 */
class EventProfileChangeRequested : Event()
