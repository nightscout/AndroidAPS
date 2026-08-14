package app.aaps.core.interfaces.rx.events

/**
 * Fired when a pump driver or keep-alive check detects that the pump's
 * basal profile does not match the requested profile and needs to be updated.
 *
 * @param silent when true, the resulting pump write must NOT raise the user-facing
 *   "Basal profile in pump updated" notification (PROFILE_SET_OK). Used by a Scene reverting its
 *   own ProfileSwitch on end — an internal, automatic write the user shouldn't have to acknowledge
 *   (issue #4959). Defaults to false so every existing sender keeps its current behavior.
 */
class EventProfileChangeRequested(val silent: Boolean = false) : Event()
